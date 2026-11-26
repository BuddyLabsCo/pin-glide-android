# Unconfirmed Sender revocation (#136)

The unconfirmed and retrying states use the centered revocation layout with
the shared yellow ringed offline emblem, localized explanation for the
selected Sender, refresh icon, full-width Retry, and return action. Retrying
keeps both actions disabled with the approved opacity and status label.
The inventory-first reconciliation and authorization rules are unchanged.

| State | Light | Dark |
| --- | --- | --- |
| Unconfirmed | [Comparison](settings-revocation-unconfirmed-evidence/light-comparison.png) | [Comparison](settings-revocation-unconfirmed-evidence/dark-comparison.png) |
| Retrying | [Comparison](settings-revocation-unconfirmed-evidence/light-retrying-comparison.png) | [Comparison](settings-revocation-unconfirmed-evidence/dark-retrying-comparison.png) |

The approved references are the unchanged
[unconfirmed](../ui-prototype/shots/05-06-logout-unconfirmed.png) and
[retrying](../ui-prototype/shots/05-revoke-retry-active.png) captures.
They appear to the left of the Android captures in each comparison.
Both use Simplified Chinese, 100% font scale, and a comparable 366 × 820 dp
viewport on API 37. Android retains native typography, wrapping and system
bars. No motion changes were made. All four comparisons were uploaded
directly in the [issue evidence comment](https://github.com/BuddyLabsCo/pin-glide-android/issues/136#issuecomment-7188492279).

## Verification

The new Compose regression failed on the missing approved explanation before
implementation. It now checks the selected Sender, centered heading, accessible
actions, disabled retry feedback and Back handling, return to the inventory,
and absence of a second QQ authorization prompt. A second case checks the
localized English text and action reachability at 200% font scale, including
the rendered text extents and unclipped bounds.

Both cases passed in light and dark themes. All eight focused revocation tests
passed in light mode. The full API 37 instrumentation runner reported
`OK (160 tests)`, and all 46 JVM tests passed. Debug, release, and
instrumentation Kotlin compilation, both debug APK assemblies, Spotless,
Detekt, and the file-size check passed. The aggregate `qualityCheck` passed
with `--max-workers=1` after the initial debug lint analysis was interrupted
due to prolonged analyzer execution under memory pressure.

No live QQ verification or real device revocation was performed; the captures
and tests use simulated service replies.
