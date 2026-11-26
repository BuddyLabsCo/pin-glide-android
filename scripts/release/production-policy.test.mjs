import assert from "node:assert/strict";
import { test } from "node:test";
import { authorizedCommit } from "./production-policy.mjs";

const repository = "BuddyLabsCo/pin-glide-android";
const sha = "a".repeat(40);
const repo = { full_name: repository };
const event = {
  action: "closed",
  pull_request: {
    merged: true, merged_by: { type: "User" }, merge_commit_sha: sha,
    head: { ref: "auto-release", repo },
    base: { ref: "main", repo },
  },
};

test("only the exact same-repository merged release PR authorizes publication", () => {
  assert.equal(authorizedCommit(event, "pull_request", repository, sha, sha), sha);
  for (const name of ["push", "workflow_dispatch"]) {
    assert.throws(() => authorizedCommit(event, name, repository, sha, sha), /Unauthorized/);
  }
  for (const change of [
    { merged: false },
    { merged_by: { type: "Bot" } },
    { auto_merge: { enabled_by: { type: "User" } } },
    { head: { ref: "feature", repo } },
    { head: { ref: "auto-release", repo: { full_name: "fork/pin-glide-android" } } },
    { base: { ref: "other", repo } },
    { base: { ref: "main", repo: { full_name: "other/pin-glide-android" } } },
  ]) {
    assert.throws(() => authorizedCommit({
      ...event, pull_request: { ...event.pull_request, ...change },
    }, "pull_request", repository, sha, sha), /Unauthorized/);
  }
  assert.throws(() => authorizedCommit(event, "pull_request", repository, sha, "b".repeat(40)), /SHA/);
  assert.throws(() => authorizedCommit(event, "pull_request", repository, "b".repeat(40), sha), /SHA/);
});
