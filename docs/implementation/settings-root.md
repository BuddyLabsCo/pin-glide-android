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
provides native font rendering, system bars and themed foreground contrast
(including the Account chevron in dark mode). The actual installed version
is `0.1.0`; the prototype illustrates `1.0`. No motion changed.

`SettingsScreenTest` uses the actual Settings root with synthetic navigation
callbacks. It verifies the installed version, read-only information rows,
navigation actions, and existing battery-settings failure feedback. A compact
English viewport at 200% font scale exercises scrolling and touch activation
of Privacy and Back. The underlying destinations and system settings intent
are unchanged.

Verification: both Settings tests passed in light and dark themes. The full
instrumentation report contains 124 tests: 123 passed, one camera-feed test
was skipped by its assumption, and none failed. All 46 JVM tests,
`qualityCheck`, debug and release Kotlin compilation, and debug and
instrumentation APK assembly passed. The initial baseline instrumentation
attempt was interrupted by another runner updating the shared emulator;
it did not produce a reliable failing assertion. Live QQ sign-in and the
external battery settings activity were not exercised by the focused tests.
