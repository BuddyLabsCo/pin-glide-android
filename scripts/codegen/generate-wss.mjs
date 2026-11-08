import assert from "node:assert/strict";
import { mkdirSync, rmSync, writeFileSync } from "node:fs";
import { join, resolve, sep } from "node:path";
import { fileURLToPath } from "node:url";
import { wssModels } from "./wss-models.mjs";

const root = fileURLToPath(new URL("../../", import.meta.url));
const output = resolve(root, "build/generated/service/wss");
const models = await wssModels();
assert.ok(output.startsWith(resolve(root, "build") + sep));
rmSync(output, { recursive: true, force: true });
mkdirSync(output, { recursive: true });
for (const model of models) {
  writeFileSync(join(output, `${model.modelName}.kt`), model.result + "\n");
}
console.log(`Generated ${models.length} concrete WSS models.`);
