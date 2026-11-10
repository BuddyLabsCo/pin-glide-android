# Single-target test messages

Issue #26 completes the shared inline test action in target details and on the
onboarding/standalone Pairing success screen. Opening rename/remove, closing the
origin, changing Account/Sender authority or selecting another target discards
that origin's feedback. Configuration changes retain the in-memory operation.
Ordinary navigation stays available during both upload attempts; tests do not
change activation, setup progress, receive switches or SMS permission.

## Payload and authority

The test uses exactly the approved SMS and string OTP 482913. It does not invoke
candidate filtering, Bailian or automatic receiving gates. CredentialRenewal
checks usable Upload authority before encryption. Only the selected active
Pairing's available 256-bit key is used, even when its receive switch is OFF or
its target is offline. Other Pairings are never included.

VerificationMessage supplies the typed verification_message JSON payload with
smsText and string otp. Encryption uses the shared AES-256-GCM primitive with
UTF-8, a fresh secure 96-bit nonce, a 128-bit appended authentication tag, empty
AAD and standard padded Base64. Each operation gets a secure random UUID
messageId. Plaintext, encrypted items and upload requests stay in operation
memory; UI state contains only pending state, the selected alias and safe results.

The protected Pairing counter starts at two to include both confirmation uses of
the shared message key. A durable reservation consumes one use before encryption,
including failed/cancelled attempts. Negative/invalid counts and the exhausted
2^32 bound cannot authorize encryption. Recovery reuses ciphertext and consumes
no additional encryption. Persisting a reservation preserves receive switches
and the other Pairings.

## Delivery and feedback

The existing generated HTTP and WSS integration is regenerated from the latest
published service release verified during implementation: v1.0.2, published on
November 8, 2026, with openapi.yaml and asyncapi.yaml. The explicit existing pin
already selects this release and is retained.

The generated DeliveryApi uploads with U and a 20-second call timeout. Automatic
connection retries, redirects and caching are disabled. Only a transport failure
without an HTTP response or a whole-request 503 delivery_status_unavailable
permits one recovery using the identical frozen messageId and encrypted item.
A body-read failure after HTTP response headers does not permit recovery.
An expired-U response reports login expiry without triggering the general
credential wrapper's resubmission of the upload. Persistent uncertainty stops
after the permitted attempts; there is no polling, queue or message retry state.
A later deliberate tap creates a new test operation.

HTTP 207 must contain exactly one result with index zero, the original messageId
and the selected receiverId. Missing, duplicate, out-of-range, mismatched,
unreadable or unknown results stay unconfirmed. Received with an error cannot
claim success. Offline and rejected have safe fallbacks when optional error
details are absent or unknown. send_failed, ack_timeout and interrupted retain
their distinct unconfirmed causes and never cause another send. Known rejection
and whole-request validation codes map to localized safe reasons; raw responses
are never displayed.

The shared action owns one pending operation across all attempts and suppresses
duplicate taps. Both origins use the same localized, accessible control and
status text. Only confirmed received shows exactly 测试消息已发送 in Chinese
(Test message sent in English), with one consumable arrival notice. Receipt
confirms arrival only; it does not establish decryption, display or copying.

## Verification

The existing focused OwnPairingsTest and GeneratedWireTest suites passed with
Debug Kotlin compilation. Final qualityCheck, Debug/Release Kotlin compilation,
Debug assembly and the full :app:test suite passed (18 tests, none skipped).
All 153 disposable assertions under build/ passed. They exercise independent
JCA payload decryption/tampering, framing, all published statuses and safe error
fallbacks, malformed/index-mismatched responses, real loopback HTTP recovery and
body-read boundaries, identical request bodies, the 20-second timeout, authority
expiry without resend, counter exhaustion, selected OFF-target isolation, key
copy erasure and stale/duplicate inline actions. These assertions are not shipped
or added to the repository's persistent test suite.

Android device execution, TalkBack/font/IME/theme visual acceptance, Keystore
crash atomicity and live target arrival require device/service acceptance.
