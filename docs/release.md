# Dev and Prod APKs

The existing Debug and Release build types are the Dev and Prod installations.
Both use namespace `com.buddylabs.pinglide`, minimum API 29 (Android 10), target
API 36 (Android 16), compile API 37, and the approved
`app/src/main/res/mipmap-nodpi/ic_launcher.png`.

| Build type | Application ID | English / Chinese launcher name | Service transport |
| --- | --- | --- | --- |
| Debug (Dev) | `com.buddylabs.pinglide.dev` | Pin Glide Dev / 鹊信开发版 | HTTP/WS or HTTPS/WSS |
| Release (Prod) | `com.buddylabs.pinglide` | Pin Glide / 鹊信 | HTTPS/WSS only |

Dev is debuggable, Prod is not. The installations can coexist and have independent
app-private Sender registration, Pairings, and settings. Prod retains its existing
identity and storage format.

The installed version comes from `versionCode` and `versionName` in
`app/build.gradle.kts`. Debug adds `-dev` to its source branch's version name
and uses that branch's version code; it does not consult production releases
or derive a code from the build run. Keep the application ID and release
signing identity stable.

## Release version policy

The production release proposal owns the two Gradle version fields and
`CHANGELOG.md` together. The first production release is `0.1.0` / code `1`.
After publication, the next proposal defaults to the next stable patch and
exactly the previous published version code plus one. A maintainer may select
a strictly higher stable minor or major version (or higher patch). Stable
names consist of three decimal components with no leading zeroes, prefixes,
prerelease suffixes or build metadata. Codes are positive integers up to
2,100,000,000. Each changelog heading records both values and has release notes;
published sections are retained, newest first.
The policy expects one top-level `android` block with one direct `defaultConfig`
child containing only literal assignments. It rejects additional version property
references, setters, or unsupported source shapes (including block comments and
multiline strings) rather than treating them as metadata.

Use the dependency-free policy from the repository root:

```powershell
node scripts/release/version-policy.mjs check
node --test scripts/release/version-policy.test.mjs
```

The check prints JSON containing `versionName` and `versionCode`. It verifies
the Gradle source matches the changelog and that the history increases without
reused names or skipped codes. Ordinary CI runs this consistency check without
deployment secrets. Release proposal and publication callers must independently
obtain the latest published production version and code, not infer publication
from the branch or changelog. Use `none` only when no production release exists.

For example, with release notes in a Markdown file (without release headings):

```powershell
node scripts/release/version-policy.mjs propose none notes.md
node scripts/release/version-policy.mjs check none
```

After `0.1.0` / code `1` has actually been published:

```powershell
node scripts/release/version-policy.mjs propose 0.1.0:1 notes.md
node scripts/release/version-policy.mjs check 0.1.0:1
```

Pass an optional final stable version, such as `0.2.0`, to select a higher
version. Refreshing an existing proposal against the same published baseline
replaces only its notes, keeps its higher chosen version (unless explicitly
overridden) and never increments its code again. When refreshing from the
published source rather than the proposal's checkout, pass the previous
proposal's chosen version explicitly.
The baseline-aware `check` is also the packaging/publication validation entry
point: it rejects an already published version, reused or skipped code, or a
changelog that does not match the supplied published baseline.
These commands do not open proposals, sign packages, tag commits or publish
releases.

## Automatic release proposals

**Auto-release proposal** runs on pushes to `main`. It checks out current
`main` with full history and serializes runs without canceling an active proposal.
The existing repository-scoped GitHub App (`APP_ID` and `APP_PRIVATE_KEY`)
must grant `contents: write` and `pull-requests: write` for this repository.
Only the `auto-release` branch and its same-repository PR into `main` are
written. The App token lets the PR trigger ordinary, independent `quality` CI;
no signing or distribution Environment inputs are used.

- A main change outside `.github/` and the release metadata is eligible.
  Changes to `CHANGELOG.md` and only the two Gradle version assignments do not
  by themselves open or refresh a proposal; ordinary Gradle changes do.
  When an ineligible push follows an interrupted proposal, current main is
  compared against the main ancestor of the open proposal, the latest published
  commit, or the initial root commit before the first release. Outstanding
  eligible changes still receive a proposal. Release PR merges and stale
  pushes already included in a published release are ignored.
- The proposal uses the latest published stable Release, its tagged source
  and the shared version policy, not a version guessed from main or a reserved
  tag. The proposal and production workflows share historical Release
  validation; the release PR's independent read-only quality check verifies
  the next version and published changelog sections. Notes summarize non-merge
  commits since that publication (or from the repository root for the first
  release). The proposal updates the Gradle
  version fields and changelog together.
- To select a higher stable version, use the version policy on the open
  `auto-release` branch and commit the resulting consistent metadata. Subsequent
  eligible pushes preserve that version and regenerate notes from current
  main without incrementing its code again. The first release is fixed at
  `0.1.0` / code `1`. Do not put unrelated changes on the proposal branch.
- The workflow checks remote main, the proposal branch and PR state before
  updates, uses an explicit force-with-lease on the proposal branch, and skips
  identical branch and PR metadata updates. If state changes concurrently,
  the next queued main push reconciles outstanding eligible changes; rerun
  the failed proposal if no newer run is queued.
- An authorized but not yet published production release blocks another
  proposal. A matching release PR merged by a bot or with auto-merge is an
  explicit error, not an authorized release awaiting publication. After the
  production workflow successfully completes, the proposal
  workflow checks current main for eligible changes since the published release
  to catch up pushes made during packaging. Failed publication must be recovered
  through the original production run, not another release proposal.

Review the version, notes and independent `quality` status before manually
merging. Retain required reviews and branch protection on `main`; do not give
the App a main-protection bypass or permission to merge automatically. Allow
the App to create and force-update only the dedicated proposal branch. The
proposal workflow never merges, signs, reserves tags or publishes. Only the
separate production workflow below can publish from the authorized merge SHA.
Ordinary CI runs the proposal fixture tests together with the existing release
policy and publication tests:

```powershell
node --test scripts/release/auto-release-pr.test.mjs
```

## Build prerequisites

Use the checked-in Gradle wrapper and pinned dependencies. The current environment
uses JDK 25, Node.js 24.18.0, Android SDK platform `android-37.0`, build tools
`36.0.0`, and GitHub CLI. Set `JAVA_HOME` and `ANDROID_HOME` for that installation.
Use `gradlew.bat` in PowerShell or `bash ./gradlew` on Linux/macOS.

Authenticate GitHub CLI with read access to the service repository, locally through
`gh auth login` or through a protected `GH_TOKEN` in CI. The existing build downloads
`openapi.yaml` and `asyncapi.yaml` from the fixed release in
`config/codegen/service-contracts.json` (currently
`BuddyLabsCo/pin-glide-service`, `v1.0.2`). It generates and compiles the HTTP client
and WSS models under `build/`; do not copy contracts or generated code into source.
`npmCi` installs the locked generator dependencies automatically.

For QQ login and Service access, set the public build inputs for the intended variant:

| Input | Dev | Prod |
| --- | --- | --- |
| Service origin | `PINGLIDE_DEV_SERVICE_URL` | `PINGLIDE_SERVICE_URL` |
| Numeric QQ Connect App ID | `PINGLIDE_DEV_QQ_APP_ID` | `PINGLIDE_QQ_APP_ID` |

Supply an HTTP(S) origin with a valid host and optional port. An optional root slash
is normalized away; credentials, non-root paths, queries and fragments are rejected.
Prod requires HTTPS and denies cleartext at runtime. Pairing WebSockets use the
corresponding WS/WSS transport. Service redirects are disabled in both builds.
The production HTTP client also permits only TLS connections.
Use only development credentials and test data with Dev HTTP. There is no runtime
environment switch or fallback between variant inputs.

Register QQ separately for each application ID and signing certificate. The callback
scheme is derived from the corresponding App ID. These public values ship in the APK;
never supply the QQ AppKey, provider tokens, signing passwords or GitHub credentials
as either value. Unconfigured builds can compile and run setup but fail explicit login
locally before opening QQ. Distribution requires both public inputs.

The build downloads the official QQ Connect Android SDK 3.5.19 archive from Tencent's
CDN and falls back to its storage origin if the transfer fails. It verifies the
pinned SHA-256 before extracting the JAR into ignored `build/dependencies/qq/`.
SDK preparation replaces its twelve static logging method bodies with no-ops,
including callback URI diagnostics, before either variant compiles. It also makes
`QQToken.saveSession` acknowledge without writing: the SDK's native login listener
otherwise caches its callback before delivering the one-use code. Changed method
signatures or counts fail preparation for review. This preserves ordinary debug
behavior and keeps authorization content out of SDK logs and session files.

## Protected signing inputs

Supply all four environment variables for the intended variant to the Gradle process:

| Dev | Prod | Supplied value |
| --- | --- | --- |
| `PINGLIDE_DEV_SIGNING_STORE_FILE` | `PINGLIDE_SIGNING_STORE_FILE` | Absolute path to a readable keystore outside the checkout. |
| `PINGLIDE_DEV_SIGNING_STORE_PASSWORD` | `PINGLIDE_SIGNING_STORE_PASSWORD` | Keystore password from the local/CI secret store. |
| `PINGLIDE_DEV_SIGNING_KEY_ALIAS` | `PINGLIDE_SIGNING_KEY_ALIAS` | Alias of the approved variant signing key. |
| `PINGLIDE_DEV_SIGNING_KEY_PASSWORD` | `PINGLIDE_SIGNING_KEY_PASSWORD` | Private-key password from the local/CI secret store. |

Use separate durable signing keys for the two environments. Use the same Dev key
locally and in CI for upgrade-compatible distributed Dev APKs.

Locally, have the credential manager inject these variables into a dedicated shell
or process. Keep the keystore in a private directory with access restricted to the
builder, and keep protected backups of both keys. Do not enter passwords in
shell history, pass them as command-line arguments, or store them in this repository
or `gradle.properties`. Keystore files and `keystore.properties` are ignored as an
additional safeguard.

In CI, use the selected protected development or production environment with approved
secret access. Map the environment's public `PINGLIDE_SERVICE_URL` and
`PINGLIDE_QQ_APP_ID` and signing secrets to the corresponding variant inputs above.
Restore the keystore from the CI secure-file store into a private runner temporary directory,
map the four variables above to the signing step, and remove the file when that
step ends, including on failure. Keep GitHub download credentials scoped to the
existing contract-download step. The ordinary quality workflow compiles Release
without signing access; it does not publish or distribute APKs.

Release APK/bundle packaging always requires all signing and public inputs,
plus the independently obtained `PINGLIDE_PUBLISHED_RELEASE` baseline
(`none` or `version:code`). The baseline is public metadata, not a signing secret.
Dev distribution packaging additionally requires `-Ppinglide.devDistribution=true`
so missing inputs cannot silently produce an APK signed with the default debug key.
Supplying any Dev signing input also requires the complete Dev configuration at
packaging. Invalid files, passwords, or aliases fail through Android's signing
validation. Ordinary Debug builds use Android's default debug key without
distribution inputs. Release compilation and static checks do not require signing.

Signing inputs are consumed only by the Gradle signing configuration. Keep these
and GitHub credentials out of `BuildConfig`, resources, assets, manifests, compiler
arguments and diagnostics. Signing inputs require both the configuration cache
and build cache to be disabled. Use normal console verbosity; do not publish verbose
build logs, environment dumps, Gradle state/caches or keystores as artifacts.
Development-only code and injections must stay in `app/src/debug/`; Release uses
the main/release source sets. Do not add debug seeds or onboarding bypasses to
produce a release.

## Manual development packaging

Configure the GitHub Environment named `development`:

| Environment variable | Value |
| --- | --- |
| `PINGLIDE_SERVICE_URL` | Development HTTP or HTTPS origin |
| `PINGLIDE_QQ_APP_ID` | Numeric Dev QQ Connect App ID |
| `PINGLIDE_SIGNING_CERT_SHA256` | Approved Dev certificate SHA-256 fingerprint (hex, optionally colon-separated) |

| Environment secret | Value |
| --- | --- |
| `PINGLIDE_SIGNING_KEYSTORE_BASE64` | Base64 of the shared Dev keystore file |
| `PINGLIDE_SIGNING_STORE_PASSWORD` | Keystore password |
| `PINGLIDE_SIGNING_KEY_ALIAS` | Dev key alias |
| `PINGLIDE_SIGNING_KEY_PASSWORD` | Dev key password |

Obtain the certificate fingerprint from the independently approved Dev key using
`keytool -list -v -keystore <dev-keystore> -alias <dev-alias>` (enter the password
at the prompt). Do not derive the expected fingerprint from the APK being verified.
Register Dev QQ Connect with this package and certificate. Protect the Environment
with appropriate branch restrictions and reviewer approval: selected branch code
will execute with the Dev signing inputs. Do not grant untrusted branches access.
Remove all three distribution variables and all four signing secrets listed above
from repository and organization configuration accessible to this repository.
Packaging rejects public-variable names (including blank values) and nonempty
signing-secret fallback before accessing the Environment. The preflight receives
only a boolean for the presence of those secrets, never their contents.

In GitHub Actions, choose **Package development APK**, select **Run workflow**,
choose the source branch and start the run. It checks out the dispatch commit,
downloads the pinned contracts with the existing read-only Service App token,
assembles Debug with the branch's version code and `-dev` version name, and
verifies its signature, signer, alignment and manifest. The keystore is decoded
only during packaging into a private runner temporary directory and removed when
the step exits, including on failure. Distribution runs use no Gradle build or
configuration cache or shared CI caches, and can run independently.

Download `pin-glide-<versionName>-<source SHA>` from the successful run's
Artifacts section and extract `app-debug.apk`. The artifact contains only that
verified APK; no GitHub Release is created. This workflow does not enable
production distribution.

For an upgrade-compatible local Dev build, inject the same shared Dev key via the
four `PINGLIDE_DEV_SIGNING_*` inputs above, alongside `PINGLIDE_DEV_SERVICE_URL`
and `PINGLIDE_DEV_QQ_APP_ID`. Use the Dev distribution build command below and
the independently approved Dev certificate fingerprint when verifying.

## Production publication

Configure the protected GitHub Environment named `production` with the same
variable and secret **names** as `development`, but with the production HTTPS
Service origin, production QQ registration and independently approved production
signing key and certificate fingerprint. Do not reuse the Dev signer.
Restrict this Environment and the `auto-release` branch to trusted maintainers,
require reviewed changes to `main`, and keep automatic merging disabled for
release proposals. The production workflow checks the merged event, including
the merging user and any auto-merge configuration in the payload; repository
protections enforce actual manual approval.

Only closing a merged same-repository `auto-release` PR into `main` can authorize
the **Production release** workflow. Ordinary pushes, closed unmerged PRs, forks
and manual workflow dispatch are not production authorizations. The separate
proposal workflow above opens or refreshes PRs; this workflow does not create
them. Before Environment access,
the workflow verifies that the event, workflow SHA and checkout match, rejects
broader-scope distribution variables and signing secrets, and compares the
Gradle/changelog version with the published stable Release history. Production
jobs queue without canceling the active job; the package job repeats the
publication-state check while holding that queue slot.

The job checks out the authorized merge SHA, not the latest `main`. It downloads
the pinned Service contracts with the read-only Service App token and assembles
Release without shared build, dependency or configuration caches. The production
keystore exists only during signing in a private runner temporary directory and is
removed when that step exits, including failures. The existing Release build
requires HTTPS and the production network policy disables cleartext and uses
TLS for HTTP and WebSockets. The shared APK verifier checks the candidate's
signature and approved signer, version, package, manifest, non-debuggability,
cleartext policy, QQ callback and page alignment.

Immediately before candidate verification, the serialized job reserves the
version with an annotated `v<versionName>` tag pointing to the authorized SHA.
The annotation identifies the owning workflow run. A conflicting stable tag or
an unfinished reservation for another version blocks packaging; the production
Release itself is not created until verification and retention succeed.
Protect these tags against deletion or modification. The job retains these files
as the immutable
`production-release-candidate` Actions artifact for the authorized workflow run:

- `pin-glide-<versionName>.apk`
- `pin-glide-<versionName>.apk.sha256` (SHA-256 digest in checksum-file format)
- `release-candidate.json` (merge SHA, version name/code, package, approved signer,
  QQ App ID, filename and digest; no signing credentials)

Publication re-verifies the retained candidate identity, digest and APK, uses
the reserved `v<versionName>` tag and creates a draft GitHub Release with the corresponding
changelog notes and candidate identity, and uploads those three files. The
Release becomes public only after the API confirms all three assets' sizes and
SHA-256 digests. No uploaded asset is replaced. A failed upload leaving an empty
`starter` asset in the matching draft may be removed and retried; a mismatched
asset, version/code, tag, draft or candidate is an error.

### Recovering an interrupted release

1. Find the original **Production release** run for the authorized merge.
   Inspect the failure and confirm the authorized SHA and candidate artifact.
   Do not create another release proposal for the same version.
2. Correct the external cause (for example, a temporary network failure) and
   rerun the failed job or original workflow. GitHub keeps the original run ID
   and authorized commit. The reservation also identifies the original artifact
   if another authorized run for the same commit is encountered. Keep the approved
   production signer and QQ identity unchanged while recovery is pending.
3. If the candidate exists, the job downloads it from that run, verifies its
   identity, digest and APK again and publishes those exact bytes. It skips
   Service contract download, assembly and signing. Successfully uploaded files
   are retained; missing files are uploaded, and a complete matching Release
   is left unchanged.
4. If an earlier attempt never reached candidate verification and there is no
   candidate, tag or Release, a retry can build normally. Once the version is
   reserved, even an interruption before APK verification completes is treated
   conservatively as requiring recovery. If verification was reached, or
   publication state exists, missing/expired/ambiguous recovery
   artifacts cause the retry to fail closed. Recover the original verified files
   from protected backups or escalate for maintainer reconciliation; never
   rebuild, resign, move a tag or overwrite a release asset to make the same
   version succeed. Retain the candidate artifact for the configured Actions
   retention period and preserve verified copies if a release remains pending.
   For attempts without a reservation, recovery checks the historical Actions
   job name `package and publish` and step name `Verify and record candidate`.
   These names are part of the recovery contract, not interchangeable UI labels.

The workflow's preflight needs read-only repository contents. Only the serialized
package/publication job has `contents: write` for tags and Releases and
`actions: read` for candidate recovery. Signing secrets are provided only to the
assembly step. Every later production release must retain each historical
changelog section from its published source and use the same approved certificate
fingerprint recorded by the earlier published candidates. The production
publication workflow must not be tested against real production Releases: use
the focused local tests instead:

```powershell
node --test scripts/release/production-policy.test.mjs scripts/release/production-publication.test.mjs scripts/release/production.test.mjs
```

The tests use isolated temporary files and an in-memory GitHub API fixture;
they never create a real tag, Release or workflow run.

## Build and verify

From the repository root, without deployment inputs:

```powershell
.\gradlew.bat qualityCheck --console=plain
.\gradlew.bat :app:compileReleaseKotlin :app:assembleDebug --console=plain
.\gradlew.bat :app:test --console=plain
node --test scripts/quality/build-variants.test.mjs
```

The variant suite also runs in ordinary CI and exercises both APK and bundle
packaging with disposable, independently generated test keys. Its build outputs
are verification artifacts, not distribution candidates. The host unit suite uses
loopback HTTP and HTTPS fixtures to check WebSocket transport, redirect denial
and certificate validation without contacting a deployed Service.

For distribution, inject the intended variant's public and protected inputs and
run its packaging command. Before production packaging, determine the latest
published production release and set `PINGLIDE_PUBLISHED_RELEASE` to its actual
version and code (for example, `0.1.0:1`), or `none` only for the first release.
The following uses `none` as the first-release example:

```powershell
.\gradlew.bat :app:assembleDebug '-Ppinglide.devDistribution=true' --no-configuration-cache --no-build-cache --console=plain
$env:PINGLIDE_PUBLISHED_RELEASE = 'none'
node scripts/release/version-policy.mjs check $env:PINGLIDE_PUBLISHED_RELEASE
if ($LASTEXITCODE -ne 0) { throw 'Production release version validation failed' }
.\gradlew.bat :app:assembleRelease --no-configuration-cache --no-build-cache --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Production packaging failed' }
```

Gradle also runs the baseline-aware policy before Release APK or bundle packaging,
so missing, reused or inconsistent release metadata fails packaging even when
invoked directly. Never publish a candidate after a failed validation or build,
including a stale APK from an earlier build. Publication must recheck the same
candidate metadata against the current published baseline.

`qualityCheck` retains the four existing gates: Android/Compose Lint,
Spotless/ktlint, handwritten Kotlin file size, and detekt. The build keeps compiler
warnings as errors and compiles generated integration sources in both variants.
These commands reproduce the packaging procedure using the pinned toolchain and
contracts; they do not promise byte-identical APKs across environments.

The signed APK is `app/build/outputs/apk/release/app-release.apk`. Its installed
version metadata is also recorded in
`app/build/outputs/apk/release/output-metadata.json`. Debug output is separate at
`app/build/outputs/apk/debug/app-debug.apk`. Distribute only APKs verified against
the intended fixed signing identity, never build caches or signing material.

Verify either distribution APK using the shared verifier and the independently
approved certificate fingerprint for that variant, supplied as the public
`PINGLIDE_SIGNING_CERT_SHA256` environment variable. Keep the intended variant's
numeric QQ App ID (`PINGLIDE_DEV_QQ_APP_ID` or `PINGLIDE_QQ_APP_ID`) configured
when invoking the verifier:

```powershell
node scripts/distribution/verify-apk.mjs debug app/build/outputs/apk/debug/app-debug.apk
node scripts/distribution/verify-apk.mjs release app/build/outputs/apk/release/app-release.apk
```

The verifier compares the APK with the source version, expected variant package,
English and Chinese launcher names, debuggability, min/target SDK, manifest
cleartext policy and QQ callback scheme, requires
exactly one approved signer and checks page alignment. For manual diagnostics,
the corresponding Android SDK tools are:

```powershell
$buildTools = Join-Path $env:ANDROID_HOME 'build-tools/36.0.0'
& "$buildTools/apksigner.bat" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
& "$buildTools/zipalign.exe" -c -P 16 -v 4 app/build/outputs/apk/release/app-release.apk
& "$buildTools/aapt.exe" dump badging app/build/outputs/apk/release/app-release.apk
```

Repeat with the Debug APK path for Dev distribution. Check the signer certificate
against the intended identity, and confirm the application ID, version, launcher
name, debuggability, cleartext policy, minimum API 29 and target API 36. A valid
signature alone does not establish that the intended key was used.

## Install

On a connected Android 10–16 device or emulator selected with `adb -s SERIAL` when
multiple devices are attached:

```powershell
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell dumpsys package com.buddylabs.pinglide
adb shell am start -n com.buddylabs.pinglide/.app.MainActivity

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell dumpsys package com.buddylabs.pinglide.dev
adb shell am start -n com.buddylabs.pinglide.dev/com.buddylabs.pinglide.app.MainActivity
```

Verify the installed `versionCode` and `versionName` in the package dump. An update
requires the same signing key and a compatible version. A default-key Debug APK
cannot replace a fixed-key Dev APK, and differently signed legacy Debug builds
using the old production application ID cannot be upgraded as Prod. Preserve their
data and use a separate emulator/device for validation; uninstalling deletes local
app data and requires setup/login/Pairing again.

## Acceptance boundary

A signed intermediate scaffold validates packaging capability only. The final
product APK must be rebuilt with the completed implementation slices from #11 and
the approved production key. A temporary validation key must never be used for
distribution or treated as evidence of the production signing identity. Packaging
does not establish QQ, SMS, Windows delivery, physical-device certification, Play
Store publication, deployment or remote CI success.

## Historical release verification (2026-11-09, before variant isolation)

Verified on Windows with the prerequisites above, using an access-restricted
temporary keystore outside the checkout:

- `qualityCheck` passed all four existing static gates.
- `:app:compileReleaseKotlin` and the focused `GeneratedWireTest` passed without
  signing inputs. `:app:assembleDebug :app:assembleRelease` then passed with signing.
- `:app:test` passed the full configured unit suite: 6 tests, no failures or skips.
- Release APK builds rejected missing inputs, a blank alias, a nonexistent keystore,
  an incorrect store password and an absent key alias. Bundle signing also rejected
  the blank alias.
- `apksigner verify` passed with APK Signature Scheme v2; the certificate matched
  the supplied temporary key. `zipalign` passed. `aapt` and output metadata confirmed
  the application ID, version code 1, version name 0.1.0, minimum 29 and target 36.
- Inspection of APK entries and DEX confirmed generated HTTP/WSS classes and no
  test classes, keystore files, supplied signing passwords/path or available
  build-time GitHub credential values.

Installation was not verified: no device was attached, and the existing API 37
emulator image refused to start because another instance held it. No existing
installation or emulator state was replaced. Android 10–16 runtime acceptance
and the approved production signing identity remain unverified.
