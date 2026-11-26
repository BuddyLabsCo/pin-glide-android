import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { checkVersion, isStableVersion, readChangelog } from "./version-policy.mjs";

export const artifactName = "production-release-candidate";
export const candidateJob = "package and publish";
export const candidateStep = "Verify and record candidate";
const marker = "\n\n<!-- verified-candidate: ";
const stableTag = tag => typeof tag === "string" && tag.startsWith("v") && isStableVersion(tag.slice(1));
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

export async function reserveCandidate(api, identity, runId, state) {
  assert.match(runId, /^[1-9]\d*$/, "Invalid candidate run ID");
  assert.ok(!state.tag && !state.release, "Candidate version is already reserved");
  const tag = await api.request("POST", "/git/tags", {
    tag: identity.tag, message: `Production candidate run ${runId}`,
    object: identity.commit, type: "commit",
    tagger: { name: "github-actions[bot]", email: "41898282+github-actions[bot]@users.noreply.github.com" },
  });
  assert.match(tag.sha, /^[a-f0-9]{40}$/);
  await api.request("POST", "/git/refs", { ref: `refs/tags/${identity.tag}`, sha: tag.sha });
}

export async function candidateRun(api, identity, tag) {
  if (!tag) return undefined;
  const ref = await api.request("GET", `/git/ref/tags/${identity.tag}`);
  assert.equal(ref.object.type, "tag", "Missing durable production candidate reservation");
  assert.match(ref.object.sha, /^[a-f0-9]{40}$/);
  const reservation = await api.request("GET", `/git/tags/${ref.object.sha}`);
  assert.equal(reservation.tag, identity.tag, "Conflicting candidate reservation tag");
  assert.equal(reservation.object.type, "commit");
  assert.equal(reservation.object.sha, identity.commit, "Conflicting candidate reservation commit");
  const runId = /^Production candidate run ([1-9]\d*)\n?$/.exec(reservation.message)?.[1];
  assert.ok(runId, "Missing production candidate reservation run");
  return runId;
}

export async function recoverPublication(api, identity, state, runId, runAttempt, directory, download, expected, verify) {
  assert.match(runId, /^[1-9]\d*$/);
  assert.match(runAttempt, /^[1-9]\d*$/);
  const owner = await candidateRun(api, identity, state.tag);
  const sourceRun = owner ?? runId;
  const artifacts = await api.list(`/actions/runs/${sourceRun}/artifacts`, "artifacts");
  let priorCandidateStep = false;
  if (!owner && !artifacts.some(artifact => artifact.name === artifactName)) {
    for (let attempt = 1; attempt < Number(runAttempt); attempt++) {
      const jobs = await api.list(`/actions/runs/${runId}/attempts/${attempt}/jobs`, "jobs");
      priorCandidateStep ||= jobs.some(job => job.name === candidateJob &&
        job.steps?.some(step => step.name === candidateStep && step.started_at && step.conclusion !== "skipped"));
    }
  }
  const plan = recoveryPlan(artifacts, priorCandidateStep, Boolean(state.tag || state.release));
  if (plan === "recover") {
    await download(sourceRun, artifactName, directory);
    const candidate = readCandidate(directory, identity, expected, verify);
    if (state.recorded) assert.deepEqual(candidate, state.recorded, "Release conflicts with retained candidate");
    if (!owner) await reserveCandidate(api, identity, sourceRun, state);
  }
  const baseline = state.baseline;
  return { plan, baseline: baseline ? `${baseline.versionName}:${baseline.versionCode}` : "none" };
}

function releaseBody(identity, candidate) {
  return `${identity.notes}${marker}${JSON.stringify(candidate)} -->`;
}

function recordedCandidate(release, identity) {
  const prefix = `${identity.notes}${marker}`;
  assert.ok(release.body?.startsWith(prefix) && release.body.endsWith(" -->"),
    "Conflicting Release notes or missing candidate metadata");
  const recorded = JSON.parse(release.body.slice(prefix.length, -4));
  assert.match(recorded.sha256, /^[0-9a-f]{64}$/, "Invalid recorded candidate digest");
  assert.deepEqual(recorded, metadata(identity, recorded, recorded.sha256), "Conflicting Release candidate identity");
  return recorded;
}

export async function inspectPublication(api, identity, gradle, changelog, readVersionAt, approvedSigner) {
  const releases = await api.list("/releases");
  const tags = (await api.list("/tags")).filter(tag => stableTag(tag.name));
  const tag = tags.find(tag => tag.name === identity.tag);
  if (tag) assert.equal(tag.commit.sha, identity.commit, "Conflicting release tag");
  const matching = releases.filter(release => release.tag_name === identity.tag || release.name === identity.tag);
  assert.ok(matching.length <= 1, "Duplicate release version");
  const release = matching[0];
  const published = [];
  const history = readChangelog(changelog);
  const signers = new Set();
  for (const other of releases.filter(item => item !== release)) {
    assert.ok(stableTag(other.tag_name) && other.name === other.tag_name &&
      other.draft === false && other.prerelease === false, "Conflicting or pending production Release");
    const source = tags.find(item => item.name === other.tag_name);
    assert.ok(source, "Published release tag missing");
    const version = readVersionAt(source.commit.sha);
    assert.equal(other.tag_name, `v${version.versionName}`, "Published release version mismatch");
    assert.equal(history.find(entry => entry.versionCode === version.versionCode)?.section,
      version.section, "Published changelog section changed or missing");
    signers.add(recordedCandidate(other, version).signerSha256);
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
  }
  const baseline = published.at(-1) ?? null;
  checkVersion(gradle, changelog, baseline);
  let recorded;
  if (release) {
    assert.ok(tag && release.name === identity.tag && release.tag_name === identity.tag &&
      release.prerelease === false && typeof release.draft === "boolean", "Conflicting Release identity");
    recorded = recordedCandidate(release, identity);
    signers.add(recorded.signerSha256);
  }
  if (approvedSigner !== undefined) signers.add(approvedSigner);
  assert.ok(signers.size <= 1, "Production signer differs from published release identity");
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
  assert.ok(state.tag, "Missing durable production candidate reservation");
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
