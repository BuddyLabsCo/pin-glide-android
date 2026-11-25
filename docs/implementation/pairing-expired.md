# Expired Pairing QR (#115)

The shared Pairing entry screen now presents the approved expired-session
screen whenever the existing workflow reports `Closed`, whether detected
while scanning, at consent, or during activation. The warning-colored clock
emblem, text hierarchy and QR action match the prototype. Home offers
“返回首页”; onboarding offers “稍后配对”. System Back retains the existing
close behavior, and the fresh-scan action uses the existing session cleanup
and scanner transition. The session validation and activation rules are
unchanged.

The comparisons use the pinned
[Home reference](../ui-prototype/shots/03-10-expired.png) and
[onboarding reference](../ui-prototype/shots/01-16-session-expired.png)
on the left, and Android on the right in Simplified Chinese at 366 × 820 dp
on API 37. Native font rendering and system bars account for the small
remaining visual differences. No motion behavior changed.

| Host | Light | Dark |
| --- | --- | --- |
| Home | [Comparison](pairing-expired-evidence/home-light-comparison.png) | [Comparison](pairing-expired-evidence/home-dark-comparison.png) |
| Onboarding | [Comparison](pairing-expired-evidence/onboarding-light-comparison.png) | [Comparison](pairing-expired-evidence/onboarding-dark-comparison.png) |

`PairingConsentNavigationTest` exercises the real Pairing entry workflow with
simulated service responses. It rejects an expired authorization, scans a
fresh session, receives closure while consent is open, and returns to Home.
It also verifies the onboarding skip action, English at 200% font scale,
and system Back without completing onboarding. The new Home regression
failed on the old UI because the expired heading was absent.

Verification: all five focused navigation tests passed in light mode.
In dark mode all three new tests and the existing onboarding Back test
passed; an independently started instrumentation process interrupted the
last existing consent test. Debug and release Kotlin compilation, both APK
assemblies, `qualityCheck`, and all 46 JVM tests passed. The full
114-test instrumentation run was attempted: three tests passed, an unrelated
Home acknowledgment test failed with no Compose hierarchy, and another
instrumentation launch force-stopped the process during the fifth test.
No live Receiver or QQ sign-in was used.

Ripwire found no incompatible `RingedEmblem` callers. Its quality delta
flags the similar Compose text layout in the progress and expired screens
and the extended navigation fixture; the screens intentionally retain
distinct presentation and the tests reuse the existing integration fixture.

Review fix verification: the expired and progress screens share their
focused, politely announced heading without changing either layout.
The English copy uses “target device”. The onboarding test verifies the
completed setup checkpoint and the visible, enabled fresh-scan action at
200% font scale. `PairingIdentityProgressTest` now exercises an expiry
event during activation: it verifies the expired screen supersedes the
generic failure, the connection and handshake close, the session is
cancelled once without retaining a Pairing, and the released QR is rejected
without another authorization request. A different session reaches consent.

All 15 focused Compose tests passed in each theme. All 46 JVM tests,
`qualityCheck`, debug and release Kotlin compilation, and both APK
assemblies passed. The full instrumentation suite was not repeated for
these review fixes. Ripwire reported recent test-fixture churn and verbosity;
the progress screen's caller contract is unchanged.
