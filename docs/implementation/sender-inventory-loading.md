# Initial Sender inventory loading (#126)

The Device access management destination now shows the approved neutral status
card beneath its warning: the existing clock artwork, localized loading copy,
16 dp corners, 13 dp padding, 12 sp text with an 18 sp line height, and a 14 dp
gap between cards. It reuses the warning card's shared information-card layout
and the existing theme colors. The clock is static, as in the prototype; no
motion was added.

Sender groups remain absent until the first inventory response. The existing
polite accessibility announcement is attached to the status card. Inventory
ownership, selection, refresh, revocation and failure behavior are unchanged;
the failure state's original spacing is retained.

| Theme | Approved prototype (left), Android (right) |
| --- | --- |
| Light | [Comparison](sender-inventory-loading-evidence/light-comparison.png) |
| Dark | [Comparison](sender-inventory-loading-evidence/dark-comparison.png) |

The reference crops come from the unchanged
[pinned loading capture](../ui-prototype/shots/05-security-loading.png).
Android evidence opens the production Settings dialog with a suspended
inventory service response. Both use Simplified Chinese, 100% font scale and
a comparable 366 x 820 dp viewport on API 37. The comparison retains Android's
native fonts and system bars. No prototype amendment or design deviation was
introduced.

## Verification

The loading regression opens Device access management, verifies the loading
copy and absent groups, checks the card's geometry, color, typography and
polite live region, then completes the service response and verifies that the
status disappears and both Sender groups appear. It failed against the
original bare-text implementation because the card was absent. The service
fixture permits subsequent reads of the same list, preserving the existing
dialog-cancellation and configuration-change checks.

- All three `SenderInventoryScreenTest` cases passed in light and dark on
  the isolated API 37 emulator, including English at 200% system font scale.
- The full `:app:testDebugUnitTest` suite passed all 46 tests, with no failures,
  errors or skips.
- The complete 142-case instrumentation run stalled for more than five minutes
  in the existing Home test
  `expiredListStillScrollsButHidesRefreshControlsAndFeedback`. Its 400 dp
  capture clips the cached list, and the test does not complete its scroll.
  This login-expired path does not render the modified information card.
  The attempt was stopped after 15 completed cases, including one
  camera-hardware assumption skip; the full instrumentation suite is not green.
  All 126 remaining cases passed in a separate run with zero failures or
  ignored cases, including Sender inventory, revocation and Settings.
- Debug, release and instrumentation Kotlin compilation, both debug APK
  assemblies, and `qualityCheck` passed. The latter includes Android/Compose
  Lint, Spotless/ktlint, file-size limits and detekt.
- Ripwire confirmed the existing component and status contracts were unchanged.
  Its quality delta flags growth of the existing UI test class (87 to 128
  lines), short-term churn and the reflectively invoked JUnit test. No
  production complexity regression was reported. Its test gate does not
  resolve these JUnit/Compose entry points; executed tests supply the coverage
  evidence instead. The test-class size finding remains visible rather than
  adding a metrics-only abstraction or suppressing it.

Earlier device attempts were interrupted by replacement APKs on the shared
emulator, termination of the first dedicated emulator, and a private device
startup that ended before reporting assertions. The successful focused runs
used a separate ADB server and emulator port. A first fixture implementation
also blocked the second inventory read after cancelling a dialog; the final
reusable deferred response fixes that test-only problem.

The service response is simulated. Live QQ authorization, destructive
revocation and physical-device acceptance were not exercised. The existing
clock is static, so there is no motion recording. GitHub's available connector
has no attachment-upload operation; issue evidence links to the committed
comparisons and will resolve after the authorized publisher pushes the local
commit. This assignment does not push code or create or modify a pull request.

## Conflict repair with issue #127

Merged target `4971358d4100292a155995c0e7fe71e1f4ee4747` into the issue #126
branch. The runtime changes combine the initial loading card with the approved
empty-group styling. The test fixture retains a reusable deferred response and
accepts a configurable Sender list. All five inventory scenarios remain; the
loading regression additionally verifies transition to the empty other-Senders
group after a response containing only the current Sender.

Verification on the merged tree:

- All 46 host unit tests passed without failures, errors or skips.
- Debug/release Kotlin compilation, debug and instrumentation APK assemblies,
  and `qualityCheck` passed, including Lint, Spotless/ktlint, file-size and detekt.
- All five `SenderInventoryScreenTest` cases and both
  `SenderRevocationConfirmationTest` cases passed in light and dark themes on
  API 37: 14 successful executions. The baseline was 366 x 820 dp at 100% font
  scale; dedicated English accessibility cases set 200% scale themselves.
- An initial light run inherited 200% system font scale and failed three
  baseline geometry assertions. After resetting the documented baseline, all
  seven cases passed. No production change was needed for those setup failures.
- `git diff --check` passed. Ripwire confirmed unchanged production component
  contracts and no incompatible callers of the extended private test fixture.
  Its quality delta flags test-class growth and the imported repetition between
  English loaded/empty scenarios; these readable scenarios are retained. Its
  test gate still cannot resolve the JUnit/Compose entry points, so it is not
  green; executed tests provide the focused coverage evidence.

The full device suite, live QQ authorization, destructive service revocation
and physical-device acceptance were not rerun for this conflict repair. The
existing prototype comparisons from both changes are retained unchanged.
