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
were made. Existing test-message feedback and delivery behavior are retained.

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
