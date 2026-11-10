# Shared Pairing entry

Issue #23 connects the onboarding Scan action and Home Add action to one scanner
and consent form. Camera permission belongs to the entry host. Ordinary denial
keeps that host visible with a Toast; a blocked request offers application
settings through a Snackbar. CameraX and the bundled ML Kit QR recognizer exist
only while the scanner is resumed, and dispose their analyzer and camera when
validation, consent, backgrounding or navigation makes scanning inactive. No
frames, QR text, PSK or recipient key enter saved state or storage.

## Validation and authority

The local decoder checks the exact version-1 QR field set, numeric protocol and
crypto versions, identifiers, canonical base64, a 32-byte PSK and a valid
65-byte uncompressed SEC1 P-256 point. Extra fields, including credentials,
service URLs and private keys, are rejected. The service origin always comes
from the application's fixed configuration.

As agreed during implementation, the published authorize operation supplies
authoritative session metadata and performs claim, ownership, state and capacity
checks. Android does not require an additional read-only session endpoint.
Authorize reserves a pending association; it does not establish a usable key or
activate a Pairing. Its response must match the scanned session and Receiver,
current Sender, supported new_receiver/add_sender mode, unconfirmed state and
original deadline before consent appears. Repeated authorization cannot extend
that deadline. Recovery, existing-target replacement and sessions that have
already entered key confirmation are rejected by this entry flow.

The existing own-Pairing synchronization runs before a valid scan is authorized.
An existing Receiver returns to Home and highlights its stable Pairing ID for
three seconds without authorizing a replacement or editing its alias, key or
receive switch. Local capacity prevents entry; authoritative reservation races
also return a capacity result. The alias editor reuses the shared trimmed,
1-40-Unicode-code-point, case-insensitive uniqueness validator.

## Lifetime and exits

The operation lives in an Activity ViewModel. Configuration changes retain its
valid QR and alias; a process restart follows persisted setup and Home facts.
Rescanning the same valid session retains edits and its original absolute
deadline. A different session starts with the localized default alias. The
Pairing WSS state stream uses management authority and pin-glide.v1 to observe
closure; its events must match the bound session and metadata. The countdown
uses the absolute service deadline across backgrounding.

Standalone scanner and consent exits return Home. Onboarding scanner exits
return to first-device entry; consent's top Back returns to the scanner, while
Cancel and system Back return to first-device entry. Exiting, expiry or rejection
discards QR secrets and best-effort cancels the owned reservation. An in-flight
authorize response is reconciled after cancellation so a late reservation is
released instead of reopening consent. A released session cannot be reused in
the same process while its cancellation may still be in flight.

The UI ViewModel chooses the host-specific Back destination; the domain exposes
scanner resumption and reservation cleanup without choosing navigation policy.
Camera permission and settings results carry a request ID bound to the entry
host and Account/Sender generation. Leaving or changing the host invalidates
that request and disposes its settings Snackbar. A late result cannot open a
scanner for an obsolete host or consume a newer request. Configuration changes
retain pending requests while the same host remains active.

The consent action validates the current alias and rechecks the live session. It
then starts the authenticated activation described in [Pairing activation](pairing-activation.md).
In v1.0.2, repeating authorize returns the existing reservation and does not update
its alias. Activation reconciles the edited alias through the existing own-alias
operation before presenting success.

## Published contract and verification

On November 10, 2026, the latest published service release is v1.0.2, published
November 8, 2026. The existing explicit release pin already selects it. The normal
generation pipeline downloaded its openapi.yaml and asyncapi.yaml assets and
regenerated the HTTP client and WSS models under build/. Generated source is not
checked in.

The implementation uses the existing focused OwnPairingsTest suite, Kotlin
compilation, repository quality checks and the full existing unit suite. A
build-directory-only assertion check covers malformed and forbidden QR content,
curve and PSK validation, alias boundaries, both supported modes, host exits,
absolute expiry, duplicate preservation, capacity, late cancellation and WSS
closure. No persistent automated tests, E2E harness or debug seeds are added.
Review-fix assertions also exercise the UI ViewModel's Back decisions and reject
camera results after host exit, request replacement and identity changes.
No Android device was connected for camera, permission, visual or TalkBack
acceptance; those remain manual checks with real target-generated QR codes.
