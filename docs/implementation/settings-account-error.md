# QQ Account profile load failure (#123)

The failed profile read retains the shared Account card and Logout row from
#121, with no nickname, retry action, or change to Account authorization. The
network-error capsule uses the existing feedback-pill artwork, horizontally
centered 164 dp above the viewport bottom as in the approved prototype.
It disappears after four seconds, extended by Android's recommended
accessibility timeout. The Account page measures its header, cards and
notice within the inset-aware viewport. Where the approved placement fits,
the cards stay stationary. In shorter windows the notice is placed in the
scrollable content without overlapping the cards or Back action; in the
shortest windows it is first so that the notice is visible.
Each newly completed failed read reveals its notice at the top of the
scrollable page, even if the user had already scrolled toward Logout.
Restoring the same notice retains the user's scroll position.

The network-error Toast required by [#32](https://github.com/BuddyLabsCo/pin-glide-android/issues/32)
is now the single app-owned capsule specified by the pinned prototype's
`role="status"` treatment. It uses the selected app language and a polite
accessibility live region. There is no duplicate platform Toast. The
saved monotonic deadline is associated with the completed failure's read
generation. It preserves the remaining timeout or dismissal on
configuration change, but a retained read that fails during restoration
receives a fresh notice and timeout.
The approved prototype is unchanged.

| Theme | Approved prototype (left), Android (right) |
| --- | --- |
| Light | [Comparison](settings-account-error-evidence/light-comparison.png) |
| Dark | [Comparison](settings-account-error-evidence/dark-comparison.png) |

The reference is [05-account-error.png](../ui-prototype/shots/05-account-error.png),
verified against pinned commit `7f69445969ebba7735af6d184738bd7e9381d97a`.
Android opens the production Settings dialog with a simulated profile read failure.
Both captures use Simplified Chinese, 100% font scale and a comparable
366 × 820 dp viewport on API 37. The originals
[light](settings-account-error-evidence/android-light.png) and
[dark](settings-account-error-evidence/android-dark.png) are retained. Native
system bars remain visible. No new motion is introduced.

The compact captures use English at 200% system font scale. The viewport
is scrollable, including the header and Logout row, while the notice
remains legible without overlapping either action.

| Actual viewport | Light | Dark |
| --- | --- | --- |
| 366 × 260 dp | [Screenshot](settings-account-error-evidence/compact-light.png) | [Screenshot](settings-account-error-evidence/compact-dark.png) |
| 366 × 164 dp | [Screenshot](settings-account-error-evidence/short-light.png) | [Screenshot](settings-account-error-evidence/short-dark.png) |

## Verification

The original implementation introduced the capsule and localized the
feedback, but review found the duplicate platform Toast, rigid placement,
and unsaved timeout. The review fix removes the duplicate, reuses the
Settings header and Account cards, and measures the notice relative to the
actual safe viewport. `AccountFailureScreenTest` checks a single readable
polite live-region capsule, absence of a platform Toast event, centering,
164 dp offset, typography, color, fallback geometry, visibility before
expiry and dismissal afterward. It also checks English copy and access to
Logout and Back in a 366 × 260 dp viewport at 200% system font scale, the
visible notice in a 366 × 164 dp window, and saved-state restoration both
during and after feedback, including a fresh read on reentry.

### Review fix verification

- All seven Account tests passed in light and dark on the isolated API 37
  emulator, including the failure layout, English compact and very short
  windows, saved timeout restoration, and fresh-read feedback.
- All 22 Settings instrumentation cases passed together in light mode.
  The subsequently shared text-layout and dp assertion helpers were
  rechecked with the focused Account tests in both themes.
- All 46 JVM tests passed. Debug, release and instrumentation Kotlin
  compilation, both debug APK assemblies, `qualityCheck` and
  `git diff --check` passed.
- Ripwire found no incompatible Account destination caller. Its quality
  delta still flags similarity between the viewport fixture and the
  existing Home test fixture, plus recent-line churn in the Account tests.
  The viewport fixture retains its dimension and font restoration checks;
  unrelated Home test infrastructure is unchanged.
- The full device suite was not rerun for this review fix; its original
  implementation verification and existing stall are recorded below.

### Review round 2

The two new regressions both failed against the round-one implementation:
the retained loading read completed unsuccessfully during disposal after
state saving, and the notice was not displayed after restoration; the
other case scrolled to Logout in a 366 × 164 dp window at 200% font scale
before the read failed, leaving the new notice outside the viewport.
Both now pass. The restoration fixture verifies that the retained read
has actually failed before the restored content is composed, and the
scroll regression compares the clipped and unclipped notice heights
before exercising the Logout action's reachability.

The success and failure tests now use the same `AccountLayoutAssertions`
for header positioning, card geometry and alignment, typography, color,
and QQ/Logout/chevron artwork. The separate failure assertions continue
to verify the capsule's position, styling, live region and lifetime.
The approved visual composition and screenshot references are unchanged.

- Both new targeted regressions failed before the fix and passed afterward.
- All nine Account cases passed in light and dark; all 24 Settings cases
  passed together in light mode on the isolated API 37 emulator.
- All 46 JVM tests, debug/release and instrumentation Kotlin compilation,
  debug APK assemblies, `qualityCheck` and `git diff --check` passed.
- The only AccountPage caller was updated. Ripwire's supplemental quality
  delta flags the two similar restoration scenarios, pre-existing artwork
  assertion similarity now attributed to the shared test class, recent
  AccountScreen churn, and the longer AccountPage function.
- The full device suite was not rerun for this review round.

### Original implementation verification

- Before the review fixes, all four `AccountScreenTest` cases passed in both light and dark on the
  isolated API 37 emulator. The initial real-time dismissal assertion timed
  out under the Compose test clock; advancing that clock by four seconds
  verified dismissal in the final runs.
- All 15 other Settings instrumentation cases passed on the same production
  code, including Language, Logout and Sender inventory navigation. All 19
  Settings cases also passed together in the final remaining-suite run.
- The full `:app:testDebugUnitTest` suite passed all 46 tests, with zero
  failures, errors or skips. Debug and release Kotlin compilation,
  instrumentation compilation, both debug APK assemblies and `qualityCheck`
  passed. `git diff --check` passed.
- The final full device run scheduled 144 cases. After 14 passes and one
  camera assumption skip, it stalled for three minutes in the existing
  `HomeLoginExpiredScreenTest.expiredListStillScrollsButHidesRefreshControlsAndFeedback`
  case, also reported in #126 and #127. The other 128 cases ran separately:
  127 passed, and `PairingConsentScreenTest.compactHeightAtDoubleFontScaleKeepsAliasAndActionsReachable`
  failed its Scan a new QR code visibility assertion, then passed in a focused
  retry. Across these runs 142 unique cases passed, one was skipped and one
  stalled. The uninterrupted full device suite is not green.
- Ripwire identified the updated Account destination caller and no
  incompatible callers. Its quality delta flagged recent-line churn in
  Settings navigation as a gate and minor churn/verbosity findings; no
  complexity or logic regression was reported.

The profile response is simulated. No live QQ authorization, service
mutations, physical-device acceptance or prototype changes were required.
