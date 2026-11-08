import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { Parser, isAsyncAPIDocument } from "@asyncapi/parser";
import { AsyncAPIInputProcessor, InputProcessor, KotlinGenerator } from "@asyncapi/modelina";
import { readContracts } from "./contracts.mjs";

// shortcut: Modelina 5.10.1 gates out parsed 3.1 documents; remove when upstream supports them.
class ParsedAsyncAPI31 extends AsyncAPIInputProcessor {
  shouldProcess(input) {
    return isAsyncAPIDocument(input) && input.version() === "3.1.0";
  }
}

function kotlinString(value) {
  return JSON.stringify(value).replaceAll("$", "\\$");
}

const serializable = {
  class: {
    self: ({ content }) => `@kotlinx.serialization.Serializable\n${content}`,
    property: ({ property }) => {
      const original = property.property.originalInput;
      const nullable = !property.required ||
        (Array.isArray(original?.type) && original.type.includes("null"));
      const type = property.property.type + (nullable && !property.property.type.endsWith("?") ? "?" : "");
      return `@kotlinx.serialization.SerialName(${kotlinString(property.unconstrainedPropertyName)})\n` +
        `val ${property.propertyName}: ${type}${property.required ? "" : " = null"},`;
    },
  },
  enum: {
    self: ({ content }) => `@kotlinx.serialization.Serializable\n${content}`,
    item: ({ item, content }) => {
      assert.equal(typeof item.originalInput, "string", "Only string wire enums are supported.");
      return `@kotlinx.serialization.SerialName(${kotlinString(item.originalInput)})\n${content}`;
    },
  },
};

export async function wssModels() {
  const { directory } = readContracts();
  const { document, diagnostics } = await new Parser().parse(
    readFileSync(join(directory, "asyncapi.yaml"), "utf8"),
  );
  assert.ok(document, JSON.stringify(diagnostics));
  assert.ok(!diagnostics.some((diagnostic) => diagnostic.severity === 0), JSON.stringify(diagnostics));
  InputProcessor.processor.setProcessor("asyncapi", new ParsedAsyncAPI31());
  const generator = new KotlinGenerator({
    presets: [serializable],
    typeMapping: {
      String: () => "String",
      Any: () => "kotlinx.serialization.json.JsonElement",
    },
  });
  const generated = await generator.generateCompleteModels(document, {
    packageName: "com.buddylabs.pinglide.generated.wss",
  });
  const models = generated.filter((model) => model.modelName);
  assert.ok(models.length > 0, "No concrete WSS models were generated.");
  for (const model of models) {
    assert.match(model.modelName, /^[A-Z][A-Za-z0-9_]*$/);
    assert.match(model.result, /\b(?:data class|enum class|class)\s/);
    assert.ok(model.result.includes("@kotlinx.serialization.Serializable"));
  }
  for (const name of [
    "PairingRelayIn", "PairingRelayOut", "PairingState", "CredentialReady",
    "WssError", "DeviceRevoked", "PairingRemoved", "Receipt", "Delivery",
  ]) {
    assert.ok(models.some((model) => model.modelName === name), `Missing WSS model: ${name}`);
  }
  assert.equal(new Set(models.map((model) => model.modelName)).size, models.length);
  return models.sort((left, right) => left.modelName.localeCompare(right.modelName, "en"));
}
