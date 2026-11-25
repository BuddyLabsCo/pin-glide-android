# Pairing complete (#117)

The shared Pairing success screen restores the centered check emblem and
heading, with the send icon and bottom-aligned Send test message and Enter Home
actions. It uses the existing Pairing action and completion navigation for
both onboarding and Home. Short windows can scroll the complete screen.

| Theme | Approved prototype and Android |
| --- | --- |
| Light | [Comparison](pairing-success-evidence/light-comparison.png) |
| Dark | [Comparison](pairing-success-evidence/dark-comparison.png) |

The comparisons use the pinned
[success reference](../ui-prototype/shots/03-13-success.png) on the left and
Android on the right, in Simplified Chinese at 366 × 820 dp on API 37.
Android supplies the native text rendering and system bars. No motion changes
were made. The delivery behavior is unchanged.

The existing `PairingIdentityProgressTest` reaches success through simulated
activation and reconciliation. Its new assertions check the accessible
heading, centered geometry, bottom actions, optional test invocation with
only the newly activated Pairing ID, simulated success feedback, and leaving
the success screen. The geometry regression failed on the previous screen
and passed with the restored layout in both themes. No live Receiver or
QQ sign-in was used.

Verification: debug and release Kotlin compilation, debug and instrumentation
APK assembly, `qualityCheck`, and all 46 JVM tests passed. The full emulator
report contains 122 tests: 120 passed (including all 30 Pairing tests), one
camera-feed assumption skipped, and one `HomeRenameSheetTest` failure (the
Save action was not found). An isolated retry of that Home test ended with
an instrumentation process crash. The Home rename flow was not modified.

## Review fixes

Success and failure now share a result layout that measures the minimum
content height before distributing available space. The heading stays
centered and actions remain at the bottom when content fits, regardless of
font scale; otherwise the complete screen scrolls.

The success result uses the approved transient feedback pill above the
stationary actions, with the existing native success Toast retained. The
notice honors Android's recommended accessibility timeout; failure copy
continues to identify the target and specific result.

| Theme | Before and during simulated success feedback |
| --- | --- |
| Light | [Comparison](pairing-success-evidence/light-feedback-comparison.png) |
| Dark | [Comparison](pairing-success-evidence/dark-feedback-comparison.png) |

These captures use the actual navigation host with simulated service
responses at the same locale and viewport as the original comparisons.
The camera privacy indicator and the lower Toast are system-owned UI.
The app's feedback pill does not shift either action.

`PairingIdentityProgressTest` now enters through real onboarding and Home
navigation, verifies the newly activated target, and checks that Enter Home
reaches the device list and completes the setup checkpoint. Its visual
assertions check the actual rendered success icon pixels, size, spacing and
alignment as well as the heading and action positions. `PairingSuccessScreenTest`
exercises English at 200% font scale in a 120 dp usable viewport and in a
650 dp viewport where content fits. It verifies touch operability, pending
disablement, stable action bounds during failure feedback and expiry.

Review verification: all 74 selected emulator tests passed, including all
33 Pairing tests and 41 Home readiness/target test-message tests. The
onboarding navigation regression also passed with evidence capture in
light and dark themes. Debug and release Kotlin compilation, debug and
instrumentation APK assembly, `qualityCheck`, and all 46 JVM tests passed.
The complete emulator suite was not repeated for this review fix.
