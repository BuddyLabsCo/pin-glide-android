import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { test } from "node:test";
import { updateProposal } from "./auto-release-pr.mjs";
import { checkVersion, proposeRelease } from "./version-policy.mjs";

const repository = "BuddyLabsCo/pin-glide-android";
const before = "1".repeat(40);
const head = "2".repeat(40);
const branch = "3".repeat(40);
const gradlePath = "app/build.gradle.kts";
const changelogPath = "CHANGELOG.md";
const gradle = 'android {\n  defaultConfig {\n    versionCode = 1\n    versionName = "0.1.0"\n  }\n}\n';
const changelog = "# Changelog\n\n## 0.1.0 (versionCode 1)\n\n- Initial release.\n";
const initial = { versionName: "0.1.0", versionCode: 1 };

function fixture({ paths = ["app/src/main/Example.kt"], published = false, proposed,
  merged = false, remoteMoved = false, after = head, pending = false,
  changedGradle = false, rejectedPush = false, closeOnPush = false, publicationRun,
  reserved = false, fork = false } = {}) {
  const files = new Map([[gradlePath, gradle], [changelogPath, changelog]]);
  const previous = proposed && proposeRelease(gradle, changelog, initial, "- Old notes", proposed);
  const pulls = [];
  if (proposed) pulls.push({
    number: 7, state: "open", head: { ref: "auto-release", repo: { full_name: repository } },
    base: { ref: "main", repo: { full_name: repository } }, title: "Old title", body: "Old body",
  });
  if (merged) pulls.push({
    number: 6, state: "closed", merged_at: "2026-11-26", merged: true, merged_by: { type: "User" },
    merge_commit_sha: pending ? before : head,
    head: { ref: "auto-release", repo: { full_name: repository }, sha: branch },
    base: { ref: "main", repo: { full_name: repository } },
  });
  if (fork) pulls.push({
    number: 8, state: "open", head: { ref: "auto-release", repo: { full_name: "fork/pin-glide-android" } },
    base: { ref: "main", repo: { full_name: repository } },
  });
  const tags = published ? [{ name: "v0.1.0", commit: { sha: before } }] : [];
  if (reserved) tags.push({ name: "v0.1.0", commit: { sha: head } });
  const candidate = {
    commit: before, ...initial, packageId: "com.buddylabs.pinglide", signerSha256: "a".repeat(64),
    qqAppId: "123", filename: "pin-glide-0.1.0.apk", sha256: "b".repeat(64),
  };
  const releases = published ? [{
    tag_name: "v0.1.0", name: "v0.1.0", draft: false, prerelease: false,
    body: `- Initial release.\n\n<!-- verified-candidate: ${JSON.stringify(candidate)} -->`,
  }] : [];
  const writes = [];
  const mutations = [];
  let remoteBranch = proposed ? branch : "";
  let localHead = head;
  const api = {
    async list(path) {
      if (path === "/releases") return releases;
      if (path === "/tags") return tags;
      assert.match(path, /^\/pulls\?/);
      return pulls;
    },
    async request(method, path, body) {
      if (method === "GET") return pulls.find(pr => `/pulls/${pr.number}` === path);
      mutations.push({ method, path, body });
    },
  };
  function git(...args) {
    switch (args[0]) {
      case "branch": return "main";
      case "status": return "";
      case "rev-parse": return localHead;
      case "rev-list": return before;
      case "merge-base": return args[1] === head && args[2] === before ? before : args[1];
      case "diff": return args.includes("--cached") ? changelogPath : paths.join("\0");
      case "for-each-ref": return remoteBranch;
      case "show": {
        const [sha, path] = args[1].split(":");
        if (sha === branch) return path === gradlePath ? previous.gradle : previous.changelog;
        return path === gradlePath
          ? (sha === head && changedGradle ? gradle.replace("android {", "android {\n  namespace = \"changed\"") : gradle)
          : changelog;
      }
      case "log": return "Improve Sender\nEarlier change";
      case "add": return "";
      case "-c": localHead = "4".repeat(40); return "";
      case "ls-remote": return `${remoteMoved ? before : head}\trefs/heads/main\n` +
        (remoteBranch ? `${remoteBranch}\trefs/heads/auto-release` : "");
      case "push":
        if (rejectedPush) throw new Error("Rejected lease");
        mutations.push({ push: args });
        remoteBranch = localHead;
        if (closeOnPush) pulls[0].state = "closed";
        return "";
      default: assert.fail(`Unexpected git call: ${args.join(" ")}`);
    }
  }
  return {
    pulls, writes, mutations, files,
    run: () => updateProposal({ before, after, publicationRun, repository, git, api,
      read: path => files.get(path), write: (path, text) => { writes.push(path); files.set(path, text); } }),
  };
}

test("initial proposal creates one same-repository PR with consistent version and notes", async () => {
  const repo = fixture();
  await repo.run();
  assert.deepEqual(checkVersion(repo.files.get(gradlePath), repo.files.get(changelogPath), null), initial);
  assert.match(repo.files.get(changelogPath), /- Improve Sender\n- Earlier change/);
  assert.equal(repo.mutations.length, 2);
  assert.ok(repo.mutations[0].push.includes("--force-with-lease=refs/heads/auto-release:"));
  const { method, path, body: { body, ...fields } } = repo.mutations[1];
  assert.deepEqual({ method, path, fields }, {
    method: "POST", path: "/pulls",
    fields: { head: "auto-release", base: "main", title: "chore: propose release v0.1.0" },
  });
  assert.match(body, /## Proposed release 0\.1\.0 \(versionCode 1\)/);
  assert.match(body, /Changelog base: the repository root\./);
  assert.match(body, /### Changelog\n\n- Improve Sender\n- Earlier change/);
  assert.match(body, /merg(?:e|ing).*manually/i);
});

test("published baseline advances the patch and code once", async () => {
  const repo = fixture({ published: true });
  await repo.run();
  assert.deepEqual(checkVersion(repo.files.get(gradlePath), repo.files.get(changelogPath), initial),
    { versionName: "0.1.1", versionCode: 2 });
  assert.ok(repo.files.get(changelogPath).endsWith(changelog.slice("# Changelog\n\n".length)));
});

test("refresh keeps a higher maintainer selection and updates only the existing PR", async () => {
  const repo = fixture({ published: true, proposed: "2.0.0" });
  await repo.run();
  assert.deepEqual(checkVersion(repo.files.get(gradlePath), repo.files.get(changelogPath), initial),
    { versionName: "2.0.0", versionCode: 2 });
  assert.ok(repo.mutations[0].push.includes(`--force-with-lease=refs/heads/auto-release:${branch}`));
  assert.equal(repo.mutations[1].method, "PATCH");
  assert.equal(repo.mutations[1].path, "/pulls/7");
  assert.equal(repo.mutations.length, 2);
});

test("already authorized and pending release changes do not propose", async () => {
  for (const options of [
    { merged: true },
    { merged: true, pending: true },
    { published: true, after: before },
  ]) {
    const repo = fixture(options);
    await repo.run();
    assert.deepEqual(repo.writes, [], JSON.stringify(options));
    assert.deepEqual(repo.mutations, [], JSON.stringify(options));
  }
});

test("non-version Gradle changes are release eligible", async () => {
  const repo = fixture({ paths: [gradlePath], changedGradle: true });
  await repo.run();
  assert.equal(repo.mutations[1].method, "POST");
});

test("successful publication catches up eligible main changes deferred during packaging", async () => {
  for (const publicationRun of [before, branch]) {
    const repo = fixture({ published: true, publicationRun, merged: true, pending: true });
    await repo.run();
    assert.deepEqual(checkVersion(repo.files.get(gradlePath), repo.files.get(changelogPath), initial),
      { versionName: "0.1.1", versionCode: 2 });
    assert.equal(repo.mutations[1].method, "POST");
  }
  for (const options of [
    { publicationRun: before },
    { publicationRun: before, published: true, merged: true, pending: true, paths: [changelogPath] },
    { publicationRun: head, published: true, merged: true, pending: true },
  ]) {
    const ignored = fixture(options);
    await ignored.run();
    assert.deepEqual(ignored.mutations, []);
  }
});

test("stale main, rejected lease or closed proposal prevents stale PR mutation", async () => {
  for (const [options, message] of [
    [{ remoteMoved: true }, /Main changed/],
    [{ rejectedPush: true }, /Rejected lease/],
    [{ published: true, proposed: "2.0.0", closeOnPush: true }, /Open release PR changed/],
  ]) {
    const repo = fixture(options);
    await assert.rejects(repo.run, message);
    assert.ok(repo.mutations.every(mutation => mutation.push));
  }
});

test("unpublished stable reservations cannot become a proposal baseline", async () => {
  const repo = fixture({ reserved: true });
  await assert.rejects(repo.run, /Unpublished conflicting stable tag/);
  assert.deepEqual(repo.writes, []);
  assert.deepEqual(repo.mutations, []);
});

test("unauthorized release merges surface an error, not a pending publication", async () => {
  for (const fields of [{ merged_by: { type: "Bot" } }, { auto_merge: {} }]) {
    const repo = fixture({ merged: true, pending: true });
    Object.assign(repo.pulls[0], fields);
    await assert.rejects(repo.run, /Unauthorized production release merge/);
    assert.deepEqual(repo.mutations, []);
  }
});

test("a fork's release-looking PR does not replace the same-repository proposal", async () => {
  const repo = fixture({ fork: true });
  await repo.run();
  assert.equal(repo.mutations[1].method, "POST");
  assert.equal(repo.mutations[1].body.head, "auto-release");
});

test("release proposal uses a scoped App token, serialized main pushes and independent quality CI", () => {
  const workflow = readFileSync(new URL("../../.github/workflows/auto-release-pr.yml", import.meta.url), "utf8");
  assert.match(workflow, /push:\s+branches: \[main\]/);
  assert.match(workflow, /workflow_run:\s+workflows: \[Production release\]\s+types: \[completed\]/);
  assert.match(workflow, /github\.event\.workflow_run\.conclusion == 'success'/);
  assert.match(workflow, /head_repository\.full_name == github\.repository/);
  assert.match(workflow, /ref: main/);
  assert.match(workflow, /concurrency:\s+group: auto-release-pr\s+queue: max/);
  assert.match(workflow, /permissions:\s+contents: read/);
  assert.match(workflow, /uses: actions\/create-github-app-token@v2/);
  assert.match(workflow, /repositories: \$\{\{ github\.event\.repository\.name \}\}/);
  assert.match(workflow, /permission-contents: write\s+permission-pull-requests: write/);
  assert.match(workflow, /GH_TOKEN: \$\{\{ steps\.app-token\.outputs\.token \}\}/);
  assert.doesNotMatch(workflow, /environment:|SIGNING_|pr merge|release create|permission-actions:/);
  const ci = readFileSync(new URL("../../.github/workflows/ci.yml", import.meta.url), "utf8");
  assert.match(ci, /pull_request:/);
  assert.match(ci, /node --test scripts\/release\/\*\.test\.mjs/);
  assert.match(ci, /name: Check release PR against published history[\s\S]*?github\.event\.pull_request\.head\.ref == 'auto-release'[\s\S]*?github\.event\.pull_request\.head\.repo\.full_name == github\.repository[\s\S]*?github\.event\.pull_request\.base\.ref == 'main'[\s\S]*?GH_TOKEN: \$\{\{ github\.token \}\}[\s\S]*?run: node scripts\/release\/check-release-pr\.mjs/);
  assert.match(ci, /^permissions:\s+contents: read/m);
  assert.match(ci, /^  quality:/m);
});
