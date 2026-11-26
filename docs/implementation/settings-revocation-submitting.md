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
