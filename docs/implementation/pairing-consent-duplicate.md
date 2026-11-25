# Duplicate target name at Pairing confirmation (#110)

The shared consent layout from #107 and #108 already provides an editable
name field with an error-colored border and halo, accessible validation
guidance, and a disabled confirm action. The duplicate state now uses the
exact approved Chinese guidance and the prototype's 38% opacity on the
primary-colored confirm button, rather than the default gray disabled style.
Alias validation, the countdown, the shared illustration and navigation are
unchanged.

The comparisons use the pinned [duplicate-name reference](../ui-prototype/shots/03-consent-duplicate.png)
on the left and an API 37 emulator on the right, in Simplified Chinese at
366 × 820 dp. Android supplies the system bars and native text rendering;
the illustration is captured at a different animation phase.

| Theme | Comparison |
| --- | --- |
| Light | [Approved and Android](pairing-consent-duplicate-evidence/light-comparison.png) |
| Dark | [Approved and Android](pairing-consent-duplicate-evidence/dark-comparison.png) |

`PairingConsentScreenTest` renders a simulated Home consent state with an
existing name, then edits it through the existing alias validator. It checks
the error semantics and guidance, disabled confirm appearance and action,
and restoration of confirmation after the name is corrected. The English
test also checks a duplicate that differs in case and surrounding whitespace.
No live service session or Receiver is required. No motion behavior changed.

Verification: the new regression initially failed on the gray disabled
button, then passed in both themes. All 15 Pairing instrumentation tests,
46 JVM unit tests, debug/release Kotlin compilation, instrumentation
assembly and `qualityCheck` passed. The full instrumentation run stalled
at `HomeLoginExpiredScreenTest.expiredListStillScrollsButHidesRefreshControlsAndFeedback`.
The retry excluding that method completed 88 non-Pairing tests successfully;
one camera-feed assumption skipped its test. The Home scrolling test also
timed out when run separately. The final Pairing suite passed independently
after removing a scroll assertion that could stall with the test clock paused.
