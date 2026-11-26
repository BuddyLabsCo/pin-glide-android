import assert from "node:assert/strict";
import { checkVersion } from "./version-policy.mjs";

export function authorizedCommit(event, eventName, repository, workflowSha, checkout) {
  const pr = event.pull_request;
  assert.ok(eventName === "pull_request" && event.action === "closed" &&
    pr?.merged === true && pr.merged_by?.type === "User" && !pr.auto_merge && repository &&
    pr.head?.ref === "auto-release" && pr.head.repo?.full_name === repository &&
    pr.base?.ref === "main" && pr.base.repo?.full_name === repository,
  "Unauthorized production release event");
  const commit = pr.merge_commit_sha;
  assert.ok(/^[0-9a-f]{40}$/.test(commit ?? "") && commit === workflowSha && commit === checkout,
    "Merge SHA mismatch: authorization, workflow and checkout must agree");
  return commit;
}

export function releaseIdentity(commit, gradle, changelog) {
  const version = checkVersion(gradle, changelog);
  const notes = changelog.replaceAll("\r\n", "\n").split(/^## /m)[1]
    .split("\n").slice(1).join("\n").trim();
  assert.ok(!notes.includes("<!-- verified-candidate:"), "Reserved release metadata marker");
  return {
    commit, ...version, tag: `v${version.versionName}`, notes,
    filename: `pin-glide-${version.versionName}.apk`,
  };
}
