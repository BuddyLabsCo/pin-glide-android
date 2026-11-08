import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { parse } from "yaml";

export const contractRoot = fileURLToPath(new URL("../../contracts/service/", import.meta.url));

function validateReferences(value, document) {
  if (value === null || typeof value !== "object") return;
  if (typeof value.$ref === "string") {
    assert.ok(value.$ref.startsWith("#/"), `External reference is not allowed: ${value.$ref}`);
    let target = document;
    for (const encoded of value.$ref.slice(2).split("/")) {
      const key = decodeURIComponent(encoded).replaceAll("~1", "/").replaceAll("~0", "~");
      assert.ok(target && Object.hasOwn(target, key), `Unresolved reference: ${value.$ref}`);
      target = target[key];
    }
  }
  for (const child of Object.values(value)) validateReferences(child, document);
}

export function readContracts(root = contractRoot) {
  const manifest = JSON.parse(readFileSync(join(root, "manifest.json"), "utf8"));
  assert.equal(manifest.repository, "BuddyLabsCo/pin-glide-service");
  assert.match(manifest.releaseTag, /^v\d+\.\d+\.\d+$/);
  assert.match(manifest.sourceCommit, /^[a-f0-9]{40}$/);
  assert.deepEqual(Object.keys(manifest.assets).sort(), ["asyncapi.yaml", "openapi.yaml"]);
  const documents = {};
  for (const [name, expected] of Object.entries(manifest.assets)) {
    const data = readFileSync(join(root, manifest.releaseTag, name));
    assert.equal(createHash("sha256").update(data).digest("hex"), expected, `${name} hash mismatch`);
    const document = parse(data.toString("utf8"));
    assert.equal(document.info.version, manifest.contractVersion, `${name} contract version mismatch`);
    validateReferences(document, document);
    documents[name] = document;
  }
  assert.match(documents["openapi.yaml"].openapi, /^3\.1\./);
  assert.equal(documents["asyncapi.yaml"].asyncapi, "3.1.0");
  return { manifest, documents, directory: join(root, manifest.releaseTag) };
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const { manifest } = readContracts();
  console.log(`Verified service ${manifest.releaseTag}, contract ${manifest.contractVersion}.`);
}
