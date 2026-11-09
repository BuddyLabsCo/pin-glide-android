# Terminal current-Sender revocation

Issue #20 closes local authority only after an authenticated current-Sender
operation or Refresh returns HTTP 409 with the exact
`device_registration_revoked` problem code and matching problem status.
Ordinary credential rejection, unknown problems, unavailable service and another
Sender's revocation do not establish this fact.

## Authority and persistence

`CredentialRenewal.withAccess` applies the common consequence to own operations
and Refresh. A short non-cancellable transaction records the closed Sender ID,
advances the identity generation and atomically replaces the protected document
with a credential-free terminal snapshot. Reconstructing that document removes
M/U/F, pending renewal, Pairing keys/selections and any other identity-owned
metadata. Language and setup preferences use separate storage.

The workflow retains proof for the originating Account/Sender before reading
storage and blocks its authorization and forwarding immediately, even while the
read is pending or after storage fails. Authorization and explicit registration
retry closure persistence once storage recovers. Self-revocation retains its
Welcome destination. Failed storage access cannot guarantee durability across
process death until closure is persisted. Old requests cannot publish success,
replace credentials or write Pairing state after closure. A delayed response
for a different current Sender cannot close the new registration.

Version-1 documents gain an optional `closedSenderIds` set. Existing active
snapshots default to an empty set. Legacy revoked snapshots that retained
credentials are atomically migrated to the terminal representation on read.
Closed IDs survive explicit registration and cannot be accepted again.

Confirmed self-revocation enters durable `RegistrationClosed` authority and
Welcome directly. The domain entry point checks the confirmed Sender ID and
also handles an own-authorization check discovering closure concurrently.
Retained proof includes the credential-free terminal snapshot produced by
closure. If self-confirmation then encounters a storage failure, authorization
retries promotion of that exact snapshot to `RegistrationClosed`, even after
identity removal. Matching its generation and closed Sender IDs prevents stale
self-confirmation from changing a later registration or terminal destination.
Remote discovery instead enters durable `RegistrationRevoked` authority, with
the approved revoked screen surviving offline restart.

## Foreground and entry

Every foreground return checks the generated `GET /v1/self/account` operation
with U through shared renewal. This check does not depend on Pairing count,
selection or local key health. It also runs after successful login/restoration.
The domain-owned `CurrentSenderAuthorization` workflow coalesces foreground
checks, selects U authority and handles expected failures. Its narrow check
interface keeps concrete HTTP and decoding details in the adapter, which returns
safe domain errors and preserves cancellation. A second credential-expiry
rejection after renewal is recoverable; it does not close the registration or
escape the foreground coroutine. Application only assembles and invokes this
workflow. Network failures do not infer closure.

The revoked screen uses the approved lock, copy and explicit sign-in action,
Chinese/English resources, light/dark colors, safe insets, scalable text and an
accessible status heading. Only explicit action opens a fresh entry context.
Prerequisites are rechecked before QQ authorization; the login request supplies
fresh QQ authorization without restoring the old Sender ID or Pairings.

## Contract and verification

The latest published service release was verified as v1.0.2, published November
8, 2026 (UTC). The existing pin already selects it. Both published contract
assets and HTTP/WSS clients were regenerated through `generateServiceClients`;
generated sources remain ignored and unmodified.

Disposable checks under ignored `build/ticket-20/` exercise exact problem
classification, closure/restart, stale renewal, foreign/self revocation, storage
failure, fresh registration and cancellation after definitive proof. The
cancellation checks failed before the persistence fix and passed afterward.
An API-37 emulator check uses real Android Keystore storage to verify atomic
cleanup, stale writes, new registration and legacy migration. UI inspection
covers offline cold restart, the explicit entry action, durable self-revocation
Welcome, both languages/themes and 200% text size.

Debug/Release compilation, repository quality checks, Debug APK assembly and
the full host test suite pass. No automated test harness or debug seed is added
to production sources. Real QQ/service/SMS delivery and physical-device
TalkBack interaction remain outside this verification.

## Review corrections

SPEC-1 probes reproduce operation and Refresh revocation followed by a failed
storage read. Intake stays blocked before a pending read completes; subsequent
authorization commits closure without another Refresh. Delayed proof for another
Account or Sender preserves the current registration. A confirmed self-revocation
with a failed read retains Welcome when persistence is retried.

SPEC-2 reproduces two generated HTTP `401 credential_expired` replies around a
successful renewal. The check completes without an uncaught failure and leaves
the registration Active. STD-1 checks exercise coalescing, later foreground
checks, recoverable domain errors, safe transport/decoding error mapping and
propagated cancellation. Storage-read and retry-exhaustion regressions fail
before their fixes and pass afterward. These disposable probes remain under
ignored `build/ticket-20/`, alongside the existing acceptance probes.

SPEC-3 combines persisted remote closure with confirmed self-revocation and a
failed storage read or write. The regression reproduces the revoked destination
before the fix; retry now persists Welcome and offline workflow restart restores
it without service or QQ requests. Additional probes cover intervening credential
renewal, a different terminal generation, and stale self-confirmation after a
new Sender registration.
