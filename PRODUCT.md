# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Jetpack Compose with Material 3 for the product; a throwaway static HTML/CSS/JS prototype is used for UI decision review.

## Users

The primary user is a QQ Account owner who installs the Android Sender on their own phone and pairs it with one or more Windows Receivers.

## Product Purpose

Pin Glide receives SMS on Android, extracts an OTP when possible, encrypts the full SMS and OTP independently for each selected Windows Receiver, and forwards the ciphertext through the Pin Glide service. Success means the owner can trust the bridge, verify that it is ready, safely manage Receivers, and understand delivery uncertainty without exposing message content.

## Positioning

Pin Glide is a deliberately narrow personal bridge between a phone's SMS inbox and the owner's Windows devices. The phone remains the source of plaintext processing; Receivers own the Windows interaction; the service only routes ciphertext.

## Operating Context

- Android 10 through Android 16, distributed as a signed APK.
- The app is never the default SMS app and does not modify the system inbox.
- `RECEIVE_SMS` is an onboarding hard gate. Turning off battery optimization is a recommendation, not a gate; onboarding must not probe or persistently report that system state.
- The user signs in with QQ Connect and pairs by scanning a Windows QR code.
- Every incoming SMS is forwarded, including messages with no valid OTP.
- There is no global forwarding switch, message history, notification surface, manual refresh, or manual retry.

## Capabilities and Constraints

- A compact foreground service handles one SMS operation at a time. If the system prevents it from starting, that operation is not forwarded and there is no hidden retry.
- Per-Receiver lift-and-forward toggles default on.
- Receiver presence and aliases come from the service; the UI never invents local presence.
- The app distinguishes per-target arrival outcomes and uncertainty, and never treats arrival as proof of Windows display or OTP copying.
- SMS bodies, OTPs, ciphertext, credentials, Pairing keys, and provider keys never appear in UI diagnostics or logs.
- The application supports Chinese and English; the default follows the Android system language.

## Brand Commitments

- Chinese product name: 鹊信.
- English product name: Pin Glide.
- Supplied app icon: a blue-green gradient messenger bird carrying an envelope.
- Material 3 structure, interaction, accessibility, and adaptive layout are required.

## Evidence on Hand

- Functional authority: GitHub issue `BuddyLabsCo/pin-glide-android#2`, including the empty-OTP amendment.
- UI decision ticket: GitHub issue `BuddyLabsCo/pin-glide-android#3`.
- App icon: `docs/ui-prototype/app-icon.png`.
- No production UI exists yet; visual decisions are under review.

## Product Principles

1. Readiness before decoration: the owner should understand whether forwarding can work now.
2. Calm trust: sensitive work should feel controlled, local, and explicit.
3. Arrival is not completion: never overstate what Android can prove.
4. Low-frequency maintenance: setup once, manage devices rarely, avoid dashboard ceremony.

## Accessibility & Inclusion

- Material 3 touch targets of at least 48 dp.
- System font scaling, light and dark themes, visible focus, and sufficient contrast.
- Chinese and English localization with English copy kept concise.
