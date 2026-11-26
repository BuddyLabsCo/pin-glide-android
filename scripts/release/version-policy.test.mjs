import assert from "node:assert/strict";
import { test } from "node:test";
import { checkVersion, proposeRelease } from "./version-policy.mjs";

const initial = { versionName: "0.1.0", versionCode: 1 };
const gradle = (versionName = "0.1.0", versionCode = 1) =>
  `android {\n    defaultConfig {\n        versionCode = ${versionCode}\n        versionName = "${versionName}"\n    }\n}\n`;
const history = "# Changelog\n\n## 0.1.0 (versionCode 1)\n\n- Initial release.\n";

test("first proposal keeps 0.1.0 and code 1", () => {
  const proposal = proposeRelease(gradle(), history, null, "- First release notes.");
  assert.deepEqual(checkVersion(proposal.gradle, proposal.changelog, null), initial);
  assert.equal(proposal.changelog, history.replace("Initial release.", "First release notes."));
  assert.throws(() => proposeRelease(gradle(), history, null, "- Notes.", "1.0.0"));
});

test("next proposal increments the patch and code once, not on refresh", () => {
  const proposal = proposeRelease(gradle(), history, initial, "- New fix.");
  assert.deepEqual(checkVersion(proposal.gradle, proposal.changelog, initial), {
    versionName: "0.1.1", versionCode: 2,
  });
  const refreshed = proposeRelease(proposal.gradle, proposal.changelog, initial, "- Updated fix.");
  assert.deepEqual(checkVersion(refreshed.gradle, refreshed.changelog, initial), {
    versionName: "0.1.1", versionCode: 2,
  });
  assert.equal(refreshed.changelog, "# Changelog\n\n## 0.1.1 (versionCode 2)\n\n- Updated fix.\n\n" +
    history.slice("# Changelog\n\n".length));
});

test("preserves a maintainer's higher stable version while refreshing", () => {
  const proposal = proposeRelease(gradle(), history, initial, "- New feature.", "2.0.0");
  const refreshed = proposeRelease(proposal.gradle, proposal.changelog, initial, "- More notes.");
  assert.deepEqual(checkVersion(refreshed.gradle, refreshed.changelog, initial), {
    versionName: "2.0.0", versionCode: 2,
  });
  const revised = proposeRelease(refreshed.gradle, refreshed.changelog, initial, "- Notes.", "1.0.0");
  assert.deepEqual(checkVersion(revised.gradle, revised.changelog, initial), {
    versionName: "1.0.0", versionCode: 2,
  });
});

test("compares version components numerically and supports minor changes", () => {
  const previous = { versionName: "0.9.9", versionCode: 2 };
  const previousHistory = history.replace("## 0.1.0", "## 0.9.9 (versionCode 2)\n\n- Later.\n\n## 0.1.0");
  const proposal = proposeRelease(gradle("0.9.9", 2), previousHistory, previous, "- Notes.", "0.10.0");
  assert.deepEqual(checkVersion(proposal.gradle, proposal.changelog, previous), {
    versionName: "0.10.0", versionCode: 3,
  });
});

test("rejects malformed, equal and older stable version selections", () => {
  for (const selected of ["0.1.0", "0.0.9", "01.2.3", "v1.2.3", "1.2", "1.2.3-dev", "1.2.3+build", "1.2.3\n"]) {
    assert.throws(() => proposeRelease(gradle(), history, initial, "- Notes.", selected), undefined, selected);
  }
});

test("rejects mismatched Gradle metadata, reused versions or codes, and skipped codes", () => {
  assert.throws(() => checkVersion(gradle("0.1.1"), history));
  assert.throws(() => checkVersion(gradle("0.1.0", 2), history));
  for (const [name, code] of [["0.1.0", 2], ["0.1.1", 1], ["0.1.1", 3], ["0.0.9", 2]]) {
    assert.throws(() => checkVersion(gradle(name, code),
      history.replace("## 0.1.0", `## ${name} (versionCode ${code})\n\n- Notes.\n\n## 0.1.0`)));
  }
  assert.throws(() => checkVersion(gradle(), history, initial));
  assert.throws(() => checkVersion(gradle(), history.replace("versionCode 1", "versionCode 01")));
  assert.throws(() => checkVersion(gradle(), history.replace("- Initial release.", "")));
  assert.throws(() => checkVersion(gradle("0.1.0", 0), history));
  assert.throws(() => checkVersion(gradle("0.1.0", 1) + '\nversionName = "9.0.0"\n', history));
});

test("published baseline must identify the previous changelog release", () => {
  const proposal = proposeRelease(gradle(), history, initial, "- Notes.");
  assert.throws(() => checkVersion(proposal.gradle, proposal.changelog, null));
  assert.throws(() => checkVersion(proposal.gradle, proposal.changelog, { versionName: "0.0.9", versionCode: 1 }));
  assert.throws(() => proposeRelease(proposal.gradle, proposal.changelog, { versionName: "0.1.0", versionCode: 2 }, "- Notes."));
});

test("rejects invalid codes and changelog notes instead of generating inconsistent metadata", () => {
  for (const code of ["01", "-1", "1.5", "2100000001", "1 + 1"]) {
    assert.throws(() => checkVersion(gradle("0.1.0", code), history));
  }
  assert.throws(() => proposeRelease(gradle(), history, initial, ""));
  assert.throws(() => proposeRelease(gradle(), history, initial, "## Unrelated heading\n\n- Notes."));
});
