import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdirSync, mkdtempSync, renameSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { test } from "node:test";
import { classifyChanges, classifyRange } from "./changes.mjs";

const kotlin = "app/src/main/kotlin/com/buddylabs/pinglide/";
const docsOnly = { android: false, instrumentation: false };
const androidOnly = { android: true, instrumentation: false };
const full = { android: true, instrumentation: true };

test("documentation alone skips Android checks", () => {
  assert.deepEqual(classifyChanges(["docs/guide.md", "docs/ui-prototype/index.html", "GLOSSARY.md"]), docsOnly);
});

test("exact UI-facing paths select Instrumentation even in mixed diffs", () => {
  for (const path of [
    ...["ui/home/HomeScreen.kt", "domain/identity/Login.kt", "domain/pairing/Pairing.kt",
      "domain/settings/Setup.kt", "domain/forwarding/PairingTestMessage.kt"].map((path) => kotlin + path),
    "app/src/main/res/values/strings.xml",
    "app/src/androidTest/kotlin/ScreenTest.kt",
  ]) {
    assert.deepEqual(classifyChanges([path]), full, path);
    assert.deepEqual(classifyChanges(["docs/guide.md", "build.gradle.kts", path]), full, path);
  }
});

test("other changes select Android checks without emulator execution", () => {
  for (const path of [
    ...["app/MainActivity.kt", "domain/forwarding/ForwardSms.kt", "domain/forwarding/PairingTestMessageTest.kt",
      "data/service/Client.kt", "data/storage/Store.kt", "data/bailian/Chat.kt",
      "platform/permissions/Permissions.kt", "platform/scanner/Scanner.kt", "platform/crypto/Crypto.kt",
      "ui-extra/Screen.kt"].map((path) => kotlin + path),
    "app/src/main/AndroidManifest.xml", "app/build.gradle.kts", "gradle/libs.versions.toml",
    "scripts/codegen/generate-wss.mjs", "config/codegen/service-contracts.json",
    "app/src/test/kotlin/ui/ScreenTest.kt", ".github/workflows/ci.yml",
  ]) {
    assert.deepEqual(classifyChanges([path]), androidOnly, path);
  }
});

test("main, empty and unavailable comparisons select the full baseline", () => {
  assert.deepEqual(classifyChanges([]), full);
  assert.deepEqual(classifyRange("push", "", ""), full);
  assert.deepEqual(classifyRange("pull_request", "", ""), full);
  assert.deepEqual(classifyRange("pull_request", "missing-base", "HEAD"), full);
});

test("full Git comparison includes deletions, both rename ends and paths beyond 300", () => {
  const directory = mkdtempSync(join(tmpdir(), "pinglide-changes-"));
  const git = (...args) => execFileSync("git", args, { cwd: directory, encoding: "utf8" }).trim();
  const add = (path) => {
    const file = join(directory, path);
    mkdirSync(dirname(file), { recursive: true });
    writeFileSync(file, path);
  };
  const commit = () => {
    git("add", "-A");
    git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.com", "-c", "commit.gpgsign=false", "commit", "-qm", "fixture");
    return git("rev-parse", "HEAD");
  };
  const compare = (base, head) => classifyRange("pull_request", base, head, directory);
  const ui = kotlin + "ui/Old Screen.kt";
  try {
    git("init", "-q");
    add("docs/guide.md");
    add(ui);
    add(kotlin + "domain/identity/Login.kt");
    const base = commit();
    renameSync(join(directory, ui), join(directory, "docs/old screen.md"));
    const renamed = commit();
    assert.deepEqual(compare(base, renamed), full);
    rmSync(join(directory, kotlin + "domain/identity/Login.kt"));
    const deleted = commit();
    assert.deepEqual(compare(renamed, deleted), full);
    renameSync(join(directory, "docs/guide.md"), join(directory, ui));
    const moved = commit();
    assert.deepEqual(compare(deleted, moved), full);
    for (let index = 0; index < 301; index++) add(`aaa-doc-${index}.md`);
    const docs = commit();
    assert.deepEqual(compare(moved, docs), docsOnly);
    assert.deepEqual(compare(docs, moved), full);
    assert.deepEqual(compare("0".repeat(40), docs), full);
    const resource = "app/src/main/res/values/strings.xml";
    add(resource);
    const headWithResource = commit();
    assert.deepEqual(compare(moved, headWithResource), full);

    git("read-tree", moved);
    git("add", "--", resource);
    const baseWithResource = git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.com",
      "-c", "commit.gpgsign=false", "commit-tree", git("write-tree"), "-p", moved, "-m", "diverged base");
    assert.deepEqual(classifyChanges(git("diff", "--name-only", baseWithResource, headWithResource).split("\n")), docsOnly);
    assert.deepEqual(compare(baseWithResource, headWithResource), full);
    assert.deepEqual(compare(baseWithResource, docs), docsOnly);
  } finally {
    rmSync(directory, { recursive: true, force: true });
  }
});

function documentationHistory(context) {
  const directory = mkdtempSync(join(tmpdir(), "pinglide-history-"));
  context.after(() => rmSync(directory, { recursive: true, force: true }));
  const git = (...args) => execFileSync("git", args, { cwd: directory, encoding: "utf8" }).trim();
  git("init", "-q");
  const commit = (message, ...parents) => {
    writeFileSync(join(directory, "guide.md"), message);
    git("add", "guide.md");
    return git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.com", "-c", "commit.gpgsign=false",
      "commit-tree", git("write-tree"), ...parents.flatMap((parent) => ["-p", parent]), "-m", message);
  };
  const compare = (base, head) => classifyRange("pull_request", base, head, directory);
  return { directory, git, commit, compare };
}

test("shallow history selects full checks even when a documentation comparison is available", (context) => {
  const { directory, git, commit, compare } = documentationHistory(context);
  const root = commit("root");
  const base = commit("base", root);
  const head = commit("head", base);
  assert.deepEqual(compare(base, head), docsOnly);

  writeFileSync(join(directory, ".git", "shallow"), `${base}\n`);
  assert.equal(git("rev-parse", "--is-shallow-repository"), "true");
  assert.equal(git("merge-base", "--all", base, head), base);
  assert.equal(git("diff", "--name-only", base, head), "guide.md");
  assert.deepEqual(compare(base, head), full);
});

test("multiple best merge bases select full checks even when either comparison is documentation-only", (context) => {
  const { git, commit, compare } = documentationHistory(context);
  const root = commit("root");
  const left = commit("left", root);
  const right = commit("right", root);
  const base = commit("base merge", left, right);
  const head = commit("head merge", right, left);

  assert.deepEqual(git("merge-base", "--all", base, head).split("\n").sort(), [left, right].sort());
  for (const ancestor of [left, right]) {
    assert.equal(git("diff", "--name-only", ancestor, head), "guide.md");
    assert.deepEqual(compare(ancestor, head), docsOnly);
  }
  assert.deepEqual(compare(base, head), full);
});
