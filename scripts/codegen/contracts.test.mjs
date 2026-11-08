import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { cpSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join, resolve } from "node:path";
import { test } from "node:test";
import { parse, stringify } from "yaml";
import { contractRoot, readContracts } from "./contracts.mjs";
import { wssModels } from "./wss-models.mjs";

function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), "pin-glide-contract-test-"));
  cpSync(contractRoot, root, { recursive: true });
  t.after(() => {
    assert.equal(dirname(resolve(root)), resolve(tmpdir()));
    rmSync(root, { recursive: true, force: true });
  });
  return root;
}

function changeDocument(root, change) {
  const manifestPath = join(root, "manifest.json");
  const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
  const path = join(root, manifest.releaseTag, "openapi.yaml");
  const document = parse(readFileSync(path, "utf8"));
  change(document);
  const data = stringify(document);
  writeFileSync(path, data);
  manifest.assets["openapi.yaml"] = createHash("sha256").update(data).digest("hex");
  writeFileSync(manifestPath, JSON.stringify(manifest));
}

test("accepts the exact pinned release assets and independent contract version", () => {
  assert.equal(readContracts().manifest.contractVersion, "1.0.0");
});

test("rejects modified release bytes", (t) => {
  const root = fixture(t);
  writeFileSync(join(root, "v1.0.1", "openapi.yaml"), "# modified\n");
  assert.throws(() => readContracts(root), /hash mismatch/);
});

test("rejects mismatched contract versions even with a corresponding new hash", (t) => {
  const root = fixture(t);
  changeDocument(root, (document) => { document.info.version = "999.0.0"; });
  assert.throws(() => readContracts(root), /contract version mismatch/);
});

test("rejects unresolved and external references without fetching them", (t) => {
  const root = fixture(t);
  changeDocument(root, (document) => {
    document.components.schemas.Broken = { $ref: "#/components/schemas/Missing" };
  });
  assert.throws(() => readContracts(root), /Unresolved reference/);
  changeDocument(root, (document) => {
    document.components.schemas.Broken = { $ref: "https://example.invalid/schema" };
  });
  assert.throws(() => readContracts(root), /External reference/);
});

test("generates deterministic, concrete annotated WSS models with original wire names", async () => {
  const first = await wssModels();
  const second = await wssModels();
  assert.deepEqual(first.map(({modelName, result}) => ({modelName, result})),
    second.map(({modelName, result}) => ({modelName, result})));
  const businessError = first.find((model) => model.modelName === "BusinessError").result;
  assert.match(businessError, /val code: String,/);
  assert.match(businessError, /val errors: List<ErrorItem>\? = null/);
  assert.match(businessError, /SerialName\("code"\)/);
  assert.ok(first.some((model) => /SerialName\("new_receiver"\)/.test(model.result)));
});
