# Sender revocation confirmation (#131)

The Sender inventory opens the shared `ConfirmationDialog` with the approved
48 dp danger emblem, lock artwork, selected Sender name, consequence copy,
and Cancel followed by a filled danger action. The Chinese title uses the
prototype's corner brackets. The dialog's lock path follows the prototype's SVG.

Cancel, system Back, and confirmation retain the existing revocation callbacks.
Confirmation opens QQ owner verification; it does not submit a revocation or
launch QQ authorization itself. The shared modal provides scrolling, wrapping
actions, a named accessibility pane, a heading, and 48 dp action targets.

| Theme | Approved prototype (left), Android (right) |
| --- | --- |
| Light | [Comparison](settings-revocation-confirmation-evidence/light-comparison.png) |
| Dark | [Comparison](settings-revocation-confirmation-evidence/dark-comparison.png) |

The reference is the unchanged pinned
[05-09 capture](../ui-prototype/shots/05-09-revoke-confirm.png).
Both captures use Simplified Chinese and comparable 366 x 820 dp viewports.
Android captures use the production Settings destination on API 37 with
simulated Sender inventory responses. Native font rasterization, system bars,
and dimming are retained. The underlying inventory page is outside this dialog
change. No prototype or motion changes were made.

The regression checks open the real Settings destination and verify the chosen
Sender, consequence copy, danger colors, action layout and minimum target size,
Cancel, system Back, QQ verification navigation, and English actions at 200%
system font scale. Live QQ and service revocation are outside this UI check.

Debug/release Kotlin compilation, instrumentation compilation, both debug APK
assemblies, `qualityCheck`, and all 46 JVM tests passed. The final dark-theme
confirmation tests passed, and the final dialog was pixel-identical to the
recorded dark comparison.

The first full UI run exposed a Settings glyph regression from changing the
shared lock. The approved revocation artwork is now a dialog-specific drawable,
and the Settings glyph regression passed on retry. The Account Toast check
also expected Chinese while this fresh emulator used English; it passed after
setting the device locale to `zh-Hans-CN`. No Account behavior was changed.

The final full instrumentation run completed all 139 tests without failures:
138 passed and one camera-hardware assumption was skipped. The dedicated
revocation tests passed in both themes, including English at 200% font scale.

The ripwire contract check reported the inventory interface unchanged. Its
structural analysis flags repeated UI assertion patterns and does not recognize
JUnit reflective entry points; these findings were reviewed against the actual
instrumentation runs. No production complexity regression was reported.
