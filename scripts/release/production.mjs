import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { appendFileSync, copyFileSync, mkdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";
import { preflight } from "../distribution/preflight.mjs";
import { verifyApk } from "../distribution/verify-apk.mjs";
import { authorizedCommit, releaseIdentity } from "./production-policy.mjs";
import {
  inspectPublication, publishCandidate, readCandidate, recordCandidate, recoverPublication, reserveCandidate,
} from "./production-publication.mjs";
const gradlePath = "app/build.gradle.kts";
const changelogPath = "CHANGELOG.md";

export function github(env, fetchResponse = fetch) {
  assert.ok(env.GH_TOKEN && /^[\w.-]+\/[\w.-]+$/.test(env.GITHUB_REPOSITORY), "Missing GitHub configuration");
  const repository = env.GITHUB_REPOSITORY;
  async function request(method, path, body, filename) {
    const upload = filename !== undefined;
    const origin = upload ? "https://uploads.github.com" : "https://api.github.com";
    const query = upload ? `?name=${encodeURIComponent(filename)}` : "";
    const response = await fetchResponse(`${origin}/repos/${repository}${path}${query}`, {
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
        const response = await request("GET", `${path}${path.includes("?") ? "&" : "?"}per_page=100&page=${page}`);
        const items = key ? response[key] : response;
        assert.ok(Array.isArray(items), "Unexpected GitHub list response");
        results.push(...items);
        if (items.length < 100) return results;
      }
    },
  };
}

export async function runProduction(command, { env, event, git, gradle, changelog, api, directory, download, verify,
  sourceApk, output }) {
  const commit = authorizedCommit(event, env.GITHUB_EVENT_NAME, env.GITHUB_REPOSITORY, env.GITHUB_SHA,
    git("rev-parse", "HEAD"));
  const identity = releaseIdentity(commit, gradle, changelog);
  function readVersionAt(sha) {
    assert.match(sha, /^[0-9a-f]{40}$/);
    assert.equal(git("merge-base", sha, commit), sha, "Published release is not an ancestor of authorized commit");
    return releaseIdentity(sha, git("show", `${sha}:${gradlePath}`), git("show", `${sha}:${changelogPath}`));
  }
  if (command === "authorize") {
    preflight(env.GITHUB_REF_TYPE, JSON.parse(env.PUBLIC_VARIABLES), env.SIGNING_FALLBACK);
    await inspectPublication(api, identity, gradle, changelog, readVersionAt);
    output({ commit });
    return;
  }
  assert.equal(commit, env.AUTHORIZED_COMMIT, "Unauthorized checkout");
  const expected = {
    signerSha256: (env.PINGLIDE_SIGNING_CERT_SHA256 ?? "").replaceAll(":", "").toLowerCase(),
    qqAppId: env.PINGLIDE_QQ_APP_ID ?? "",
  };
  assert.match(expected.signerSha256, /^[0-9a-f]{64}$/, "Missing approved production signer");
  assert.match(expected.qqAppId, /^[0-9]+$/, "Missing production QQ App ID");
  const verifyCandidate = apk => verify("release", apk, expected.signerSha256, expected.qqAppId);
  const inspect = () => inspectPublication(api, identity, gradle, changelog, readVersionAt, expected.signerSha256);
  if (command === "candidate") {
    await reserveCandidate(api, identity, env.GITHUB_RUN_ID, await inspect());
    mkdirSync(directory, { recursive: true });
    copyFileSync(sourceApk, join(directory, identity.filename));
    recordCandidate(directory, identity, expected, verifyCandidate);
  } else {
    if (command === "prepare") {
      output(await recoverPublication(api, identity, await inspect(), env.GITHUB_RUN_ID,
        env.GITHUB_RUN_ATTEMPT, directory, download, expected, verifyCandidate));
    } else if (command === "publish") {
      const candidate = readCandidate(directory, identity, expected, verifyCandidate);
      await publishCandidate(api, identity, directory, candidate, inspect);
    } else {
      throw new Error("Expected authorize, prepare, candidate or publish");
    }
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const env = process.env;
  await runProduction(process.argv[2], {
    env, event: JSON.parse(readFileSync(env.GITHUB_EVENT_PATH, "utf8")),
    git: (...args) => execFileSync("git", args, { encoding: "utf8" }).trim(),
    gradle: readFileSync(gradlePath, "utf8"), changelog: readFileSync(changelogPath, "utf8"),
    api: github(env), directory: "build/production-candidate",
    sourceApk: "app/build/outputs/apk/release/app-release.apk", verify: verifyApk,
    download: (runId, name, directory) => execFileSync("gh", [
      "run", "download", runId, "--repo", env.GITHUB_REPOSITORY, "--name", name, "--dir", directory,
    ], { stdio: "inherit" }),
    output: values => appendFileSync(env.GITHUB_OUTPUT,
      Object.entries(values).map(([key, value]) => `${key}=${value}\n`).join("")),
  });
}
