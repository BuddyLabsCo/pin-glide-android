# Welcome and shared privacy

Issue #12 replaces the launch label with the approved Welcome screen. The source
is the immutable UI snapshot `7f69445969ebba7735af6d184738bd7e9381d97a`,
whose Welcome, privacy, icon and illustration assets match this checkout.

## Presentation

- `PinGlideTheme` and the day/night color resources follow system appearance.
- `welcome_push.xml` contains the approved illustration's static devices, home
  screen and routes. `WelcomePushVisual` draws the notifications with Compose,
  using the source SVG's day/night palette, 6.4-second loop, cubic routes, easing
  and keyframes: the notification slides into the source, copies travel to both
  targets, expand into banners and display confirmations, then the cycle resets.
- Compose's system motion-duration scale controls the loop. Disabling animations
  selects the meaningful settled still with the source notification, both target
  banners and confirmations. Changing the setting while Welcome is open switches
  between motion and the still without restarting the Activity.
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
boundary. Start setup writes `setup_started=true` to the private `setup`
preferences. Setup now resumes from durable checkpoints and the actual SMS grant;
see [SMS and battery setup](sms-battery-setup.md). Back on Welcome or a stable
setup step backgrounds the task and never completes setup.
Existing backup exclusions also cover these separate setup preferences.

Issue #13 implements the Receive SMS prerequisite, denial recovery and battery
recommendation. QQ authentication belongs to its separate implementation slice.

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

The review fix also passed Debug/Release Kotlin compilation, qualityCheck, both
APK builds and all six host tests. On the API 37 emulator, complete light/dark
cycles were captured and inspected for notification arrival, both routes, banner
expansion, confirmations and reset. Pixel assertions verified changing hero
frames with normal motion, unchanged text/actions, and identical hero frames
after switching the animator-duration scale to zero in each theme. The reduced
light frame was compared against the original vector still (mean maximum RGB
channel difference below 1/255 across the hero). Captures, assertion results and
check logs are under ignored `build/ticket-12/fix-*`.

Physical devices, TalkBack speech, older Android versions and signed Release
installation were not exercised. Settings navigation is outside this slice.
