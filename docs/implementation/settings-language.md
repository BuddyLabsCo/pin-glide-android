# Language picker (#120)

The Language destination reuses the Settings header and rounded group from
[#119](settings-root.md). Its three 60 dp rows have the approved separators,
bold labels and radio outlines. Each full row remains a selectable radio
option. The existing Settings navigation applies a choice immediately and
returns to the root, with no confirmation or additional screen.

| Theme | Comparison |
| --- | --- |
| Light | [Approved and Android](settings-language-evidence/light-comparison.png) |
| Dark | [Approved and Android](settings-language-evidence/dark-comparison.png) |

The left sides are crops of the
[approved reference capture](../ui-prototype/shots/05-02-language.png),
scaled to the same 366 × 820 dp viewport as the API 37 Android captures on the
right. Both use Simplified Chinese and 100% font scale. The Android captures
use the production Settings dialog, with native font rendering and system
bars. No prototype changes or new navigation motion are introduced.

`SettingsScreenTest` verifies the Settings header, 180 dp group, accessible
selected/unselected options, immediate return to Settings, and the selected
value when revisiting the picker. It exercises all three choices, including
the immediate switch to English and back to the simulated Chinese system
locale. The regression test failed against the original header before the
layout fix. All four Settings tests pass in both light and dark themes,
including the existing root layout, large-font navigation and keyboard
focus checks.

The previous shared evidence-helper rename left two stale calls in the
Pairing success assertions; these now use the existing helper so the
instrumentation APK compiles. Debug and release Kotlin compilation, debug
and instrumentation APK assembly, `qualityCheck` and all 46 JVM tests pass.

The full instrumentation run on the shared emulator was interrupted by APK
replacement. Two Home assertions failed before the interruption but passed
on the isolated API 37 emulator. On that emulator the full run and its
continuation covered all 129 tests: 127 passed, one camera-feed assumption
was skipped, and the existing
`HomeLoginExpiredScreenTest.expiredListStillScrollsButHidesRefreshControlsAndFeedback`
stalled. The isolated single-test retry also stalled despite a configured
60-second test timeout and was terminated. No change to the unrelated Home
test or implementation was made.

Review follow-up: the root and language rows now share their presentation
geometry and label typography, with the language-specific selectable modifier
and compact separator spacing unchanged. `LanguageScreenTest` holds the
picker's existing navigation assertions and checks the rendered ring centers,
stroke thicknesses, circular outlines and both separators. The group height
tolerance is in physical pixels so the test accepts independent dp rounding.
The compact 240 dp viewport test uses 200% font scale and activates all three
radio options by touch, checking the radio role and selected state.

The five focused tests passed in light and dark on API 37, and both picker
tests passed at density 2.625. Debug/instrumentation assembly, debug/release
compilation, `qualityCheck` and the 46 JVM tests passed.

The operator confirmed that the approved prototype is correct and requested
that this review fix remain limited to UI changes. The unselected radio
outlines therefore retain the prototype colors in both themes, rather than
applying the proposed contrast increase. The measured contrast limitation
(approximately 2.06:1 in light and 2.68:1 in dark) remains. The additional
signed-in production activity test was removed as out of scope; the existing
picker selection, navigation and accessibility UI assertions are retained.
