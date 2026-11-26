import assert from "node:assert/strict";
import { pathToFileURL } from "node:url";

export function preflight(refType, variables, signingFallback) {
  assert.equal(refType, "branch", "Select a source branch");
  const distributionVariables = [
    "PINGLIDE_SERVICE_URL",
    "PINGLIDE_QQ_APP_ID",
    "PINGLIDE_SIGNING_CERT_SHA256",
  ];
  for (const name of Object.keys(variables)) {
    assert.ok(!distributionVariables.includes(name.toUpperCase()),
      `Remove repository/organization ${name}; configure it only in the Environment.`);
  }
  assert.equal(signingFallback, "false",
    "Remove repository/organization distribution signing secrets; configure them only in the Environment.");
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  preflight(process.env.GITHUB_REF_TYPE, JSON.parse(process.env.PUBLIC_VARIABLES),
    process.env.SIGNING_FALLBACK);
}
