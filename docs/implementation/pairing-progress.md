# Pairing channel-opening progress

The approved reference for issue #111 is the [channel-opening capture](../ui-prototype/shots/03-09-progress.png).
The comparisons use Simplified Chinese on an API 37 emulator at 366 × 820 dp,
with the approved prototype on the left and Android on the right.
Android supplies the status and navigation bars.

| Theme | Comparison | Motion |
| --- | --- | --- |
| Light | [Approved and Android](pairing-progress-evidence/light-comparison.png) | [Recording](pairing-progress-evidence/light-motion.mp4) |
| Dark | [Approved and Android](pairing-progress-evidence/dark-comparison.png) | [Recording](pairing-progress-evidence/dark-motion.mp4) |

The captures render the shared progress presentation with the simulated
channel-opening stage. They require no sign-in or live Receiver. The UI uses
the existing reduced-motion-aware progress ring, localized current-stage
heading, and explanatory copy. The existing Pairing workflow and Back policy
remain unchanged.

`PairingProgressScreenTest` verifies that only the current stage is visible
when the supplied stage changes, progress and live-region accessibility are
exposed, and the presentation offers no actions. `QqLoadingEmblemTest` verifies
the shared ring's motion and reduced-motion behavior.

## Identity generation (#112)

The shared presentation already matches the [identity-generation reference](../ui-prototype/shots/03-progress-identity.png).
No runtime or motion changes are needed. The Identity event still selects the
heading before the cryptographic exchange starts.

| Theme | Comparison |
| --- | --- |
| Light | [Approved and Android](pairing-progress-identity-evidence/light-comparison.png) |
| Dark | [Approved and Android](pairing-progress-identity-evidence/dark-comparison.png) |

These captures use the same API 37 viewport (366 × 820 dp) and Simplified Chinese,
with the approved prototype on the left and Android on the right. System bars,
safe-area centering and native font rendering account for the small positioning
and text-wrapping differences. The progress arc is captured at a different phase.

The Identity stage is simulated at the presentation boundary, without a live
Receiver. A temporary Compose evidence test passed in both themes, verifying
the transition from Opening to Identity, the localized accessible heading,
the instruction, and absence of actions. The existing stage-transition and
shared progress tests remain the permanent regression coverage.

Verification: all six focused Compose tests (including the temporary Identity
check) passed in light mode, and the Identity check passed in dark mode.
All 46 JVM tests, `qualityCheck`, release Kotlin compilation and instrumentation
test compilation passed. The full instrumentation run stopped with a process
crash in `HomeDuplicateScanTest`; live Pairing was not exercised.
