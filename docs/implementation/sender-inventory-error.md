# Initial Sender inventory failure (#128)

Device access management now displays the approved inline warning card with
the cloud-off icon, localized failure copy and polite accessibility live
region. It uses the existing warning colors, 16 dp corners, 13 dp padding,
12 sp text and 18 sp line height. The cards are separated by 14 dp and the
full-width, 48 dp minimum tonal Retry action touches the failure card, as in
the prototype. The action retains its existing inventory refresh callback.

The initial failure does not display Sender groups or the empty-inventory
message. Retry returns to the loading card, then displays the actual
inventory when the read succeeds. No inventory, revocation or navigation
behavior was changed. The same status component also serves refresh failures
with retained Sender rows.

| Theme | Approved prototype (left), Android (right) |
| --- | --- |
| Light | [Comparison](sender-inventory-error-evidence/light-comparison.png) |
| Dark | [Comparison](sender-inventory-error-evidence/dark-comparison.png) |

The reference is the unchanged
[pinned capture](../ui-prototype/shots/05-security-error.png), verified
byte-for-byte against commit `7f69445969ebba7735af6d184738bd7e9381d97a`.
Both captures use Simplified Chinese and a comparable viewport. The Android
screenshots use API 37, 366 x 820 dp and 100% font scale, preserving native
fonts and system bars. The comparisons show the top of each viewport because
the reference is cropped; full Android captures are available in
[light](sender-inventory-error-evidence/android-light.png) and
[dark](sender-inventory-error-evidence/android-dark.png).
No design deviation or motion change was introduced.

## Verification

- Before the fix, the new initial-failure regression failed because the
  warning card was absent. After the fix, it verifies the warning background,
  polite live region, full-width tonal action and minimum touch target,
  absent Sender rows, and transition through loading to a successful
  inventory with no other Senders.
- All six `SenderInventoryScreenTest` cases passed in both light and dark
  themes (12 executions), including the existing English 200% font-scale
  scenarios for loaded inventories.
- All 46 host unit tests passed with no failures, errors or skips.
- The full API 37 instrumentation suite completed with `OK (150 tests)`.
- Debug, release and instrumentation Kotlin compilation, debug and
  instrumentation APK assembly, and `qualityCheck` passed. The latter
  includes Android/Compose Lint, Spotless/ktlint, file-size limits and detekt.
- Ripwire reports no changed status interface or production complexity
  regression. Its quality delta flags the existing test class's growth and
  the reflectively invoked JUnit test. The new regression stays with the
  established screen test fixture rather than splitting it for a metric.
  Its test gate does not resolve JUnit/Compose entry points; the executed
  instrumentation test supplies the evidence.

The failure and recovery use simulated inventory responses. Live QQ and
service revocation were not exercised. No motion recording is needed for
this static visual change.

The tracker comment links to the committed comparison images because the
available GitHub tools do not support uploading issue image attachments.
The links resolve once the authorized publisher pushes the local commit.

## Review follow-up

- A retained-inventory refresh failure keeps both Sender rows displayed
  while disabling both revocation actions. Retry removes the failure
  notice, replaces the stale row with the newly returned Sender name and
  restores the available revocation actions.
- At 200% system font scale, the English failure copy has no text overflow,
  the warning retains its polite live region, and the full-width Retry
  remains reachable with a minimum 48 dp touch target. Successful Retry
  removes the failure and restores the current Sender's revocation action.
- All eight inventory screen tests passed in both light and dark themes
  on API 37. Instrumentation Kotlin compilation and `qualityCheck` passed;
  the host unit-test task was up-to-date. The full device suite was not
  repeated for this test-only follow-up.

No production code changed. Ripwire reports the growth of the existing
inventory test class and cannot resolve its reflective JUnit entry points;
the executed scenarios provide the coverage evidence.
