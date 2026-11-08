# SMS permission and battery setup

Issue #13 implements the SMS prerequisite and battery recommendation from the
approved UI snapshot `7f69445969ebba7735af6d184738bd7e9381d97a`. The relevant
states are `sms`, `sms-system`, `sms-denied` and `battery`.

## Ownership and durable facts

- `MainActivity` registers Android's `RequestPermission` result contract. Only an
  explicit Allow incoming SMS action launches it, and only for `RECEIVE_SMS`.
  Android owns the dialog, wording and result delivery across recreation.
- `SetupPermissions` reads the real grant on creation, every foreground return,
  permission results and Check again. It opens application details and battery
  optimization settings, falling back to general settings for unavailable battery
  settings. Failed settings launches produce accessible inline feedback.
- `SetupProgress` exposes installation checkpoints, backed by the existing private
  `setup` preferences. `setup_started` remains compatible with #12. Separate
  `sms_requested`, `sms_completed` and `battery_acknowledged` booleans record a
  deliberate request, an observed grant and explicit battery acknowledgement.
  They never store credentials, identity, message content or exemption status.
- `SetupNavigation` owns startup routing. Before setup starts it shows Welcome.
  Missing SMS permission shows the prerequisite or recovery screen, regardless
  of completed checkpoints. With permission it resumes battery until acknowledged,
  then the QQ login entry. Regranting permission preserves battery acknowledgement.

Check again never launches another permission prompt. The recovery screen offers
application settings and announces when permission remains off. An in-flight
permission request disables duplicate actions and survives Activity recreation;
restarting without a grant resumes recovery without automatically prompting.
While the native dialog is pending, the SMS explanation stays visible underneath
it. Only an ungranted result selects denial recovery; the durable request marker
still selects recovery on a fresh task after an interrupted request.

Opening or returning from battery settings never advances setup. Only Next writes
`battery_acknowledged`. There is no exemption probe, direct exemption request,
exemption cache, battery gate or global forwarding switch. Back and the toolbar's
Back action background the task at stable setup steps without resetting progress.

## Presentation and integration boundary

The screens reuse the system-following Material theme, Chinese/English resources
and safe drawing insets. Headings, pane titles, polite status announcements and
native focusable controls expose accessible equivalents. All actions meet 48dp
minimum targets. The complete step scrolls when needed, including with large fonts
in landscape; short screens keep actions at the bottom. These steps add no motion.
The shared header shows the app icon, localized setup title, step count and four
rounded progress segments. The segments expose discrete accessibility progress;
placing them below the branding keeps the title and count readable at large fonts.

QQ login is the next stable destination. This slice supplies its entry copy and
Back behavior; QQ authentication and later onboarding belong to their own issues.
No inbox access, default-SMS role, notification listener, notification permission,
test harness, debug seed or new dependency is introduced.

## Verification

Run from the repository root on Windows:

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:testDebugUnitTest --tests com.buddylabs.pinglide.data.service.GeneratedWireTest --console=plain
.\gradlew.bat qualityCheck :app:compileDebugKotlin :app:compileReleaseKotlin :app:assembleDebug :app:test --console=plain
```

Debug/Release compilation, the four static checks, Debug APK assembly and the
complete existing host suite pass. The focused wire test class was run repeatedly
during implementation. No automated test files were added, matching this ticket's
production-only scope.

On the API 37 emulator, verified the native permission dialog through rotation,
denial/recheck without repeated prompts, application settings permission grant,
foreground recheck, restart at denial/battery/QQ, explicit battery acknowledgement,
revocation after acknowledgement and regrant without repeating battery setup.
Verified stable Back/background behavior, Chinese/English, light/dark presentation,
and scrolling through content and actions at 200% font scale in landscape.
Local XML captures and screenshots are under ignored `build/ticket-13/`.

Review regression verification on API 37 covers grant and denial after rotating
the open native dialog, its unchanged SMS explanation/action underneath, denial
restart and Check again without a new prompt, and a fresh task after interrupting
an unanswered request. Header captures cover SMS, denial, battery and QQ entry;
Chinese/English, light/dark themes and 200% font scale remain readable. Large-font
landscape content/actions remain reachable by scrolling, and the header Back
action backgrounds the task. Assertions and captures were run locally without
adding a test harness; review evidence is under ignored `build/ticket-13-review/`.

Physical devices, older Android versions, TalkBack speech and OEM settings fallback
were not exercised. QQ authentication and actual SMS forwarding are outside this
slice; this verification does not establish their operation.
