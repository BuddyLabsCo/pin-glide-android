# Incoming SMS forwarding

Issue #27 adds the SMS_RECEIVED boundary and a short foreground service. The
receiver requires the system BROADCAST_SMS sender permission and the current
RECEIVE_SMS grant. Each broadcast starts one independent, memory-only service
operation. Android decodes the supplied PDUs; each decoded text body is forwarded
unchanged, without application multipart reassembly, deduplication or inbox
access. No SMS role, history ingestion or additional runtime permission is used.

## Service lifetime

The unexported service supplies Android's required Notification object on a
low-importance channel, with a generic localized description and no message
content. It uses shortService on API 34+ and foreground type zero on API 29–33.
There is no POST_NOTIFICATIONS request or failure-alert feature.

Operations share the foreground notification but have independent coroutine
lifetimes and a 150-second deadline. Each accepted broadcast calls
startForeground again while its start exemption applies, renewing the API 34+
shortService deadline so earlier work cannot cut short a newer operation.
Completion releases the foreground state only when no operation remains and
stopSelfResult accepts the latest start ID.
The shortService timeout callbacks cancel all outstanding operations and stop
the service; destruction cancels its scope and removes the notification. A new
start received before destruction can re-enter the foreground. Exceptions,
cancelled operations, empty input and rejected foreground entry terminate safely.
START_NOT_STICKY prevents redelivery after process death. Rejected starts are
dropped without a persistent queue, WorkManager or automatic restart.

## Selection and delivery

SmsForwarding freezes the initially enabled, usable own Pairings before acquiring
upload access. Presence never excludes a selected target. It checks current
Account/Sender authority, session and key identity while preparing each target,
reserves a protected encryption use and erases each temporary key copy. Exhausted
or unusable keys cannot authorize encryption. Original text, including whitespace
and non-OTP messages, is encrypted unchanged. Issue #28 adds optional validated
OTP enrichment before upload authorization; absent candidates or failed extraction
use otp="". See [OTP extraction](otp-extraction.md) for its provider/cache policy.

The existing AES-GCM implementation now accepts the original text and OTP. Each
target gets independent encryption with a fresh nonce. The explicit test-message
action retains its approved payload/OTP and may still test an OFF target.

Both paths use the same generated DeliveryApi adapter, 20-second upload timeout,
safe status mapping and recovery rules. Immediately before submission the SMS
selector removes disabled, removed or unusable targets; initially excluded
targets cannot join. One frozen request carries one UUID messageId and its
encrypted items. HTTP 207 results are matched by input index and receiver ID,
independently of response ordering. Invalid response indexes remain unconfirmed.

Only no-response transport failure or whole-request 503
delivery_status_unavailable allows one identical recovery request. Recovery
revalidates authority and the complete frozen selection. Any selection change
stops recovery rather than changing the already-submitted request. Expired upload
access cannot trigger the credential wrapper's resubmission of an SMS. Confirmed
Sender revocation closes authority through the existing renewal workflow.
Logout, permission loss and obsolete identity/key generations prevent subsequent
submission. Work already submitted cannot be recalled.

SMS, OTP, ciphertext and pending results are confined to operation memory and
transient service-start extras. Only existing protected encryption counters are
persisted. No content enters application logs, saved UI state or delivery history.

## Verification

The focused GeneratedWireTest and OwnPairingsTest suites passed during
implementation. Final qualityCheck, Debug/Release Kotlin compilation, Debug
assembly and the full :app:test suite passed (18 tests, none skipped). Debug and
Release Android Lint each reported zero errors and 29 nonfatal warnings.

All 189 disposable assertions under build/ passed. They exercised original-text
AES-GCM decryption, independent nonces, empty OTP, OFF-target explicit tests,
encryption-use bounds, key-copy erasure, target pruning and generation/logout
guards. Real loopback HTTP checks covered reordered/invalid result indexes,
offline outcomes, identical recovery payloads, selection changes before recovery,
the two-attempt limit and upload expiry without resubmission.

An API 37 emulator accepted a system SMS-triggered foreground start with the
shortService type, then stopped the no-target operation without a crash or an
orphaned service. Its original denied RECEIVE_SMS permission was restored after
the smoke check. API 29–36 device execution, real target arrival, overlapping
in-flight uploads and forced timeout/process-death acceptance remain unverified
on devices. No persistent tests, debug seeds or E2E harness are added by this
production-only ticket.

The review regression check used disposable instrumentation on API 37 with the
system shortService timeout temporarily reduced from 180 to 20 seconds. Two
synthetic delivery operations each suspended for 15 seconds, with the second
starting 12 seconds after the first. Before the fix, the first completed and the
system timeout cancelled the second. With foreground entry renewed on each
start, both completed after the original system deadline. The system timeout
override was removed after each run. This checks the actual Android service
lifecycle with an in-memory delivery adapter; it does not establish live target
delivery or execution on API 29–36. The instrumentation stays under ignored
build output and is not part of the production change.
