# Pairing failure (#116)

The shared Pairing result screen now uses the approved
[failure-preserved reference](../ui-prototype/shots/03-12-failure-preserved.png)
for a failed connection or confirmation. It centers the warning illustration,
status label, heading and assurance that existing Pairings are unaffected.
The bottom actions rescan or return to Home; onboarding uses its own approved
assurance and “Pair later” label. Unconfirmed and expired-session results keep
their existing behavior and presentation.

| Theme | Comparison |
| --- | --- |
| Light | [Approved and Android](pairing-failure-evidence/light-comparison.png) |
| Dark | [Approved and Android](pairing-failure-evidence/dark-comparison.png) |

The comparisons use Simplified Chinese on an API 37 emulator at 366 × 820 dp,
with the approved prototype on the left and Android on the right. Android
supplies the status and navigation bars and native font rendering. No motion
changes were made.

`PairingIdentityProgressTest.failureResultDisplaysPreservationAssuranceAndHostActions`
uses the simulated failure state at the shared result-screen boundary. It
checks the accessible heading, approved Chinese copy for both hosts, the
return callback and the existing fresh-scan transition. This does not require
live sign-in or a Receiver, and the underlying Pairing workflow is unchanged.

Verification: the regression initially failed on the missing approved status
label, then passed in both themes. The full instrumentation report contains
112 tests: 111 passed, one camera-feed test was skipped by its assumption,
and none failed. All 46 JVM tests, `qualityCheck`, debug and release Kotlin
compilation, and debug and instrumentation APK assembly passed.
