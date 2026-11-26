import assert from "node:assert/strict";
import { spawnSync } from "node:child_process";
import { randomBytes, X509Certificate } from "node:crypto";
import { mkdtempSync, readFileSync, rmSync, rmdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

const environment = Object.fromEntries(
  Object.entries(process.env).filter(([name]) => !name.startsWith("PINGLIDE_")),
);

function gradle(tasks, inputs = {}) {
  const args = [...tasks, "--console=plain", "--no-configuration-cache", "--no-build-cache"];
  const result = process.platform === "win32"
    ? spawnSync("cmd.exe", ["/d", "/c", `.\\gradlew.bat ${args.join(" ")}`], {
      env: { ...environment, ...inputs }, encoding: "utf8", maxBuffer: 8 * 1024 * 1024,
    })
    : spawnSync("bash", ["./gradlew", ...args], {
      env: { ...environment, ...inputs }, encoding: "utf8", maxBuffer: 8 * 1024 * 1024,
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

for (const [variant, inputs, expected] of [
  ["Debug", {
    PINGLIDE_DEV_SERVICE_URL: "http://localhost:8080",
    PINGLIDE_DEV_QQ_APP_ID: "123",
    PINGLIDE_SERVICE_URL: "http://invalid-prod.example",
    PINGLIDE_QQ_APP_ID: "invalid",
  }, "http://localhost:8080"],
  ["Release", {
    PINGLIDE_SERVICE_URL: "https://production.example",
    PINGLIDE_QQ_APP_ID: "456",
    PINGLIDE_DEV_SERVICE_URL: "invalid",
    PINGLIDE_DEV_QQ_APP_ID: "invalid",
  }, "https://production.example"],
]) {
  test(`${variant} compiles independently of invalid inputs for the other variant`, () => {
    const result = gradle([
      `:app:compile${variant}Kotlin`, `:app:process${variant}MainManifest`,
    ], inputs);
    assert.equal(result.status, 0, result.output);
    assert.ok(buildConfig(variant.toLowerCase()).includes(`SERVICE_URL = "${expected}"`));
  });
}

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
  const result = gradle([":app:assembleDebug"]);
  assert.equal(result.status, 0, result.output);
  assert.match(apkSigner("debug"), /CN=Android Debug/);
});

test("partial Dev signing inputs cannot fall back to the default key", () => {
  const result = gradle([":app:assembleDebug"], {
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
  for (const entry of ["assemble", "bundle"]) {
    test(`${entry}${variant} distribution rejects missing public and signing inputs`, () => {
      const tasks = [`:app:${entry}${variant}`, ...options];
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
}

function javaTool(name, args, inputs = {}) {
  const executable = join(environment.JAVA_HOME, "bin", name + (process.platform === "win32" ? ".exe" : ""));
  const result = spawnSync(executable, args, {
    env: { ...environment, ...inputs }, encoding: "utf8",
  });
  assert.ifError(result.error);
  assert.equal(result.status, 0, result.stdout + result.stderr);
  return result.stdout;
}

function apkSigner(variant) {
  return javaTool("java", [
    "-jar", join(environment.ANDROID_HOME, "build-tools", "36.0.0", "lib", "apksigner.jar"),
    "verify", "--print-certs", `app/build/outputs/apk/${variant}/app-${variant}.apk`,
  ]);
}

function testSigning(directory, variant, prefix) {
  const store = join(directory, `${variant}.p12`);
  const secret = randomBytes(24).toString("hex");
  const inputs = {
    [`${prefix}SIGNING_STORE_FILE`]: store,
    [`${prefix}SIGNING_STORE_PASSWORD`]: secret,
    [`${prefix}SIGNING_KEY_ALIAS`]: variant,
    [`${prefix}SIGNING_KEY_PASSWORD`]: secret,
  };
  const common = [
    "-keystore", store, "-storepass:env", `${prefix}SIGNING_STORE_PASSWORD`, "-alias", variant,
  ];
  javaTool("keytool", [
    "-genkeypair", ...common, "-keypass:env", `${prefix}SIGNING_KEY_PASSWORD`,
    "-storetype", "PKCS12", "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
    "-dname", `CN=Temporary ${variant} test`,
  ], inputs);
  const certificate = new X509Certificate(javaTool("keytool", ["-exportcert", "-rfc", ...common], inputs));
  return { inputs, fingerprint: certificate.fingerprint256.replaceAll(":", "").toLowerCase() };
}

test("APK and bundle packaging use the intended independent signing identities", () => {
  const directory = mkdtempSync(join(tmpdir(), "pinglide-build-test-"));
  try {
    const debug = testSigning(directory, "debug", "PINGLIDE_DEV_");
    const release = testSigning(directory, "release", "PINGLIDE_");
    assert.notEqual(debug.fingerprint, release.fingerprint);
    const result = gradle([
      ":app:assembleDebug", ":app:bundleDebug", ":app:assembleRelease", ":app:bundleRelease",
      "-Ppinglide.devDistribution=true",
    ], {
      ...debug.inputs, ...release.inputs,
      PINGLIDE_DEV_SERVICE_URL: "http://localhost:8080",
      PINGLIDE_DEV_QQ_APP_ID: "123",
      PINGLIDE_SERVICE_URL: "https://production.example",
      PINGLIDE_QQ_APP_ID: "456",
    });
    assert.equal(result.status, 0, result.output);
    for (const [variant, signing] of [["debug", debug], ["release", release]]) {
      const apk = apkSigner(variant);
      assert.match(apk, new RegExp(`certificate SHA-256 digest: ${signing.fingerprint}\\b`));
      const bundle = `app/build/outputs/bundle/${variant}/app-${variant}.aab`;
      assert.match(javaTool("jarsigner", ["-J-Duser.language=en", "-verify", bundle]), /jar verified\./);
      const certs = javaTool("keytool", ["-printcert", "-rfc", "-jarfile", bundle]);
      const certificates = certs.match(/-----BEGIN CERTIFICATE-----[\s\S]+?-----END CERTIFICATE-----/g);
      assert.equal(certificates?.length, 1);
      assert.equal(new X509Certificate(certificates[0]).fingerprint256.replaceAll(":", "").toLowerCase(),
        signing.fingerprint);
    }
  } finally {
    for (const variant of ["debug", "release"]) {
      rmSync(join(directory, `${variant}.p12`), { force: true });
    }
    rmdirSync(directory);
  }
});
