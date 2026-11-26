# Cancelled QQ owner verification (#133)

The shared owner-proof screen now uses the existing warning card with the
approved warning icon, centered content width and localized cancellation
message. It retains the selected Sender, assertive accessibility announcement,
and the enabled verification and Cancel actions. Cancellation submits no
revocation; another verification requests fresh QQ authorization.

| Theme | Approved prototype (left), Android (right) |
| --- | --- |
| Light | [Comparison](settings-owner-proof-cancelled-evidence/light-comparison.png) |
| Dark | [Comparison](settings-owner-proof-cancelled-evidence/dark-comparison.png) |

Both comparisons use Simplified Chinese, 100% font scale and a 366 × 820 dp
API 37 emulator viewport. The left screen is cropped from the unchanged
[pinned reference](../ui-prototype/shots/05-owner-proof-cancelled.png) and
resized to the Android capture. Native system bars and font rendering are
retained. No motion or prototype changes were made.

The focused UI regression failed before implementation on the missing warning
card and passed afterward. All three `SenderRevocationConfirmationTest` cases
passed in both themes, including the warning palette and icon, centered
placement, selected Sender, absence of submission on cancellation, fresh
authorization on retry and English cancellation at 200% font scale.

Debug, release and instrumentation Kotlin compilation, both debug APK
assemblies, `qualityCheck` and all 46 JVM tests passed. The first full
device run stalled on a Home gesture test. The retry completed all 155
cases: 153 passed, one camera-hardware assumption was skipped, and one
unrelated Home rename test timed out waiting for the IME. That test
passed when rerun individually. No Home code was changed.

Ripwire's quality delta flags churn and increased size in the existing
instrumentation test class. Its test gate does not resolve reflective
JUnit entry points; the focused and full device runs provide direct
coverage instead.

No real QQ authorization or destructive service operation was exercised.
Issue evidence links require publication of the local commit by the
authorized publishing workflow; no issue attachment upload tool is
available.
