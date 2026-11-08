# Welcome and shared privacy

Issue #12 replaces the launch label with the approved Welcome screen. The source
is the immutable UI snapshot `7f69445969ebba7735af6d184738bd7e9381d97a`,
whose Welcome, privacy, icon and illustration assets match this checkout.

## Presentation

- `PinGlideTheme` and the day/night color resources follow system appearance.
- `welcome_push.xml` transcribes the approved SVG's reduced-motion still frame,
  including the source notification and both delivered notifications. It stays
  static for every motion setting and uses the source SVG's day/night palette.
- The app uses the supplied launcher icon and native versions of the review icons.
  It loads no HTML, JavaScript, browser state, review controls or fixture identity.
- Default strings are Simplified Chinese, with English translations. Unsupported
  locales fall back to Chinese. All text uses scalable Compose typography.
- `ScreenLayout` handles safe drawing insets and limits content width to 560dp.
  Text can scroll while the main action remains reachable. Actions are at least
  48dp high; the main action is at least 56dp high.
- Native Material controls provide focus and touch behavior. Headings and pane
  titles expose screen context to accessibility services; decorative images have
  no duplicate descriptions.

## Navigation and reuse

`MainActivity` assembles `SetupPreferences` through the domain's `SetupProgress`
boundary. Start setup writes only `setup_started=true` to the private `setup`
preferences and opens `SetupEntryScreen`. Restart resumes that entry. Back on
Welcome or the setup entry backgrounds the task and never completes setup.
Existing backup exclusions also cover these separate setup preferences.

The entry shows the Receive SMS prerequisite. Permission requests, denial
recovery, further checkpoints and the battery recommendation belong to #13.

`PrivacyScreen(onDismiss)` is a shared read-only screen. Welcome opens it in a
full-screen native dialog, preserving the underlying scroll position and focus.
Close, Got it and Back dismiss it to the caller. Its visibility survives Activity
recreation. Settings can use the same screen and supply its own dismiss action;
`PrivacyContent` also exposes the shared copy for a column-based host.

The copy distinguishes direct Bailian processing from ciphertext routing by the
Pin Glide service, explains client-only keys and safe diagnostics, and states
cloud/device-transfer exclusions and the login/Pairing consequences of data loss.

## Verification

Run from the repository root on Windows:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.buddylabs.pinglide.data.service.GeneratedWireTest --console=plain
.\gradlew.bat spotlessApply qualityCheck :app:assembleDebug :app:assembleRelease :app:compileReleaseKotlin :app:test --console=plain
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -W -n com.buddylabs.pinglide/.app.MainActivity
```

Both builds, Debug/Release Kotlin compilation, qualityCheck and all six existing
host tests passed. The focused wire tests passed before and during implementation.
The Release APK is unsigned. No test dependencies, fixtures or harnesses were added.

On the API 37 Medium Phone emulator, manually verified Welcome Back/background,
all three privacy dismiss actions, all privacy sections, keyboard Tab/Enter,
English/Chinese and unsupported-locale fallback, both themes, 200% font scale,
portrait/landscape, a 360dp compact viewport, privacy preservation through rotation,
and setup-start persistence through force-stop/relaunch. Captures and local check
logs are under ignored `build/ticket-12/`.

Physical devices, TalkBack speech, older Android versions and signed Release
installation were not exercised. Settings navigation is outside this slice.
