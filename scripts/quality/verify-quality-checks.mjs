import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { existsSync, unlinkSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../../", import.meta.url));
const probe = fileURLToPath(
  new URL(
    "../../app/src/main/kotlin/com/buddylabs/pinglide/app/QualityGateProbe.kt",
    import.meta.url,
  ),
);
const wrapper = fileURLToPath(new URL("../../gradle/wrapper/gradle-wrapper.jar", import.meta.url));
assert.equal(existsSync(probe), false, "Refusing to overwrite an existing probe file.");

function verifyFailure(task, source, expected) {
  writeFileSync(probe, source);
  try {
    const result = spawnSync(
      "java",
      ["-cp", wrapper, "org.gradle.wrapper.GradleWrapperMain", task, "--console=plain"],
      { cwd: root, encoding: "utf8", maxBuffer: 16 * 1024 * 1024 },
    );
    assert.ifError(result.error);
    const output = `${result.stdout}\n${result.stderr}`;
    assert.notEqual(result.status, null, output);
    assert.notEqual(result.status, 0, `${task} accepted its deliberately invalid input.\n${output}`);
    for (const pattern of expected) assert.match(output, pattern);
    console.log(`Verified ${task}: expected violation rejected.`);
  } finally {
    unlinkSync(probe);
  }
}

verifyFailure(
  "spotlessCheck",
  "package com.buddylabs.pinglide.app\n\nfun badFormat( )=Unit\n",
  [/format violations/i],
);

verifyFailure("checkFileSize", "// size probe\n".repeat(501), [/501 physical lines/]);

const branches = Array.from(
  { length: 14 },
  (_, index) => `    if (value > ${index}) result++`,
).join("\n");
verifyFailure(
  "detekt",
  `package com.buddylabs.pinglide.app

fun complexProbe(value: Int): Int {
    var result = 0
${branches}
    return result
}

fun nestedProbe(value: Int) {
    if (value > 0) {
        if (value > 1) {
            if (value > 2) {
                if (value > 3) {
                    if (value > 4) {
                        println(value)
                    }
                }
            }
        }
    }
}
`,
  [/CyclomaticComplexMethod/, /NestedBlockDepth/],
);

verifyFailure(
  ":app:lintDebug",
  `package com.buddylabs.pinglide.app

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf

@Composable
fun UnrememberedStateProbe() {
    val state = mutableStateOf(0)
    Text(text = state.value.toString())
}
`,
  [/UnrememberedMutableState/],
);

console.log("All four static gates rejected their representative invalid inputs.");
