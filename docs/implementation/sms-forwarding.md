# Incoming SMS forwarding

Issue #27 adds the SMS_RECEIVED boundary and a short foreground service. The
receiver requires the system BROADCAST_SMS sender permission and the current
RECEIVE_SMS grant. Each broadcast starts one independent, memory-only service
operation. Android decodes the supplied PDUs; each decoded text body is forwarded
unchanged, without application multipart reassembly, deduplication or inbox
access. No SMS role, history ingestion or additional runtime permission is used.

## Message time

Issue #64 carries each decoded message's body and timestampMillis together in one
Bundle through the transient service Intent and forwarding operation. The source
is Android SmsMessage's service-centre timestamp, not handset arrival or the time
extraction, authorization or upload finishes. Android performs PDU/timezone
decoding; the application does not apply another timezone adjustment.

The encrypted VerificationMessage includes optional receivedAt as an RFC 3339
UTC timestamp with Z, preserving the source millisecond precision. Positive
epoch milliseconds whose UTC year is at most 9999 are usable. Missing, zero,
negative or unrepresentable values omit the JSON field entirely while forwarding
the unchanged SMS and OTP normally. This positive-value rule is application
policy, not an Android validity guarantee. Old and future values are retained
without skew/age limits, and missing times are never replaced by the local clock.
The Windows receiver can fall back to its local arrival time when receivedAt is absent.

All targets of one SMS receive the same source time. Recovery reuses the frozen
messageId and ciphertext, including that time. The field exists only inside the
encrypted payload; the HTTP/WSS envelope and service contracts are unchanged.

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

The existing AES-GCM implementation accepts the original text, OTP and source time. Each
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

## Earlier forwarding verification

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

## Timestamp verification (issue #64)

Seven persistent JVM regression tests pass at the shared encryption and forwarding
boundaries. Independent JCA decryption checks OTP and empty-OTP payloads, exact
millisecond precision, old/future dates, timezone independence and field omission
for missing/nonpositive/out-of-range values. Forwarding checks cover delayed
extraction, two independently encrypted targets, identical HTTP 503 recovery,
unchanged non-OTP SMS delivery with invalid times, and test creation before
credential renewal with a fresh timestamp on the next deliberate send.

A disposable app_process smoke check on API 37 passes using two distinct GSM
PDUs, including one with a service-centre timezone offset. The production receiver
decodes both through Android and retains the correct body/time pairs through an
Intent Parcel round trip. Unrelated actions and denied RECEIVE_SMS remain
rejected. The check intercepts foreground starts; it does not verify the service
lifecycle or live target delivery. Its harness remains under ignored build output.

Physical manufacturer, dual-SIM, background/lock-screen and delayed-radio delivery
acceptance, API 29-36 device execution and live Windows receipt remain unverified.
The API 37 framework check does not establish those device guarantees.
