import assert from "node:assert/strict";
import { test } from "node:test";
import { wssModels } from "./wss-models.mjs";

test("generates deterministic, concrete annotated WSS models with original wire names", async () => {
  const first = await wssModels();
  const second = await wssModels();
  assert.deepEqual(first.map(({ modelName, result }) => ({ modelName, result })),
    second.map(({ modelName, result }) => ({ modelName, result })));
  const businessError = first.find((model) => model.modelName === "BusinessError").result;
  assert.match(businessError, /val code: String,/);
  assert.match(businessError, /val errors: List<ErrorItem>\? = null/);
  assert.match(businessError, /SerialName\("code"\)/);
  assert.ok(first.some((model) => /SerialName\("new_receiver"\)/.test(model.result)));
});
