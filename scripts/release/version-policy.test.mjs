import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";
import { fileURLToPath, pathToFileURL } from "node:url";
import { checkVersion, proposeRelease } from "./version-policy.mjs";

const initial = { versionName: "0.1.0", versionCode: 1 };
const gradle = (versionName = "0.1.0", versionCode = 1) =>
  `android {\n    defaultConfig {\n        versionCode = ${versionCode}\n        versionName = "${versionName}"\n    }\n}\n`;
const history = "# Changelog\n\n## 0.1.0 (versionCode 1)\n\n- Initial release.\n";

test("first proposal keeps 0.1.0 and code 1", () => {
  const proposal = proposeRelease(gradle(), history, null, "- First release notes.");
  assert.deepEqual(checkVersion(proposal.gradle, proposal.changelog, null), initial);
  assert.equal(proposal.changelog, history.replace("Initial release.", "First release notes."));
  assert.throws(() => proposeRelease(gradle(), history, null, "- Notes.", "1.0.0"));
});

test("next proposal increments the patch and code once, not on refresh", () => {
  const proposal = proposeRelease(gradle(), history, initial, "- New fix.");
  assert.deepEqual(checkVersion(proposal.gradle, proposal.changelog, initial), {
    versionName: "0.1.1", versionCode: 2,
  });
  const refreshed = proposeRelease(proposal.gradle, proposal.changelog, initial, "- Updated fix.");
  assert.deepEqual(checkVersion(refreshed.gradle, refreshed.changelog, initial), {
    versionName: "0.1.1", versionCode: 2,
  });
  assert.equal(refreshed.changelog, "# Changelog\n\n## 0.1.1 (versionCode 2)\n\n- Updated fix.\n\n" +
    history.slice("# Changelog\n\n".length));
});

test("preserves a maintainer's higher stable version while refreshing", () => {
  const proposal = proposeRelease(gradle(), history, initial, "- New feature.", "2.0.0");
  const refreshed = proposeRelease(proposal.gradle, proposal.changelog, initial, "- More notes.");
  assert.deepEqual(checkVersion(refreshed.gradle, refreshed.changelog, initial), {
    versionName: "2.0.0", versionCode: 2,
  });
  const revised = proposeRelease(refreshed.gradle, refreshed.changelog, initial, "- Notes.", "1.0.0");
  assert.deepEqual(checkVersion(revised.gradle, revised.changelog, initial), {
    versionName: "1.0.0", versionCode: 2,
  });
});

test("compares version components numerically and supports minor changes", () => {
  const previous = { versionName: "0.9.9", versionCode: 2 };
  const previousHistory = history.replace("## 0.1.0", "## 0.9.9 (versionCode 2)\n\n- Later.\n\n## 0.1.0");
  const proposal = proposeRelease(gradle("0.9.9", 2), previousHistory, previous, "- Notes.", "0.10.0");
  assert.deepEqual(checkVersion(proposal.gradle, proposal.changelog, previous), {
    versionName: "0.10.0", versionCode: 3,
  });
});

test("rejects malformed, equal and older stable version selections", () => {
  for (const selected of ["0.1.0", "0.0.9", "01.2.3", "v1.2.3", "1.2", "1.2.3-dev", "1.2.3+build", "1.2.3\n"]) {
    assert.throws(() => proposeRelease(gradle(), history, initial, "- Notes.", selected), undefined, selected);
  }
});

test("rejects mismatched Gradle metadata, reused versions or codes, and skipped codes", () => {
  assert.throws(() => checkVersion(gradle("0.1.1"), history));
  assert.throws(() => checkVersion(gradle("0.1.0", 2), history));
  for (const [name, code] of [["0.1.0", 2], ["0.1.1", 1], ["0.1.1", 3], ["0.0.9", 2]]) {
    assert.throws(() => checkVersion(gradle(name, code),
      history.replace("## 0.1.0", `## ${name} (versionCode ${code})\n\n- Notes.\n\n## 0.1.0`)));
  }
  assert.throws(() => checkVersion(gradle(), history, initial));
  assert.throws(() => checkVersion(gradle(), history.replace("versionCode 1", "versionCode 01")));
  assert.throws(() => checkVersion(gradle(), history.replace("- Initial release.", "")));
  assert.throws(() => checkVersion(gradle("0.1.0", 0), history));
  assert.throws(() => checkVersion(gradle("0.1.0", 1) + '\nversionName = "9.0.0"\n', history));
});

test("published baseline must identify the previous changelog release", () => {
  const proposal = proposeRelease(gradle(), history, initial, "- Notes.");
  assert.throws(() => checkVersion(proposal.gradle, proposal.changelog, null));
  assert.throws(() => checkVersion(proposal.gradle, proposal.changelog, { versionName: "0.0.9", versionCode: 1 }));
  assert.throws(() => proposeRelease(proposal.gradle, proposal.changelog, { versionName: "0.1.0", versionCode: 2 }, "- Notes."));
});

test("rejects invalid codes and changelog notes instead of generating inconsistent metadata", () => {
  for (const code of ["01", "-1", "1.5", "1 + 1"]) {
    assert.throws(() => checkVersion(gradle("0.1.0", code), history), /literal versionCode/);
  }
  assert.throws(() => checkVersion(gradle("0.1.0", 2100000001), history), /Invalid Android versionCode: 2100000001/);
  assert.throws(() => checkVersion(gradle("0.1.0", 2100000000), history), /Gradle and changelog version metadata must agree/);
  assert.throws(() => proposeRelease(gradle(), history, initial, ""));
  assert.throws(() => proposeRelease(gradle(), history, initial, "## Unrelated heading\n\n- Notes."));
});

test("only accepts active literal assignments in defaultConfig", () => {
  const commented = gradle().replace("        versionCode", "        /*\n        versionCode")
    .replace('versionName = "0.1.0"', 'versionName = "0.1.0"\n        */');
  assert.throws(() => checkVersion(commented, history), /defaultConfig|unsupported/i);
  assert.throws(() => checkVersion(gradle().replace("defaultConfig", "release"), history), /defaultConfig/);
  assert.throws(() => checkVersion(gradle().replace("android", "other"), history), /defaultConfig/);
  assert.throws(() => checkVersion("android {\n    other {\n" +
    gradle().split("\n").slice(1, -2).map(line => `    ${line}`).join("\n") + "\n    }\n}\n", history), /defaultConfig/);
  assert.throws(() => checkVersion(gradle().replace("defaultConfig {", "defaultConfig {\n        other {")
    .replace("    }\n}", "        }\n    }\n}"), history), /defaultConfig/);
});

const cliPath = fileURLToPath(new URL("./version-policy.mjs", import.meta.url));

function checkout(context) {
  const directory = mkdtempSync(join(tmpdir(), "pinglide-version-"));
  context.after(() => rmSync(directory, { recursive: true, force: true }));
  mkdirSync(join(directory, "app"));
  const gradleFile = join(directory, "app", "build.gradle.kts");
  const changelogFile = join(directory, "CHANGELOG.md");
  const notesFile = join(directory, "notes.md");
  writeFileSync(gradleFile, gradle());
  writeFileSync(changelogFile, history);
  writeFileSync(notesFile, "- First notes.");
  const run = (args, options = []) => {
    const result = spawnSync(process.execPath, [...options, cliPath, ...args], {
      cwd: directory, encoding: "utf8",
      env: Object.fromEntries(Object.entries(process.env).filter(([name]) => !name.startsWith("PINGLIDE_"))),
    });
    assert.ifError(result.error);
    return result;
  };
  const contents = () => [readFileSync(gradleFile, "utf8"), readFileSync(changelogFile, "utf8")];
  return { directory, notesFile, run, contents };
}

test("CLI generates initial and subsequent proposals, refreshes and checks the baseline", context => {
  const fixture = checkout(context);
  function succeeds(args, expected) {
    const result = fixture.run(args);
    assert.equal(result.status, 0, result.stderr);
    assert.deepEqual(JSON.parse(result.stdout), expected);
  }
  succeeds(["propose", "none", "notes.md"], initial);
  const firstHistory = history.replace("Initial release.", "First notes.");
  assert.deepEqual(fixture.contents(), [gradle(), firstHistory]);
  succeeds(["check", "none"], initial);
  const next = { versionName: "0.2.0", versionCode: 2 };
  writeFileSync(fixture.notesFile, "- New feature.");
  succeeds(["propose", "0.1.0:1", "notes.md", "0.2.0"], next);
  assert.deepEqual(fixture.contents(), [gradle("0.2.0", 2),
    "# Changelog\n\n## 0.2.0 (versionCode 2)\n\n- New feature.\n\n" + firstHistory.slice(13)]);
  writeFileSync(fixture.notesFile, "- Refreshed.");
  succeeds(["propose", "0.1.0:1", "notes.md"], next);
  const refreshed = [gradle("0.2.0", 2),
    "# Changelog\n\n## 0.2.0 (versionCode 2)\n\n- Refreshed.\n\n" + firstHistory.slice(13)];
  assert.deepEqual(fixture.contents(), refreshed);
  succeeds(["check", "0.1.0:1"], next);
  for (const [args, diagnostic] of [
    [["check", "0.2.0:2"], /higher stable version/],
    [["check", "0.1.0:01"], /Expected published version:code/],
    [["propose", "0.1.0:1", "notes.md", "0.1.0"], /higher stable version/],
    [["propose", "0.1.0:1", "missing.md"], /ENOENT/],
  ]) {
    const result = fixture.run(args);
    assert.equal(result.status, 1);
    assert.match(result.stderr, diagnostic);
    assert.deepEqual(fixture.contents(), refreshed);
  }
});

test("CLI restores both original files if the second staged replacement fails", context => {
  const fixture = checkout(context);
  const before = fixture.contents();
  const preload = join(fixture.directory, "fail-rename.mjs");
  writeFileSync(preload, `
    import fs from "node:fs";
    import { syncBuiltinESMExports } from "node:module";
    import { basename } from "node:path";
    const rename = fs.renameSync;
    fs.renameSync = (source, destination) => {
      if (basename(destination) === "CHANGELOG.md") throw new Error("Injected replacement failure");
      return rename(source, destination);
    };
    syncBuiltinESMExports();
  `);
  const result = fixture.run(["propose", "0.1.0:1", "notes.md"], ["--import", pathToFileURL(preload).href]);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /Injected replacement failure/);
  assert.deepEqual(fixture.contents(), before);
  assert.deepEqual(readdirSync(fixture.directory).sort(), ["CHANGELOG.md", "app", "fail-rename.mjs", "notes.md"]);
});
