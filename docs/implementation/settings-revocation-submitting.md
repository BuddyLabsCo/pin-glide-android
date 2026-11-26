# Sender revocation submitting (#135)

The in-flight revocation destination uses the approved centered ringed lock
emblem, heading, selected Sender description, and disabled full-width wait
button. The shared screen layout preserves system insets and allows scrolling
on small viewports or at larger font scales. Existing revocation state
transitions and Back handling are unchanged.

| Theme | Approved prototype (left), Android (right) |
| --- | --- |
| Light | [Comparison](settings-revocation-submitting-evidence/light-comparison.png) |
| Dark | [Comparison](settings-revocation-submitting-evidence/dark-comparison.png) |

The reference is the unchanged pinned
[05-revoke-submitting capture](../ui-prototype/shots/05-revoke-submitting.png).
Both captures use Simplified Chinese, 100% font scale, and a comparable
366 × 820 dp viewport on API 37. The reference is cropped and enlarged from
the approved composite. Native Android font rendering and system bars remain
visible. No motion changes were made.

## Verification

- The new UI regression failed before the change because the disabled wait
  control was missing. It now verifies the selected Sender, centered heading,
  disabled 48 dp wait action, ignored system Back, and departure from the
  submitting state when the simulated response arrives.
- All four `SenderRevocationConfirmationTest` cases passed in both themes
  at standard font scale. The new case also passed in dark mode with the
  emulator initially set to 200% font scale; two existing cases failed at
  that nonstandard baseline and passed after restoring standard scale.
- Debug, release, and instrumentation Kotlin compilation, both debug APK
  assemblies, `qualityCheck`, and all 46 JVM tests passed.
- The full API 37 device suite completed with 155 passed, one camera-hardware
  assumption skipped, and zero failures (156 cases).

No live QQ authorization or real revocation was performed. Issue comparison
links point to the local commit and require the authorized publishing workflow
before they resolve.

## Review follow-up

An exhaustive screen dispatch preserves the existing step behavior. The
submitting heading and selected-Sender description form one polite live
region with a single heading. The three text elements can use the available
width so English remains readable at 200% system font scale.

The focused regression verifies the rendered lock body and keyhole, badge
tile, vertical group centering, and full-width wait action. The English
case checks the merged announcement, unclipped text, absence of text layout
overflow, disabled action, and exit after the simulated response.

All five focused cases passed in both themes on API 37. Debug, release and
instrumentation Kotlin compilation, both debug APK assemblies,
`qualityCheck`, and all 46 JVM tests passed. The comparison images were
recaptured; the full device suite was not repeated for this focused fix.

Issue attachment publication is complete: GitHub CLI 2.106.0 uploaded both
comparisons directly in [the evidence comment](https://github.com/BuddyLabsCo/pin-glide-android/issues/135#issuecomment-7186961307).
The attachments do not depend on publishing the local commits.

## Second review follow-up

The heading assertion measures the rendered text line rather than the
full-width text node. The wait action and vertical centering are measured
against the actual safe, width-capped scroll viewport. Artwork samples use
the status group's screen coordinates.

All five focused cases passed in both themes at 366 × 820 dp, and the
submitting regression also passed at 700 × 820 dp. Both debug APK assemblies
and `qualityCheck` passed; all 46 JVM tests remain passing. The full device
suite was not repeated.

The dark comparison was recaptured in an isolated submitting test at settled
100% font scale. Its status icons are normal size and its white gesture
indicator is visible. The [corrected direct attachment](https://github.com/BuddyLabsCo/pin-glide-android/issues/135#issuecomment-7187307246)
supersedes the previous dark attachment. Runtime styling is unchanged.
