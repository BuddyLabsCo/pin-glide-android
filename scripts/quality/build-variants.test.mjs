import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { test } from "node:test";

const environment = Object.fromEntries(
  Object.entries(process.env).filter(([name]) => !name.startsWith("PINGLIDE_")),
);

function gradle(tasks, inputs = {}) {
  const args = [...tasks, "--console=plain", "--no-configuration-cache", "--no-build-cache"];
  const result = process.platform === "win32"
    ? spawnSync("cmd.exe", ["/d", "/c", `.\\gradlew.bat ${args.join(" ")}`], {
      env: { ...environment, ...inputs }, encoding: "utf8",
    })
    : spawnSync("bash", ["./gradlew", ...args], {
      env: { ...environment, ...inputs }, encoding: "utf8",
    });
  assert.ifError(result.error);
  return { status: result.status, output: result.stdout + result.stderr };
}

function buildConfig(variant) {
  return readFileSync(
    `app/build/generated/source/buildConfig/${variant}/com/buddylabs/pinglide/BuildConfig.java`,
    "utf8",
  );
}

function manifest(variant) {
  return readFileSync(
    `app/build/intermediates/merged_manifest/${variant}/process${variant === "debug" ? "Debug" : "Release"}MainManifest/AndroidManifest.xml`,
    "utf8",
  );
}

test("Debug HTTP and Release HTTPS produce independent identities and configuration", () => {
  const result = gradle([
    ":app:generateDebugBuildConfig", ":app:generateReleaseBuildConfig",
    ":app:processDebugMainManifest", ":app:processReleaseMainManifest",
  ], {
    PINGLIDE_DEV_SERVICE_URL: "http://localhost:8080/",
    PINGLIDE_DEV_QQ_APP_ID: "123",
    PINGLIDE_SERVICE_URL: "https://production.example",
    PINGLIDE_QQ_APP_ID: "456",
  });
  assert.equal(result.status, 0, result.output);
  assert.match(buildConfig("debug"), /APPLICATION_ID = "com\.buddylabs\.pinglide\.dev"/);
  assert.match(buildConfig("debug"), /SERVICE_URL = "http:\/\/localhost:8080"/);
  assert.match(buildConfig("debug"), /QQ_APP_ID = "123"/);
  assert.match(buildConfig("release"), /APPLICATION_ID = "com\.buddylabs\.pinglide"/);
  assert.match(buildConfig("release"), /SERVICE_URL = "https:\/\/production\.example"/);
  assert.match(buildConfig("release"), /QQ_APP_ID = "456"/);
  assert.match(manifest("debug"), /android:usesCleartextTraffic="true"/);
  assert.match(manifest("debug"), /android:debuggable="true"/);
  assert.match(manifest("debug"), /android:label="@string\/dev_launcher_name"/);
  assert.match(manifest("debug"), /android:scheme="tencent123"/);
  assert.match(manifest("release"), /android:usesCleartextTraffic="false"/);
  assert.doesNotMatch(manifest("release"), /android:debuggable="true"/);
  assert.match(manifest("release"), /android:label="@string\/app_name"/);
  assert.match(manifest("release"), /android:scheme="tencent456"/);
});

test("Dev inputs alone leave Release unconfigured", () => {
  const result = gradle([":app:generateDebugBuildConfig", ":app:generateReleaseBuildConfig"], {
    PINGLIDE_DEV_SERVICE_URL: "https://[::1]:8443/",
    PINGLIDE_DEV_QQ_APP_ID: "123",
  });
  assert.equal(result.status, 0, result.output);
  assert.match(buildConfig("debug"), /SERVICE_URL = "https:\/\/\[::1\]:8443"/);
  assert.match(buildConfig("release"), /SERVICE_URL = ""/);
  assert.match(buildConfig("release"), /QQ_APP_ID = ""/);
});

for (const [name, inputs] of [
  ["Release HTTP", { PINGLIDE_SERVICE_URL: "http://localhost:8080" }],
  ["malformed host", { PINGLIDE_DEV_SERVICE_URL: "http://bad_host" }],
  ["missing host", { PINGLIDE_DEV_SERVICE_URL: "http:///path" }],
  ["empty port", { PINGLIDE_DEV_SERVICE_URL: "http://localhost:" }],
  ["out-of-range port", { PINGLIDE_DEV_SERVICE_URL: "http://localhost:65536" }],
  ["scoped IPv6", { PINGLIDE_DEV_SERVICE_URL: "http://[fe80::1%eth0]" }],
  ["credentials", { PINGLIDE_DEV_SERVICE_URL: "http://user@localhost" }],
  ["non-root path", { PINGLIDE_DEV_SERVICE_URL: "http://localhost/api" }],
  ["query", { PINGLIDE_DEV_SERVICE_URL: "http://localhost?" }],
  ["fragment", { PINGLIDE_DEV_SERVICE_URL: "http://localhost#" }],
]) {
  test(`rejects ${name} in Service origin`, () => {
    const result = gradle([":app:generateDebugBuildConfig", ":app:generateReleaseBuildConfig"], inputs);
    assert.notEqual(result.status, 0);
    assert.match(result.output, /Service URL must be a valid/);
  });
}

test("rejects nonnumeric QQ App ID", () => {
  const result = gradle([":app:generateDebugBuildConfig"], { PINGLIDE_DEV_QQ_APP_ID: "123a" });
  assert.notEqual(result.status, 0);
  assert.match(result.output, /QQ App ID must contain only digits/);
});

test("ordinary Debug packaging needs no distribution inputs", () => {
  const result = gradle([":app:validateDebugPackaging"]);
  assert.equal(result.status, 0, result.output);
});

test("partial Dev signing inputs cannot fall back to the default key", () => {
  const result = gradle([":app:validateDebugPackaging"], {
    PINGLIDE_DEV_SERVICE_URL: "https://example.test",
    PINGLIDE_DEV_QQ_APP_ID: "123",
    PINGLIDE_DEV_SIGNING_KEY_ALIAS: "dev-test",
  });
  assert.notEqual(result.status, 0);
  assert.match(result.output, /Missing signing inputs/);
});

for (const [variant, prefix, options] of [
  ["Debug", "PINGLIDE_DEV_", ["-Ppinglide.devDistribution=true"]],
  ["Release", "PINGLIDE_", []],
]) {
  test(`${variant} distribution needs its own public configuration and signing inputs`, () => {
    const tasks = [`:app:validate${variant}Packaging`, ...options];
    const unconfigured = gradle(tasks);
    assert.notEqual(unconfigured.status, 0);
    assert.match(unconfigured.output, /Distribution requires/);
    const unsigned = gradle(tasks, {
      [`${prefix}SERVICE_URL`]: "https://example.test",
      [`${prefix}QQ_APP_ID`]: "123",
    });
    assert.notEqual(unsigned.status, 0);
    assert.match(unsigned.output, /Missing signing inputs/);
  });
}
