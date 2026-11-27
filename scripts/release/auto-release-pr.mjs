import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { readFileSync, writeFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
import { github } from "./github.mjs";
import { readVersionAt, releaseIdentity, requireAuthorizedMerge } from "./production-policy.mjs";
import { publicationSnapshot, publishedHistory, checkPublishedHistory, checkUnreservedPublication } from "./production-publication.mjs";
import { checkVersion, compareVersions, proposeRelease } from "./version-policy.mjs";

const gradlePath = "app/build.gradle.kts";
const changelogPath = "CHANGELOG.md";
const branchRef = "refs/heads/auto-release";
const mainRef = "refs/heads/main";
const sha = /^[0-9a-f]{40}$/;

async function releasePulls(api, repository) {
  const owner = encodeURIComponent(repository.split("/")[0]);
  const pulls = (await api.list(`/pulls?state=all&base=main&head=${owner}:auto-release`))
    .filter(pr => pr.head?.ref === "auto-release" && pr.head.repo?.full_name === repository &&
      pr.base?.ref === "main" && pr.base.repo?.full_name === repository);
  const open = pulls.filter(pr => pr.state === "open");
  assert.ok(open.length <= 1, "Expected at most one open release PR");
  const merged = [];
  for (const pr of pulls.filter(pr => pr.merged_at)) {
    const detail = await api.request("GET", `/pulls/${pr.number}`);
    merged.push({ commit: requireAuthorizedMerge(detail, repository), head: detail.head.sha });
  }
  return { open: open[0], merged: merged.sort((left, right) => left.commit.localeCompare(right.commit)) };
}

async function verifyState(git, api, repository, head, branch, state) {
  const refs = new Map(git("ls-remote", "origin", mainRef, branchRef).split("\n")
    .filter(Boolean).map(line => line.split("\t").reverse()));
  assert.equal(refs.get(mainRef), head, "Main changed during proposal preparation");
  assert.equal(refs.get(branchRef) ?? "", branch, "Release branch changed during proposal preparation");
  const current = await releasePulls(api, repository);
  assert.equal(current.open?.number, state.open?.number, "Open release PR changed during preparation");
  assert.deepEqual(current.merged, state.merged, "Release merge state changed during preparation");
}

function eligiblePaths(git, before, after) {
  const paths = (/^0+$/.test(before)
    ? git("ls-tree", "-r", "--name-only", "-z", after)
    : git("diff", "--no-renames", "--name-only", "-z", before, after, "--")).split("\0").filter(Boolean);
  if (paths.some(path => !path.startsWith(".github/") && path !== gradlePath && path !== changelogPath)) return true;
  if (!paths.includes(gradlePath)) return false;
  const withoutVersion = source => source.replace(/^[ \t]*version(?:Name|Code)[ \t]*=.*$/gm, "");
  return /^0+$/.test(before) ||
    withoutVersion(git("show", `${before}:${gradlePath}`)) !== withoutVersion(git("show", `${after}:${gradlePath}`));
}

function proposalMetadata({ versionName, versionCode, notes }, baseline) {
  return {
    title: `chore: propose release v${versionName}`,
    body: [
      `## Proposed release ${versionName} (versionCode ${versionCode})`, "",
      `Changelog base: ${baseline?.tag ?? "the repository root"}.`, "", "### Changelog", "", notes, "",
      "Review the version, changelog and independent quality checks before merging manually.",
      "Only the manual merge authorizes the separate production APK publication workflow.",
    ].join("\n"),
  };
}

export async function updateProposal({ before, after, publicationRun, repository, git, api, read, write }) {
  assert.equal(git("branch", "--show-current"), "main", "Expected main checkout");
  assert.equal(git("status", "--porcelain"), "", "Expected clean checkout");
  const head = git("rev-parse", "HEAD");
  const state = await releasePulls(api, repository);
  if (!publicationRun) {
    assert.ok(sha.test(before) && sha.test(after), "Expected push event SHAs");
    assert.equal(git("merge-base", after, head), after, "Push is not an ancestor of current main");
    if (state.merged.some(pr => pr.commit === after)) return;
  }

  const snapshot = await publicationSnapshot(api);
  const { published, baseline } = publishedHistory(snapshot.releases.filter(release => !release.draft),
    snapshot.tags, commit => readVersionAt(git, commit, head));
  if (publicationRun) {
    assert.match(publicationRun, sha, "Expected publication workflow SHA");
    if (!state.merged.some(pr => pr.commit === baseline?.commit &&
        (publicationRun === pr.commit || publicationRun === pr.head))) return;
    if (!baseline || baseline.commit === head) return;
    after = head;
  }
  if (baseline && git("merge-base", after, baseline.commit) === after) return;
  if (state.merged.some(({ commit }) => git("merge-base", commit, head) === commit &&
      (!baseline || git("merge-base", commit, baseline.commit) !== commit))) {
    console.log("An authorized production release is pending; defer until publication completes.");
    return;
  }
  const previous = git("for-each-ref", "--format=%(objectname)", "refs/remotes/origin/auto-release");
  if (state.open) assert.match(previous, sha, "Open release PR has no branch reference");
  async function syncPull(proposalHead, metadata) {
    if (state.open?.title === metadata.title && state.open.body === metadata.body) return;
    await verifyState(git, api, repository, head, proposalHead, state);
    if (state.open) {
      await api.request("PATCH", `/pulls/${state.open.number}`, metadata);
    } else {
      await api.request("POST", "/pulls", { head: "auto-release", base: "main", ...metadata });
    }
  }

  const eventEligible = !publicationRun && eligiblePaths(git, before, after);
  if (!eventEligible) {
    const covered = state.open ? git("merge-base", previous, head) :
      baseline?.commit ?? git("rev-list", "--max-parents=0", head);
    assert.match(covered, sha, "Expected a unique release comparison base");
    if (!eligiblePaths(git, covered, head)) {
      if (!state.open) return;
      checkUnreservedPublication(snapshot);
      const gradle = git("show", `${previous}:${gradlePath}`);
      const changelog = git("show", `${previous}:${changelogPath}`);
      checkVersion(gradle, changelog, baseline);
      checkPublishedHistory(changelog, published);
      await syncPull(previous, proposalMetadata(releaseIdentity(previous, gradle, changelog), baseline));
      return;
    }
  }
  checkUnreservedPublication(snapshot);

  const range = baseline ? `${baseline.commit}..${head}` : head;
  const entries = git("log", "--no-merges", "--format=%s", range).split("\n").filter(Boolean);
  assert.ok(entries.length, "No commits for release notes");
  const notes = entries.map(entry => `- ${entry}`).join("\n");
  const gradle = read(gradlePath);
  const changelog = read(changelogPath);
  let proposal = proposeRelease(gradle, changelog, baseline, notes);
  const version = checkVersion(proposal.gradle, proposal.changelog, baseline);
  if (state.open) {
    const selected = checkVersion(git("show", `${previous}:${gradlePath}`),
      git("show", `${previous}:${changelogPath}`), baseline);
    if (compareVersions(selected.versionName, version.versionName) > 0) {
      proposal = proposeRelease(gradle, changelog, baseline, notes, selected.versionName);
    }
  }
  checkPublishedHistory(proposal.changelog, published);
  const metadata = proposalMetadata(releaseIdentity(head, proposal.gradle, proposal.changelog), baseline);
  write(gradlePath, proposal.gradle);
  write(changelogPath, proposal.changelog);
  git("add", "--", gradlePath, changelogPath);
  if (!git("diff", "--cached", "--name-only")) return;
  let proposalHead = previous;
  if (!previous || git("diff", "--cached", "--name-only", previous)) {
    git("-c", "user.name=github-actions[bot]", "-c",
      "user.email=41898282+github-actions[bot]@users.noreply.github.com", "commit", "-m", metadata.title);
    await verifyState(git, api, repository, head, previous, state);
    git("push", `--force-with-lease=${branchRef}:${previous}`, "origin", `HEAD:${branchRef}`);
    proposalHead = git("rev-parse", "HEAD");
  }
  await syncPull(proposalHead, metadata);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  await updateProposal({
    before: process.env.PUSH_BEFORE, after: process.env.PUSH_AFTER, repository: process.env.GITHUB_REPOSITORY,
    publicationRun: process.env.GITHUB_EVENT_NAME === "workflow_run" ? process.env.PUBLICATION_RUN_SHA : undefined,
    git: (...args) => execFileSync("git", args, { encoding: "utf8", maxBuffer: 16 * 1024 * 1024 }).trimEnd(),
    api: github(process.env), read: path => readFileSync(path, "utf8"), write: writeFileSync,
  });
}
