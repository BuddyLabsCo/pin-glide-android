# Android static quality checks: research and proposed adoption

Date: 2026-11-01.

Status: research input to the open HITL architecture decision, not an adopted
architecture or a working build configuration.

## Scope and evidence

The current Android worktree has product requirements and a static UI prototype,
but no production Kotlin sources, Gradle Wrapper, AGP configuration, or selected
Kotlin/Compose dependency versions. The product uses Kotlin, Compose Material 3,
Android SMS/background integrations, QQ Connect, Keystore-protected keys, service
HTTP and Pairing WSS clients, and direct Bailian HTTPS requests. Product support
is Android 10-16; Android 10 corresponds to API 29.

This investigation read primary tool documentation and source. It did not build
the app, run the proposed checks, install dependencies, or establish a compatible
toolchain. The existing uncommitted product/UI work was not copied or edited.

Built-in web requests returned empty output in this session; readable primary
pages were obtained through the prescribed Anysearch fallback. Konsist source
was also inspected through the first-party GitHub repository API.

## Smallest useful initial set

The following rows are recommendations, not upstream defaults.

| Gate | Initial role | Proposed configuration and command |
| --- | --- | --- |
| Android Lint, including the checks shipped by Compose | Android API/permission/resource/manifest correctness, Compose correctness, accessibility and localization diagnostics | `lint.xml`, `app/build.gradle.kts`; `./gradlew :app:lintDebug :app:lintRelease` |
| Spotless with a pinned ktlint engine | One formatting authority for handwritten Kotlin and Gradle Kotlin DSL | `.editorconfig`, root `build.gradle.kts`; `./gradlew spotlessCheck`, local fix with `./gradlew spotlessApply` |
| Package architecture assertions | Check the agreed package dependency table and forbidden platform/UI/network/storage imports | `app/src/test/kotlin/.../architecture/ArchitectureTest.kt`; `./gradlew :app:testDebugUnitTest --tests '*ArchitectureTest'` |
| Handwritten file-size check | Reject files over 500 physical lines, following the service repository's chosen limit | One small proposed root Gradle task `checkFileSize`; no new third-party analyzer |
| Kotlin/Compose compilation | Check actual compiler, plugin and generated-runtime compatibility | `./gradlew :app:compileDebugKotlin :app:compileReleaseKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin` |
| Contract input/generation check | Check pinned input provenance, reproducible nonempty output, then compile it | Proposed root tasks `checkContracts` and `checkGenerated`; task names are project choices |

All task names above assume a later single `:app` module with `debug` and
`release` variants. Custom tasks and test classes do not exist yet. Windows uses
`.\gradlew.bat` in place of `./gradlew`.

Compilation is a compiler gate. Architecture assertions execute on the JVM but
inspect source structure. Contract serialization tests execute code. Keep these
distinctions clear rather than calling every check "lint".

## Android Lint and Compose

Android Lint inspects project sources and resources without running the app.
Its documented coverage includes correctness, security, performance,
accessibility and internationalization. Gradle's ordinary lint task checks the
default variant; other variants need explicit variant tasks. Android documents
that lint must be invoked explicitly in CI rather than inferred from an APK
build. Local editor diagnostics are useful, but are not the merge gate. [S1]

Compose already ships lint checks. The official compatibility table says
Compose 1.9 requires AGP/Lint 8.8.2 or newer. This is a minimum for that Compose
line, not a recommendation to pin the entire future project to those versions.
First-party Compose lint and successful compilation should precede an additional
Compose rule plugin. [S2]

Proposed policy: keep default error/fatal findings blocking, review warnings,
and promote project-relevant security/correctness findings after inspecting the
first report. Check manifest, permission/API usage, resource translations and
Compose state/effect/modifier diagnostics supported by the selected versions.
Do not claim lint proves the short foreground service can start on every
supported device or that a UI remains accessible at all font scales.

Start without a baseline because production code does not yet exist. Any later
suppression should identify a narrow issue and location, with a reviewable
reason. Do not globally disable categories or use a baseline to admit fresh
violations. Include handwritten test sources where supported/configured.

## Formatting: choose one ktlint integration

Spotless supports `spotlessCheck` and `spotlessApply`, explicit targets/excludes,
Kotlin/Gradle Kotlin DSL, an explicitly versioned ktlint engine, and
`.editorconfig`. Its Android documentation explicitly warns that sources cannot
be inferred automatically; configure targets rather than trusting a green task
that saw no sources. [S3]

Proposed targets are handwritten `app/src/*/kotlin/**/*.kt`,
`app/src/*/java/**/*.kt`, and owned Gradle Kotlin scripts. Exclude build output,
contract generator output, third-party code and UI prototype assets. Format
tests as well as production code. Scope exclusions by actual generated paths,
not a broad package that could hide handwritten adapters.

Do not install Spotless/ktlint plus another ktlint Gradle wrapper plus detekt's
formatting wrapper. They repeat the same job and complicate version/suppression
ownership. CI checks format without rewriting files. Pin Spotless and ktlint
separately; select Compose-compatible naming/style configuration during the
compatibility trial.

## detekt: useful, but compatibility is an adoption prerequisite

The currently published compatibility table distinguishes stable detekt
`1.23.8`, built with Kotlin `2.0.21`, Gradle `8.12.1` and AGP `8.8.1`, from
`2.0.0-alpha.6`, built with Kotlin `2.4.10`, Gradle `9.6.1` and AGP `9.3.1`.
These are upstream build/test alignment points, not proof that every other
combination fails or that this application's toolchain is selected. [S4]

The Gradle integration offers ordinary and type-aware/variant tasks. Type
resolution requires the appropriate predefined task or an explicitly populated
classpath and JVM target. detekt is coupled to its compiler runtime; globally
forcing Kotlin dependency versions can break the analyzer. [S5]

Recommendation: detekt is the preferred next analyzer after the initial gates.
Its distinct job would be complexity, suspicious code, exception handling and
coroutine rules available in the pinned version; it does not own formatting.
Do not hold the application on an old Kotlin/AGP stack solely for stable detekt.
Do not silently make a prerelease analyzer mandatory either.

Before accepting detekt, use a small disposable fixture on the selected
application toolchain. Confirm production/test/variant sources are discovered,
generated sources are excluded, a deliberately invalid fixture produces the
expected failure, and rules requiring resolution actually resolve types.
Only then pin the plugin/engine and document the actual tasks, rule severities
and configuration at `config/detekt/detekt.yml`. A proposed
`:app:detektDebug` task must be verified on the pinned plugin, not assumed.

If the matching supported toolchain requires detekt 2 alpha, accepting that
prerelease and its maintenance cost is a separate human decision. Otherwise
retain the initial gates and explicitly record the temporary missing analysis.

## Optional Compose rules

The compose-rules project offers detekt plugins for modifier contracts,
remember/effects, composable parameters, Material 2 restrictions and other
Compose conventions. Its own matrix maps plugin lines to specific detekt and
Kotlin syntax versions; the fetched documentation lists `0.4.23` for
detekt `1.23.8`/Kotlin `2.0.21`, and `0.5.0+` for detekt `2.0.0-alpha.1`/
Kotlin `2.2.21`. Do not infer compatibility with a newer detekt alpha from the
`+` notation without testing the specific release. [S6]

Defer this plugin until first-party lint leaves a demonstrated gap and detekt
compatibility is settled. Add a small subset of useful rules, avoiding duplicate
diagnostics and broad stylistic constraints. Do not assert "unstable collection"
warnings prove measured recomposition slowness.

## Architecture checks and their limits

Konsist's official implementation provides layer dependency assertions and
checks for empty and overlapping layer definitions. The inspected source
explicitly states that imports inside a file's own layer are not checked.
That makes layer scope/coverage a part of the gate's correctness. [S7]

A single `:app` project can use source assertions for the agreed package layout:
pure business packages must not import Android/Compose, concrete networking,
serialization or persistence implementations; UI packages must not import
credential/key stores or generated HTTP/WSS types directly; generated types
belong behind handwritten transport mapping. Define permitted inward imports
and prohibit dependency cycles in the packages under test.

Use Konsist if its selected version parses the selected Kotlin syntax and the
required assertions are concise. A focused existing static-analysis facility is
also acceptable; do not add both just to assert the same imports.

Source/package assertions are not compiler isolation. Reflection, generated
code, inline fully qualified references, transitive types, same-package use and
runtime dependency injection need explicit coverage or reviewer attention; do
not claim an import-oriented rule catches all of them. The initial check must
prove a deliberately forbidden `domain -> android.content.Context` fixture and
a `ui -> credentials store` fixture fail, and prove all handwritten production
files belong to a checked scope. If stronger isolation becomes necessary, a
plain Kotlin Gradle module provides a different guarantee by excluding Android
dependencies from its compile classpath. Do not pre-split the app just for a
diagram.

## Generation and scope

The service owns wire definitions. Android already requires a pinned service
release's `openapi.yaml` and `asyncapi.yaml`, OpenAPI Generator's Kotlin client,
and AsyncAPI parsing followed by Modelina Kotlin models. A successful generator
exit alone previously produced no WSS models; the existing local investigation
records that failure and the adopted parser-first correction. [R1]

The proposed gate must verify both exact release assets, hashes/provenance,
matching independent contract versions, resolved references, pinned generator/
parser/templates/runtime versions and a nonempty expected Kotlin model set.
Regenerate into a clean temporary output and compare the expected files.
Whether generated files are committed remains an architecture decision; when
build-only, compare deterministic manifests rather than a nonexistent committed
tree.

Exclude generated sources from handwritten formatting, complexity and file-size
limits. Always compile them. Do not hand-edit generated files to satisfy lint.
Retain applicable Lint diagnostics on the integrated application and narrowly
document generator-owned style exceptions; generated compilation, serializer
round trips and contract fixture tests remain mandatory evidence.

Fixture tests must eventually cover optional/default/null values, unknown
optional fields, mixed indexed HTTP 207 results, Problem Details/BusinessError,
version/type rejection and correlation. These are executable tests beyond
pure static validation. Crypto interoperability and lifecycle/storage behavior
need their own tests; source scanners cannot replace them.

## File-size and dependency reproducibility

The 500-physical-line limit is a proposed project policy, not an Android
standard. Count blank/comment lines consistently, include handwritten tests and
owned build code, and exempt only generated/vendor outputs explicitly. Do not
equate detekt's different size metrics with that policy. One small check should
name the path and observed count on failure, with a self-check for counting and
exclusion behavior.

Gradle dependency locks record resolved direct/transitive versions for
configurations that are actually resolved. Lock files belong in source control;
lock generation is a deliberate update operation, not part of ordinary CI.
Resolving only the root project's `dependencies` task can miss the app project's
configurations. [S8]

Dependency verification uses `gradle/verification-metadata.xml` to check downloaded
artifacts/plugins and supports checksums/signatures. It checks integrity/
provenance, not whether an otherwise legitimate dependency is vulnerable.
Bootstrapping records whatever is downloaded, so review new verification entries
rather than automatically trusting regenerated metadata. [S9]

Use the platform's existing locking/verification rather than an additional
dependency framework. Keep SDK credentials, signing secrets and contract-download
tokens in development/CI secret storage. A secret scanner can be added using an
existing organization policy; it does not prove SMS/OTP never reach logs.

## Proposed invocation and enforcement

After the tasks exist, CI's static job should explicitly run full owned-source
format checks, both relevant Lint variants, architecture assertions, file-size
and contract-generation gates. Compiler and unit-test jobs remain visible as
such. Do not replace whole-module Lint with only changed-file scans: changed
resources, manifests and dependency graphs can affect other sources.

On a new repository, use no blanket legacy baseline. For accepted analyzer
findings, fail rather than silently ignoring failures. Review per-rule
suppressions, source-exclusion changes and tool upgrades like code changes.
Upload readable reports. CI must fail when a required gate sees zero sources
unexpectedly, generated output is empty, or the configured analyzer/task is
missing.

Do not add SonarQube, Java Checkstyle/PMD/SpotBugs, duplicate formatters, mandatory
third-party Compose rules, blanket metric quotas or a device suite to every
format-only change without a demonstrated need. Physical-device SMS, QQ,
foreground-service startup, Keystore/HPKE interoperability, migrations and
accessibility remain separate acceptance evidence.

## Primary sources

- [S1: Android Lint](https://developer.android.com/studio/write/lint),
  fetched 2026-11-01; page updated 2026-10-28.
- [S2: Compose lint compatibility](https://developer.android.com/develop/ui/compose/tooling/lint),
  fetched 2026-11-01; page updated 2026-10-28.
- [S3: Spotless Gradle documentation](https://github.com/diffplug/spotless/blob/main/plugin-gradle/README.md),
  fetched 2026-11-01; mutable main documentation, not a version pin.
- [S4: detekt compatibility table](https://detekt.dev/docs/introduction/compatibility/),
  fetched 2026-11-01; current documentation version 2.0.0-alpha.6.
- [S5: detekt Gradle integration](https://detekt.dev/docs/gettingstarted/gradle/),
  fetched 2026-11-01; current documentation version 2.0.0-alpha.6.
- [S6: compose-rules detekt integration](https://mrmans0n.github.io/compose-rules/detekt/),
  fetched 2026-11-01; matrix may trail release artifacts.
- [S7: Konsist layer dependency implementation](https://github.com/LemonAppDev/konsist/blob/57d3f3d68849bd65670517fe9174e0bff92ee38d/lib/src/main/kotlin/com/lemonappdev/konsist/core/architecture/LayerDependenciesCore.kt),
  GitHub API source and main commit inspected 2026-11-01.
- [S8: Gradle dependency locking](https://docs.gradle.org/current/userguide/dependency_locking.html),
  fetched 2026-11-01; fetched current manual identifies Gradle 9.8.2.
- [S9: Gradle dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html),
  fetched 2026-11-01; fetched current manual identifies Gradle 9.8.2.
- [R1: existing Android contract investigation](service-contract-conflicts-2026-10-21.md),
  especially the Modelina empty-output reproduction and resolution update.

## Remaining decision

Agree to the initial set, then settle the actual JDK/Gradle/AGP/Kotlin/Compose
tuple and test tool compatibility before making exact pins normative in root
`code-architecture.md`. Decide the handwritten layout, generated-file ownership
and storage-evolution policy in the same open architecture ticket. No agreement
or completed gate is implied by this research branch.
