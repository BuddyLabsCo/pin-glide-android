import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { cpSync, existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { github, runProduction } from "./production.mjs";
import { artifactName, candidateJob, candidateStep } from "./production-publication.mjs";

const repository = "BuddyLabsCo/pin-glide-android";
const commit = "a".repeat(40);
const signer = "b".repeat(64);
const repo = { full_name: repository };
const event = { action: "closed", pull_request: {
  merged: true, merged_by: { type: "User" }, merge_commit_sha: commit,
  head: { ref: "auto-release", repo }, base: { ref: "main", repo },
} };
const gradle = 'android {\n  defaultConfig {\n    versionCode = 1\n    versionName = "0.1.0"\n  }\n}\n';
const changelog = "# Changelog\n\n## 0.1.0 (versionCode 1)\n\n- Initial release.\n";
const tagSha = "c".repeat(40);

function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), "production-run-"));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  const directory = join(root, "candidate");
  const sourceApk = join(root, "source.apk");
  writeFileSync(sourceApk, "approved APK");
  const tags = [];
  const releases = [];
  const assets = [];
  const artifacts = new Map();
  const jobs = new Map();
  const outputs = [];
  let annotation;
  const api = {
    async list(path) {
      if (path === "/releases") return releases;
      if (path === "/releases/1/assets") return assets;
      if (path === "/tags") return tags;
      const artifactRun = /^\/actions\/runs\/(\d+)\/artifacts$/.exec(path)?.[1];
      if (artifactRun) return artifacts.get(artifactRun) ?? [];
      const jobPath = /^\/actions\/runs\/\d+\/attempts\/\d+\/jobs$/.test(path);
      if (jobPath) return jobs.get(path) ?? [];
      assert.fail(`Unexpected list: ${path}`);
    },
    async request(method, path, body) {
      if (method === "POST" && path === "/git/tags") {
        annotation = { ...body, sha: tagSha, object: { sha: body.object, type: body.type } };
        return annotation;
      }
      if (method === "POST" && path === "/git/refs") {
        assert.equal(tags.length, 0, "Never overwrite an existing reservation");
        assert.equal(body.sha, tagSha);
        tags.push({ name: annotation.tag, commit: { sha: annotation.object.sha } });
        return;
      }
      if (method === "GET" && path === "/git/ref/tags/v0.1.0") return { object: { type: "tag", sha: tagSha } };
      if (method === "GET" && path === `/git/tags/${tagSha}`) return annotation;
      if (method === "POST" && path === "/releases") {
        assert.equal(releases.length, 0);
        releases.push({ id: 1, ...body });
        return;
      }
      if (method === "PATCH" && path === "/releases/1") {
        Object.assign(releases[0], body);
        return;
      }
      assert.fail(`Unexpected request: ${method} ${path}`);
    },
    async upload(id, name, bytes) {
      assert.equal(id, 1);
      assert.ok(!assets.some(asset => asset.name === name));
      assets.push({ name, size: bytes.length, state: "uploaded",
        digest: `sha256:${createHash("sha256").update(bytes).digest("hex")}` });
    },
  };
  const context = {
    env: {
      GITHUB_EVENT_NAME: "pull_request", GITHUB_REPOSITORY: repository, GITHUB_SHA: commit,
      GITHUB_REF_TYPE: "branch", AUTHORIZED_COMMIT: commit, GITHUB_RUN_ID: "10", GITHUB_RUN_ATTEMPT: "1",
      PUBLIC_VARIABLES: "{}", SIGNING_FALLBACK: "false",
      PINGLIDE_SIGNING_CERT_SHA256: signer, PINGLIDE_QQ_APP_ID: "123",
    },
    event, git: (...args) => {
      assert.deepEqual(args, ["rev-parse", "HEAD"]);
      return commit;
    },
    gradle, changelog, api, directory, sourceApk,
    verify: t.mock.fn((variant, apk, fingerprint, qqAppId) => {
      assert.equal(variant, "release");
      assert.equal(readFileSync(apk, "utf8"), "approved APK");
      assert.equal(fingerprint, signer);
      assert.equal(qqAppId, "123");
    }),
    download: t.mock.fn((run, name, destination) => {
      assert.equal(run, "10");
      assert.equal(name, artifactName);
      cpSync(directory, destination, { recursive: true });
    }),
    output: values => outputs.push(values),
  };
  return { root, context, tags, artifacts, jobs, outputs, releases, assets };
}

test("production command routing reserves a version before candidate verification and recovers across runs", async t => {
  const { root, context, tags, artifacts, outputs, releases, assets } = fixture(t);
  await runProduction("authorize", context);
  assert.deepEqual(outputs.pop(), { commit });
  await runProduction("prepare", context);
  assert.deepEqual(outputs.pop(), { plan: "build", baseline: "none" });
  const originalVerify = context.verify;
  context.verify = (...args) => {
    assert.equal(tags[0].commit.sha, commit, "Reservation must exist before verification");
    originalVerify(...args);
  };
  await runProduction("candidate", context);
  assert.equal(originalVerify.mock.callCount(), 1);
  await assert.rejects(runProduction("candidate", context), /already reserved/);
  const retry = { ...context, directory: join(root, "retry"), env: { ...context.env, GITHUB_RUN_ID: "20" } };
  await assert.rejects(runProduction("prepare", retry), /required for recovery/);
  assert.equal(existsSync(retry.directory), false);
  artifacts.set("10", [{ name: artifactName, expired: true }]);
  await assert.rejects(runProduction("prepare", retry), /expired candidate recovery/);
  artifacts.set("10", [{ name: artifactName, expired: false }]);
  await runProduction("prepare", retry);
  assert.deepEqual(outputs.pop(), { plan: "recover", baseline: "none" });
  assert.equal(context.download.mock.callCount(), 1);
  assert.equal(originalVerify.mock.callCount(), 2);
  await assert.rejects(runProduction("prepare", {
    ...retry, directory: join(root, "rejected"),
    verify: () => { throw new Error("APK rejected"); },
  }), /APK rejected/);
  assert.equal(outputs.length, 0);
  assert.equal(releases.length, 0, "Recovery must not publish without the publish command");
  await runProduction("publish", retry);
  assert.equal(releases[0].draft, false);
  assert.equal(assets.length, 3);
  assert.equal(assets[0].digest, `sha256:${createHash("sha256").update("approved APK").digest("hex")}`);
  await runProduction("publish", retry);
  assert.equal(assets.length, 3);
});

test("failed candidate verification leaves a reservation that prevents rebuilding", async t => {
  const { context, tags, outputs } = fixture(t);
  context.verify = () => { throw new Error("APK rejected"); };
  await assert.rejects(runProduction("candidate", context), /APK rejected/);
  assert.equal(tags[0].commit.sha, commit);
  assert.equal(existsSync(join(context.directory, "release-candidate.json")), false);
  context.env.GITHUB_RUN_ATTEMPT = "2";
  await assert.rejects(runProduction("prepare", context), /required for recovery/);
  assert.equal(outputs.length, 0);
});

test("previous attempts without artifacts can build only when verification never started", async t => {
  const { context, jobs, outputs } = fixture(t);
  context.env.GITHUB_RUN_ATTEMPT = "2";
  const jobPath = "/actions/runs/10/attempts/1/jobs";
  jobs.set(jobPath, [{ name: candidateJob, steps: [{ name: candidateStep, conclusion: "skipped" }] }]);
  await runProduction("prepare", context);
  assert.deepEqual(outputs.pop(), { plan: "build", baseline: "none" });
  for (const conclusion of ["success", "failure", "cancelled"]) {
    jobs.set(jobPath, [{ name: candidateJob, steps: [{ name: candidateStep, started_at: "2026-01-01", conclusion }] }]);
    await assert.rejects(runProduction("prepare", context), /required for recovery/);
  }
  assert.equal(outputs.length, 0);
});

test("workflow preserves the historical recovery step naming contract", () => {
  const workflow = readFileSync(".github/workflows/production-release.yml", "utf8");
  assert.ok(workflow.includes(`name: ${candidateJob}\n`) || workflow.includes(`name: ${candidateJob}\r\n`));
  assert.ok(workflow.includes(`name: ${candidateStep}\n`) || workflow.includes(`name: ${candidateStep}\r\n`));
});

test("GitHub adapter paginates collection and array responses without real API requests", async () => {
  const urls = [];
  const api = github({ GH_TOKEN: "fixture", GITHUB_REPOSITORY: repository }, async (url, options) => {
    urls.push(new URL(url));
    assert.equal(options.method, "GET");
    const items = urls.at(-1).searchParams.get("page") === "1"
      ? Array.from({ length: 100 }, (_, index) => index) : [100];
    return { ok: true, status: 200, json: async () => url.includes("/artifacts") ? { artifacts: items } : items };
  });
  assert.equal((await api.list("/releases")).length, 101);
  assert.equal((await api.list("/actions/runs/10/artifacts", "artifacts")).length, 101);
  assert.deepEqual(urls.map(url => url.searchParams.get("page")), ["1", "2", "1", "2"]);
});
