# OTP extraction

Issue #28 enriches automatic SMS forwarding before the existing per-target
encryption/upload workflow. Explicit test messages continue to bypass extraction.

## Candidate and result policy

`domain/forwarding/OtpExtraction` calls inference only for independent 4-8
ASCII-alphanumeric runs containing a digit. ASCII letters/digits immediately
outside that run disqualify it; punctuation, underscores and Chinese characters
are boundaries. Pure letters, Unicode digits and keywords alone do not trigger
credential issuance or inference. Keywords are context in the Android-owned
prompt, not a second trigger or a requirement that discards numeric candidates.

The model result is trimmed and accepted only if it is 4-8 ASCII letters/digits
present contiguously in the original SMS. Any valid original result may be
accepted when several candidates occur. Missing, null, blank, malformed,
non-string, extra-field, truncated, non-original or failed output becomes the
empty-string OTP sentinel. Cancellation remains cancellation. Provider failure
does not alter the SMS or suppress ordinary delivery while targets and Upload
authority remain usable.

## Published service integration

The latest published service release was checked on November 12, 2026:
`BuddyLabsCo/pin-glide-service` v1.0.2, published November 8, 2026. The existing
explicit release pin already selects it; both released `openapi.yaml` and
`asyncapi.yaml` were downloaded and the existing HTTP/WSS pipeline regenerated.
No generated source is checked in or manually changed.

`data/service/ProviderCredentialService` uses generated `ModelApi` and the current
U to POST `/v1/model/credentials`. That published operation takes no parameters
or request DTO: the service requests the adopted 600-second provider TTL. Android
does not invent a TTL field or use the requested lifetime as the returned expiry.
The returned `ModelCredential` binds its key and actual `expiresAt` to one
`ModelConfiguration` snapshot. Expired U uses the shared M/U/F renewal workflow;
invalid U and confirmed registration revocation follow existing authority handling.

## Direct provider call

`data/bailian/` validates Bailian/cn-beijing, HTTPS port 443, no URL credentials,
query or fragment, and the Beijing DashScope or workspace-specific MAAS host.
It appends `/chat/completions` to the configured `/compatible-mode/v1` base.
It sends the returned model and inference configuration unchanged, requiring
`enable_thinking=false` and strict `json_schema` with only required nullable-string
`otp` and `additionalProperties=false`. Invalid configuration takes the empty-OTP
path. There is no JSON Object, provider or service-inference fallback.

The system message contains Android's extraction policy and context keywords;
the user message contains the unmodified original SMS as text. Only the provider
key authorizes inference. U is used only with the routing service. Redirects,
connection retries and HTTP caching are disabled; one-shot bodies prevent HTTP
follow-up replay. Calls have a 20-second deadline and responses are capped at
64 KiB. A normal, complete, single-choice response must contain a strict OTP object.

Provider HTTP 401 or an explicit expired-key response invalidates that key and
permits one reacquisition through current U and one more inference call. A second
expired-key failure also invalidates its key, then falls back. Other provider
failures do not retry inference. Diagnostics contain only a fixed failure label,
never an exception, request, response, SMS, OTP, key or configuration.

## Protected cache and lifecycle

`data/storage/ProviderCredentialCache` seals the entire returned key/configuration
bundle with Android Keystore AES-GCM. Its ciphertext is process memory only;
restarting the process requests a fresh bundle. A stable provider-specific Keystore
alias avoids accumulating orphan keys on every process start. Identity snapshots,
Pairing keys and preferences are unaffected.

`BailianCredentials` single-flights issuance across SMS operations, including
Upload renewal, and reuses the protected bundle only strictly before actual
`expiresAt` minus 30 seconds. Completed acquisition flights are released so they
do not retain a plaintext credential as a second cache. Only the key/configuration,
never SMS/model content, belongs to the shared acquisition operation.

Cache revisions, credential generations and session generations guard asynchronous
results. Logout, accepted login/session replacement, revocation, bundle replacement
and unavailable authority clear the encrypted cache. Pending issuance cannot refill
a cleared revision, and pending inference cannot publish an obsolete result or
retry under another login. Already-submitted provider requests cannot be recalled.

## Verification scope

The implementation uses existing focused host suites and disposable assertions
under ignored `build/` for candidate/result boundaries, provider requests and
failures, cache lifetime/concurrency, authority races and original-SMS encryption.
Those disposable checks are not included in the product or committed as test or
debug-seeding infrastructure.

The focused `GeneratedWireTest` and `LogoutRaceTest` suites passed. Final
`qualityCheck`, Debug/Release Kotlin compilation, Debug assembly and the full
`:app:test` suite passed (28 tests, none failed or skipped). Debug and Release
Android Lint each reported zero errors and 30 nonfatal warnings. The API 37
emulator passed all 130 disposable assertions, including real Keystore cache
roundtrips, 20 concurrent SMS operations sharing issuance across U renewal,
actual-expiry boundaries, bounded provider-key recovery, logout/replacement/
revocation/renewal races and decryption of full-SMS fallback payloads. The final
ordinary Debug APK was checked to exclude the disposable verification classes.

Live Bailian/QQ credentials, Android 10-16 device execution and real target-device
arrival are outside these local checks. The emulator checks intercept external
HTTP boundaries and do not establish live provider availability.
