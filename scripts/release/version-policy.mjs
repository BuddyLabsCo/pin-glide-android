import { readFileSync, writeFileSync } from "node:fs";
import { pathToFileURL } from "node:url";

const initial = { versionName: "0.1.0", versionCode: 1 };
const stable = /^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;
const gradlePath = "app/build.gradle.kts";
const changelogPath = "CHANGELOG.md";

function requireVersion(versionName) {
  if (typeof versionName !== "string" || versionName.trim() !== versionName || !stable.test(versionName)) {
    throw new Error(`Expected stable version, found ${versionName}`);
  }
  return versionName;
}

function metadata(versionName, versionCode) {
  requireVersion(versionName);
  if (!Number.isInteger(versionCode) || versionCode < 1 || versionCode > 2100000000) {
    throw new Error(`Invalid Android versionCode: ${versionCode}`);
  }
  return { versionName, versionCode };
}

function compareVersions(left, right) {
  const parts = requireVersion(left).split(".").map(BigInt);
  const other = requireVersion(right).split(".").map(BigInt);
  for (const [index, part] of parts.entries()) {
    if (part !== other[index]) return part > other[index] ? 1 : -1;
  }
  return 0;
}

function sameVersion(left, right) {
  return left?.versionName === right.versionName && left?.versionCode === right.versionCode;
}

function requireSuccessor(current, previous) {
  if (compareVersions(current.versionName, previous.versionName) <= 0 ||
      current.versionCode !== previous.versionCode + 1) {
    throw new Error("Release must have a higher stable version and increment versionCode by exactly one");
  }
}

function assignment(name) {
  return new RegExp(`^(\\s*${name}\\s*=\\s*)([^\\r\\n]+)(?=\\r?$)`, "gm");
}

function readAssignment(gradle, name, pattern) {
  const matches = [...gradle.matchAll(assignment(name))];
  const value = matches.length === 1 && pattern.exec(matches[0][2].trim());
  if (!value) throw new Error(`Expected exactly one literal ${name} assignment in ${gradlePath}`);
  return value[1];
}

function readGradle(gradle) {
  return metadata(
    readAssignment(gradle, "versionName", /^"([^"]+)"$/),
    Number(readAssignment(gradle, "versionCode", /^([1-9]\d*)$/)),
  );
}

function readChangelog(changelog) {
  const [preamble, ...sections] = changelog.replaceAll("\r\n", "\n").trim().split(/(?=^##(?:[ \t]|$))/m);
  if (preamble.trim() !== "# Changelog" || sections.length === 0) {
    throw new Error("Expected # Changelog and release sections");
  }
  const releases = sections.map(section => {
    const heading = /^## (\S+) \(versionCode ([1-9]\d*)\)\n/.exec(section);
    if (!heading || !section.slice(heading[0].length).trim()) {
      throw new Error("Expected a version and versionCode heading with release notes");
    }
    return { ...metadata(heading[1], Number(heading[2])), section: section.trimEnd() };
  });
  if (!sameVersion(releases.at(-1), initial)) throw new Error("Changelog must start at 0.1.0 / code 1");
  for (let index = 1; index < releases.length; index++) {
    requireSuccessor(releases[index - 1], releases[index]);
  }
  return releases;
}

export function checkVersion(gradle, changelog, published) {
  const current = readGradle(gradle);
  const releases = readChangelog(changelog);
  if (!sameVersion(releases[0], current)) throw new Error("Gradle and changelog version metadata must agree");
  if (published === null) {
    if (!sameVersion(current, initial)) throw new Error("First production release must be 0.1.0 / code 1");
  } else if (published !== undefined) {
    metadata(published.versionName, published.versionCode);
    requireSuccessor(current, published);
    if (!sameVersion(releases[1], published)) throw new Error("Changelog must include the previous published release");
  }
  return current;
}

export function proposeRelease(gradle, changelog, published, notes, selected) {
  const current = checkVersion(gradle, changelog);
  const releases = readChangelog(changelog);
  let next = initial;
  let kept = [];
  if (published === null) {
    checkVersion(gradle, changelog, null);
    if (selected !== undefined && selected !== initial.versionName) {
      throw new Error("First production release must be 0.1.0");
    }
  } else {
    metadata(published.versionName, published.versionCode);
    const refreshing = !sameVersion(current, published);
    if (refreshing) checkVersion(gradle, changelog, published);
    const defaultVersion = refreshing ? current.versionName :
      published.versionName.replace(/\d+$/, patch => String(BigInt(patch) + 1n));
    next = metadata(selected ?? defaultVersion, published.versionCode + 1);
    requireSuccessor(next, published);
    kept = refreshing ? releases.slice(1) : releases;
  }
  const updatedGradle = gradle
    .replace(assignment("versionName"), (_, prefix) => `${prefix}"${next.versionName}"`)
    .replace(assignment("versionCode"), (_, prefix) => `${prefix}${next.versionCode}`);
  const updatedChangelog = [
    "# Changelog",
    `## ${next.versionName} (versionCode ${next.versionCode})\n\n${notes.trim()}`,
    ...kept.map(release => release.section),
  ].join("\n\n") + "\n";
  checkVersion(updatedGradle, updatedChangelog, published);
  return { gradle: updatedGradle, changelog: updatedChangelog };
}

function parsePublished(value) {
  if (value === "none") return null;
  const match = /^([^:]+):([1-9]\d*)$/.exec(value);
  if (!match || match[0] !== value) throw new Error("Expected published version:code or none");
  return metadata(match[1], Number(match[2]));
}

function main([command, baseline, notesPath, selected, ...extra]) {
  const gradle = readFileSync(gradlePath, "utf8");
  const changelog = readFileSync(changelogPath, "utf8");
  if (command === "check" && notesPath === undefined) {
    console.log(JSON.stringify(checkVersion(gradle, changelog,
      baseline === undefined ? undefined : parsePublished(baseline))));
  } else if (command === "propose" && baseline && notesPath && extra.length === 0) {
    const published = parsePublished(baseline);
    const proposal = proposeRelease(gradle, changelog, published, readFileSync(notesPath, "utf8"), selected);
    writeFileSync(gradlePath, proposal.gradle);
    writeFileSync(changelogPath, proposal.changelog);
    console.log(JSON.stringify(checkVersion(proposal.gradle, proposal.changelog, published)));
  } else {
    throw new Error("Usage: version-policy.mjs check [none|version:code] or propose <none|version:code> <notes-file> [version]");
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    main(process.argv.slice(2));
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
