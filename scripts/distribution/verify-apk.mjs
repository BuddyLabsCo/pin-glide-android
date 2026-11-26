import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { pathToFileURL } from "node:url";

function sourceVersion(debug) {
  const source = readFileSync(new URL("../../app/build.gradle.kts", import.meta.url), "utf8");
  const code = source.match(/^\s*versionCode = ([1-9]\d*)\s*$/m)?.[1];
  const name = source.match(/^\s*versionName = "([a-zA-Z0-9.-]+)"\s*$/m)?.[1];
  assert.ok(code && Number(code) <= 2100000000 && name, "Invalid source version");
  return { code, name: debug ? `${name}-dev` : name };
}

export function verifyApk(variant, apk, fingerprint) {
  assert.ok(["debug", "release"].includes(variant), "Expected debug or release variant");
  const debug = variant === "debug";
  const version = sourceVersion(debug);
  const expectedSigner = (fingerprint ?? "").replaceAll(":", "").toLowerCase();
  assert.match(expectedSigner, /^[0-9a-f]{64}$/, "Expected approved signer SHA-256 fingerprint");

  const suffix = process.platform === "win32" ? ".exe" : "";
  const tools = join(process.env.ANDROID_HOME, "build-tools", "36.0.0");
  const run = (command, args) => execFileSync(command, args, { encoding: "utf8" });
  const aapt = (...args) => run(join(tools, `aapt${suffix}`), args);
  const badging = aapt("dump", "badging", apk);
  const packageInfo = badging.match(/^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'/m);
  assert.ok(packageInfo, "Missing APK package metadata");
  assert.equal(packageInfo[1], `com.buddylabs.pinglide${debug ? ".dev" : ""}`, "Unexpected APK package");
  assert.equal(packageInfo[2], version.code, "Unexpected APK version code");
  assert.equal(packageInfo[3], version.name, "Unexpected APK version name");
  assert.equal(/^application-debuggable\r?$/m.test(badging), debug, "Unexpected APK debuggability");
  assert.match(badging, /^sdkVersion:'29'\r?$/m);
  assert.match(badging, /^targetSdkVersion:'36'\r?$/m);
  assert.ok(badging.includes(`application-label-en:'${debug ? "Pin Glide Dev" : "Pin Glide"}'`),
    "Unexpected APK launcher label");
  assert.match(badging, /^launchable-activity: name='com\.buddylabs\.pinglide\.app\.MainActivity'/m);

  const manifest = aapt("dump", "xmltree", apk, "AndroidManifest.xml");
  const application = manifest.match(/^[ \t]*E: application\b[^\n]*\n((?:[ \t]+A:[^\n]*\n)+)/m)?.[1];
  assert.ok(application, "Missing manifest application");
  const cleartext = application.match(/android:usesCleartextTraffic\([^)]*\)=\(type 0x12\)(0x[0-9a-f]+)/)?.[1];
  assert.ok(cleartext !== undefined && (Number(cleartext) !== 0) === debug,
    "Unexpected manifest cleartext policy");
  assert.doesNotMatch(application, /android:networkSecurityConfig/, "Unexpected network policy override");

  const signature = run(join(process.env.JAVA_HOME, "bin", `java${suffix}`), [
    "-jar", join(tools, "lib", "apksigner.jar"), "verify", "--verbose", "--print-certs", apk,
  ]);
  const signers = [...signature.matchAll(/^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)\r?$/gm)]
    .map(match => match[1]);
  assert.deepEqual(signers, [expectedSigner], "Unexpected APK signer");
  run(join(tools, `zipalign${suffix}`), ["-c", "-P", "16", "-v", "4", apk]);
  return version.name;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  console.log(verifyApk(process.argv[2], process.argv[3], process.env.PINGLIDE_SIGNING_CERT_SHA256));
}
