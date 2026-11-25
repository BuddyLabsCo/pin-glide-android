# Logout confirmation (#124)

The Account screen now opens a native modal with the approved rounded surface,
primary-container logout emblem, heading, consequence copy and right-aligned
Cancel and filled Logout controls. The two controls retain 48 dp touch targets
and can wrap when space is limited. The dialog scrolls when its content exceeds
the available height, and Cancel retains the existing focus-restoration behavior.
The existing localized resources and immediate logout callback are unchanged.

| Theme | Comparison |
| --- | --- |
| Light | [Approved and Android](settings-logout-evidence/light-comparison.png) |
| Dark | [Approved and Android](settings-logout-evidence/dark-comparison.png) |

The left sides are crops of the unchanged
[approved reference](../ui-prototype/shots/05-05-logout-confirm.png), and
the right sides are production Settings dialog captures on API 37, in
Simplified Chinese at 366 × 820 dp and 100% font scale. The Account nickname
is simulated. Android retains its own system bars, font rendering and modal
dimming. The underlying Account screen is outside this dialog change, so its
existing header and QQ badge remain unchanged. No prototype or motion changes
were made.

`LogoutConfirmationTest` opens the real Account destination, checks the
announced dialog title, approved copy, emblem color, paired control layout and
minimum touch-target heights, then verifies that cancellation does not request
logout and confirmation invokes the existing callback exactly once. The test
passed in light and dark themes on the isolated API 37 emulator.

Debug and release Kotlin compilation, instrumentation Kotlin compilation,
debug and instrumentation APK assembly, `qualityCheck` and all 46 JVM unit
tests passed.

The full instrumentation run started all 131 tests across the main run and
its continuation. Initially, 128 passed, one camera assumption was skipped,
one Pairing navigation assertion failed, and the existing
`HomeLoginExpiredScreenTest.expiredListStillScrollsButHidesRefreshControlsAndFeedback`
stalled beyond the 60-second timeout. The stalled run was terminated and the
remaining 115 tests were run. The failed
`PairingConsentNavigationTest.backReturnsToScannerAndCancelReturnsToFirstDeviceEntry`
passed on its isolated retry. The final coverage is 129 passed, one skipped,
and one stalled. No unrelated test or runtime code was changed.
The shared-emulator focused attempt was interrupted by APK replacement;
both successful focused captures and the full run used a dedicated AVD.

## Review follow-up

Logout and Pairing removal now use the same `ConfirmationDialog` presentation
in `ui/components/`: native window bounds and dimming, rounded surface,
scrolling, emblem geometry, heading and body typography, and wrapping action
row. Each caller still owns its focus, dismissal, feedback and action
semantics; pending removal remains non-dismissible.

The additional English tests exercise keyboard entry, Cancel and Logout focus,
system Back, and focus restoration after both Back and keyboard Cancel. At
200% system font scale they scroll to and activate both actions, preserving the
existing callback assertions. All three logout tests passed before extraction.
After extraction, all 17 focused tests passed in each theme on the dedicated
API 37 emulator, covering Logout confirmation, Pairing removal, Home capacity,
Settings and Language. In both themes the 732 × 590 pixel region surrounding
the logout modal was pixel-identical to the original Android evidence.

Debug/release and instrumentation Kotlin compilation, APK assembly,
`qualityCheck` and all 46 JVM tests passed again. The full instrumentation
suite was not repeated for this review-only change.
