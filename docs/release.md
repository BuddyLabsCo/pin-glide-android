# Signed release APK

Release packaging uses the existing Android scaffold: application ID and namespace
`com.buddylabs.pinglide`, minimum API 29 (Android 10), target API 36 (Android 16),
compile API 37, and the approved `app/src/main/res/mipmap-nodpi/ic_launcher.png`.
The installed version comes from `versionCode` and `versionName` in
`app/build.gradle.kts` (currently `1` and `0.1.0`). Increase these deliberately when
preparing an upgrade; keep the application ID and release signing identity stable.

## Build prerequisites

Use the checked-in Gradle wrapper and pinned dependencies. The current environment
uses JDK 25, Node.js 24.18.0, Android SDK platform `android-37.0`, build tools
`36.0.0`, and GitHub CLI. Set `JAVA_HOME` and `ANDROID_HOME` for that installation.
Use `gradlew.bat` in PowerShell or `bash ./gradlew` on Linux/macOS.

Authenticate GitHub CLI with read access to the service repository, locally through
`gh auth login` or through a protected `GH_TOKEN` in CI. The existing build downloads
`openapi.yaml` and `asyncapi.yaml` from the fixed release in
`config/codegen/service-contracts.json` (currently
`BuddyLabsCo/pin-glide-service`, `v1.0.1`). It generates and compiles the HTTP client
and WSS models under `build/`; do not copy contracts or generated code into source.
`npmCi` installs the locked generator dependencies automatically.

## Protected signing inputs

Supply all four environment variables to the Gradle process:

| Variable | Supplied value |
| --- | --- |
| `PINGLIDE_SIGNING_STORE_FILE` | Absolute path to a readable keystore outside the checkout. |
| `PINGLIDE_SIGNING_STORE_PASSWORD` | Keystore password from the local/CI secret store. |
| `PINGLIDE_SIGNING_KEY_ALIAS` | Alias of the approved release signing key. |
| `PINGLIDE_SIGNING_KEY_PASSWORD` | Private-key password from the local/CI secret store. |

Locally, have the credential manager inject these variables into a dedicated shell
or process. Keep the keystore in a private directory with access restricted to the
builder, and keep a protected backup of the release key. Do not enter passwords in
shell history, pass them as command-line arguments, or store them in this repository
or `gradle.properties`. Keystore files and `keystore.properties` are ignored as an
additional safeguard.

In CI, use a protected release environment with approved secret access. Restore the
keystore from the CI secure-file store into a private runner temporary directory,
map the four variables above to the signing step, and remove the file when that
step ends, including on failure. Keep GitHub download credentials scoped to the
existing contract-download step. The ordinary quality workflow compiles Release
without signing access; it does not publish or distribute APKs.

Missing or blank signing inputs make release APK/bundle packaging fail instead of
silently producing an unsigned artifact or using the debug key. Invalid files,
passwords, or aliases fail through Android's signing validation. Debug builds,
Release compilation, and static checks do not require signing inputs.

Signing inputs are consumed only by the Gradle signing configuration. Keep these
and GitHub credentials out of `BuildConfig`, resources, assets, manifests, compiler
arguments and diagnostics. Use normal console verbosity; do not publish verbose
build logs, environment dumps, Gradle state/caches or keystores as artifacts.
Development-only code and injections must stay in `app/src/debug/`; Release uses
the main/release source sets. Do not add debug seeds or onboarding bypasses to
produce a release.

## Build and verify

From the repository root, with protected signing inputs injected:

```powershell
.\gradlew.bat qualityCheck --console=plain
.\gradlew.bat :app:compileReleaseKotlin :app:assembleDebug :app:assembleRelease --console=plain
.\gradlew.bat :app:test --console=plain
```

`qualityCheck` retains the four existing gates: Android/Compose Lint,
Spotless/ktlint, handwritten Kotlin file size, and detekt. The build keeps compiler
warnings as errors and compiles generated integration sources in both variants.
These commands reproduce the packaging procedure using the pinned toolchain and
contracts; they do not promise byte-identical APKs across environments.

The signed APK is `app/build/outputs/apk/release/app-release.apk`. Its installed
version metadata is also recorded in
`app/build/outputs/apk/release/output-metadata.json`. Debug output is separate at
`app/build/outputs/apk/debug/app-debug.apk` and uses the Android debug certificate.
Distribute only the verified release APK, never build caches or signing material.

Verify the signature, alignment and manifest with the installed SDK tools:

```powershell
$buildTools = Join-Path $env:ANDROID_HOME 'build-tools/36.0.0'
& "$buildTools/apksigner.bat" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
& "$buildTools/zipalign.exe" -c -P 16 -v 4 app/build/outputs/apk/release/app-release.apk
& "$buildTools/aapt.exe" dump badging app/build/outputs/apk/release/app-release.apk
```

Check the signer certificate against the approved release identity, and confirm
the application ID, version, minimum API 29 and target API 36. A valid signature
alone does not establish that the intended production key was used.

## Install

On a connected Android 10–16 device or emulator selected with `adb -s SERIAL` when
multiple devices are attached:

```powershell
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell dumpsys package com.buddylabs.pinglide
adb shell am start -n com.buddylabs.pinglide/.app.MainActivity
```

Verify the installed `versionCode` and `versionName` in the package dump. An update
requires the same signing key and a compatible version. If a debug or differently
signed installation exists, Android rejects replacement. Preserve its data and use
a separate emulator/device for validation; uninstalling deletes local app data
and requires setup/login/Pairing again.

## Acceptance boundary

A signed intermediate scaffold validates packaging capability only. The final
product APK must be rebuilt with the completed implementation slices from #11 and
the approved production key. A temporary validation key must never be used for
distribution or treated as evidence of the production signing identity. Packaging
does not establish QQ, SMS, Windows delivery, physical-device certification, Play
Store publication, deployment or remote CI success.

## Local verification (2026-11-09)

Verified on Windows with the prerequisites above, using an access-restricted
temporary keystore outside the checkout:

- `qualityCheck` passed all four existing static gates.
- `:app:compileReleaseKotlin` and the focused `GeneratedWireTest` passed without
  signing inputs. `:app:assembleDebug :app:assembleRelease` then passed with signing.
- `:app:test` passed the full configured unit suite: 6 tests, no failures or skips.
- Release APK builds rejected missing inputs, a blank alias, a nonexistent keystore,
  an incorrect store password and an absent key alias. Bundle signing also rejected
  the blank alias.
- `apksigner verify` passed with APK Signature Scheme v2; the certificate matched
  the supplied temporary key. `zipalign` passed. `aapt` and output metadata confirmed
  the application ID, version code 1, version name 0.1.0, minimum 29 and target 36.
- Inspection of APK entries and DEX confirmed generated HTTP/WSS classes and no
  test classes, keystore files, supplied signing passwords/path or available
  build-time GitHub credential values.

Installation was not verified: no device was attached, and the existing API 37
emulator image refused to start because another instance held it. No existing
installation or emulator state was replaced. Android 10–16 runtime acceptance
and the approved production signing identity remain unverified.
