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
- There is no global forwarding switch, message history, or manual retry. Device lists support pull-to-refresh and refresh when the app returns to the foreground.
- Forwarding failures are shown in the app; there are no app-generated system failure alerts, notification-permission requests, or notification settings.

## Capabilities and Constraints

- Each Sender can pair with at most five distinct target devices. At five, Home hides Add device and shows an orange "已满5台" count label; removing a device restores the entry. Capacity does not have a separate full-screen page.
- After onboarding, Add device opens the scanner directly once camera permission is available. Camera permission prompts, denial feedback, and the system-settings action belong to Home; standalone pairing has no connection-introduction page.
- Scanning an already paired target returns to Home, shows "这台设备已添加", and highlights that target's card without changing its pairing or receive toggle. Keep the card outline for 3 seconds, then fade it out and restore the original card appearance, including removal of the temporary scan cue. The review demo keeps this feedback visible for inspection; it does not simulate the timed fade.
- Standalone pairing has no dedicated login-expired page. Refresh ordinary access-token expiry automatically; only confirmed unrecoverable login loss exits pairing to Home's shared relogin flow. After login is restored, start with a fresh scan. QR-session expiry and transient network failures do not imply login loss.
- Standalone pairing confirmation Back, Cancel, and system Back all exit to Home. Progress cannot be cancelled; process termination follows ordinary app startup without a special pairing resume flow.
- Send test message acts inline from device details or pairing success. It uses the fixed simulated SMS and OTP `482913`, skips model extraction and model credentials, and goes directly through normal encryption and upload.
- The device-details test targets only that device, including when its automatic receive toggle is off. The pairing-success test targets only the newly added device and stays on pairing success; failure does not undo pairing.
- While a test is pending, remain on the entry page and disable its send control. Only confirmed `received` outcomes produce the Toast "测试消息已发送"; failed or mixed HTTP 207 results show the target and specific error, while uncertain results explicitly say delivery was not confirmed. There are no dedicated test preparation, processing, or result pages.
- A compact foreground service handles one SMS operation at a time. Its required Android notification object and possible system Task Manager visibility remain accepted platform behavior; the app does not request `POST_NOTIFICATIONS`. If the system prevents it from starting, that operation is not forwarded and there is no hidden retry.
- Per-Receiver lift-and-forward toggles default on.
- Receiver presence and aliases come from the service; the UI never invents local presence.
- Confirmed permanent revocation of the current Sender stops SMS intake and persists the revoked-registration screen across ordinary app restarts. Remote revocation is detected through authenticated service contact, not invented local presence or background polling.
- Settings planning includes owner-authorized revocation of other Senders in the same QQ Account. Selecting those Senders requires account-level Sender inventory support from the service.
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
