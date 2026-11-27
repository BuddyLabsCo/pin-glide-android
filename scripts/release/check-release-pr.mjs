import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { pathToFileURL } from "node:url";
import { github } from "./github.mjs";
import { readVersionAt, releaseIdentity } from "./production-policy.mjs";
import { publicationSnapshot, publishedHistory, checkPublishedHistory, checkUnreservedPublication } from "./production-publication.mjs";
import { checkVersion } from "./version-policy.mjs";

export async function checkReleasePr(api, git, gradle, changelog) {
  const snapshot = await publicationSnapshot(api);
  checkUnreservedPublication(snapshot);
  const head = git("rev-parse", "HEAD");
  const { published, baseline } = publishedHistory(snapshot.releases, snapshot.tags,
    commit => readVersionAt(git, commit, head));
  checkPublishedHistory(changelog, published);
  const version = checkVersion(gradle, changelog, baseline);
  releaseIdentity(head, gradle, changelog);
  return version;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const version = await checkReleasePr(github(process.env),
    (...args) => execFileSync("git", args, { encoding: "utf8" }).trim(),
    readFileSync("app/build.gradle.kts", "utf8"), readFileSync("CHANGELOG.md", "utf8"));
  console.log(JSON.stringify(version));
}
