# QQ sign-in progress

The approved reference for issue #73 is the [QQ loading capture](../ui-prototype/shots/01-08-qq-loading.png).
The Android comparisons show Simplified Chinese at 366 × 820 dp on API 37.
Android supplies the status and navigation bars. The progress ring is centered
at y=351 dp in both the approved and Android viewports.

| Theme | Approved and Android comparison | Active motion recording |
| --- | --- | --- |
| Light | [Comparison](qq-progress-evidence/light-comparison.png) | [Recording](qq-progress-evidence/light-motion.mp4) |
| Dark | [Comparison](qq-progress-evidence/dark-comparison.png) | [Recording](qq-progress-evidence/dark-motion.mp4) |

Tapping **Sign in with QQ** entered the real onboarding workflow. For the
captures, a temporary test build supplied a synthetic QQ code and held the
generated `POST /v1/auth/qq` call open at the transport boundary. Both Cancel and
Android Back cancelled the call and returned to QQ sign-in. Live QQ authorization
and service responses were outside this verification.

In both themes, frame checks confirmed that a fixed quarter arc completed a
rotation around the stationary emblem and that the ring stayed pixel-identical
with the animator scale set to zero. The accessibility hierarchy exposed an
indeterminate progress indicator, the localized heading and a bottom Cancel
target at least 48 dp tall in Simplified Chinese and in English at 200% font scale.

Run `./gradlew :app:connectedDebugAndroidTest` with an emulator or device attached
to repeat the shared emblem check. It advances the Compose clock with motion
enabled, then at zero animator scale, and checks that a visible arc stays
stationary while progress remains indeterminate. The test requires no sign-in.
