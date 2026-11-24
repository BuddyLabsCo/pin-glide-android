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
