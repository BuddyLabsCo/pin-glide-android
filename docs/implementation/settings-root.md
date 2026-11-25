# Main Settings (#119)

The Settings root follows the [approved reference](../ui-prototype/shots/05-01-settings.png):
Account, Display, Background, Security and About are separate rounded groups.
Display and About have contiguous rows with a shared divider. The account badge,
language and appearance glyphs, info icon, row values and chevrons follow the
prototype. Language keeps its selected value; appearance follows the system;
the version comes from the installed package rather than the prototype's sample.

| Theme | Comparison |
| --- | --- |
| Light | [Approved and Android](settings-root-evidence/light-comparison.png) |
| Dark | [Approved and Android](settings-root-evidence/dark-comparison.png) |

The comparisons use Simplified Chinese at 366 × 820 dp on an API 37 emulator,
with the approved prototype on the left and Android on the right. Android
provides native font rendering and system bars, with theme-appropriate
system bar icon contrast in the full-screen dialog. The Account chevron
matches the other chevrons in both themes, as confirmed by the operator in
the [second review design clarification](https://github.com/BuddyLabsCo/pin-glide-android/issues/119#issuecomment-7162893031).
The actual installed version
is `0.1.0`; the prototype illustrates `1.0`. The shared `SettingsPage` keeps
the header and Back control fixed while the groups scroll, preserving the
original Android scrolling behavior.

`SettingsScreenTest` verifies the actual Settings root's five group surfaces,
row heights, contiguous dividers, icons, installed version, read-only
information rows, navigation actions, and existing battery-settings failure
feedback. A compact English viewport at 200% font scale verifies a distinctive
supplied version and touch activation of Privacy and the fixed Back control.
The production `SettingsNavigation` dialog is exercised with the real
application dependencies: Language returns to the focused row, and a
blocking Android instrumentation monitor observes the system-owned battery
settings intent without changing a system setting. The captures use this
production dialog, not the isolated root.

Initial implementation verification: both Settings tests passed in light and dark themes. The full
instrumentation report contains 124 tests: 123 passed, one camera-feed test
was skipped by its assumption, and none failed. All 46 JVM tests,
`qualityCheck`, debug and release Kotlin compilation, and debug and
instrumentation APK assembly passed. The initial baseline instrumentation
attempt was interrupted by another runner updating the shared emulator;
it did not produce a reliable failing assertion. Live QQ sign-in and the
external battery settings activity were not exercised by the focused tests.

Review fix verification: the new compact-window assertion failed against
the initial implementation because Back scrolled off-screen. The repaired
shared page passes that assertion at 200% font scale. All three Settings
tests passed in each theme on API 37, including layout pixel and extent
assertions, dynamic version rendering, keyboard focus restoration through
the production dialog, and the intercepted system settings launch. All 23
affected Pairing tests passed with the existing `consentEvidence` argument
enabled, exercising the shared evidence helper. The 46 JVM tests,
`qualityCheck`, debug and release Kotlin compilation, and debug and
instrumentation APK assembly passed. The full instrumentation suite was
not repeated for this review fix. Some intermediate attempts were
interrupted by other runner APK replacements or an isolated emulator's
system UI ANR; the successful final runs used the shared API 37 emulator.

Second review test verification: the reference-layout test explicitly uses
100% font scale and compares six row glyphs against fixed shape masks from
the reviewed visual evidence, allowing small rasterization differences.
The independent compact 200% font-scale test remains in place. All three
Settings tests passed in both themes, and the reference-layout test also
passed with the emulator's system font scale set to 150%. The six masks
reject each of the other five glyphs in the existing light capture.
`qualityCheck` and debug and instrumentation APK assembly passed.

After the operator confirmed the original black chevron was a prototype
mistake, the prototype SVG and Android row were updated to use the same
theme-specific color as the other chevrons. The updated reference was
rendered in the browser; before/after captures in the same browser differ
in exactly 44 pixels, all within the two Account chevrons. The refreshed
reference has minor font rasterization differences from the older capture.
Both Android comparisons were visually inspected. Account chevron contrast
is 5.78:1 in light and 8.19:1 in dark; the reference-layout test checks
both the expected tint and a minimum 3:1 contrast. All three Settings tests
passed in each theme on API 37. All 46 JVM tests, `qualityCheck`, debug and
release Kotlin compilation, debug and instrumentation APK assembly, and
`node docs/ui-prototype/check-pairing.cjs` passed. The full instrumentation
suite was not repeated for the second review fix.
