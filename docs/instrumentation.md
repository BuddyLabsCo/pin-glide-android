# Android Instrumentation baseline

The **Android Instrumentation** workflow (`workflow_dispatch`) runs the entire
Debug instrumentation suite, independently of PR classification and release
packaging. Select the desired ref with **Run workflow** in Actions. It requires
the same read-only Service contract GitHub App credentials as Android quality;
no Service origin, QQ App ID, or distribution signing credentials are needed.
Service contract generation still uses `config/codegen/service-contracts.json`.

## Emulator and toolchain

Use an accelerated x86_64 emulator with the API 37.0 **Google APIs** image
(`system-images;android-37.0;google_apis;x86_64`, SDK package revision 8), not the
Google Play Store image. The Google APIs image has CameraX-compatible virtual
cameras, a system keyboard and the gesture navigation used by the UI tests.
Its `com.android.vending` package is the system LicenseChecker, not the Play
Store application.

The workflow pins JDK 25.0.3, Node 24.18.0, the repository's checked-in Gradle
wrapper and version catalog, command-line tools 23.0, SDK platform 37.0
revision 2, build tools 36.0.0, platform tools 37.0.3 and emulator 37.3.10.
When updating these pins, rerun the full suite and record the resulting
baseline; do not silently substitute a different system image.

A fresh Pixel 2 AVD runs headless with software GPU rendering, 4 GiB of guest
memory, up to four guest cores, emulated front and back cameras, and no saved
snapshot. The setup script waits for boot and user provisioning (five-minute
limit), unlocks the device, and sets portrait mode, 732 × 1640 pixels at
320 dpi (366 × 820 dp), 100% font scale, light theme and zero system animation
scales. It checks the resulting configuration, en-US locale, gesture
navigation and Launcher window focus before testing. Tests that exercise
animation, dark theme and alternate viewport/font sizes keep their own
explicit settings.

## Local execution

Install the pinned SDK packages above using Android SDK Manager or `android
sdk install <package>@<version>`; install JDK and Node and authenticate `gh`
for the pinned Service release. From the repository root, build the APKs
before starting the emulator to avoid concurrent compilation during startup:

```sh
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
```

Create a fresh, dedicated AVD and launch it (use `avdmanager.bat` on Windows):

```sh
echo no | avdmanager create avd -n pinglide-instrumentation \
  -k 'system-images;android-37.0;google_apis;x86_64' -d pixel_2
emulator -avd pinglide-instrumentation -port 5556 \
  -no-window -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect \
  -memory 4096 -cores 4 -camera-back emulated -camera-front emulated
```

In a second shell (Git Bash on Windows), use the matching emulator serial:

```sh
export ANDROID_SERIAL=emulator-5556
bash scripts/quality/prepare-instrumentation-emulator.sh
timeout --kill-after=30s 25m bash ./gradlew :app:connectedDebugAndroidTest --console=plain
```

On PowerShell, set `$env:ANDROID_SERIAL = 'emulator-5556'` and run
`.\gradlew.bat :app:connectedDebugAndroidTest --console=plain` (use a 25-minute
timeout when supervising the process). Do not run against a shared or
production device: the tests change display settings and app state. To repeat
a single class, add
`-Pandroid.testInstrumentationRunnerArguments.class=<fully-qualified-class>`.
Always finish a baseline verification with the unfiltered suite.

CI bounds the entire job to 50 minutes, the emulator boot/setup step to eight
minutes and the test command to 25 minutes. It uploads the
`instrumentation-reports` artifact even on failure, containing JUnit XML,
HTML reports, emulator startup output, logcat and window diagnostics. Local
Gradle reports are under `app/build/outputs/androidTest-results/connected/`
and `app/build/reports/androidTests/connected/`.

## Failures and conditional coverage

Do not retry away or ignore failing assertions. Inspect the first failing
assertion/stack trace and device diagnostics (especially ANRs and focused
windows); reproduce the affected class on the same prepared image before
changing product or test code. An emulator boot or process failure is a failed
run, not a passing suite. A repeat on a fresh emulator helps distinguish
environment failures from app/test defects.

`HomeDuplicateScanTest.cameraRecognizesAnotherQrAfterRejection` is the one
conditional test. It genuinely requires a back-camera video presenting an
invalid QR followed by the fixture's valid QR. Follow
[the camera sequence procedure](implementation/pairing-scanner-camera-test.md)
and pass `cameraQrSequence=true` to execute that case separately. The default
suite reports it as skipped, not passed; the other real-camera preview and
scanner tests still run. Actual TalkBack speech also remains opt-in using
`verifyTalkBackSpeech=true` with TalkBack installed and enabled; the default
accessibility assertions still check the native live-region updates.

## Baseline evidence (2026-11-27)

The checked-out main baseline was `4fe64ae`. #211's API 37 Play Store run
reported 144 cases (128 passed, 15 failed, one skipped). The current suite
discovers 168 cases. A fresh AOSP-image attempt configured with two guest
cores and 2 GiB of memory suffered a SystemUI service startup ANR:

```text
ANR in com.android.systemui
Reason: executing service com.android.systemui/.SystemUIService, waited 20188ms
mCurrentFocus=Window{... Application Not Responding: com.android.systemui}
```

The overlay stole window focus. The interrupted run recorded 61 passes,
19 failures and one conditional skip before it was stopped; failures included
dialog Back/focus, IME, accessibility events and captured divider pixels.
Those results are not a valid application baseline.

On the fresh Google APIs revision 8 image, configured with 4 GiB, four guest
cores, gesture navigation and the viewport/settings above, the unchanged
main APK and test APK completed in 565.955 seconds: **167 passed, zero failed,
one camera-feed assumption skipped**. The raw JUnit runner prints
`OK (168 tests)` including that assumption, so count the per-test statuses
rather than treating all 168 as passes. None of the 15 historical failures
reproduced in this run.

The subsequent unfiltered Gradle run on pinned emulator 37.3.10 reported
166 passes, one failure and one skip. The failure was
`PairingConsentScreenTest.compactHeightAtDoubleFontScaleKeepsAliasAndActionsReachable`:
the rescan action was not displayed after scrolling. The test had been
editing the alias without synchronizing with the real keyboard; the
asynchronous IME insets can change the compact viewport during scrolling.
The test now explicitly opens the keyboard and waits for its visibility,
edits the alias, then dismisses it and waits for it to hide before checking
the compact layout. All existing reachability and click assertions remain.
The individual consent class passed all eight cases after this change.
The separate real-keyboard navigation tests remain unchanged.

Final unfiltered `:app:connectedDebugAndroidTest` verification completed
successfully in 7m 42s (446.628 seconds reported in JUnit XML). The XML
records 168 cases: **167 passed, zero failed, zero errors, one skip**,
specifically `cameraRecognizesAnotherQrAfterRejection`. The focused scanner
class also recorded five passes and the same one skip. All 46 JVM unit
tests, debug/release/instrumentation Kotlin compilation, debug and test APK
assembly, `qualityCheck`, `actionlint` and Bash syntax checks passed.

The local runs used Windows WHPX, platform tools 37.0.1 and JBR 25.0.3.
The first used emulator 37.2.12; the subsequent runs used 37.3.10 as pinned
by the workflow. Hosted execution is not established by the local evidence.
