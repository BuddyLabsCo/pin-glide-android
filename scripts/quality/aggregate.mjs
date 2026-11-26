import { pathToFileURL } from "node:url";

export function qualityPassed(needs) {
  if (needs.classify?.result !== "success" || needs.lightweight?.result !== "success") return false;
  const { android, instrumentation } = needs.classify.outputs ?? {};
  if (![android, instrumentation].every((value) => value === "true" || value === "false")) return false;
  if (instrumentation === "true" && android === "false") return false;
  return Object.entries({ android, instrumentation }).every(
    ([job, selected]) => needs[job]?.result === (selected === "true" ? "success" : "skipped"),
  );
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const needs = JSON.parse(process.env.QUALITY_NEEDS);
  console.log(JSON.stringify(needs, null, 2));
  if (!qualityPassed(needs)) {
    console.error("Quality failed: classification or job results do not match the required selection.");
    process.exitCode = 1;
  }
}
