# Pairing failure (#116)

The shared Pairing result screen now uses the approved
[failure-preserved reference](../ui-prototype/shots/03-12-failure-preserved.png)
for a failed connection or confirmation. It centers the warning illustration,
status label, heading and assurance that existing Pairings are unaffected.
The bottom actions rescan or return to Home; onboarding uses its own approved
assurance and “Pair later” label. Unconfirmed results keep their existing
behavior and presentation. Expired-session results use the separate
[approved expired QR screen](pairing-expired.md).

The onboarding variant follows its own approved
[failure reference](../ui-prototype/shots/01-25-pairing-failed.png).

| Theme | Home comparison | Onboarding comparison |
| --- | --- | --- |
| Light | [Approved and Android](pairing-failure-evidence/light-comparison.png) | [Approved and Android](pairing-failure-evidence/onboarding-light-comparison.png) |
| Dark | [Approved and Android](pairing-failure-evidence/dark-comparison.png) | [Approved and Android](pairing-failure-evidence/onboarding-dark-comparison.png) |

The comparisons use Simplified Chinese on an API 37 emulator at 366 × 820 dp,
with the approved prototype on the left and Android on the right. Android
supplies the status and navigation bars and native font rendering. No motion
changes were made.

The captures use the real Pairing entry workflow with a simulated connection
failure. Four `PairingIdentityProgressTest` cases enter the actual failure
state for Home and onboarding, check the accessible heading and approved
Chinese copy, and independently exercise the rescan and respective return
actions. Rescan returns to Scanner for the same host and clears the failure;
return invokes the host's leave callback and closes the entry. This does not
require live sign-in or a Receiver, and the underlying workflow is unchanged.

On compact windows the failure screen scrolls as a whole, rather than
allowing the fixed actions to consume the viewport. At normal height the
centered warning illustration and bottom actions retain their approved layout.
`PairingFailureScreenTest` verifies that the heading and assurance remain
reachable in a 120 dp usable viewport at 200% font scale for both hosts,
and that actual touch input after scrolling invokes each action's callback.

Initial implementation verification: the regression initially failed on the
missing approved status label, then passed in both themes. The full instrumentation report contains
112 tests: 111 passed, one camera-feed test was skipped by its assumption,
and none failed. All 46 JVM tests, `qualityCheck`, debug and release Kotlin
compilation, and debug and instrumentation APK assembly passed.

Review fix verification: the compact-window regression initially failed
because the actions were outside the scroll container; it now passes. All
24 Pairing instrumentation tests passed, including all four failed-host
action tests. Both retry tests also passed with screenshot capture in light
and dark themes. All 46 JVM tests, `qualityCheck`, debug and release Kotlin
compilation, and debug and instrumentation APK assembly passed. The full
instrumentation suite was not repeated for the review fixes.

Second review verification: both compact host tests successfully tap the
scrolled retry and return actions and observe the corresponding callbacks.
The English Home workflow test confirms through the correct localized action
and reaches the failure screen. All 26 Pairing instrumentation tests and
`qualityCheck` passed; debug/release Kotlin compilation and the 46-test JVM
suite were up to date. No runtime or screenshot changes were needed.
