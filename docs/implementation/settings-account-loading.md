# QQ Account profile loading (#122)

The shared Account destination was already corrected by #121. While the
profile read is pending, the approved profile card contains the blue QQ text
badge and bold localized loading label, with no nickname subtitle. The
separate Logout row remains actionable. There is no need for another runtime
component or a prototype change.

| Theme | Approved prototype (left), Android (right) |
| --- | --- |
| Light | [Comparison](settings-account-loading-evidence/light-comparison.png) |
| Dark | [Comparison](settings-account-loading-evidence/dark-comparison.png) |

The reference crops use the unchanged
[approved loading capture](../ui-prototype/shots/05-account-loading.png).
Android opens the production Settings dialog with a suspended profile response
on API 37. Both comparisons use Simplified Chinese, 100% font scale and a
comparable 366 × 820 dp viewport. Native fonts and system bars are retained.
No motion or design deviation was introduced.

## Verification

The existing `AccountScreenTest` now checks the approved card and Logout row
geometry, typography, badge and action artwork in the pending state as well
as the loaded state. The pending label is read-only and announced through
a polite live region. The test opens and cancels Logout confirmation before
the simulated profile response arrives, verifies that the loading label
remains visible, then completes the profile read and checks the existing
nickname and Logout confirmation behavior.

The compact English test also checks the pending label at 200% system font
scale. All four Account tests passed in both themes. Debug, release and
instrumentation Kotlin compilation, both debug APK assemblies,
`qualityCheck` and all 46 JVM tests passed.

The full 144-case instrumentation suite completed on API 37 with 143 passes,
one camera-hardware assumption skip (`cameraRecognizesAnotherQrAfterRejection`)
and no failures.

Ripwire reported no incompatible callers or gating quality regressions; its
only finding was the minor increase in the existing Account test class size.

Simulated profile data was used; no live QQ login, service logout, or
physical-device acceptance was exercised.

Review follow-up: the compact English test now checks the loading label's
actual 200% font scale, absence of visual overflow or ellipsized lines, and
the complete visible text length. The loaded subtitle typography assertion
is unconditional in the loaded-profile test. The pending-state helper's
name now identifies capture and opening/cancelling Logout confirmation.
All four Account tests passed again in both themes on API 37.
Instrumentation compilation and APK assembly and `qualityCheck` passed;
the JVM test task was up to date. The full instrumentation suite was not
rerun for this test-only follow-up. Production UI and the comparisons are unchanged.
