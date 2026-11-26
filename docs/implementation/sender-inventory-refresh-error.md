# Retained Sender inventory refresh failure (#130)

The shared warning and Retry treatment from #128 and disabled Sender action
palette from #129 already cover this state. No production UI or inventory
behavior needs to change. The existing failed-refresh Compose regression now
also checks the polite warning live region, full-width Retry with a minimum
48 dp touch target, and retained Sender groups below Retry. Both revoke
actions remain disabled through the failure and pending Retry and are
restored only after a successful inventory response.

| Theme | Approved prototype (left), Android (right) |
| --- | --- |
| Light | [Comparison](sender-inventory-refresh-error-evidence/light-comparison.png) |
| Dark | [Comparison](sender-inventory-refresh-error-evidence/dark-comparison.png) |

The [approved capture](../ui-prototype/shots/05-security-refresh-error.png)
is unchanged from the pinned revision `7f69445969ebba7735af6d184738bd7e9381d97a`.
The screenshots use Simplified Chinese, API 37, 366 x 820 dp and 100% font
scale. Native fonts, status and navigation bars remain system-owned. The
full Android captures are available in
[light](sender-inventory-refresh-error-evidence/android-light.png) and
[dark](sender-inventory-refresh-error-evidence/android-dark.png).
No design deviation or motion change was introduced.

## Verification

- All ten Sender inventory Compose tests passed in each theme (20 executions),
  including the retained-failure and recovery regressions and English
  double-font-scale coverage.
- Debug, release and instrumentation Kotlin compilation, debug and
  instrumentation APK assembly, `qualityCheck` and all 46 JVM unit tests passed.
- The full 154-case API 37 instrumentation run stopped at case 67 with
  `Process crashed` in `PendingTargetTestMessageTest`. The interrupted
  accessibility case passed individually. Resumed batches reached the end
  of the suite, including all Settings cases. A Pairing consent assertion
  and a Home rejected-message layout assertion passed on individual retries.
  Four unrelated Home cases still crashed individually:
  `darkDetailsDimsThePendingTestRowAndIgnoresDuplicateTaps`,
  `androidAccessibilityReportsOneSuccessWithoutAToast`,
  `stalledInventoryReturnsTheSpecificErrorWithoutResending`, and
  `unknownRejectionUsesGenericCopyWithoutCheckingInventory`.
  The full device suite is not green.
- Ripwire reports no incompatible callers. Its quality delta flags overlap
  (a major duplication finding) between the existing failed-refresh and
  refreshing UI tests, which intentionally share assertions about retained
  groups and disabled actions.
  The test gate does not resolve reflective JUnit entry points.

Inventory responses were simulated; real QQ authorization and service
revocation were not exercised. Issue evidence links to the locally committed
comparisons and will resolve after the authorized publisher pushes the commit.
