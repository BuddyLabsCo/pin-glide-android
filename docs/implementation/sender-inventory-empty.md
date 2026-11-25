# Sender inventory with no other devices (#127)

The empty other-Senders group now follows the pinned Settings prototype:
12 sp muted text, 18 sp line height, zero letter spacing and 18 dp padding.
The warning, current Sender row, smartphone icon and filled revoke action reuse
the shared layout corrected in #125. The current device remains selectable for
revocation when the inventory permits it; confirmation and cancellation use the
existing workflow.

| Theme | Approved prototype and Android |
| --- | --- |
| Light | [Comparison](sender-inventory-empty-evidence/light-comparison.png) |
| Dark | [Comparison](sender-inventory-empty-evidence/dark-comparison.png) |

The left column is cropped from the issue's
[reference capture](../ui-prototype/shots/05-security-empty.png), verified
byte-for-byte against commit `7f69445969ebba7735af6d184738bd7e9381d97a`.
The right column shows the production Settings navigation with a controlled
inventory response containing only the current Sender. Captures use API 37,
Simplified Chinese, 100% font scale and a 366 x 820 dp viewport. The original
[light](sender-inventory-empty-evidence/android-light.png) and
[dark](sender-inventory-empty-evidence/android-dark.png) Android screenshots
are retained. Android system bars and native font rendering are preserved.
No prototype or motion changes were introduced.

## Verification

- Before the styling fix, the new empty-state test failed: expected a 54 dp
  empty group, got 56 dp. The corrected case passed with the specified text
  size, line height, muted color and padding, a 60 dp current Sender row,
  and working current-device revoke confirmation and cancellation.
- All four `SenderInventoryScreenTest` cases passed in both light and dark
  themes (eight executions). English at 200% font scale keeps the empty
  message, current-device revoke action and Back navigation reachable.
- The focused `ServiceSenderRevocationTest` passed all three cases. The full
  host unit suite passed all 46 tests without failures or skips.
- Debug, release and instrumentation Kotlin compilation, both debug APK
  assemblies, and `qualityCheck` passed. `git diff --check` passed.
- Full device-suite verification did not pass. The initial shared-emulator
  Gradle run scheduled 141 tests and terminated early: its XML reports 45
  entries, six failures and one skip, including Home duplicate-scan,
  login-button sizing and refresh failures, followed by an instrumentation
  crash. The final isolated API 37 run scheduled all 143 tests, including the
  new cases. Fourteen passed and one camera test was skipped before
  `HomeLoginExpiredScreenTest.expiredListStillScrollsButHidesRefreshControlsAndFeedback`
  stalled for over three minutes. That run was stopped; the remaining device
  tests are unverified. All eight focused Sender inventory executions passed
  on the isolated emulator before this full run.
- The runtime `SenderGroup` contract is unchanged. Ripwire reports test-class
  growth and repetition between the two short English user-flow cases;
  these are retained as readable, separate loaded/empty-state scenarios.

The screenshots use simulated inventory data. Live QQ verification and
destructive service revocation were not exercised. Direct GitHub attachment
upload was unavailable: the connector has no upload operation, agent-browser
is signed out with no saved GitHub credentials, and the computer-use browser
host is unavailable. The issue comment links to the committed comparisons;
those links become available when the authorized publisher pushes the local
commit. This assignment did not push code or create or modify a pull request.
