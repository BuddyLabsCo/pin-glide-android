# Sender inventory refresh (#129)

The shared loading card restored by #126 already shows the approved clock,
localized status copy and polite live region above the retained Sender groups.
The shared Sender row now uses the prototype's disabled button palette:
the highest surface container and surface-variant text at 38% opacity.
Enabled danger actions and the existing inventory verification gate remain
unchanged.

| Theme | Approved prototype (left), Android (right) |
| --- | --- |
| Light | [Comparison](sender-inventory-refreshing-evidence/light-comparison.png) |
| Dark | [Comparison](sender-inventory-refreshing-evidence/dark-comparison.png) |

The reference crops render the `security-refreshing` state of the approved
[Settings prototype](../ui-prototype/05-settings.html?screen=security-refreshing).
That state's markup, styles and reference capture are unchanged from the
pinned revision `7f69445969ebba7735af6d184738bd7e9381d97a`.
Both comparisons use Simplified Chinese, 100% font scale and a 366 × 820 dp
viewport on API 37. Native text and system bars are retained; there is no
design deviation or motion change.

## Verification

The new inventory regression opens the production Settings destination with
simulated Sender rows, triggers the existing foreground inventory refresh,
and holds the response pending. It checks the clock status card's placement,
height and accessible announcement, retained groups, disabled revoke actions
and button background color. Completing the response removes the card and
re-enables both actions.

- The color assertion failed before the presentation change and passed
  afterward.
- All six `SenderInventoryScreenTest` cases passed in light and dark
  (12 executions), including existing English 200% font-scale coverage.
- Debug, release and instrumentation Kotlin compilation, debug and
  instrumentation APK assemblies, `qualityCheck` and all 46 JVM tests
  passed.
- The 150-case device suite stalled after 14 passes and one camera
  assumption skip in the previously documented
  `HomeLoginExpiredScreenTest.expiredListStillScrollsButHidesRefreshControlsAndFeedback`.
  The run excluding that case completed with 146 passes, one camera
  assumption skip and two unrelated Pairing consent visibility failures.
  Both failed cases passed individually on retry:
  `backReturnsToScannerAndCancelReturnsToFirstDeviceEntry` and
  `compactHeightAtDoubleFontScaleKeepsAliasAndActionsReachable`.
  In total, 148 distinct device cases passed, one was skipped and one
  stalled. The uninterrupted full device suite is not green.
- Ripwire found no incompatible callers. Its quality delta is not green:
  the existing inventory test class has short-horizon churn and minor
  growth, and the new reflectively invoked JUnit test is reported as dead
  code. Its test gate does not resolve the JUnit/Compose entry points.
  Executed tests establish coverage without a metrics-only refactor.

No live QQ authorization, destructive revocation or physical-device
acceptance was exercised. The status icon is static, so no recording is
needed. Issue evidence links to the locally committed comparisons and will
resolve when the authorized publisher pushes the commit; no attachment
upload operation is available through the GitHub connector.
