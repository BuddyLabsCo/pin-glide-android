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
Identity generation now runs off the presentation thread so the Identity event
can render the heading during generation. The Key event is emitted only after
generation completes. Cancellation closes any resulting handshake without
sending it. The exchange also rejects a replaced authorization session before
sending, even for the same Account and Sender. No artificial delay or motion
change is introduced.

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
the instruction, and absence of actions. `PairingIdentityProgressTest` additionally
exercises the real entry workflow and shared screen with controllable generation:
Identity stays visible while generation is pending, Key follows completion,
and abandoning or replacing the authorization session during generation closes
the handshake without sending.

Verification: all six focused Compose tests (including the temporary Identity
check) passed in light mode, and the Identity check passed in dark mode.
All 46 JVM tests, `qualityCheck`, release Kotlin compilation and instrumentation
test compilation passed. The full instrumentation run stopped with a process
crash in `HomeDuplicateScanTest`; live Pairing was not exercised.

Review fix verification: the workflow test failed before the dispatcher change
because the Identity heading was not displayed. After the change, all 13
Pairing and shared progress Compose tests passed, including the abandonment
regression. All 46 JVM tests, `qualityCheck`, debug and instrumentation assembly,
and release Kotlin compilation passed.

## Key exchange (#113)

The [key-exchange reference](../ui-prototype/shots/03-progress-key.png) uses
the same single-stage composition corrected by #111. The existing Key event
selects the localized heading and retains the shared instruction, emblem and
indeterminate progress feedback. No production layout, motion or workflow
change is needed.

| Theme | Comparison |
| --- | --- |
| Light | [Approved and Android](pairing-progress-key-evidence/light-comparison.png) |
| Dark | [Approved and Android](pairing-progress-key-evidence/dark-comparison.png) |

Both comparisons use Simplified Chinese and a 366 × 820 dp API 37 emulator
viewport, with the pinned prototype on the left and Android on the right.
Native font rendering, safe-area centering and system bars account for the
small visual differences. The screenshots show the stationary progress arc
with system motion disabled; the unchanged motion recordings are linked above.

`PairingProgressScreenTest` now also verifies the approved Chinese heading
and instruction for Key, heading accessibility, the absence of other stage
headings and the absence of actions. The existing `PairingIdentityProgressTest`
checks the workflow transition into Key. The captures simulate the stage at the
presentation boundary, without live sign-in or a Receiver.

Verification: the key-exchange evidence check passed in both themes. All nine
focused Compose tests (shared progress, identity transition and ring motion),
all 108 instrumentation tests, and all 46 JVM tests passed. `qualityCheck`,
debug and release Kotlin compilation, and debug and instrumentation assembly
also passed.

## Final confirmation (#114)

The shared presentation corrected by #111 already matches the
[confirmation reference](../ui-prototype/shots/03-progress-confirm.png).
No runtime layout, motion or workflow change is needed.

| Theme | Comparison |
| --- | --- |
| Light | [Approved and Android](pairing-progress-confirmation-evidence/light-comparison.png) |
| Dark | [Approved and Android](pairing-progress-confirmation-evidence/dark-comparison.png) |

Both comparisons use Simplified Chinese on API 37 at 366 × 820 dp, with the
approved prototype on the left and Android on the right. The Android system
bars, safe-area centering, native font rendering and progress arc phase account
for the small differences. Existing motion evidence is recorded under #111.

The screenshots were captured during the actual Pairing entry and activation
workflow using simulated service, handshake and storage collaborators.
`PairingIdentityProgressTest.confirmationRemainsVisibleUntilActivationIsReconciled`
holds the service completion response, the authoritative Pairing inventory,
and then the local reconciliation operation independently. At all three
boundaries it verifies the final localized heading,
absence of previous stages and actions, the instruction, and accessible
indeterminate progress. It verifies the provisional Pairing is not active
before reconciliation and the expected usable Pairing is active afterward,
before accepting the transition to Pairing complete. No live Receiver or QQ
sign-in is used.

Verification: all nine focused Compose tests passed in light and dark themes.
All 46 JVM tests, `qualityCheck`, debug and instrumentation APK assembly, and
debug and release Kotlin compilation passed.
The full 108-test instrumentation run was stopped after test 16,
`HomeLoginExpiredScreenTest.expiredListStillScrollsButHidesRefreshControlsAndFeedback`,
stalled for over five minutes; 14 tests had passed and one camera test was
skipped by its assumption. Ripwire reported test-fixture verbosity and recent
change churn; there are no runtime source changes.
The subsequent focused run of `PairingConsentNavigationTest`,
`PairingConsentScreenTest` and `HomeCapacityScreenTest` passed all 13 tests.

Review fix verification: the extracted `HomePairingStore` fixture rejects
invalid provisional keys and duplicate Pairing identities, as well as
mismatched receivers during reconciliation. The workflow test now holds
local reconciliation separately from the inventory response. All 15 focused
tests passed, including the two fixture invariant tests. The remaining Home
fixture consumers passed 25 tests, with one camera test skipped by its
assumption. All eight Pairing progress tests passed in dark mode. All 46 JVM
tests, `qualityCheck`, debug and release Kotlin compilation, and debug and
instrumentation APK assembly passed. The full instrumentation suite was not
repeated for the review fixes.
