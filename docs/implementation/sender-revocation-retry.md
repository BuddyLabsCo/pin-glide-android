# Unconfirmed selected-Sender revocation

Issue #35 adds an explicit Retry action to the named unconfirmed view. It keeps
that view and selection while busy, disables duplicate Retry and Back/Return,
and announces the localized busy label. It uses the existing light/dark theme,
scrolling layout, safe insets, scalable text and 48dp actions. Ordinary logout
continues through its separate local logout workflow.

## Reconciliation and resubmission

Retry starts a new complete Account Sender inventory read. Only that completed
read, for the same Account, acting Sender, session and current protected identity
generation, can establish presence or absence. Backgrounding, closing inventory,
superseding the read, storage errors and failed or incomplete responses cannot
authorize a mutation. Authority and the exact inventory result are checked again
before resubmission.

Absence completes the selected revocation without another mutation. Presence
permits one resubmission using the selected Sender ID, current management
credential and original QQ authorization code. No QQ interaction occurs on
Retry. Rejections and uncertain responses leave the same operation unconfirmed.
Other-Sender success preserves the acting Sender and reloads inventory. A
definitive current-registration 409 during a self-revocation Retry, either while
reading inventory or resubmitting, confirms self success and enters durable
RegistrationClosed/Welcome through CredentialRenewal.

## Explicit follow-up and service compatibility

The implementation follows the user's subsequent instruction to resend the old
code, superseding the ticket's original recoverable-authorization requirement.
The service release was checked again on November 12, 2026: v1.0.2, published
November 8, remains the latest release and the existing fixed Android pin. Both
published HTTP/WSS assets have been regenerated with the existing pipeline.
Generated sources and downloaded assets remain under ignored build output.

The published revoke body accepts qqCode, described as fresh and single-use.
This change neither invents an endpoint nor claims reusable service authority.
A consumed or expired code may be rejected; Retry then remains unconfirmed.
No authorization lifetime is invented on Android.

## Operation lifetime

Proof and selected/unconfirmed/Retry state live only in the process-owned domain
operation. Configuration changes retain the operation. Dismissal or closing the
Settings host clears proof, cancels pending work and invalidates captured Retry
and Return callbacks. A late response cannot affect a new selection. A fresh
process has no operation, modal, journal, automatic mutation or QQ replay.

## Verification

Disposable checks under ignored build/ticket-35 exercise the public inventory
and revocation workflows, including unchanged proof on resubmission, absence,
failed/stale/backgrounded reads, storage failure, duplicate actions, rejected
proof, self 204/409 completion, closed operations and fresh workflow startup.
No test harness or debug seed is added to tracked sources.

On November 12, 2026, all 46 host tests passed (28 existing tests and 18
disposable behavioral checks). The focused run passed all 18 retry checks, 11
logout/race tests and 3 service adapter tests. The final Gradle run passed
qualityCheck, Debug/Release Kotlin compilation, assembleDebug and :app:test.
Lint completed with no errors; it reported 30 warnings/2 hints for Debug and
26 warnings/2 hints for Release. The file-size check passed all 90 files.

An isolated API 37 emulator preview rendered the production screen in English
light mode and Chinese dark mode with 200% text and a long Sender name. Retry
and Return stayed visible and disabled while retrying, with wrapping text and
safe bottom clearance. The preview package was uninstalled afterward. The
final APK has the production application ID and no preview activity. Live QQ,
live revocation, TalkBack speech and physical-device behavior were not tested.
