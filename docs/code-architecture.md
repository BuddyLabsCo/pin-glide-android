# Code Architecture

Status: ready for review, updated 2026-11-08. This guide records the accepted
architecture decisions and their initial build configuration. Product features
and their runtime acceptance are subsequent implementation work.

## Scope

This effort delivers a minimal buildable Android project, four runnable static
checks, and this English architecture document. Product implementation follows
separately.

The four selected checks are:

1. Android Lint, including checks shipped with Compose.
2. Spotless with one pinned ktlint engine.
3. A handwritten source file-size check.
4. detekt.

Ordinary compilation and contract generation belong to the build. They are not
additional static-analysis tools. Package dependency rules are documented review
requirements; no separate architecture assertion library has been selected.

## Runtime and product constraints

Use Kotlin and Jetpack Compose with Material 3. Support Android 10-16, with
minimum runtime API 29, target SDK 36 and compile SDK 37. Successful compilation is not
evidence of runtime acceptance across all supported Android versions.

The latest functional and UI amendments take precedence over historical ticket
wording. In particular:

- Never request the default SMS role or modify the system inbox.
- Forward every incoming SMS, including the empty-OTP case.
- Keep SMS bodies, OTPs and ciphertext in operation memory only, without a
  forwarding-history store or sensitive logging.
- The fixed test-message action bypasses model credentials and extraction.
- Do not restore the removed dedicated delivery-result pages, in-place
  re-pair/key-update feature, or Receiver credential-recovery flow.
- Ordinary logout preserves the permitted Sender restoration and Pairing-key
  context. A successful different-Account login commits its replacement before
  discarding the prior local context; cancellation or failure preserves it.

## Source placement

Start with one Gradle module, `:app`. Split code by responsibility and feature
inside that module, not into speculative Gradle subprojects.

The minimal application ID and Kotlin namespace are `com.buddylabs.pinglide`.
`<namespace-path>` below means `com/buddylabs/pinglide`.

| Location | Owns |
| --- | --- |
| `app/src/main/kotlin/<namespace-path>/app/` | Application entry and composition of concrete implementations |
| `app/src/main/kotlin/<namespace-path>/ui/{onboarding,home,pairing,settings}/` | Compose screens, navigation, UiState and ViewModels |
| `app/src/main/kotlin/<namespace-path>/domain/{identity,pairing,forwarding}/` | Business rules and state transitions |
| `app/src/main/kotlin/<namespace-path>/data/{service,bailian,storage}/` | Transport/model mapping, provider access, persistence and format evolution |
| `app/src/main/kotlin/<namespace-path>/platform/{qq,sms,permissions,crypto}/` | Android/SDK callbacks, foreground-service glue and Keystore adapters |
| `app/src/test/kotlin/<namespace-path>/` | Host business, serialization and storage-format tests |
| `app/src/androidTest/kotlin/<namespace-path>/` | Android, Compose and system-integration tests |

Paths describe ownership; create a package when its first implementation is
needed. Do not create empty layers, generic utilities or pass-through wrappers.

### Dependency directions

- UI delegates business operations and renders their state/results.
- Business rules do not depend directly on Compose, the Android SDK, generated
  transport models, concrete network clients or concrete persistence.
- Data/platform implementations satisfy the required business-facing
  interfaces. Put an interface at a meaningful seam, not around every function.
- Data/platform code does not import UI. Generated code does not import
  handwritten application packages.
- The composition root connects concrete implementations to their callers.
- Generated service models remain behind handwritten transport mapping.

Review dependency changes against these rules. A successful Kotlin compilation
does not by itself enforce package isolation inside one Gradle module.

## Static quality policy

### Scope and failures

Check handwritten Android Kotlin, host/device tests and owned Gradle Kotlin
scripts where the selected tool supports those source types. The throwaway
HTML/CSS/JS UI prototype is outside these Android source checks.

Exclude generated sources, build outputs and third-party code from handwritten
formatting, complexity and file-size rules. Keep generated code in ordinary
compilation and applicable Android correctness checks. Keep handwritten
transport/storage outside generated exclusions.

Do not create a blanket baseline for new code. Review every suppression and
source exclusion at the narrowest useful scope, with its reason. CI checks
without rewriting source files.

| Check | Accepted initial policy |
| --- | --- |
| Android Lint and bundled Compose checks | Error/Fatal findings block. Decide warning severity per rule. Run explicit Debug and Release checks. |
| Spotless + ktlint | One formatter authority for handwritten Kotlin, tests and Gradle Kotlin DSL. Local fixing is allowed; CI only checks. |
| File size | At most 500 physical lines per handwritten `.kt`/`.kts` file, including blank and comment lines. Include tests and owned build scripts. Report the path and actual count. |
| detekt | Maximum cyclomatic complexity 14 and block nesting depth 4. Select other defect rules individually. Enabled findings block. Do not add a second ktlint/formatting wrapper. |

Prove the checks work using representative invalid inputs: bad formatting,
an Android/Compose Lint violation, excessive size and a selected detekt
violation. Unexpected empty source discovery or a missing required task is a
configuration failure, not a successful check.

The user accepts a pinned detekt 2 alpha release. The current detekt rules are
`CyclomaticComplexMethod`, `NestedBlockDepth` and `SwallowedException`, defined
in `config/detekt/detekt.yml`. This initial analysis is syntax based; it does not
claim type-aware coroutine checking. Add a type-dependent rule only with a
validated analysis classpath and representative positive/negative cases.

Android Lint uses the default rule severities and `config/lint/lint.xml`.
Compose's bundled diagnostics remain enabled. Formatting uses `.editorconfig`
and the root `build.gradle.kts`; the file-size implementation is
`scripts/quality/check-file-size.mjs`.

### Pinned toolchain and commands

| Tool/input | Selected version |
| --- | --- |
| JDK used to run Gradle | 25; local validation used Android Studio JBR 25.0.3 |
| Gradle Wrapper | 9.6.1, with distribution SHA-256 verification |
| Android Gradle Plugin / Android Lint | 9.3.3 |
| Kotlin / Compose compiler plugin | 2.4.10 |
| Compose BOM | 2026.03.01 |
| Activity Compose | 1.13.0 |
| Spotless / ktlint | 8.10.4 / 1.8.0 |
| detekt | 2.0.0-alpha.6 |
| Android compile platform / build tools | API 37 / 36.0.0 |
| Node used for quality scripts | 24.18.0 |

The dependency/plugin pins are in `gradle/libs.versions.toml`. Gradle runs on
JDK 25 while application Kotlin/Java bytecode targets JVM 17. AGP's built-in
Kotlin is used; do not apply the incompatible legacy Android Kotlin plugin.
OkHttp 5.5.1's Android artifact requires compile SDK 37. This compile-time input
does not increase the target SDK or declare Android 17 runtime acceptance.

From the repository root on Windows:

```powershell
.\gradlew.bat qualityCheck
.\gradlew.bat spotlessApply
.\gradlew.bat :app:assembleDebug :app:compileReleaseKotlin
.\gradlew.bat :app:testDebugUnitTest
npm ci --ignore-scripts
.\gradlew.bat downloadServiceContracts
```

On Unix use `bash ./gradlew` instead of `.\gradlew.bat`. `qualityCheck` aggregates
only the four selected checks; the root `check` task depends on it. Individual
tasks are `spotlessCheck`, `checkFileSize`, `detekt`, `:app:lintDebug` and
`:app:lintRelease`. Debug/Release Lint are variants of the same selected tool.

Keep `local.properties` untracked. Configure `sdk.dir` for the local Android
SDK with Java-properties escaping, or supply the supported SDK environment.
Do not commit a machine-specific SDK or JDK path.

The local `.github/workflows/ci.yml` runs the four gates, minimal build and
host tests on Linux with JDK 25, Node 24.18.0 and Android API 37/build tools
36.0.0. Local evidence below excludes remote CI; inspect the PR checks separately.

### Validation boundary

Local validation covers formatting, detekt, Debug/Release Android Lint,
Debug APK assembly and Release Kotlin compilation. The 2026-11-08 local run
passed six JVM tests covering generated serialization, HTTP cancellation and
the HPKE dependency profile. Both generated source trees were included in
Debug and Release Kotlin compilation.

The download path was exercised against the configured private release.
Removing a cached asset made ordinary Debug assembly download it again and
succeed; a subsequent download task reported `UP-TO-DATE`.

Lint dependency/SDK update advisories remain visible. They do not override
explicitly pinned compatible versions or establish tested Android 17 support.
No application feature, emulator/physical-device or cross-client crypto
acceptance is implied by these minimal-project checks.

## Service contracts and generated code

Use the exact `openapi.yaml` and `asyncapi.yaml` assets from one explicitly
pinned `pin-glide-service` release. The service release tag and the independent
contract version are different identifiers. Keep development/build download
credentials outside the APK.

Generate the HTTP client with OpenAPI Generator's Kotlin generator. Parse the
AsyncAPI input before passing it to Modelina Kotlin to generate WSS models;
Modelina does not provide the handwritten WebSocket transport.

Generated sources belong in the build directory and are not committed. Normal
local and CI builds depend on generation. Declare inputs and outputs so
unchanged valid output can be reused. No committed-output drift check is needed.

Download the configured release assets before generation. The upstream service
owns contract production and validation; the existing parsers/generators report
unusable input. Reject missing/empty expected generated output and compile it
normally. Do not duplicate upstream validation with a handwritten contract
validator.

### Transport and serialization

Use OkHttp for service HTTP, the Pairing WebSocket and Bailian HTTP. Configure
service and provider authorization separately. Reusing transport infrastructure
must never attach service credentials to provider requests, or provider keys to
service requests.

Use `kotlinx.serialization` for handwritten and generated JSON. Select OpenAPI
Generator's Kotlin `jvm-okhttp4` library with `kotlinx_serialization` and coroutine
operations, subject to generated-code compilation and cancellation tests.
The owned Modelina generation configuration supplies serialization annotations
and original wire-name mappings; never patch generated models by hand.

Use separate JSON policies. Service envelopes tolerate unknown optional fields
under the adopted compatibility rules. The model-produced OTP object uses strict
syntax/schema/field validation and original-SMS validation; a shared serializer
runtime does not make that output trustworthy.

### Reproducible generation

Configure only the source repository, release tag and two asset names in
`config/codegen/service-contracts.json`. The current source is
`BuddyLabsCo/pin-glide-service`, release `v1.0.1`, with `openapi.yaml` and
`asyncapi.yaml`. Commit that configuration, not the downloaded YAML or a
separate hash/provenance manifest. Changing the configured release is a
deliberate dependency update; never resolve floating "latest" during builds.

`downloadServiceContracts` invokes `gh release download` and stores the assets
under `build/contracts/service/<owner>/<repo>/<releaseTag>/`.
`generateServiceClients` depends on that download and runs both generators
before Android `preBuild`. The source configuration is a download-task input;
both files are declared outputs. Existing unchanged downloads and generated
outputs can be reused locally. A clean CI workspace downloads the assets, and
deleting the root `build/` directory makes the next local build do the same.
Download failures stop the build.

Local builds require GitHub CLI access to the configured private repository,
using `gh auth login` or `GH_TOKEN`. CI reads the source repository from the
same configuration and uses the existing `APP_ID`/`APP_PRIVATE_KEY` secrets to
request an installation token scoped to that repository with contents-read
permission. The App must be installed on the source repository with that
permission. The token is supplied only to the download step and stays outside
generated source and APK resources.

Normal Android builds resolve the download prerequisite automatically. No extra
YAML/reference/hash validator runs in Android.

| Component | Pin/configuration |
| --- | --- |
| OpenAPI Generator CLI | 7.26.0, `config/codegen/openapi.json` |
| AsyncAPI parser / Modelina | 3.6.6 / 5.10.1, `package-lock.json` |
| Owned WSS serialization preset | `scripts/codegen/wss-models.mjs` |
| OkHttp | 5.5.1 |
| kotlinx.serialization / coroutines | 1.11.0 / 1.11.0 |
| HTTP output | `build/generated/service/http/src/main/kotlin/` |
| WSS model output | `build/generated/service/wss/` |

`npmCi` installs the exact lockfile with lifecycle scripts disabled. The lockfile
includes narrow patched transitive overrides for the generator's legacy parser
dependencies; changes require regeneration, host tests and audit to pass.

Modelina 5.10.1's input selector does not recognize parsed AsyncAPI 3.1 documents.
The owned processor bridge admits the parser's 3.1 document to its
existing AsyncAPI processor; it does not rewrite the service contract version.
The preset preserves wire names, string enums, required/optional nullability
and serializers. Generation checks all nine required message models and actual
annotated declarations. Remove the bridge when an upgraded Modelina supports
3.1 directly and passes compilation and the host serialization tests.

HTTP dates remain wire strings. Service adapters validate/convert those at the
boundary. Generated models are serialization shapes, not sufficient validation
of message type/version, authorization, sizes, challenge correlation or crypto
parameters. The Pairing socket transport, reconnect policy and protocol state
machine remain handwritten product work. Generated Receiver delivery/Receipt
models do not give Android a Receiver role.

OpenAPI Generator 7.26.0's HTTP infrastructure template still assumes nullable
OkHttp 4 response bodies. `prepareHttpTemplates` extracts that exact pinned
template and adds `config/codegen/http-api-client-header.mustache`, suppressing
only `USELESS_ELVIS` in generated `ApiClient`. Handwritten warning enforcement
stays enabled. Upgrade/remove this overlay when the upstream template supports
OkHttp 5 directly; never edit the generated output.

Configure generated `Serializer.kotlinxSerializationJsonConfiguration` before
the first client access, including `isLenient = false` and unknown-field
tolerance. Do not mutate generated global credential defaults; give each
service client its explicit credential scope/provider. A generated unknown
enum sentinel is an unrecognized outcome, never a success or retry instruction.
Host tests cover mixed indexed upload results, unknown optional fields/error
codes, required WSS fields and cancellation reaching the underlying HTTP call.
The HPKE dependency probe checks matching exporters and authenticated
encryption with the selected suite; it is not a protocol acceptance vector.

The published release checked on 2026-11-01 and rechecked on 2026-11-08 is service
`v1.0.1`, with contract version `1.0.0`. Its exact assets are recorded in
the service Release; Android downloads the configured filenames to its build
directory. They exercise the existing generation path but do not
contain the approved mandatory Sender name, Sender inventory or own-account
profile operations. Those additions are tracked in service
[Sender names/inventory](https://github.com/BuddyLabsCo/pin-glide-service/issues/109)
and [account profile](https://github.com/BuddyLabsCo/pin-glide-service/issues/108).
Require a published release containing the approved operations before complete
feature integration; do not invent transport fields or endpoints locally.

## Cryptography implementation

Use the pinned Bouncy Castle lightweight HPKE implementation for the adopted
PSK/P-256/HKDF-SHA256/AES-256-GCM profile and exporter. Do not implement HPKE
primitives or change the shared protocol. In particular, do not substitute the
EXPORT_ONLY AEAD suite for the agreed AES-256-GCM suite.

Use Android Keystore for local key protection and platform AES-GCM for local
state encryption. Keep the HPKE implementation behind the platform crypto
adapter; business/UI code does not depend on concrete crypto-library classes.

Bouncy Castle is pinned to `bcprov-jdk18on` 1.86. Use lightweight `HPKE` with
`mode_psk`, `kem_P256_SHA256`, `kdf_HKDF_SHA256` and `aead_AES_GCM256`;
sender/receiver contexts and `export` use the service protocol's exact bytes.
Do not register/replace Android's global JCA provider. Local encryption uses
platform `AES/GCM/NoPadding` and a Keystore AES key.

Before product crypto is accepted, test the agreed RFC vectors, invalid peer
keys, wrong PSK/context and authentication failures, then run the shared protocol
fixtures against the Windows client. A JVM dependency probe or successful
compilation cannot establish Android Keystore behavior or cross-client
interoperability.

## Persistence direction

Prefer versioned local state/settings storage. Do not introduce Room or a SQL
database before a concrete requirement justifies it.

The existing functional requirements distinguish durable installation identity,
atomic M/U/F credentials and pending refresh state, Pairing keys/references,
target receive settings, application settings and the protected temporary
provider-key/configuration cache from memory-only operation content.

### Atomic state and independent stores

Keep Account/Sender identity, the complete M/U/F bundle, pending refresh
operation, login/logout generation, Pairing information/key references and
receive toggles in one versioned encrypted snapshot. Commit related changes
together. Keep ordinary settings and the short-lived provider credential cache
separate from that snapshot.

The selected file API must provide atomic replacement; serialize access through
one store. Atomic file replacement does not provide a transaction with
independent Keystore entries. Prepare new keys before committing their
references and retain old keys until replacement is durable.

### Storage contract

Use `AtomicFile` under the application's no-backup files directory and a single
store owner with a coroutine `Mutex` around complete read/modify/write
operations. Do disk/crypto work off the main thread. `AtomicFile` recovers its
backup before reading; never use a separate uncoordinated writer, including a
background component. Initial Android components run in the same process.

The encrypted envelope has independent `envelopeVersion: 1` and a
`schemaVersion` inside authenticated plaintext. It contains a stable Keystore
alias reference, a fresh 96-bit random nonce and ciphertext including the
128-bit AES-GCM tag, encoded with standard padded Base64. Authenticate a
fixed UTF-8 header `pin-glide-state:1` as AAD; reject other envelope versions
before decryption. Bound the input size before parsing. The alias is a selector,
not a secret and never authority to create a replacement key during a read.

Create the initial protection key only when no state exists. An existing
snapshot with an unavailable/invalidated key returns a classified failure and
preserves bytes. Keep protection keys outside biometric-enrollment-dependent
authentication so background SMS work can use them after device unlock.
Do not promise work before first unlock; verify key availability on supported
devices during feature implementation. Pairing secrets can be encrypted inside
this snapshot; use separate Keystore entries only where the selected algorithm
or SDK requires them.

The following is a field inventory, with synthetic placeholders, rather than
generated service models or production credentials:

```json
{
  "schemaVersion": 1,
  "localGeneration": 12,
  "identity": { "accountId": "example-account", "senderId": "example-sender" },
  "credentials": { "M": "example", "U": "example", "F": "example" },
  "pendingRefresh": null,
  "pairings": [
    { "pairingId": "example-pairing", "receiverId": "example-receiver",
      "key": "example-protected-secret", "receivingEnabled": false }
  ]
}
```

Production credential entries also retain their authoritative expiry/rotation
metadata. A non-null `pendingRefresh` retains the operation/idempotency identity
and the exact old-bundle context required by the adopted refresh protocol.
Persist that marker before the request; commit the whole returned M/U/F and
clear the marker together. A process restart resumes the approved operation,
never combines a new M with an old U/F. A local generation change invalidates
late in-flight results after logout, Account change or registration revocation.

| State | Lifetime/store |
| --- | --- |
| Identity, complete credentials, refresh marker, Pairing secrets/preferences | Atomic encrypted snapshot |
| Language and ordinary display preferences | Separate versioned preferences; no secrets |
| Temporary provider key, expiry and corresponding model configuration | Separate protected cache, expire together; never mix configurations |
| SMS, extracted OTP, model request/response, ciphertext | Operation memory only; no disk, saved state, WorkManager input or logs |
| Live QR/challenge/socket correlation and transient screen state | Memory; re-enter the approved flow after process death |

Local formats belong to `data/storage`, with explicit readers, migration
functions and writers. Keep service DTOs and disk DTOs separate so a service
generator update cannot silently rewrite persistent identity or key state.

### Backup, upgrade and downgrade

Exclude all application-private state from system cloud backup and device
transfer restoration. Normal installation-over-existing-app upgrades retain
state. Reinstallation, clearing application data and moving to another device
follow new-installation login and Pairing.

Support upgrades that skip application releases, including recovery from an
interrupted migration. Preserve old data on migration failure; never treat a
silent reset or key replacement as successful migration.

Do not support writing a newer stored format with an older application. Retain
the original data and require a compatible newer application rather than
clearing data to complete a downgrade.

Start envelope/schema versions at 1. Preserve an original until a complete
read, validation, migration, encryption and atomic commit succeed. Unknown
future schema/envelope versions are read-only failures. Do not deserialize a
future format by ignoring fields and then overwrite it with an older writer.

For example, a future schema 2 may add a non-security display preference; its
1-to-2 migration supplies the documented display default while preserving all
identity/credential/secret bytes. A subsequent schema 3 that changes a key
reference must validate and prepare the target key before committing 3.
A direct 1-to-3 upgrade runs both explicit steps in memory and commits once.
Interruption before the atomic commit leaves 1 readable; after it leaves 3.
Failure to resolve the key preserves 1 and returns the failure. A binary that
understands only 2 refuses to write 3. Never default an unknown
`receivingEnabled` or fabricate missing security metadata as a migration.

When implementing the store, add fixtures for each supported old schema,
direct skipped-release upgrades, interruption before/after commit, truncated
and authenticated-but-invalid payloads, wrong/missing keys, future versions,
refresh-bundle atomicity and stale generations. These are required feature
tests; this scaffold does not yet implement a store or claim those tests pass.

### Existing recovery decisions

This architecture consumes the approved business behavior rather than opening
a new generic recovery/reset flow. The source audit on 2026-11-08 covered the
functional, onboarding, Home, Pairing, delivery, Settings and architecture
tickets, including their resolution comments and superseding amendments.

| Condition | Existing required behavior | Authoritative decision |
| --- | --- | --- |
| This Sender's key for one Pairing is missing or unreadable | Show the approved local-key error, persist that Pairing's receiving preference OFF, prevent sending with it, retain its service Pairing and allow ordinary rename/removal. Other usable Pairings continue. Recovery uses ordinary removal followed by adding a new Pairing; no re-pair shortcut or automatic replacement. | [Approved Home/device specification, local Pairing-key failure](https://github.com/BuddyLabsCo/pin-glide-android/issues/5#issuecomment-6411192776), confirmed by the [Home resolution](https://github.com/BuddyLabsCo/pin-glide-android/issues/5#issuecomment-6423232531) |
| An active saved Sender's credentials cannot be renewed | Reuse Home's explicit QQ re-login to restore the saved registration. Preserve old Pairings/keys/preferences on cancellation or failure. A failed restore does not authorize silently dropping senderId and creating another registration. | [Approved Home specification, login recovery](https://github.com/BuddyLabsCo/pin-glide-android/issues/5#issuecomment-6411192776) and [Settings-derived functional amendment](https://github.com/BuddyLabsCo/pin-glide-android/issues/2#issuecomment-6549580538) |
| The discussed Receiver-side credentials/local data are lost | Remove the affected Pairing and add a device normally. No dedicated recover_receiver, in-place key update or automatic credential replacement. | [Removal of Receiver credential recovery](https://github.com/BuddyLabsCo/pin-glide-android/issues/6#issuecomment-6434247202), carried into the [approved standalone Pairing specification](https://github.com/BuddyLabsCo/pin-glide-android/issues/6#issuecomment-6530860917) |
| App data was actually cleared or the app reinstalled | The lost local identity/keys cannot be recovered from public IDs or QQ login alone. Follow the applicable new-installation login/Pairing behavior. | [Functional resolution, privacy and local persistence](https://github.com/BuddyLabsCo/pin-glide-android/issues/2#issuecomment-6357314110) |
| Ordinary logout or a subsequent login attempt | Preserve the permitted restoration context. Same-Account/same-Sender success can reuse only its retained keys. Different-Account state replaces old local context only after verified successful login; cancellation/failure preserves the old context. | [Approved Settings specification, ordinary logout and next login](https://github.com/BuddyLabsCo/pin-glide-android/issues/8#issuecomment-6549582545), confirmed by the [Settings resolution](https://github.com/BuddyLabsCo/pin-glide-android/issues/8#issuecomment-6554476124) |

The previous suggestion to provide a whole-installation reset confirmation after
encrypted-snapshot corruption or loss of its protection key was not accepted.
The audited resolutions do not define that generic destructive-reset journey.
Reinstallation/data clearing and a corrupt but still present store are different
conditions; do not infer permission for the latter from the former.

Storage readers must distinguish absent state, unreadable/corrupt state,
unavailable protection keys and unsupported format versions. Do not convert an
I/O/decryption/format error to an empty installation, auto-register a new Sender,
silently delete Pairings or introduce a new reset UI under this architecture
effort. Preserve original state and return a classified failure without using
unreadable authority. If product implementation needs a new whole-store repair
interaction, that is an explicit functional/UI gap; it is not a new approved
recovery rule in this document.

## Placement examples and review

| Change | Put it here; review consequence |
| --- | --- |
| Decide which enabled Pairings can receive an SMS | `domain/forwarding`; unit-test selection, never read Compose state or call OkHttp here |
| Validate a model-returned OTP against the original SMS | Pure `domain/forwarding` validator; Bailian JSON/HTTP mapping stays in `data/bailian`; test null/empty/foreign-text cases |
| Request SMS/camera permission and observe revocation | `platform/permissions` plus the owning screen's UI action; domain receives capability state, never an Activity |
| Add a Home target row or error presentation | `ui/home`; render approved UiState and delegate actions rather than renewing credentials from a composable |
| Persist a receive toggle | `domain/pairing` owns the operation; `data/storage` atomically updates the snapshot; UI observes committed state |
| Consume a new service field | Update the configured service release, download/regenerate, then map in `data/service`; keep generated DTOs out of UI and storage |
| Encrypt a forwarding payload | `platform/crypto` implements the required domain seam; no library-specific crypto objects in screens |

Before accepting a change, review package direction, memory-only sensitive
content, authorization separation, complete credential commits, cancellation
and stale-generation handling, local-format compatibility and the existing
recovery decisions. Run `qualityCheck`, the affected feature tests and normal
Debug/Release builds.

The minimal scaffold establishes a build and review contract; it does not
implement login, forwarding, Pairing, persistent storage or their screens.
External service release additions and later Android/Windows runtime acceptance
are explicit implementation prerequisites, not unanswered architecture
preferences.
