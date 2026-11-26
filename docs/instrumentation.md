# Android Instrumentation

CI: Actions → **Android Instrumentation** → **Run workflow** (select ref).

## Local execution

Install the SDK and toolchain pinned in [the workflow](../.github/workflows/instrumentation.yml), authenticate `gh`, and use a dedicated accelerated Google APIs (non-Play-Store) emulator.

```sh
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
echo no | avdmanager create avd -n pinglide-instrumentation \
  -k 'system-images;android-37.0;google_apis;x86_64' -d pixel_2
emulator -avd pinglide-instrumentation -port 5556 \
  -no-window -no-snapshot -no-audio -no-boot-anim -gpu swiftshader_indirect \
  -memory 4096 -cores 4 -camera-back emulated -camera-front emulated
```

In a second Bash shell:

```sh
export ANDROID_SERIAL=emulator-5556
bash scripts/quality/prepare-instrumentation-emulator.sh
timeout --kill-after=30s 25m bash ./gradlew :app:connectedDebugAndroidTest --console=plain
```

On Windows, use Git Bash and `avdmanager.bat`. For a single class, add `-Pandroid.testInstrumentationRunnerArguments.class=<fully-qualified-class>`.

## Reports and failures

- CI: `instrumentation-reports` artifact (including diagnostics).
- Local: `app/build/outputs/androidTest-results/connected/` (XML), `app/build/reports/androidTests/connected/` (HTML).
- Diagnose failures using assertions, logcat and window focus. Rerun the full suite after fixes or toolchain changes; report passes, failures and skips separately.

## Conditional coverage

- Camera QR sequence: skipped by default; requires `cameraQrSequence=true` and a back-camera feed with an invalid QR followed by the fixture's valid QR.
- TalkBack speech: install and enable TalkBack and set `verifyTalkBackSpeech=true`; native live-region checks always run.
