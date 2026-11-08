import assert from "node:assert/strict";
import { execFileSync, spawnSync } from "node:child_process";
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { test } from "node:test";

const script = fileURLToPath(new URL("./check-file-size.mjs", import.meta.url));

function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), "pin-glide-size-test-"));
  execFileSync("git", ["init", "--quiet", root]);
  execFileSync("git", ["config", "core.autocrlf", "false"], { cwd: root });
  t.after(() => {
    assert.equal(dirname(resolve(root)), resolve(tmpdir()));
    rmSync(root, { recursive: true, force: true });
  });
  return root;
}

function run(root) {
  return spawnSync(process.execPath, [script], { cwd: root, encoding: "utf8" });
}

test("accepts 500 physical lines with CRLF and with no final newline", (t) => {
  const root = fixture(t);
  writeFileSync(join(root, "Boundary.kt"), "// line\r\n".repeat(500));
  writeFileSync(join(root, "build.gradle.kts"), "// line\n".repeat(499) + "// final");
  assert.equal(run(root).status, 0);
});

test("reports 501 lines and checks both tracked and untracked sources", (t) => {
  const root = fixture(t);
  writeFileSync(join(root, "Tracked.kt"), "// line\n".repeat(501));
  execFileSync("git", ["add", "Tracked.kt"], { cwd: root });
  writeFileSync(join(root, "Untracked.kt"), "// line\n".repeat(501));
  const result = run(root);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /Tracked\.kt: 501 physical lines/);
  assert.match(result.stderr, /Untracked\.kt: 501 physical lines/);
});

test("excludes generated build output and ignores deleted tracked sources", (t) => {
  const root = fixture(t);
  writeFileSync(join(root, "Deleted.kt"), "");
  execFileSync("git", ["add", "Deleted.kt"], { cwd: root });
  rmSync(join(root, "Deleted.kt"));
  mkdirSync(join(root, "app", "build"), { recursive: true });
  writeFileSync(join(root, "app", "build", "Generated.kt"), "// line\n".repeat(501));
  writeFileSync(join(root, "Empty.kt"), "");
  assert.equal(run(root).status, 0);
});

test("rejects an unexpectedly empty source scope", (t) => {
  const root = fixture(t);
  const result = run(root);
  assert.equal(result.status, 1);
  assert.match(result.stderr, /No handwritten Kotlin/);
});
