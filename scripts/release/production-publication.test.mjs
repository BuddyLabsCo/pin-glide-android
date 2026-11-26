import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import {
  candidateFiles, inspectPublication, publishCandidate, readCandidate, recordCandidate, recoveryPlan, reserveCandidate,
} from "./production-publication.mjs";
import { releaseIdentity } from "./production-policy.mjs";
import { proposeRelease } from "./version-policy.mjs";

const gradle = `android {\n  defaultConfig {\n    versionCode = 1\n    versionName = "0.1.0"\n  }\n}\n`;
const changelog = "# Changelog\n\n## 0.1.0 (versionCode 1)\n\n- Initial release.\n";
const identity = releaseIdentity("a".repeat(40), gradle, changelog);
const expected = { signerSha256: "b".repeat(64), qqAppId: "123" };

function verifyFixture(apk) {
  assert.equal(readFileSync(apk, "utf8"), "verified bytes");
}

test("candidate binds the APK, digest, source version, package and approved signer", t => {
  const directory = mkdtempSync(join(tmpdir(), "candidate-test-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const [apk, digest, metadata] = candidateFiles(identity);
  writeFileSync(join(directory, apk), "verified bytes");
  const verify = t.mock.fn(verifyFixture);
  const reject = () => { throw new Error("APK verification rejected"); };
  assert.throws(() => recordCandidate(directory, identity, expected, reject), /verification rejected/);
  assert.equal(existsSync(join(directory, metadata)), false);
  const candidate = recordCandidate(directory, identity, expected, verify);
  assert.equal(candidate.packageId, "com.buddylabs.pinglide");
  assert.equal(candidate.commit, identity.commit);
  assert.equal(candidate.signerSha256, expected.signerSha256);
  assert.equal(candidate.versionCode, identity.versionCode);
  assert.deepEqual(readCandidate(directory, identity, expected, verify), candidate);
  assert.equal(verify.mock.callCount(), 2);
  assert.throws(() => readCandidate(directory, identity, expected, reject), /verification rejected/);
  assert.throws(() => readCandidate(directory, { ...identity, commit: "c".repeat(40) }, expected, verify), /identity/);
  assert.throws(() => readCandidate(directory, identity, { ...expected, signerSha256: "c".repeat(64) }, verify), /identity/);
  writeFileSync(join(directory, digest), "wrong digest");
  assert.throws(() => readCandidate(directory, identity, expected, verify), /digest/i);
  writeFileSync(join(directory, digest), `${candidate.sha256}  ${apk}\n`);
  writeFileSync(join(directory, apk), "altered bytes");
  assert.throws(() => readCandidate(directory, identity, expected, verify), /digest/i);
  assert.ok(readFileSync(join(directory, metadata), "utf8").includes(candidate.sha256));
});

test("retry never rebuilds if a candidate could already have been created", () => {
  assert.equal(recoveryPlan([], false, false), "build");
  assert.equal(recoveryPlan([{ name: "production-release-candidate", expired: false }], true, true), "recover");
  assert.throws(() => recoveryPlan([], true, false), /recovery/i);
  assert.throws(() => recoveryPlan([], false, true), /recovery/i);
  assert.throws(() => recoveryPlan([{ name: "production-release-candidate", expired: true }], false, false), /recovery/i);
  assert.throws(() => recoveryPlan([
    { name: "production-release-candidate", expired: false },
    { name: "production-release-candidate", expired: false },
  ], false, false), /recovery/i);
});

function releaseStore() {
  const releases = [];
  const tags = [];
  const assets = [];
  let interrupted = false;
  let nextId = 1;
  let reservation;
  return {
    releases, tags, assets,
    async list(path) {
      if (path === "/releases") return releases;
      if (path === "/tags") return tags;
      assert.equal(path, "/releases/1/assets");
      return assets;
    },
    async request(method, path, body) {
      if (method === "POST" && path === "/git/tags") {
        reservation = { ...body, sha: "f".repeat(40) };
        return reservation;
      } else if (method === "POST" && path === "/git/refs") {
        assert.equal(body.sha, reservation.sha);
        tags.push({ name: body.ref.slice("refs/tags/".length), commit: { sha: reservation.object } });
      } else if (method === "POST" && path === "/releases") {
        releases.push({ id: 1, ...body });
      } else if (method === "PATCH" && path === "/releases/1") {
        Object.assign(releases[0], body);
      } else if (method === "DELETE" && path.startsWith("/releases/assets/")) {
        const index = assets.findIndex(asset => path.endsWith(`/${asset.id}`));
        assert.equal(assets[index].state, "starter", "Never delete a completed upload");
        assets.splice(index, 1);
      } else assert.fail(`Unexpected request: ${method} ${path}`);
    },
    async upload(id, name, bytes) {
      assert.equal(id, 1);
      assert.ok(!assets.some(asset => asset.name === name), "Never replace an existing asset");
      if (name.endsWith(".sha256") && !interrupted) {
        interrupted = true;
        assets.push({ id: nextId++, name, state: "starter", size: 0 });
        throw new Error("Interrupted upload");
      }
      assets.push({
        id: nextId++, name, state: "uploaded", size: bytes.length,
        digest: `sha256:${createHash("sha256").update(bytes).digest("hex")}`,
      });
    },
  };
}

test("partial publication resumes with exact bytes and never overwrites conflicting assets", async t => {
  const directory = mkdtempSync(join(tmpdir(), "publication-test-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  writeFileSync(join(directory, identity.filename), "verified bytes");
  const api = releaseStore();
  const inspect = () => inspectPublication(api, identity, gradle, changelog, () => assert.fail());
  assert.equal((await inspect()).baseline, null);
  await reserveCandidate(api, identity, "12", await inspect());
  const candidate = recordCandidate(directory, identity, expected, verifyFixture);
  await assert.rejects(publishCandidate(api, identity, directory, candidate, inspect), /Interrupted upload/);
  assert.equal(api.releases[0].draft, true);
  assert.equal(api.assets[0].name, identity.filename);
  const original = { ...api.assets[0] };
  api.assets[0].digest = "sha256:" + "0".repeat(64);
  await assert.rejects(publishCandidate(api, identity, directory, candidate, inspect), /digest/);
  Object.assign(api.assets[0], original);
  await publishCandidate(api, identity, directory, candidate, inspect);
  assert.equal(api.releases[0].draft, false);
  assert.equal(api.tags[0].commit.sha, identity.commit);
  assert.deepEqual(api.assets[0], original);
  assert.deepEqual(api.assets.map(asset => asset.name), candidateFiles(identity));
  const published = structuredClone(api.assets);
  await publishCandidate(api, identity, directory, candidate, inspect);
  assert.deepEqual(api.assets, published);
  assert.equal(api.releases.length, 1);
});

test("version baseline comes from published tags, not the current proposal", async t => {
  const api = releaseStore();
  const previous = { versionName: "0.1.0", versionCode: 1 };
  const proposal = proposeRelease(gradle, changelog, previous, "- Next release.");
  const next = releaseIdentity("c".repeat(40), proposal.gradle, proposal.changelog);
  const readVersionAt = sha => {
    assert.equal(sha, identity.commit);
    return identity;
  };
  const inspect = () => inspectPublication(api, next, proposal.gradle, proposal.changelog, readVersionAt);
  await assert.rejects(inspect(), /First production release/);
  api.tags.push({ name: identity.tag, commit: { sha: identity.commit } });
  const directory = mkdtempSync(join(tmpdir(), "previous-release-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  writeFileSync(join(directory, identity.filename), "verified bytes");
  const candidate = recordCandidate(directory, identity, expected, verifyFixture);
  api.releases.push({
    name: identity.tag, tag_name: identity.tag, draft: false, prerelease: false,
    body: `${identity.notes}\n\n<!-- verified-candidate: ${JSON.stringify(candidate)} -->`,
  });
  assert.deepEqual((await inspect()).baseline, identity);
  await assert.rejects(inspectPublication(api, next, proposal.gradle,
    proposal.changelog.replace("Initial release.", "Rewritten notes."), readVersionAt), /changelog section changed/i);
  await assert.rejects(inspectPublication(api, next, proposal.gradle,
    proposal.changelog, readVersionAt, "d".repeat(64)), /signer differs/);
  assert.deepEqual((await inspectPublication(api, next, proposal.gradle,
    proposal.changelog, readVersionAt, expected.signerSha256)).baseline, identity);
  api.tags.push({ name: next.tag, commit: { sha: identity.commit } });
  await assert.rejects(inspect(), /Conflicting release tag/);
  api.tags.pop();
  api.tags.push({ name: "v0.2.0", commit: { sha: "d".repeat(40) } });
  await assert.rejects(inspect(), /Unpublished conflicting stable tag/);
  api.tags.pop();
  api.releases.push({ ...api.releases[0] });
  await assert.rejects(inspect(), /Duplicate published versionCode/);
});
