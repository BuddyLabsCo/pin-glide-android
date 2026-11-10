# Authenticated Pairing activation

Issue #24 extends the shared consent workflow for onboarding and Home. The
service contract remains explicitly pinned to v1.0.2: the latest published
release checked on November 10, 2026, published November 8, 2026. The existing
pipeline regenerated its HTTP client and WSS models from that release's assets;
generated code and downloaded contracts remain under ignored build/.

## Protocol and ownership

PairingActivation owns the domain workflow. PairingChannel maps generated
PairingRelayIn/PairingRelayOut and session events over management-authenticated
WSS with pin-glide.v1. It rejects unsupported versions, binary frames, wrong
session IDs, relays from the Sender side, malformed payloads and unsafe closure.
Android does not implement delivery WSS, issue/redeem Receiver credentials or
send Receiver Receipts.

The platform uses the installed Bouncy Castle dependency with HPKE PSK mode 1,
P-256/HKDF-SHA256/AES-256-GCM (0x0010/0x0001/0x0002), ASCII
pin-glide/pairing/v1, UTF-8 sessionId as psk_id and empty AAD. It exports 32 bytes
with pin-glide/message-key/v1. The interoperable client framing follows the
adopted service requirements at commit 85b6ccc001559b2918f7b4218251f7f790e807ca:
padded Base64 of UTF-8 JSON inside pairing_relay; hpke_init carries an
encapsulation and sealed pairing_init/challengeA; AES-GCM key_confirmation uses
a fresh 12-byte nonce and a combined ciphertext/16-byte tag. Receiver role,
challengeA and 32-byte challengeW must authenticate before the Sender returns
its own role and challengeW. Each attempt creates fresh ephemeral material.

QR secrets, challenges, ciphertext and handshake state remain in operation
memory. The UI receives stage, alias, problem and activated Pairing ID only.
Malformed authenticated plaintext, invalid UTF-8 or authentication failure maps
to a safe unavailable result.

## Durable activation and recovery

The existing encrypted atomic identity snapshot stores a verified provisional
key under the service-allocated Pairing ID before the Sender confirmation/report.
Provisional entries cannot appear among active forwarding targets. The additive
metadata preserves old keys, switches and unknown snapshot fields; updates use
the existing serialized writer and authority/revision guard.

Both confirmation flags and the bound service metadata must agree. new_receiver
activates at credential_ready (also credential_issued/completed); add_sender
activates at completed. Own-Pairing membership then reconciles the alias and
promotes the retained key, default ON. A service result without a usable local
key cannot establish success.

A lost completion response or disconnected WSS uses a fresh authenticated
session subscription and authoritative membership; only the idempotent
confirmation report may be repeated. The handshake is never replayed. Startup
reconciles retained provisional entries against active own membership and restores
the chosen alias before promotion. Unresolved entries remain provisional;
process restart starts ordinary navigation without a handshake-resume screen.
Existing Pairings/preferences survive failed attempts and missing keys are never
manufactured.

## Presentation and explicit test

The four approved progress labels follow opening the channel, creating the HPKE
identity, sending the key exchange and authenticating/persisting confirmation.
Progress consumes Back and provides no Cancel. Failure offers fresh scanning and
host return. Unrecoverable onboarding authority shows QQ recovery in the first
Pairing host; standalone recovery uses Home relogin and requires fresh scanning.
Ordinary access expiry uses the shared credential renewal workflow first.

Authoritative local activation completes onboarding before the optional inline
test. The explicit test workflow belongs to domain/forwarding and its button is
a shared UI component. It uses the fixed approved SMS/OTP 482913, fresh AES-GCM
nonce and one frozen messageId/items request to the generated Delivery API with
Upload authority. It bypasses Bailian, SMS permission and the automatic receive
switch while retaining Pairing/key/authority checks. A protected key-use counter
reserves each new encryption before use and includes the two confirmations.

Uploads have a 20-second timeout. Only a transport failure before an HTTP
response or whole-request 503 delivery_status_unavailable permits one identical
recovery request. Indexed 207 received with matching correlation is arrival
success; offline/rejected are terminal and all other outcomes remain unconfirmed.
Duplicate taps are suppressed while navigation remains available. Leaving the
origin or changing identity/target suppresses stale results; test failure cannot
undo activation.

## Review corrections

Startup and foreground authorization checks now inspect retained provisional
sessions over management-authenticated WSS. Account/Sender ownership, session,
Pairing and Receiver IDs must match the usable protected key. Only an
authoritative receiverConfirmed fact permits resubmitting an interrupted Sender
completion report. Recovery never starts another handshake or creates a key.
Membership reconciliation still controls local promotion and alias restoration.

An unavailable session or membership response leaves completion explicitly
unconfirmed. The result screen offers Check again; leaving it retains the
provisional facts without cancelling a possibly activated session. A confirmed
closed session with successfully refreshed, absent membership remains a terminal
failure. Returning to the app can resume retained-session recovery.
The shared Pairing screen keeps its resume observer installed on progress and
result pages too. Returning with unchanged credentials rechecks an unconfirmed
result through the active entry, so recovered membership updates the displayed
success state without another scan, handshake or manual Check again.

Test-message feedback carries the selected alias and maps published rejection
codes to localized reasons. Unknown codes and ambiguous outcomes remain
unconfirmed; raw service details never enter UI state. Confirmed received
results create a consumable success notice, retained in the ViewModel across
configuration changes and consumed before showing the localized Toast.
Duplicate sends wait for that notice to be consumed, and leaving the origin or
changing ownership clears pending feedback. Onboarding failure offers Pair later
through the existing setup-completion callback; the Home host retains Return.

Review-fix validation includes disposable build-directory checks for interrupted
Sender reports, missing Receiver confirmation or key, every retained correlation
field, authority changes, unavailable-versus-terminal evidence, reconciliation
without handshake replay, cancellation suppression, safe delivery reasons,
one-time success-event consumption and obsolete-result suppression. These JVM
checks do not establish native Toast rendering or device navigation acceptance.

## Verification and limits

Local validation uses generated-client compilation, the focused OwnPairingsTest
suite, qualityCheck, Debug assembly, Release Kotlin compilation and the complete
existing JUnit suite. Build-directory-only assertion checks exercise an independent
JCA RFC 9180 recipient, malformed/tampered confirmations, fresh secrets, actual
WSS upgrade/relay framing, activation ordering, storage refusal, lost responses,
restart membership/alias reconciliation, snapshot metadata compatibility, explicit
OFF-target tests, stale result suppression and frozen HTTP recovery boundaries.
No persistent automated tests, E2E harness or debug seeds are added by this
production-only slice. The final existing suite passed all 14 tests; the temporary
acceptance checks passed 12 tests across the protocol, workflow, wire and snapshot
seams.

Ripwire delta/test-gate reports were inspected. Session observation now reuses
the same WSS channel and HTTP session mapping is shared. Remaining heuristic
findings cover small domain-specific guards (milliseconds versus displayed
seconds, usable-key checks), enum/resource mappings, existing composition-root
growth and recent edits. These are retained without a new generic framework or
a blanket acknowledgement; a clean Ripwire heuristic gate is not claimed.

The independent crypto recipient does not use Bouncy Castle HPKE. Snapshot
metadata assertions do not exercise Android Keystore or filesystem crash
atomicity. No Android device/emulator was connected: real QQ, actual target QR,
Keystore execution, visual/font/IME/TalkBack acceptance and live target arrival
remain unverified. Release signing and emulator/service E2E are not claimed.
