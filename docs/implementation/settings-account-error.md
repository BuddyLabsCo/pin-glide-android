# QQ Account profile load failure (#123)

The failed profile read retains the shared Account card and Logout row from
#121, with no nickname, retry action, or change to Account authorization. The
network-error capsule uses the existing feedback-pill artwork, horizontally
centered 164 dp above the viewport bottom as in the approved prototype.
It disappears after four seconds, extended by Android's recommended
accessibility timeout, without moving the cards or blocking their actions.

The original system Toast required by [#32](https://github.com/BuddyLabsCo/pin-glide-android/issues/32)
is retained for its accessibility announcement. Its text now uses the selected
app language, not the dialog context's system locale. The in-app capsule is
hidden from accessibility to avoid a duplicate error announcement. The lower
Toast's style, app icon, position and system animation remain Android-owned,
following the existing [#117 evidence](https://github.com/BuddyLabsCo/pin-glide-android/issues/117#issuecomment-7161020417)
accepted in [PR #189](https://github.com/BuddyLabsCo/pin-glide-android/pull/189).
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
system bars and the system Toast remain visible. No new motion is introduced.

## Verification

The existing failure regression first reproduced an English system Toast
on a Simplified Chinese Account page on an English-system emulator. Resolving
the Toast's localized message through Compose corrected that failure.
The extended regression also failed before the in-app capsule was added.
It verifies the actual system accessibility notification, displayed capsule,
fallback card geometry, typography and artwork, and transient dismissal.

- All four `AccountScreenTest` cases passed in both light and dark on the
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
The issue comment embeds the committed comparison images; they will render
once the authorized runner publishes the commit, not through this assignment.
