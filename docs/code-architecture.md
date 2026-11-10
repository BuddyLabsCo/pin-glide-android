# Code architecture
```text
app/
|-- src/main/
|   |-- AndroidManifest.xml                    # Android entries, permissions and backup exclusions.
|   |-- kotlin/com/buddylabs/pinglide/
|   |   |-- app/
|   |   |   |-- MainActivity.kt                # Activity, Compose host and Android result delivery.
|   |   |   `-- PinGlideApplication.kt         # Concrete wiring, process scope and startup reconciliation.
|   |   |-- ui/
|   |   |   |-- navigation/                   # Startup destinations and host-specific Back/exit policy.
|   |   |   |-- onboarding/                   # Setup checkpoints, permissions, QQ and optional first Pairing.
|   |   |   |-- home/                         # Readiness, target list/details, aliases and local switches.
|   |   |   |-- pairing/                      # Shared scanner, consent, real progress and success screens.
|   |   |   |-- settings/                     # Language, Account display/logout and Sender access management.
|   |   |   `-- components/                   # Shared theme, privacy content and inline test-message action.
|   |   |-- domain/
|   |   |   |-- identity/                     # Login/restore, M/U/F renewal, logout and Sender revocation.
|   |   |   |-- pairing/                      # QR/session validation, handshake, activation and target state.
|   |   |   |-- forwarding/                   # OTP policy, SMS/test orchestration and delivery outcomes.
|   |   |   `-- settings/                     # Installation language and stable setup checkpoints.
|   |   |-- data/
|   |   |   |-- service/                      # Generated-client mapping, credential scopes and Pairing WSS I/O.
|   |   |   |-- bailian/                      # Prompt, strict JSON request/response and provider-key acquisition.
|   |   |   `-- storage/                      # Encrypted snapshot, preferences, provider cache and migrations.
|   |   `-- platform/
|   |       |-- sms/                          # SMS_RECEIVED and short foreground-service lifecycle.
|   |       |-- qq/                           # QQ Connect callbacks and fresh one-use authorization codes.
|   |       |-- permissions/                  # SMS/camera grants and Android settings intents.
|   |       |-- scanner/                      # Camera lifecycle and QR decoding; no retained frames.
|   |       |-- crypto/                       # HPKE, confirmation primitives, AES-GCM and Keystore protection.
|   |       `-- DeviceInfo.kt                 # Automatic Sender name and installed app version.
|   `-- res/                                  # Localized strings, theme, icon and backup rules.
|-- src/debug/                                # Development seed/credential injection; excluded from release.
|-- src/test/kotlin/com/buddylabs/pinglide/     # Domain, wire mapping, crypto and old-format migration tests.
`-- src/androidTest/kotlin/com/buddylabs/pinglide/ # Compose, permissions, Keystore and emulator integration.
config/
|-- codegen/service-contracts.json             # Service repository, fixed release tag and asset filenames.
|-- codegen/openapi.json                       # HTTP generator and serialization options.
|-- codegen/http-api-client-header.mustache    # Owned generated-client compatibility overlay.
|-- lint/lint.xml                             # Android/Compose Lint severity overrides.
`-- detekt/detekt.yml                          # Complexity <=14, nesting <=4 and selected defect rules.
scripts/
|-- codegen/                                  # AsyncAPI parser, Modelina serialization preset and WSS generation.
`-- quality/check-file-size.mjs                # <=500 physical lines per handwritten .kt/.kts, including tests.
build/                                        # Downloaded/generated outputs; ignored by Git.
|-- contracts/service/                        # Assets downloaded from the configured service release.
`-- generated/service/{http,wss}/              # Generated HTTP client and WSS models, compiled into :app.
build.gradle.kts                              # Downloads, generation, Spotless/ktlint and qualityCheck.
settings.gradle.kts                           # :app and dependency/plugin repositories.
gradle/libs.versions.toml                     # Toolchain, plugin and JVM-library pins.
package.json, package-lock.json                # Node generator dependencies and exact dependency resolution.
docs/ui-prototype/                            # Approved review artifacts; outside the Android runtime.
```

Ordinary logout writes a credential-free marker in no-backup storage before replacing the encrypted identity snapshot. The marker keeps old snapshot credentials inactive if replacement fails; only a successfully committed explicit login removes it. Signed-out snapshots retain only Sender restoration context, Pairing keys/metadata and receive preferences. Logout cancels pending terminal commits through the storage commit guard and prevents stale authorization publication. Setup stays at Welcome until local persistence succeeds; failures expose logout Retry there. Remote logout uses one captured refresh credential in memory and never blocks navigation or becomes a persisted retry.

## Responsibilities

UI owns presentation and navigation; domain owns workflow policy and its required I/O interfaces. Data/platform implement those operations; `app/` assembles concrete dependencies. Domain imports no Android, Compose, OkHttp or generated DTOs. UI does not access credentials, keys, concrete storage or network clients. Create interfaces at these seams, not around every class.

| Concern | Owner | Boundary |
| --- | --- | --- |
| Setup and navigation | `ui/navigation/`, `ui/onboarding/`, `domain/settings/` | Resume stable setup checkpoints and recheck actual SMS permission. Battery preparation records acknowledgement only, never detected exemption status. First Pairing may be skipped. Completed onboarding uses Home permission/login recovery. |
| Screen state | `ui/` ViewModels | Preserve pending work across configuration changes without duplicate requests; ignore results after navigation or Account/Sender/target generation changes. Share Pairing UI between onboarding and Home; only entry, Back/Cancel and completion policy differ. |
| Identity and renewal | `domain/identity/` | Atomically accept accountId, senderId and complete M/U/F. One refresher owns the persisted operationId/attempt and generation guards. Home relogin restores the original Sender; only verified post-logout login may replace Account context. Preserve old keys on failure/cancel; never reuse a consumed QQ code or infer Account mismatch from a conflated 404. |
| Logout and revocation | `domain/identity/` | Logout stops local authority immediately but retains permitted restoration context/Pairing keys. Verified replacement commits before old context is discarded. Current-Sender `409 device_registration_revoked` stops intake durably; ordinary Refresh 401 does not. Logout/confirmed self-revocation go to welcome; revocation discovered elsewhere uses the durable revoked destination. |
| Account administration | `domain/identity/`, `ui/settings/` | Read own profile without an Android display-profile cache. List same-Account unrevoked Senders, target by ID, and require fresh matching QQ owner proof for revocation. Complete-list absence reconciles success; failed/stale reads cannot authorize a mutation. Retry uses service-authorized resubmission, never the consumed QQ code. Selection/proof/unconfirmed actions stay in memory and are not replayed after process death. |
| Target management | `domain/pairing/`, `ui/home/` | Service owns membership, aliases, order and presence; local receive switches default ON. Home refreshes on foreground/mutation and pull; Sender inventory has failure Retry, no pull. Keep cached targets on fetch failure and reject stale writes. Missing/unreadable local key persists that target OFF; others continue. Recovery is ordinary remove/add, never automatic replacement. |
| Pairing | `domain/pairing/` | One workflow validates QR/service identity, P-256 point, PSK, version, challenge, role and session deadline; supports new_receiver/add_sender. Persist provisional key before confirmation, enable it only after authoritative activation, reconcile uncertain completion without replaying a handshake. Preserve existing Pairings on failure. No recover_receiver or in-place key update. |
| Pairing entry and progress | `ui/home/`, `ui/onboarding/`, `ui/pairing/` | The host handles camera permission and capacity before scanner entry. Duplicate scan returns to the existing target without mutation. Progress follows real events and cannot be cancelled through UI. Standalone process death follows ordinary startup; no special resume screen. |
| SMS ingestion | `platform/sms/` | Each broadcast starts an independent operation, without deduplication, multipart reassembly or inbox access. Manage the short foreground service and its required Notification object; release work on every terminal path. A rejected start/process death has no durable retry, WorkManager or always-on service. |
| OTP policy | `domain/forwarding/` | Candidate tokens control only whether Bailian is called. Validate the trimmed result as 4-8 ASCII alphanumeric characters appearing contiguously in the original SMS. No candidate, null/invalid output or provider failure still forwards the original SMS with otp="". |
| Forwarding and outcomes | `domain/forwarding/` | Select enabled usable Pairings, including offline targets. Encrypt once per target with fresh nonce, freeze one messageId/items, and map 207 results by input index. Use 20-second uploads; only no-response transport failure or whole-request delivery_status_unavailable permits one identical recovery request. Terminal per-target outcomes never trigger a resend; received means arrival only. |
| Explicit test message | `domain/forwarding/`, `ui/components/` | Use the fixed approved payload/OTP 482913 and the normal encryption/upload path, bypassing Bailian and automatic receive/SMS-permission gates. Test only the selected/new Pairing with a usable key; preserve switches and activation. Show pending/result inline, suppress duplicate taps and stale feedback; no delivery-result pages or history. |
| External I/O | `data/service/`, `data/bailian/`, `platform/qq/` | Map generated DTOs and safe errors into domain types. Keep M/U/F and provider-key scopes separate. Pairing WSS owns framing/dispatch, not activation policy; Android has no delivery WSS or Receiver Receipt sender. Bailian owns the configured prompt/strict JSON and single-flight key/config acquisition, actual expiry minus 30 seconds and one expired-key reacquisition. QQ SDK supplies codes; the service owns AppKey and exchange. |
| Cryptography | `platform/crypto/`, `domain/pairing/`, `domain/forwarding/` | Platform implements pinned HPKE PSK P-256/HKDF-SHA256/AES-256-GCM and exporter, message AES-GCM framing and Keystore protection. Domain owns when a key is provisional/active and may be used. Keys and SDK/library objects never reach UI; follow the service protocol rather than defining another wire format. |
| Durable state | `data/storage/` | One versioned encrypted atomic snapshot holds identity, complete credentials, refresh marker, generation, revocation state and active/provisional Pairing keys/metadata/switches. Serialize writers. Keep language/setup preferences and protected provider key/config cache separate. Clear provider cache on authority changes; never attach old Account keys to a new identity. |
| Format evolution | `data/storage/`, `platform/crypto/` | Prepare new keys before committing references; retain old keys until durable replacement. Migrate skipped versions and commit atomically. Preserve originals on corruption, missing keys, migration failure or future schema; never reset silently. Exclude private state from cloud/device-transfer restore. Reinstall/data clearing requires new login/Pairing; no Room/history store. |
| Settings and resources | `domain/settings/`, `ui/settings/`, `res/` | Language is an installation preference across logout/restart: system, Simplified Chinese or English, with unsupported locales falling back to Chinese. Follow system appearance; preserve pending work on configuration changes. Supply accessible Material 3 light/dark UI, font scaling and 48dp targets. No battery-status probe, global forwarding switch or system failure alerts. |
| Sensitive data | Operation owners | SMS, OTP, model payloads, ciphertext, QR secrets and pending handshakes remain operation-memory only; never enter logs, saved UI state or a durable queue. Diagnostics retain only safe operation metadata. No READ_SMS, default-SMS role, POST_NOTIFICATIONS request, notification listener or system-inbox mutation. |
| Generation and checks | Root build, `config/`, `scripts/` | Download the configured release in local/CI builds; downloaded YAML/generated Kotlin stay under build/. Keep GitHub credentials outside the APK. qualityCheck runs Android/Compose Lint, Spotless + ktlint, file size and detekt; generated code is compiled but excluded from handwritten format/size/complexity rules. |
| Tests | `src/test/`, `src/androidTest/`, `src/debug/` | Test domain transitions, wire outcomes and non-destructive storage upgrades on the host; Compose/system/Keystore behavior on emulators. Debug seeds support local-service SMS E2E and never ship in release. A seeded Receiver without a live client returns offline; this does not establish Windows display/copy or real QQ/hardware acceptance. |

Service profile, mandatory Sender names/unrevoked inventory, and no-repeat-QQ revocation resubmission require published service support. `data/service/` consumes those contracts; do not invent endpoints, reuse owner codes or substitute Pairing lists for Sender inventory. Automatic names come from `DeviceInfo.kt`, are trimmed/nonempty and limited to 64 Unicode characters; names never identify a destructive target.
