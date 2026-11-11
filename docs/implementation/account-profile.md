# Current Account profile

## Published service contract

On November 11, 2026, the latest published service release was v1.0.2
(published November 8). The existing explicit pin in
config/codegen/service-contracts.json already selects that release. The normal
Gradle pipeline downloaded its openapi.yaml and asyncapi.yaml assets and
regenerated both clients under ignored build/ output.

ServiceAccount uses the generated GetSelfAccount operation: GET /v1/self/account
with the current Sender's U credential and implicit authenticated identity. The
published AccountProfile has accountId plus nullable nickname and avatarUrl.
The adapter checks accountId against the authorized identity and maps a null or
blank nickname to absent display data. Account IDs and OpenIDs never become a
numeric QQ account label. The profile read does not acquire QQ authorization or
a provider token, and Android does not persist profile results.

## Presentation and lifetime

The Account card shows the current read's nickname and QQ account subtitle,
the approved loading text, or the generic QQ account label for missing data or
failure. Request failures show the localized network-error Toast. Leaving the
page cancels that Toast. Loading status retains its polite live region.

The compact QQ emblem reuses the shared component at 46dp; its default login
size stays 92dp. The Logout action uses the approved destructive list row with
a logout icon and chevron, a minimum 56dp height, and keyboard focus. The existing
confirmation and immediate local logout workflow remain available in every
profile state. Resources cover Simplified Chinese and English and follow the
existing system theme, font scaling, scrolling and system-bar insets.

AccountSession associates an open read with the login session generation. A new
session starts a fresh read even if the previous Account page was not closed.
Closing the page clears its transient profile state. Both successful reads and
ordinary service failures pass through CredentialRenewal's identity-generation
validation before publication; obsolete reads are discarded. Cancellation,
expired access and current-Sender revocation keep their existing shared handling.
A final session check also rejects obsolete failure results. No previous
successful nickname substitutes for a subsequent failed or empty read.

## Verification

Debug Kotlin compilation and the existing LogoutRaceTest passed before editing.
Temporary public-boundary probes under ignored build/ticket-32/ first reproduced
retaining an old nickname after a new login, publishing a superseded read as a
network failure, and publishing a superseded transport failure. They passed after
the corresponding fixes. Ten AccountSession probes cover identity/session
replacement, reentry, absent display data, late cancellation, pending-read logout,
credential renewal and Sender revocation. Four local HTTP probes exercise the
actual generated client, U header, fixed own-account route, missing/blank fields
and foreign-account response rejection. No test sources, harnesses or seeds are
committed.

Commands run from the repository root:

~~~powershell
./gradlew.bat qualityCheck :app:compileDebugKotlin :app:compileReleaseKotlin --console=plain
./gradlew.bat -I build/ticket-32/checks.gradle :app:compileDebugKotlin :app:compileReleaseKotlin :app:assembleDebug :app:test --console=plain
~~~

All four required static checks, Debug/Release compilation, Debug APK assembly
and the final full configured :app:test suite passed. The suite ran 32 tests with
zero failures, errors or skips: 18 repository tests and 14 temporary probes.

An isolated API-37 emulator ran the production Account composables in an ignored,
separately identified preview APK. Captures and UI hierarchies verified loading,
nickname, missing data and failure, both themes, both languages, network-error
Toast text, long nicknames, 200% font scaling, landscape rotation, keyboard
activation and Logout during loading/failure. Animation scales were disabled.
The preview package was uninstalled and the isolated emulator stopped. The final
normal APK has the production application ID and no preview activity.

Ripwire quality-delta exits successfully with zero gating findings, one minor
AccountSession length warning and a false dead-code finding for readProfile's
function reference. Its test gate exits 4 because tracked tests do not map to
these UI/domain changes; ignored probes and emulator checks are outside that
static map. No suppressions were added and the test gate is not claimed as passing.

Live QQ/service reads, physical devices, older Android versions and TalkBack
speech were not exercised. Emulator and local HTTP checks do not establish live
provider/service operation.
