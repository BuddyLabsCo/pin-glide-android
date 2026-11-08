import { execFileSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";

const root = execFileSync("git", ["rev-parse", "--show-toplevel"], {
  encoding: "utf8",
}).trim();
const output = execFileSync(
  "git",
  ["ls-files", "-z", "--cached", "--others", "--exclude-standard", "--", "*.kt", "*.kts"],
  { cwd: root, encoding: "utf8" },
);
const paths = [...new Set(output.split("\0"))].filter(
  (path) => path && !/(^|\/)(build|\.gradle|vendor|third-party)\//.test(path),
);
let checked = 0;
let failed = false;
for (const path of paths) {
  const absolutePath = join(root, path);
  if (!existsSync(absolutePath)) continue;
  const text = readFileSync(absolutePath, "utf8");
  const lines =
    text === "" ? 0 : text.replace(/\r\n?/g, "\n").replace(/\n$/, "").split("\n").length;
  checked += 1;
  if (lines > 500) {
    console.error(`${path}: ${lines} physical lines (maximum 500)`);
    failed = true;
  }
}
if (checked === 0) {
  console.error("No handwritten Kotlin or Gradle Kotlin sources found.");
  failed = true;
}
if (failed) process.exitCode = 1;
else console.log(`File-size check passed: ${checked} files, maximum 500 physical lines.`);
