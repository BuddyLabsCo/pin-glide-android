# QQ owner verification (#132)

The owner-proof destination now uses the Settings header, a centered ringed
Pin Glide emblem, the owner-proof eyebrow, the selected Sender description,
and the bottom verification and Cancel actions. The verification button uses
the approved account glyph. The layout scrolls when the viewport or system
font scale requires it.

The existing revocation workflow remains unchanged. Confirmation does not
launch QQ or submit a revocation; the verification action requests fresh QQ
authorization for the selected Sender. Pending verification disables that
action. Cancellation, wrong-Account feedback and return to inventory retain
their existing behavior.

| Theme | Approved prototype (left), Android (right) |
| --- | --- |
| Light | [Comparison](settings-owner-proof-evidence/light-comparison.png) |
| Dark | [Comparison](settings-owner-proof-evidence/dark-comparison.png) |

The reference is the unchanged pinned
[05-08 capture](../ui-prototype/shots/05-08-owner-proof.png).
Each comparison uses Simplified Chinese, 100% font scale and a comparable
366 × 820 dp viewport on API 37. The reference screen is cropped from the
approved composite and enlarged to match the Android screenshot; native
font rendering and system bars remain visible. No motion or prototype
changes were made.

## Verification

- The owner-proof UI regression failed on the missing eyebrow before the
  layout change and passed afterward.
- All three `SenderRevocationConfirmationTest` cases passed in each theme
  on an isolated API 37 emulator. They cover layout and copy, 48 dp action
  targets, cancellation, English at 200% system font scale, fresh proof
  after a cancelled request, pending verification, and wrong-Account
  rejection of a submission scoped to the selected Sender.
- Debug, release and instrumentation Kotlin compilation, both debug APK
  assemblies, `qualityCheck`, and all 46 JVM tests passed.
- The full 155-case device suite completed on the isolated emulator:
  154 passed, one camera-hardware assumption was skipped, and no tests
  failed. Earlier attempts on the shared emulator were interrupted by
  concurrent instrumentation and device configuration changes.

The ripwire contract check reports no incompatible callers. Its quality
delta flags similar wizard composition to the existing expired-Pairing
screen and growth in the instrumentation test class. The test gate does not
resolve reflective JUnit entry points. The existing shared screen primitives
are reused without changing the unrelated Pairing destination.

No real QQ authorization or destructive service operation was exercised.
The issue evidence links require the authorized publisher to publish the
local commit before they resolve. The GitHub browser session was signed out
and the connector does not expose an issue-attachment upload operation.

## Review follow-up

The emblem and eyebrow now sit 9 dp lower, matching the pinned reference
in both updated comparisons. The heading and description spacing is adjusted
to preserve their approved positions and keep the bottom actions anchored.

The focused regression checks rendered brand tile edges and bird pixels,
the emblem's centered position relative to the header and actions, the
Cancel bottom inset and the action gap. The English 200% font-scale case
scrolls to the full selected-Sender description and checks its clipped
and unclipped dimensions and text layout for overflow.

All three focused cases passed in each theme on the isolated API 37
emulator. Debug, release and instrumentation Kotlin compilation, both
debug APK assemblies, `qualityCheck`, and all 46 JVM tests passed. The
full instrumentation suite was not repeated for this focused review fix.
Ripwire reports the screen contract unchanged and flags test-class growth;
the additional regression assertions are intentional.
