import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { appendFileSync, copyFileSync, mkdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { preflight } from "../distribution/preflight.mjs";
import { verifyApk } from "../distribution/verify-apk.mjs";
import { authorizedCommit, releaseIdentity } from "./production-policy.mjs";
import {
  artifactName, inspectPublication, publishCandidate, readCandidate, recordCandidate, recoveryPlan,
} from "./production-publication.mjs";
import { checkVersion } from "./version-policy.mjs";

const env = process.env;
const git = (...args) => execFileSync("git", args, { encoding: "utf8" }).trim();
const commit = authorizedCommit(JSON.parse(readFileSync(env.GITHUB_EVENT_PATH, "utf8")),
  env.GITHUB_EVENT_NAME, env.GITHUB_REPOSITORY, env.GITHUB_SHA, git("rev-parse", "HEAD"));
const gradlePath = "app/build.gradle.kts";
const changelogPath = "CHANGELOG.md";
const gradle = readFileSync(gradlePath, "utf8");
const changelog = readFileSync(changelogPath, "utf8");
const identity = releaseIdentity(commit, gradle, changelog);
const output = values => appendFileSync(env.GITHUB_OUTPUT,
  Object.entries(values).map(([key, value]) => `${key}=${value}\n`).join(""));
const command = process.argv[2];
const directory = "build/production-candidate";

function github() {
  assert.ok(env.GH_TOKEN && /^[\w.-]+\/[\w.-]+$/.test(env.GITHUB_REPOSITORY), "Missing GitHub configuration");
  const repository = env.GITHUB_REPOSITORY;
  async function request(method, path, body, filename) {
    const upload = filename !== undefined;
    const origin = upload ? "https://uploads.github.com" : "https://api.github.com";
    const query = upload ? `?name=${encodeURIComponent(filename)}` : "";
    const response = await fetch(`${origin}/repos/${repository}${path}${query}`, {
      method, redirect: "error",
      headers: {
        Authorization: `Bearer ${env.GH_TOKEN}`,
        Accept: "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        ...(body ? { "Content-Type": upload ? "application/octet-stream" : "application/json" } : {}),
      },
      ...(body ? { body: upload ? body : JSON.stringify(body) } : {}),
    });
    if (!response.ok) throw new Error(`GitHub ${method} ${path} failed (${response.status})`);
    return response.status === 204 ? undefined : response.json();
  }
  return {
    request,
    upload: (id, filename, bytes) => request("POST", `/releases/${id}/assets`, bytes, filename),
    async list(path, key) {
      const results = [];
      for (let page = 1; ; page++) {
        const response = await request("GET", `${path}?per_page=100&page=${page}`);
        const items = key ? response[key] : response;
        assert.ok(Array.isArray(items), "Unexpected GitHub list response");
        results.push(...items);
        if (items.length < 100) return results;
      }
    },
  };
}

function readVersionAt(sha) {
  assert.match(sha, /^[0-9a-f]{40}$/);
  assert.equal(git("merge-base", sha, commit), sha, "Published release is not an ancestor of authorized commit");
  return checkVersion(git("show", `${sha}:${gradlePath}`), git("show", `${sha}:${changelogPath}`));
}

if (command === "authorize") {
  preflight(env.GITHUB_REF_TYPE, JSON.parse(env.PUBLIC_VARIABLES), env.SIGNING_FALLBACK);
  await inspectPublication(github(), identity, gradle, changelog, readVersionAt);
  output({ commit });
} else {
  assert.equal(commit, env.AUTHORIZED_COMMIT, "Unauthorized checkout");
  const expected = {
    signerSha256: (env.PINGLIDE_SIGNING_CERT_SHA256 ?? "").replaceAll(":", "").toLowerCase(),
    qqAppId: env.PINGLIDE_QQ_APP_ID ?? "",
  };
  assert.match(expected.signerSha256, /^[0-9a-f]{64}$/, "Missing approved production signer");
  assert.match(expected.qqAppId, /^[0-9]+$/, "Missing production QQ App ID");
  const verify = apk => verifyApk("release", apk, expected.signerSha256, expected.qqAppId);
  if (command === "candidate") {
    mkdirSync(directory, { recursive: true });
    copyFileSync("app/build/outputs/apk/release/app-release.apk", join(directory, identity.filename));
    recordCandidate(directory, identity, expected, verify);
  } else {
    const api = github();
    const inspect = () => inspectPublication(api, identity, gradle, changelog, readVersionAt);
    if (command === "prepare") {
      const state = await inspect();
      assert.match(env.GITHUB_RUN_ID, /^\d+$/);
      assert.match(env.GITHUB_RUN_ATTEMPT, /^[1-9]\d*$/);
      const artifacts = await api.list(`/actions/runs/${env.GITHUB_RUN_ID}/artifacts`, "artifacts");
      let priorCandidateStep = false;
      if (!artifacts.some(artifact => artifact.name === artifactName)) {
        for (let attempt = 1; attempt < Number(env.GITHUB_RUN_ATTEMPT); attempt++) {
          const jobs = await api.list(`/actions/runs/${env.GITHUB_RUN_ID}/attempts/${attempt}/jobs`, "jobs");
          priorCandidateStep ||= jobs.some(job => job.name === "package and publish" &&
            job.steps?.some(step => step.name === "Verify and record candidate" &&
              step.started_at && step.conclusion !== "skipped"));
        }
      }
      const plan = recoveryPlan(artifacts, priorCandidateStep, Boolean(state.tag || state.release));
      if (plan === "recover") {
        execFileSync("gh", ["run", "download", env.GITHUB_RUN_ID, "--repo", env.GITHUB_REPOSITORY,
          "--name", artifactName, "--dir", directory], { stdio: "inherit" });
        readCandidate(directory, identity, expected, verify);
      }
      const baseline = state.baseline;
      output({ plan, baseline: baseline ? `${baseline.versionName}:${baseline.versionCode}` : "none" });
    } else if (command === "publish") {
      const candidate = readCandidate(directory, identity, expected, verify);
      await publishCandidate(api, identity, directory, candidate, inspect);
    } else {
      throw new Error("Expected authorize, prepare, candidate or publish");
    }
  }
}
