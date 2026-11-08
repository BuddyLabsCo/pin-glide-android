# Explicit QQ login and protected Sender identity

Issue #14 implements the QQ step after the SMS prerequisite and battery guidance.
Ordinary launch only restores local identity. A deliberate Sign in or Retry starts
QQ Connect server-side authorization and sends its fresh one-use code with the
automatic Sender name through the generated `postAuthQQ` client. Connection retries
and redirects are disabled, and the generated request body is marked one-shot to
prevent HTTP follow-ups from replaying a consumed code. An uncertain result requires another
explicit authorization. The service owns QQ AppKey, code exchange and provider tokens.

## Ownership and acceptance

- `domain/identity/ExplicitLogin` owns safe restoring, entry, loading, error,
  storage-error and first-device states. Its collaborators are the authorization,
  service and identity-store seams. UI observes these states without receiving an
  Account ID, Sender ID, credential, SDK object or storage implementation.
- `platform/qq` retains each callback in the process-owned request, including across
  Activity recreation. Only an opaque request ID goes into the internal Activity
  Intent. Cancellation, duplicate callbacks and callbacks for old requests cannot
  deliver another identity. Process death discards the attempt.
- `platform/DeviceInfo` uses the available system name, then brand/model, then
  `Android device`. It trims and limits the name to 64 Unicode code points without
  splitting a supplementary character. There is no name editor or identity lookup
  by name.
- `data/service/QqLoginService` maps the published `userSession`, `uploader` and
  `refresh` fields to separate M, U and F types, preserving each expiry. Complete,
  distinct, unexpired credentials and nonblank Account/Sender IDs are required
  before first acceptance. The fixed service release is `v1.0.2`; HTTP and WSS code
  remains generated under ignored `build/`.
  Expected HTTP, transport and response-decoding failures become a safe domain
  `LoginServiceException` without the original body, message or cause. Coroutine
  cancellation continues to cancel the generated call. The adapter owns its
  one-shot request integration; generated sources are unchanged.
- `app/PinGlideApplication` owns the single store, provider callback registry and
  concrete service wiring. `LoginViewModel` coalesces user actions and cancels work
  when cleared. No login operation or credential is saved in Compose state.

## Protected snapshot

`data/storage/ProtectedIdentityStore` serializes reads and writes with one mutex.
Its version-1 JSON envelope contains a version-1 encrypted document with a
generation and the complete identity. AES-256-GCM uses an Android Keystore key, a
fresh nonce and authenticated version/key-alias context. Key creation finishes
before a file can reference that alias. Plaintext byte buffers are cleared after
use; credentials are never written to preferences.

Acceptance validates the expected generation and absence of an existing identity.
It encrypts a complete replacement, writes and syncs a sibling temporary file,
then atomically renames it under the same cancellation gate that records successful
acceptance. Cancellation before the rename prevents acceptance; cancellation after
it preserves the accepted result. An abandoned temporary file is never restored.

Unknown document fields survive a replacement. Corrupt authentication, missing
keys, unsupported versions and invalid identity stop restoration or acceptance
without resetting or replacing the original file. Existing identity and nonzero
identity-free generations cannot be enrolled over by this first-login writer.
Later renewal, restoration, logout and Pairing tickets extend this authority; they
must keep generation checks and preserve unrelated context. Old keys are retained
instead of being deleted during a failed or partial operation. The snapshot lives
in `noBackupFilesDir`; existing manifest and cloud/device-transfer exclusions also
exclude all private application domains.

The pinned QQ SDK is prepared before Debug and Release compilation: its twelve
static diagnostic overloads become no-ops and `QQToken.saveSession` acknowledges
without caching the callback. This prevents SDK callback-URI logs and automatic
code persistence. Method counts/signatures fail closed if the SDK changes. See
`docs/release.md` for the verified SDK archive and public QQ App ID/service URL
build inputs.

## UI boundary

Entry, loading and error screens use real workflow state, localized Chinese/English
copy, the existing system-following themes and safe insets. Loading offers Cancel;
error offers a fresh Retry and Back. Storage failure offers a read-only recheck.
Headings, progress, polite status announcements, minimum 48dp actions and scrolling
support accessibility and large fonts without fixture timers or extra motion.

Accepted identity restores to the first-device entry after restart, even when
setup preferences are absent; actual SMS permission is still checked. Loading
and error screens are never restored. This slice supplies the first-device header
and explanation. Set up later/Back to Home belongs to #16, and scanner/first Pairing
actions belong to #23 and the Pairing tickets.

## Initial implementation verification

Focused Kotlin compilation and the existing `GeneratedWireTest` ran during
implementation. Eight local domain acceptance probes passed for explicit-only
launch, duplicate actions, cancellation on both sides of commit, stale results,
fresh-code retry, expired credentials and unavailable storage. Local probes and
captures live only under ignored `build/ticket-14/`; no automated-test, harness or
debug-seeding source is included in the production change.

API 37 emulator checks cover scoped identity round-trip, encrypted file content,
existing-identity preservation, unsupported versions, corruption, missing keys,
failed writes, cancelled acceptance and concurrent writers. The prepared SDK's
session method was invoked with a synthetic callback and did not change its
session preferences. Chinese light and English dark screens were inspected,
including scrolling to the login action at 200% fonts in landscape, safe error
copy and restart at entry. A synthetic protected identity restored to first-device
entry across restart without QQ or service enrollment.
The final thirteen platform checks also cover retained callback identity, duplicate
completion, cancelled late callbacks and a name truncated at a whitespace boundary.

Final verification passed:

```powershell
.\gradlew.bat spotlessApply qualityCheck :app:compileDebugKotlin :app:compileReleaseKotlin :app:assembleDebug :app:test --console=plain
```

The full existing host suite contains six tests, all passing. Android/Compose Lint
for Debug and Release, Spotless/ktlint, file-size checks and detekt all pass.
`ripwire --quality-delta` reports four gating structural findings: the added safe
workflow callbacks increase `SetupNavigation` parameters, recent edits and size,
and concrete wiring increases `MainActivity` size. These are accepted integration
trade-offs, without a suppression ledger or unrelated extraction. Its test gate
reports no tracked test reach for the two QQ composables; the ticket excludes
committed automated tests, and the local UI/domain/platform checks above supply
the exercised evidence. Neither ripwire report is claimed as passing.

Live QQ SSO and service enrollment require registered public build inputs and
matching package/signing registration, unavailable in this checkout. Physical
devices, older Android versions, provider-process interruption and TalkBack speech
were not exercised. These checks do not establish live provider/service operation.

## Review corrections and verification

The review fixes address S1, R1 and R2. The service adapter now translates expected
failures into the safe domain error and marks its generated POST body one-shot.
The approved prototype penguin paths are reused in a 92dp QQ emblem on entry and
inside the loading indicator. Loading keeps indeterminate progress semantics;
disabling system animation renders a stationary ring around the emblem.

Local ignored probes first reproduced two sends for one login after a `503` with
`Retry-After: 0`, and a transport exception crossing the domain boundary. Both
failed before their fixes and passed afterward. Four service probes cover that
single-send behavior, safe transport/HTTP/decoding errors and cancellation of the
underlying generated call. The eight existing local domain probes also passed,
including fresh authorization after an uncertain result. No test harness or debug
seed is added to the production change.

Debug and Release Kotlin compilation, `qualityCheck`, Debug APK assembly and the
final full `:app:test` run passed. The host run used the ignored
`build/ticket-14/checks.gradle` init script and passed all 18 tests: six existing
repository tests, eight local domain probes and four local service probes. On the
API 37 emulator, QQ entry and loading passed accessibility checks in light and dark
themes. Screen captures were inspected in both themes, and paired captures
confirmed the loading graphic stays stationary with animation disabled.

The review delta from `9139677` has one ripwire gating size warning:
`LoginActionScreen` grows from 59 to 64 lines to supply the branded emblem. This
is an accepted presentation integration trade-off, without a suppression ledger
or unrelated extraction. Minor shared-screen parameter/churn warnings and four
unresolved static call edges for OkHttp request-body overrides are also reported;
the real HTTP probe exercises those overrides. The ripwire delta is not claimed
as passing. Live QQ/service enrollment, physical devices and TalkBack speech
remain unverified.
