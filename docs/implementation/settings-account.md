# QQ Account profile (#121)

The Account destination uses the approved Settings header, the shared blue
QQ text badge, a 76 dp profile card at the reference font scale, and a
60 dp Logout row with the existing logout glyph and chevron. It renders the
nickname from the existing account state, without changing the profile service
or logout operation. Loading and failed profile states retain their existing
copy and error feedback.

| Theme | Comparison |
| --- | --- |
| Light | [Approved and Android](settings-account-evidence/light-comparison.png) |
| Dark | [Approved and Android](settings-account-evidence/dark-comparison.png) |

The left side is cropped from the
[approved reference](../ui-prototype/shots/05-03-account.png).
The right side is Android running the production page layout and account
content with a simulated nickname in the instrumentation activity. Both use
Simplified Chinese, 100% font scale and a comparable 366 × 820 dp viewport.
Native type rendering and system bars are preserved. No prototype changes or
new motion are introduced.

`AccountScreenTest` verifies the loading, failure and nickname states, the
read-only profile, and the existing Logout confirmation, cancellation and
confirmation callback. The test passed in light and dark on API 37. The first
dark attempt was interrupted by replacement of the APK on the shared emulator;
the reinstalled build passed the retry. Debug and release Kotlin compilation,
instrumentation compilation, debug and instrumentation APK assembly, and
`qualityCheck` passed. The final Settings instrumentation run passed all
six tests, including the existing root and Language picker regressions.

The comparison uses simulated presentation data, not a live QQ account. No
live profile, credential handling or SMS-forwarding scenarios were exercised.
The full project-wide test suite was not run; verification is scoped to
Settings UI.
