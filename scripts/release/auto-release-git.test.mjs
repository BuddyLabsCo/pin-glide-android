import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { updateProposal } from "./auto-release-pr.mjs";
import { checkReleasePr } from "./check-release-pr.mjs";
import { checkVersion, proposeRelease } from "./version-policy.mjs";

const repository = "BuddyLabsCo/pin-glide-android";
const gradlePath = "app/build.gradle.kts";
const changelogPath = "CHANGELOG.md";
const initial = { versionName: "0.1.0", versionCode: 1 };
const gradle = 'android {\n  defaultConfig {\n    versionCode = 1\n    versionName = "0.1.0"\n  }\n}\n';
const changelog = "# Changelog\n\n## 0.1.0 (versionCode 1)\n\n- Initial release.\n";

function fixture(t, published = false) {
  const directory = mkdtempSync(join(tmpdir(), "auto-release-git-"));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const cwd = join(directory, "checkout");
  const origin = join(directory, "origin.git");
  mkdirSync(cwd);
  const gitOptions = {
    cwd, encoding: "utf8", stdio: ["ignore", "pipe", "pipe"],
  };
  const rawGit = (...args) => execFileSync("git", args, gitOptions).trimEnd();
  rawGit("init", "-b", "main");
  rawGit("init", "--bare", origin);
  rawGit("config", "user.name", "Fixture");
  rawGit("config", "user.email", "fixture@example.com");
  rawGit("config", "commit.gpgsign", "false");
  rawGit("config", "core.autocrlf", "false");
  rawGit("remote", "add", "origin", origin);
  const read = file => readFileSync(join(cwd, file), "utf8");
  const write = (file, text) => {
    mkdirSync(join(cwd, file, ".."), { recursive: true });
    writeFileSync(join(cwd, file), text);
  };
  const commit = (message, files) => {
    for (const [file, text] of Object.entries(files)) write(file, text);
    rawGit("add", "-A");
    rawGit("commit", "-m", message);
    return rawGit("rev-parse", "HEAD");
  };
  const seed = commit("Seed Android project", { [gradlePath]: gradle, [changelogPath]: changelog });
  const pushMain = commit => rawGit("push", "origin", `${commit}:refs/heads/main`);
  pushMain(seed);
  const pulls = [];
  const mutations = [];
  const tags = published ? [{ name: "v0.1.0", commit: { sha: seed } }] : [];
  const candidate = {
    commit: seed, ...initial, packageId: "com.buddylabs.pinglide", signerSha256: "a".repeat(64),
    qqAppId: "123", filename: "pin-glide-0.1.0.apk", sha256: "b".repeat(64),
  };
  const releases = published ? [{
    name: "v0.1.0", tag_name: "v0.1.0", draft: false, prerelease: false,
    body: `- Initial release.\n\n<!-- verified-candidate: ${JSON.stringify(candidate)} -->`,
  }] : [];
  const api = {
    async list(path) {
      if (path === "/releases") return releases;
      if (path === "/tags") return tags;
      assert.match(path, /^\/pulls\?/);
      return pulls;
    },
    async request(method, path, body) {
      if (method === "GET") return pulls.find(pr => path === `/pulls/${pr.number}`);
      mutations.push({ method, path, body });
      if (method === "POST") {
        assert.equal(pulls.length, 0);
        pulls.push({ ...body, number: 7, state: "open",
          head: { ref: body.head, repo: { full_name: repository } },
          base: { ref: body.base, repo: { full_name: repository } },
        });
      } else {
        assert.equal(path, "/pulls/7");
        Object.assign(pulls[0], body);
      }
    },
  };
  const proposalHead = () => rawGit("rev-parse", "refs/remotes/origin/auto-release");
  let beforeVerify;
  let afterProposalPush;
  const git = (...args) => {
    if (args[0] === "ls-remote" && beforeVerify) {
      const callback = beforeVerify;
      beforeVerify = undefined;
      callback();
    }
    if (args[0] === "push") mutations.push({ push: args });
    const result = rawGit(...args);
    if (args[0] === "push" && afterProposalPush) {
      const callback = afterProposalPush;
      afterProposalPush = undefined;
      callback();
    }
    return result;
  };
  return {
    seed, api, pulls, mutations, read, write, commit, pushMain, proposalHead, rawGit,
    checkoutMain: sha => rawGit("reset", "--hard", sha),
    beforeVerify: callback => { beforeVerify = callback; },
    afterProposalPush: callback => { afterProposalPush = callback; },
    run: (before, after) => updateProposal({ before, after, repository, api, git, read, write }),
    check: () => checkReleasePr(api, rawGit, read(gradlePath), read(changelogPath)),
  };
}

test("real Git creates and refreshes a proposal, preserves maintainer version, and skips identical retries", async t => {
  const repo = fixture(t, true);
  const first = repo.commit("Improve sender", { "app/feature.txt": "first" });
  repo.pushMain(first);
  await repo.run(repo.seed, first);
  const original = repo.proposalHead();
  assert.equal(repo.rawGit("rev-parse", `${original}^`), first);
  assert.deepEqual(await repo.check(), { versionName: "0.1.1", versionCode: 2 });
  assert.match(repo.read(changelogPath), /## 0\.1\.1 \(versionCode 2\)\n\n- Improve sender\n\n## 0\.1\.0/);
  assert.match(repo.pulls[0].body, /Changelog base: v0\.1\.0\./);
  assert.match(repo.pulls[0].body, /### Changelog\n\n- Improve sender/);

  const selected = proposeRelease(repo.read(gradlePath), repo.read(changelogPath), initial, "- Maintainer notes", "2.0.0");
  const override = repo.commit("Select major version", { [gradlePath]: selected.gradle, [changelogPath]: selected.changelog });
  repo.rawGit("push", "origin", `${override}:refs/heads/auto-release`);
  repo.checkoutMain(first);
  const second = repo.commit("Improve pairing", { "app/feature.txt": "second" });
  repo.pushMain(second);
  repo.mutations.length = 0;
  await repo.run(first, second);
  const refreshed = repo.proposalHead();
  assert.equal(repo.rawGit("rev-parse", `${refreshed}^`), second);
  assert.deepEqual(await repo.check(), { versionName: "2.0.0", versionCode: 2 });
  assert.match(repo.read(changelogPath), /## 2\.0\.0 \(versionCode 2\)\n\n- Improve pairing\n- Improve sender/);
  assert.equal(repo.pulls.length, 1);
  assert.equal(repo.mutations[1].method, "PATCH");
  assert.equal(repo.mutations[1].path, "/pulls/7");
  repo.checkoutMain(second);
  repo.mutations.length = 0;
  await repo.run(first, second);
  assert.deepEqual(repo.mutations, []);
  assert.equal(repo.proposalHead(), refreshed);

  repo.checkoutMain(refreshed);
  repo.pushMain(refreshed);
  Object.assign(repo.pulls[0], {
    state: "closed", merged: true, merged_at: "2026-11-26", merged_by: { type: "User" },
    merge_commit_sha: refreshed,
  });
  repo.pulls[0].head.sha = refreshed;
  await repo.run(second, refreshed);
  const pending = repo.commit("Change after merge", { "app/feature.txt": "pending" });
  repo.pushMain(pending);
  await repo.run(refreshed, pending);
  assert.deepEqual(repo.mutations, []);
});

test("real version-only and workflow-only changes do not propose a release", async t => {
  const repo = fixture(t, true);
  const selected = proposeRelease(gradle, changelog, initial, "- Manual metadata", "2.0.0");
  const metadata = repo.commit("Change release metadata", { [gradlePath]: selected.gradle, [changelogPath]: selected.changelog });
  assert.match(repo.rawGit("diff", repo.seed, metadata, "--", gradlePath), /-    versionCode = 1[\s\S]*\+    versionCode = 2/);
  repo.pushMain(metadata);
  await repo.run(repo.seed, metadata);
  const workflow = repo.commit("Change workflow", { ".github/workflows/ci.yml": "name: Changed\n" });
  repo.pushMain(workflow);
  await repo.run(metadata, workflow);
  assert.deepEqual(repo.mutations, []);
});

test("ordinary Gradle changes open a proposal, then workflow-only changes leave it untouched", async t => {
  const repo = fixture(t, true);
  const eligible = repo.commit("Change Gradle namespace", {
    [gradlePath]: gradle.replace("android {", 'android {\n  namespace = "changed"'),
  });
  repo.pushMain(eligible);
  await repo.run(repo.seed, eligible);
  const original = repo.proposalHead();
  repo.checkoutMain(eligible);
  repo.mutations.length = 0;
  const workflow = repo.commit("Change workflow", { ".github/workflows/ci.yml": "name: Changed\n" });
  repo.pushMain(workflow);
  await repo.run(eligible, workflow);
  assert.deepEqual(repo.mutations, []);
  assert.equal(repo.proposalHead(), original);
});

test("a later workflow-only push recovers the eligible proposal lost to stale main", async t => {
  const repo = fixture(t);
  const eligible = repo.commit("Improve forwarding", { "app/feature.txt": "eligible" });
  const concurrent = repo.commit("Change workflow", { ".github/workflows/ci.yml": "name: Changed\n" });
  repo.pushMain(eligible);
  repo.checkoutMain(eligible);
  repo.beforeVerify(() => repo.pushMain(concurrent));
  await assert.rejects(repo.run(repo.seed, eligible), /Main changed/);
  assert.deepEqual(repo.mutations, []);
  repo.checkoutMain(concurrent);
  await repo.run(eligible, concurrent);
  assert.equal(repo.rawGit("rev-parse", `${repo.proposalHead()}^`), concurrent);
  assert.deepEqual(checkVersion(repo.read(gradlePath), repo.read(changelogPath), null), initial);
  assert.match(repo.pulls[0].body, /- Change workflow\n- Improve forwarding\n- Seed Android project/);
  assert.equal(repo.pulls.length, 1);
  assert.equal(repo.mutations[1].method, "POST");
});

test("a later release-only push recovers an interrupted refresh of an open proposal", async t => {
  const repo = fixture(t, true);
  const first = repo.commit("Improve forwarding", { "app/feature.txt": "first" });
  repo.pushMain(first);
  await repo.run(repo.seed, first);
  repo.checkoutMain(first);
  const eligible = repo.commit("Improve pairing", { "app/feature.txt": "second" });
  const concurrent = repo.commit("Change notes", { [changelogPath]: changelog + "\n" });
  repo.pushMain(eligible);
  repo.checkoutMain(eligible);
  repo.mutations.length = 0;
  repo.beforeVerify(() => repo.pushMain(concurrent));
  await assert.rejects(repo.run(first, eligible), /Main changed/);
  assert.deepEqual(repo.mutations, []);
  repo.checkoutMain(concurrent);
  await repo.run(eligible, concurrent);
  assert.equal(repo.rawGit("rev-parse", `${repo.proposalHead()}^`), concurrent);
  assert.equal(repo.pulls.length, 1);
  assert.equal(repo.mutations[1].method, "PATCH");
  assert.match(repo.pulls[0].body, /- Change notes\n- Improve pairing\n- Improve forwarding/);
});

test("a workflow-only push repairs stale PR metadata after a successful proposal branch push", async t => {
  const repo = fixture(t, true);
  const first = repo.commit("Improve forwarding", { "app/feature.txt": "first" });
  repo.pushMain(first);
  await repo.run(repo.seed, first);
  const oldTitle = repo.pulls[0].title;
  const oldBody = repo.pulls[0].body;
  const selected = proposeRelease(repo.read(gradlePath), repo.read(changelogPath),
    initial, "- Maintainer notes", "2.0.0");
  const override = repo.commit("Select major version", {
    [gradlePath]: selected.gradle, [changelogPath]: selected.changelog,
  });
  repo.rawGit("push", "origin", `${override}:refs/heads/auto-release`);
  repo.checkoutMain(first);
  const eligible = repo.commit("Improve pairing", { "app/feature.txt": "second" });
  const concurrent = repo.commit("Change workflow", { ".github/workflows/ci.yml": "name: Changed\n" });
  repo.pushMain(eligible);
  repo.checkoutMain(eligible);
  repo.mutations.length = 0;
  repo.afterProposalPush(() => repo.pushMain(concurrent));
  await assert.rejects(repo.run(first, eligible), /Main changed/);
  const refreshed = repo.proposalHead();
  assert.equal(repo.rawGit("rev-parse", `${refreshed}^`), eligible);
  assert.equal(repo.mutations.length, 1);
  assert.ok(repo.mutations[0].push);
  assert.equal(repo.pulls[0].title, oldTitle);
  assert.equal(repo.pulls[0].body, oldBody);
  assert.match(repo.rawGit("show", `${refreshed}:${changelogPath}`),
    /## 2\.0\.0 \(versionCode 2\)\n\n- Improve pairing\n- Improve forwarding/);

  repo.checkoutMain(concurrent);
  repo.mutations.length = 0;
  await repo.run(eligible, concurrent);
  assert.equal(repo.rawGit("rev-parse", "HEAD"), concurrent);
  assert.equal(repo.rawGit("status", "--porcelain"), "");
  assert.equal(repo.proposalHead(), refreshed);
  assert.equal(repo.pulls.length, 1);
  assert.equal(repo.mutations.length, 1);
  assert.equal(repo.mutations[0].method, "PATCH");
  assert.equal(repo.mutations[0].path, "/pulls/7");
  assert.equal(repo.pulls[0].title, "chore: propose release v2.0.0");
  assert.match(repo.pulls[0].body, /## Proposed release 2\.0\.0 \(versionCode 2\)/);
  assert.match(repo.pulls[0].body, /### Changelog\n\n- Improve pairing\n- Improve forwarding\n\n/);
  assert.doesNotMatch(repo.pulls[0].body, /Change workflow/);
  repo.mutations.length = 0;
  await repo.run(eligible, concurrent);
  assert.deepEqual(repo.mutations, []);
  assert.equal(repo.proposalHead(), refreshed);
});

test("release quality rejects internally consistent but unpublished or rewritten history", async t => {
  const repo = fixture(t, true);
  assert.deepEqual(checkVersion(repo.read(gradlePath), repo.read(changelogPath)), initial);
  await assert.rejects(repo.check(), /higher stable version/);
  const valid = proposeRelease(gradle, changelog, initial, "- Release notes");
  repo.write(gradlePath, valid.gradle);
  repo.write(changelogPath, valid.changelog);
  assert.deepEqual(await repo.check(), { versionName: "0.1.1", versionCode: 2 });
  const skipped = proposeRelease(valid.gradle, valid.changelog, { versionName: "0.1.1", versionCode: 2 }, "- Later release.");
  repo.write(gradlePath, skipped.gradle);
  repo.write(changelogPath, skipped.changelog);
  assert.equal(checkVersion(repo.read(gradlePath), repo.read(changelogPath)).versionCode, 3);
  await assert.rejects(repo.check(), /higher stable version/);
  repo.write(gradlePath, valid.gradle);
  repo.write(changelogPath, valid.changelog.replace("Initial release.", "Rewritten release."));
  await assert.rejects(repo.check(), /Published changelog section changed/);
});
