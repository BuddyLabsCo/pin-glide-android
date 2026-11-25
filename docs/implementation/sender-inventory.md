# Device access management list (#125)

The loaded Sender inventory now uses the shared yellow warning card with the
approved warning glyph, the Settings header, rounded Sender groups, smartphone
icons and filled danger buttons. Group labels, device names, row dimensions,
spacing and button colors follow the pinned Settings prototype. Existing
inventory ownership checks, selection, confirmation, cancellation, disabled
actions and focus restoration remain in place.

| Theme | Approved prototype and Android |
| --- | --- |
| Light | [Comparison](sender-inventory-evidence/light-comparison.png) |
| Dark | [Comparison](sender-inventory-evidence/dark-comparison.png) |

The left column is cropped from the issue's
[pinned reference capture](../ui-prototype/shots/05-07-security.png), which is
unchanged from commit `7f69445969ebba7735af6d184738bd7e9381d97a`. The right column
shows the production Settings dialog on API 37, navigating to Device access
management with a controlled inventory service response. Both use Simplified
Chinese, 100% font scale and a comparable 366 x 820 dp viewport. Android system
bars and native text rendering are retained. No prototype deviation or motion
change was introduced.

## Verification

- `SenderInventoryScreenTest`: both tests passed in light and dark (four test
  executions). Checks cover loaded current/other Sender grouping even when the
  service returns the current Sender last, 60 dp rows, device-name typography,
  danger-button colors, 48 dp action targets, correct confirmation targets,
  cancellation, and English at 200% system font scale with reachable actions
  and Back navigation.
- `ServiceSenderRevocationTest` passed as a focused existing behavior check.
- Full `:app:testDebugUnitTest`: 46 tests passed, no failures or skips.
- Debug, release and instrumentation Kotlin compilation passed. Both debug
  APK assemblies and `qualityCheck` (Android/Compose Lint, Spotless/ktlint,
  file size and detekt) passed. `git diff --check` passed.
- Full `:app:connectedDebugAndroidTest` was attempted with 139 scheduled tests
  per connected device. Both shared API 37 devices terminated early with
  `Instrumentation run failed due to Process crashed`: 13 reported tests on
  Issue131 and seven on Medium_Phone_API_37.0. The Issue131 log records an APK
  update of `com.buddylabs.pinglide` during the interrupted Home test. Its
  focused retry was also interrupted. The full device suite did not pass;
  these interruptions provide no assertion failure attributable to this UI
  change. One camera-dependent test was skipped on Issue131.

The screenshots use simulated inventory data; live QQ verification and
destructive service revocation were not exercised. GitHub attachment upload
was unavailable: the connector has no upload operation and neither the local
Chrome fallback nor the computer-use browser host was accessible. The issue
comment links to these committed comparisons; those links become available
when the authorized publisher pushes the local commit. This assignment did
not push code or create or modify a pull request.
