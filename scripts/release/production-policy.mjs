import assert from "node:assert/strict";
import { checkVersion, readChangelog } from "./version-policy.mjs";

export function requireAuthorizedMerge(pr, repository) {
  assert.ok(pr?.merged === true && pr.merged_by?.type === "User" && !pr.auto_merge && repository &&
    pr.head?.ref === "auto-release" && pr.head.repo?.full_name === repository &&
    pr.base?.ref === "main" && pr.base.repo?.full_name === repository,
  "Unauthorized production release merge");
  assert.match(pr.merge_commit_sha, /^[0-9a-f]{40}$/, "Invalid release merge SHA");
  return pr.merge_commit_sha;
}

export function authorizedCommit(event, eventName, repository, workflowSha, checkout) {
  assert.ok(eventName === "pull_request" && event.action === "closed", "Unauthorized production release event");
  const commit = requireAuthorizedMerge(event.pull_request, repository);
  assert.ok(commit === workflowSha && commit === checkout,
    "Merge SHA mismatch: authorization, workflow and checkout must agree");
  return commit;
}

export function readVersionAt(git, commit, ancestorOf) {
  assert.match(commit, /^[0-9a-f]{40}$/);
  assert.equal(git("merge-base", commit, ancestorOf), commit, "Published release is not an ancestor of checkout");
  return releaseIdentity(commit, git("show", `${commit}:app/build.gradle.kts`), git("show", `${commit}:CHANGELOG.md`));
}

export function releaseIdentity(commit, gradle, changelog) {
  const version = checkVersion(gradle, changelog);
  const { notes, section } = readChangelog(changelog)[0];
  assert.ok(!notes.includes("<!-- verified-candidate:"), "Reserved release metadata marker");
  return {
    commit, ...version, tag: `v${version.versionName}`, notes, section,
    filename: `pin-glide-${version.versionName}.apk`,
  };
}
