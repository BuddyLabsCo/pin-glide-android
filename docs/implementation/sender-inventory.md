# Account Sender inventory

Device access management reads the complete current Account inventory through
the generated GET /v1/self/senders client with management credential M and the
shared renewal workflow. The Account is implicit in M; this screen never reads
the current Sender's Receiver or Pairing list. The adapter validates required
names, unique Sender IDs, active state, null revokedAt and creation timestamps,
and orders creation time descending, then Sender ID ascending. A response missing
the acting Sender is rejected as incomplete. Duplicate display names remain
unchanged, including ordinary-logged-out registrations returned by the service.

The latest published service release was checked through GitHub Releases on
November 11, 2026: v1.0.2, published November 8. The existing explicit pin already
matches it. Both published HTTP/WSS assets and generated clients were regenerated
with generateServiceClients; downloaded contracts and generated sources remain
ignored build outputs.

## Ownership and lifecycle

AccountSenders owns the in-memory inventory, request generation and selected
target. Entry starts a read. An actual background transition invalidates current
selection authority, cancels the old read and reloads on foreground return.
Activity configuration changes retain the process-owned workflow and its pending
read rather than starting duplicate reads. Returning to root Settings closes the
inventory. Logout, Account-session replacement and definitive registration
closure discard its rows and selection.

Initial loading and failure have no rows or invented empty success. Later reads
and failures retain rows while disabling Revoke; only failure offers Retry. A
successful fresh read restores selection. A selection rechecks protected identity
generation, Account, acting Sender, session and current authorization, and rejects
an obsolete UI snapshot. Old requests cannot replace a later read or closed view.

SenderSelection contains only the stable Sender ID, displayed name, Account and
authority/session/read generations. It is neither serializable nor saved UI
state. A named, cancellable selection preview exposes that target without
requesting QQ proof or sending a mutation; the confirmation/proof/submission
workflow is issue #34. Its consumer can invoke refresh after confirmed revocation.
confirmsAbsence accepts only the retained selection and a later successful,
complete, currently authorized read of the same Account/Sender/session. Failed,
pending, stale or foreign reads cannot establish absence.

## Verification

Focused Debug Kotlin compilation and GeneratedWireTest pass. Six disposable
host probes under ignored build/ticket-33 cover initial loading/failure/retry,
duplicate names and exact target capture, retained disabled rows, failure versus
successful absence reconciliation, background/foreground, changed authority,
Account replacement/logout and late non-cancellable responses. Real local HTTP
requests verify the generated endpoint, M header, ordering and rejection of
missing/invalid/incomplete rows, 503 and expired credentials. All six probes pass;
no automated tests or preview harness are added to tracked sources.

The final full host suite passes all 24 checks (18 repository tests and six
disposable inventory probes), with no failures, errors or skips. Debug/Release
Kotlin compilation, Debug APK assembly and all four repository static gates pass:

```powershell
./gradlew.bat -I build/ticket-33/checks.gradle spotlessApply qualityCheck :app:compileDebugKotlin :app:compileReleaseKotlin :app:assembleDebug :app:test --console=plain
```

An isolated API-37 emulator preview renders the production composables for all
six approved inventory states. English/light and Chinese/dark checks cover
initial states without rows, later states with all Revoke controls disabled,
self-only success, duplicate names, long-name wrapping and a 200% font scale.
The preview uses its own application ID and does not enroll or alter an Account.
Keyboard-only navigation selects the intended duplicate-name row, contains focus
on Cancel and returns focus to that exact Revoke action after dismissal. The
owner waits for its window to regain focus before requesting that return.

Live QQ/service enrollment, physical devices and TalkBack speech are not verified.
The ripwire advisory delta reports seven gating duplication findings in the
adapter/lifecycle patterns, and its test map leaves UI/lifecycle callers unmapped.
These advisories are not claimed as passing. The separate credential scopes and
workflow lifetimes retain their existing patterns without a generic abstraction.
The repository's four static gates remain the authoritative static checks.
