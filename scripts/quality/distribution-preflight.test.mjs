import assert from "node:assert/strict";
import { test } from "node:test";
import { preflight } from "../distribution/preflight.mjs";

test("branch dispatch allows unrelated repository configuration", () => {
  assert.doesNotThrow(() => preflight("branch", { UNRELATED: "value" }, "false"));
});

test("distribution preflight rejects tags and all public fallback variables, even blank", () => {
  assert.throws(() => preflight("tag", {}, "false"), /branch/);
  for (const name of ["PINGLIDE_SERVICE_URL", "PINGLIDE_QQ_APP_ID", "PINGLIDE_SIGNING_CERT_SHA256"]) {
    for (const value of ["", "configured"]) {
      assert.throws(() => preflight("branch", { [name]: value }, "false"), new RegExp(name));
    }
  }
});

test("distribution preflight rejects signing fallback and missing presence information", () => {
  for (const value of ["true", undefined]) {
    assert.throws(() => preflight("branch", {}, value), /signing secrets/);
  }
});
