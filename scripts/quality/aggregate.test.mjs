import assert from "node:assert/strict";
import { test } from "node:test";
import { qualityPassed } from "./aggregate.mjs";

function needs(android, instrumentation) {
  return {
    classify: { result: "success", outputs: { android: String(android), instrumentation: String(instrumentation) } },
    lightweight: { result: "success" },
    android: { result: android ? "success" : "skipped" },
    instrumentation: { result: instrumentation ? "success" : "skipped" },
  };
}

test("only the exact classified selection passes the aggregate", () => {
  for (const selection of [[false, false], [true, false], [true, true]]) {
    const baseline = needs(...selection);
    assert.equal(qualityPassed(baseline), true);
    for (const job of ["lightweight", "android", "instrumentation"]) {
      for (const result of ["success", "skipped", "failure", "cancelled", undefined]) {
        if (result === baseline[job].result) continue;
        assert.equal(qualityPassed({ ...baseline, [job]: { result } }), false, `${job}: ${result}`);
      }
    }
  }
});

test("classification must succeed and emit a valid selection", () => {
  for (const result of ["failure", "cancelled", "skipped", undefined]) {
    assert.equal(qualityPassed({ ...needs(false, false), classify: { result, outputs: {} } }), false);
  }
  for (const outputs of [{}, { android: "false", instrumentation: "true" }, { android: "true", instrumentation: "" }]) {
    assert.equal(qualityPassed({ ...needs(false, false), classify: { result: "success", outputs } }), false);
  }
  assert.equal(qualityPassed({}), false);
});
