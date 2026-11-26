import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { checkVersion } from "./version-policy.mjs";

export const artifactName = "production-release-candidate";
const marker = "\n\n<!-- verified-candidate: ";
const stableTag = /^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;
const sha256 = bytes => createHash("sha256").update(bytes).digest("hex");

export function candidateFiles(identity) {
  return [identity.filename, `${identity.filename}.sha256`, "release-candidate.json"];
}

function metadata(identity, expected, digest) {
  assert.match(expected.signerSha256, /^[0-9a-f]{64}$/, "Invalid approved signer");
  assert.match(expected.qqAppId, /^[0-9]+$/, "Invalid production QQ App ID");
  return {
    commit: identity.commit, versionName: identity.versionName, versionCode: identity.versionCode,
    packageId: "com.buddylabs.pinglide", signerSha256: expected.signerSha256,
    qqAppId: expected.qqAppId, filename: identity.filename, sha256: digest,
  };
}

export function recordCandidate(directory, identity, expected, verify) {
  const [apk, digest, manifest] = candidateFiles(identity);
  verify(join(directory, apk));
  const candidate = metadata(identity, expected, sha256(readFileSync(join(directory, apk))));
  writeFileSync(join(directory, digest), `${candidate.sha256}  ${apk}\n`, { flag: "wx" });
  writeFileSync(join(directory, manifest), JSON.stringify(candidate, null, 2) + "\n", { flag: "wx" });
  return candidate;
}

export function readCandidate(directory, identity, expected, verify) {
  const [apk, digest, manifest] = candidateFiles(identity);
  const candidate = JSON.parse(readFileSync(join(directory, manifest), "utf8"));
  assert.deepEqual(candidate, metadata(identity, expected, candidate.sha256), "Conflicting candidate identity");
  assert.equal(sha256(readFileSync(join(directory, apk))), candidate.sha256, "Candidate APK digest mismatch");
  assert.equal(readFileSync(join(directory, digest), "utf8"), `${candidate.sha256}  ${apk}\n`,
    "Candidate digest file mismatch");
  verify(join(directory, apk));
  return candidate;
}

export function recoveryPlan(artifacts, priorCandidateStep, existingReleaseState) {
  const candidates = artifacts.filter(artifact => artifact.name === artifactName);
  assert.ok(candidates.length <= 1 && !candidates.some(artifact => artifact.expired),
    "Ambiguous or expired candidate recovery artifact");
  if (candidates.length) return "recover";
  assert.ok(!priorCandidateStep && !existingReleaseState, "Original verified candidate required for recovery");
  return "build";
}

function releaseBody(identity, candidate) {
  return `${identity.notes}${marker}${JSON.stringify(candidate)} -->`;
}

export async function inspectPublication(api, identity, gradle, changelog, readVersionAt) {
  const releases = await api.list("/releases");
  const tags = (await api.list("/tags")).filter(tag => stableTag.test(tag.name));
  const tag = tags.find(tag => tag.name === identity.tag);
  if (tag) assert.equal(tag.commit.sha, identity.commit, "Conflicting release tag");
  const matching = releases.filter(release => release.tag_name === identity.tag || release.name === identity.tag);
  assert.ok(matching.length <= 1, "Duplicate release version");
  const release = matching[0];
  const published = [];
  for (const other of releases.filter(item => item !== release)) {
    assert.ok(stableTag.test(other.tag_name) && other.name === other.tag_name &&
      other.draft === false && other.prerelease === false, "Conflicting or pending production Release");
    const source = tags.find(item => item.name === other.tag_name);
    assert.ok(source, "Published release tag missing");
    const version = readVersionAt(source.commit.sha);
    assert.equal(other.tag_name, `v${version.versionName}`, "Published release version mismatch");
    published.push(version);
  }
  assert.equal(new Set(published.map(version => version.versionCode)).size, published.length,
    "Duplicate published versionCode");
  for (const other of tags.filter(item => item !== tag)) {
    assert.ok(releases.some(item => item.tag_name === other.name), "Unpublished conflicting stable tag");
  }
  published.sort((left, right) => left.versionCode - right.versionCode);
  for (const [index, version] of published.entries()) {
    assert.equal(version.versionCode, index + 1, "Missing published versionCode");
    assert.ok(changelog.replaceAll("\r\n", "\n").split("\n").includes(
      `## ${version.versionName} (versionCode ${version.versionCode})`), "Published version missing from changelog");
  }
  const baseline = published.at(-1) ?? null;
  checkVersion(gradle, changelog, baseline);
  let recorded;
  if (release) {
    assert.ok(tag && release.name === identity.tag && release.tag_name === identity.tag &&
      release.prerelease === false && typeof release.draft === "boolean", "Conflicting Release identity");
    const prefix = `${identity.notes}${marker}`;
    assert.ok(release.body?.startsWith(prefix) && release.body.endsWith(" -->"),
      "Conflicting Release notes or missing candidate metadata");
    recorded = JSON.parse(release.body.slice(prefix.length, -4));
    assert.match(recorded.sha256, /^[0-9a-f]{64}$/, "Invalid recorded candidate digest");
    assert.deepEqual(recorded, metadata(identity, recorded, recorded.sha256), "Conflicting Release candidate identity");
  }
  return { tag, release, recorded, baseline };
}

export async function publishCandidate(api, identity, directory, candidate, inspect) {
  const files = candidateFiles(identity).map(filename => {
    const bytes = readFileSync(join(directory, filename));
    return { filename, bytes, digest: `sha256:${sha256(bytes)}` };
  });
  assert.equal(files[0].digest, `sha256:${candidate.sha256}`, "Candidate changed before publishing");
  assert.equal(files[1].bytes.toString("utf8"), `${candidate.sha256}  ${identity.filename}\n`,
    "Candidate checksum changed before publishing");
  assert.deepEqual(JSON.parse(files[2].bytes.toString("utf8")), candidate, "Candidate metadata changed before publishing");

  async function inspectAssets(state) {
    assert.deepEqual(state.recorded, candidate, "Release conflicts with retained candidate");
    const assets = await api.list(`/releases/${state.release.id}/assets`);
    const seen = new Set();
    for (const asset of assets) {
      const file = files.find(item => item.filename === asset.name);
      assert.ok(file && !seen.has(asset.name), "Conflicting Release asset name");
      seen.add(asset.name);
      const uploaded = asset.state === "uploaded" && asset.size === file.bytes.length && asset.digest === file.digest;
      const starter = state.release.draft && asset.state === "starter" && asset.size === 0;
      assert.ok(uploaded || starter, "Conflicting Release asset digest or state");
    }
    const complete = files.every(file => assets.some(asset => asset.name === file.filename && asset.state === "uploaded"));
    assert.ok(state.release.draft || complete, "Published Release has missing assets");
    return { assets, complete };
  }

  let state = await inspect();
  if (state.release) await inspectAssets(state);
  if (!state.tag) await api.request("POST", "/git/refs", { ref: `refs/tags/${identity.tag}`, sha: identity.commit });
  if (!state.release) {
    await api.request("POST", "/releases", {
      tag_name: identity.tag, target_commitish: identity.commit, name: identity.tag,
      body: releaseBody(identity, candidate), draft: true, prerelease: false,
    });
  }
  state = await inspect();
  const { assets } = await inspectAssets(state);
  if (!state.release.draft) return;
  for (const file of files) {
    const asset = assets.find(item => item.name === file.filename);
    if (asset?.state === "uploaded") continue;
    if (asset) await api.request("DELETE", `/releases/assets/${asset.id}`);
    await api.upload(state.release.id, file.filename, file.bytes);
  }
  state = await inspect();
  assert.ok((await inspectAssets(state)).complete, "Not all Release assets were verified");
  if (state.release.draft) await api.request("PATCH", `/releases/${state.release.id}`, { draft: false });
  state = await inspect();
  assert.ok(!state.release.draft && (await inspectAssets(state)).complete, "Release publication incomplete");
}
