# Shared credential renewal

Issue #15 adds one process-owned credential renewal workflow. Foreground and
background callers use the authorizer through
`withAccess(CredentialAccess.Management)` or `withAccess(CredentialAccess.Upload)`.
It checks the required access expiry before business work and renews at the
inclusive five-minute threshold. A data adapter calls `requireUnexpiredAccess`
for a management/upload response; only HTTP 401 with the published
`credential_expired` problem and a matching problem status triggers one
authorization recovery and one business retry. An expired rejection from an
older identity generation reuses the accepted newer bundle. Other errors do
not trigger that retry.

## Ownership and callers

- `domain/identity/CredentialRenewal` owns single-flight coordination, operation
  ordering, expiry policy and safe authorization outcomes. Caller cancellation
  does not cancel the shared process operation. Outcomes also settle when the
  only waiting screen goes away. If the final reconciliation read fails, the
  process operation publishes `StorageUnavailable` itself. A successful read
  still checks the current generation and authority before publishing a failure.
- `data/service/CredentialRenewalService` maps the generated refresh request and
  complete reply, including operation ID and attempt. The shared identity-call
  adapter preserves one-shot POST bodies, coroutine cancellation and success
  media-type validation for login and renewal. Transport/decoding failures and
  5xx replies are uncertain outcomes, with no response body or cause exposed to
  the domain. Permanent registration revocation, unusable F, and operation
  ordering failures remain distinct. Unknown problem fields are tolerated;
  unknown error codes do not prove logout.
- `data/storage/ProtectedIdentityStore` serializes snapshot updates and atomically
  replaces the encrypted file. Renewal never writes independent M or U values.
- `app/PinGlideApplication` owns the store, process scope and single refresher.
  Startup reconciles existing authority and interrupted renewal without QQ.
  Successful explicit first login also updates authorization readiness. Renewal
  calls have a ten-second timeout so a lost response leaves time for the
  permitted retransmission; QQ login retains its existing timeout.

The observable `AuthorizationState` contains no identity or credentials. It is
available to Home and later workflow wiring without adding a renewal screen.
Credentials stay in the domain/data boundary and encrypted snapshot; no refresh
facts enter Compose saved state, preferences, logs or exception causes.

## Durable ordering and restart

The existing version-1 protected document gains optional `renewal` and
`refreshState` fields. Earlier snapshots default to no pending operation and
active refresh authority. Unsupported versions, unknown authority states,
corruption and missing keys preserve the original file and report storage
unavailability. Unrelated top-level fields, including Pairing context, survive
every renewal transition.

Before sending attempt 0, save a unique operation ID, attempt, identity generation,
pre-send timestamp and the predecessor F's original expiry alongside the complete
old identity. An uncertain outcome permits at most attempt 1 with that same ID
and predecessor. Save attempt 1 before sending it. An interrupted attempt 0 is
recovered through this same path after restart; a durable attempt 1 is never sent
again.

The recovery window is conservatively bounded by thirty seconds from the durable
pre-send timestamp and by F's original expiry. Backward clock movement does not
extend it. Recheck both bounds after persisting attempt 1 and immediately before
calling the service. The service remains authoritative about actual predecessor
overlap and consumption.

Accept only a complete, correctly correlated reply with the same Account/Sender,
the current operation ID/attempt, distinct credentials, a changed F, and sufficient
freshness to leave the renewal threshold. Commit the entire bundle, clear its
marker and advance the generation together. Snapshot compare-and-set checks the
generation, operation facts and refresh state. Old-generation, lower-attempt and
already-superseded successes or failures cannot replace newer authority.

An expired/revoked/invalid F requires explicit login; permanent Sender revocation
has a separate durable state. An exhausted or out-of-window uncertain operation
reports recovery required without claiming the registration was revoked. A
transport/5xx failure reports temporary unavailability and preserves pending
facts. Definitive operation mismatch/supersession and other rejected protocol
responses persist recovery-required authority, preventing restart from treating
them as another uncertain send. No path clears these facts and reuses a possibly
spent F as a fresh ordinary renewal. Explicit identity restoration belongs to its
own workflow.

## Published service contract

GitHub's latest published service release was verified as `v1.0.2`, published
November 8, 2026 (UTC). The existing fixed pin already selected that release and remains
unchanged. `generateServiceClients` downloaded both published assets and regenerated
HTTP/WSS sources through the existing pipeline under ignored `build/`. The HTTP
adapter consumes `postAuthRefreshWithHttpInfo`, `RefreshRequest` and
`RefreshBundle`; generated sources remain unmodified.

Production sources contain no new test harness, debug seed or automated tests.
Local verification probes live only under ignored `build/ticket-15/`.

## Implementation verification

Debug and Release Kotlin compilation, `qualityCheck` (Spotless/ktlint, file size,
detekt and both Android/Compose lint variants), and Debug APK assembly pass.
The final full `:app:test` run with the ignored `build/ticket-15/checks.gradle`
init script passes all 31 host tests: six existing repository tests, nineteen
domain acceptance probes and six generated-HTTP integration probes. The HTTP
probes use an actual local server, including a 503 with `Retry-After: 0`, to
verify a generated refresh request is sent once.

Fourteen API 37 emulator checks pass for old-format restoration, required operation
facts before bundle acceptance, encrypted pending facts, restart recovery,
unrelated Pairing preservation, stale-generation/lower-attempt rejection,
failed writes, concurrent writers, durable login-required authority, unsupported
versions/states, corruption and missing keys. Probes use a separate disposable
cache directory. The final APK is rebuilt without the probe sources or
instrumentation entry, and installed instrumentation is checked to be absent.
The production APK also passes a cold-start smoke check on the emulator.

The final ripwire quality delta against the implementation base reports two
gating warnings: increased `ProtectedIdentityStore` class size and a newly
unresolved/dead-code classification of the existing QQ authorization method.
The latter still has its unchanged typed caller in application wiring. These
warnings are recorded without a suppression or unrelated extraction; ripwire
is not claimed as passing. Its static test gate does not account for the ignored
acceptance probes. Live QQ/service renewal, physical devices and real network
latency remain unverified.

## Review correction

The review identified a persistent-storage-failure path that could leave the
observable authorization state at `Renewing` after the last waiter was cancelled.
An ignored local regression probe reproduces that sequence by waiting for the
service request, cancelling the sole waiter, failing storage reads/writes, then
completing the service response. Before the fix it observes `Renewing`; with the
reconciliation read guarded it observes `StorageUnavailable` after the shared
operation completes, with no new caller or additional request. All twenty domain
probes pass, including stale-generation and authority checks.

The review-fix validation passes Debug/Release Kotlin compilation, `qualityCheck`,
Debug APK assembly and the full host suite (32 tests) using the existing ignored
init script. The ripwire delta against the reviewed commit has no gating
regressions and one minor class-size increase for the added handler. Its static
test gate still reports uncovered callers because it excludes the local probes.
Emulator checks and live QQ/service renewal were not repeated for this fix.
