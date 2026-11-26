# Android Instrumentation

Run the **Android Instrumentation** workflow from Actions using **Run workflow**
and select the desired ref. It runs the full Debug suite independently of PR
classification and packaging, using the existing read-only Service contract
credentials without deployment configuration or signing secrets.

## Local execution

Install the toolchain versions pinned in
[the workflow](../.github/workflows/instrumentation.yml) and authenticate `gh`
for the pinned Service release. Use an accelerated API 37.0 **Google APIs**
x86_64 image, rather than a Play Store image, to avoid Store background
interference while retaining virtual cameras, the keyboard and gesture navigation.
Revalidate the full suite when changing toolchain or image versions.

Build before starting the emulator to avoid resource contention during boot:

```sh
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
```

Create and launch a fresh, dedicated AVD (use `avdmanager.bat` on Windows):

```sh
echo no | avdmanager create avd -n pinglide-instrumentation \
  -k 'system-images;android-37.0;google_apis;x86_64' -d pixel_2
emulator -avd pinglide-instrumentation -port 5556 \
  -no-window -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect \
  -memory 4096 -cores 4 -camera-back emulated -camera-front emulated
```

In a second shell (Git Bash on Windows), prepare the emulator and run the suite:

```sh
export ANDROID_SERIAL=emulator-5556
bash scripts/quality/prepare-instrumentation-emulator.sh
timeout --kill-after=30s 25m bash ./gradlew :app:connectedDebugAndroidTest --console=plain
```

The setup script waits for boot and provisioning, sets a 366 × 820 dp portrait
viewport at 320 dpi, 100% font scale, light theme and zero system animation
scales, and checks en-US locale, gesture navigation and Launcher focus.
Use only a dedicated test emulator because tests change device settings
and app state.

On PowerShell, set `$env:ANDROID_SERIAL = 'emulator-5556'` and run the Gradle
command using `.\gradlew.bat` with an equivalent 25-minute timeout.
To repeat a single class, add
`-Pandroid.testInstrumentationRunnerArguments.class=<fully-qualified-class>`.

## Reports and failures

CI uploads the `instrumentation-reports` artifact even on failure, containing
JUnit XML, HTML reports, emulator startup output, logcat and window diagnostics.
Local reports are under `app/build/outputs/androidTest-results/connected/` and
`app/build/reports/androidTests/connected/`.

Inspect the first failing assertion and device diagnostics (ANRs and focused
windows). Reproduce affected classes before changing product or test code.
Treat boot and process failures as failed runs; verify fixes with the
unfiltered suite. Report pass, failure and skip counts separately from
JUnit XML, rather than relying on the runner's aggregate count.

## Conditional coverage

`HomeDuplicateScanTest.cameraRecognizesAnotherQrAfterRejection` requires a
back-camera QR video. Follow
[the camera sequence procedure](implementation/pairing-scanner-camera-test.md)
and pass `cameraQrSequence=true` to run it separately. The default suite
reports it as skipped; other camera preview and scanner tests still run.

TalkBack speech checks require TalkBack installed and enabled, plus
`verifyTalkBackSpeech=true`. Native live-region assertions run by default.
