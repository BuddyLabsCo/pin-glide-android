import { execFileSync } from "node:child_process";
import { pathToFileURL } from "node:url";

const full = { android: true, instrumentation: true };
const uiPath = /^app\/src\/(?:main\/(?:kotlin\/com\/buddylabs\/pinglide\/(?:ui\/|domain\/(?:identity\/|pairing\/|settings\/|forwarding\/PairingTestMessage\.kt$))|res\/)|androidTest\/)/;
const documentation = /^(?:docs\/|[^/]+\.md$)/;

export function classifyChanges(paths) {
  if (paths.length === 0) return full;
  const instrumentation = paths.some((path) => uiPath.test(path));
  return {
    android: instrumentation || paths.some((path) => !documentation.test(path)),
    instrumentation,
  };
}

export function classifyRange(event, base, head, cwd = process.cwd()) {
  if (event !== "pull_request") return full;
  try {
    if (!/^[0-9a-f]{40}$/.test(base) || !/^[0-9a-f]{40}$/.test(head)) {
      throw new Error("Missing or invalid comparison commits");
    }
    const git = (...args) => execFileSync("git", args, {
      cwd, encoding: "utf8", maxBuffer: 16 * 1024 * 1024, stdio: ["ignore", "pipe", "pipe"],
    });
    if (git("rev-parse", "--is-shallow-repository").trim() !== "false") {
      throw new Error("Incomplete comparison history");
    }
    const mergeBases = git("merge-base", "--all", base, head).trim().split(/\s+/);
    if (mergeBases.length !== 1 || !/^[0-9a-f]{40}$/.test(mergeBases[0])) {
      throw new Error("No unique merge base");
    }
    const paths = git("diff", "--name-only", "--no-renames", "-z", mergeBases[0], head, "--");
    return classifyChanges(paths.split("\0").filter(Boolean));
  } catch (error) {
    console.error(`Cannot compare changed paths; running all checks: ${error.message}`);
    return full;
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  for (const [name, enabled] of Object.entries(classifyRange(...process.argv.slice(2)))) {
    console.log(`${name}=${enabled}`);
  }
}
