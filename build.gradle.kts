import groovy.json.JsonSlurper
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

val openApiGenerator = configurations.create("openApiGenerator")
dependencies {
    add(openApiGenerator.name, libs.openapi.generator)
}

val npmCi = tasks.register<Exec>("npmCi") {
    inputs.files("package.json", "package-lock.json")
    outputs.dir("node_modules")
    if (System.getProperty("os.name").startsWith("Windows")) {
        commandLine("cmd", "/c", "npm", "ci", "--ignore-scripts")
    } else {
        commandLine("npm", "ci", "--ignore-scripts")
    }
}

val contractConfig = file("config/codegen/service-contracts.json")
val contractSource = JsonSlurper().parse(contractConfig) as Map<*, *>
val contractRepository = contractSource["repository"] as String
val contractRelease = contractSource["releaseTag"] as String
val openApiAsset = contractSource["openapi"] as String
val asyncApiAsset = contractSource["asyncapi"] as String
val contractDirectory = layout.buildDirectory.dir(
    "contracts/service/$contractRepository/$contractRelease"
)

val downloadServiceContracts = tasks.register<Exec>("downloadServiceContracts") {
    inputs.file(contractConfig)
    outputs.files(
        contractDirectory.map {
            it.file(openApiAsset)
        },
        contractDirectory.map { it.file(asyncApiAsset) }
    )
    commandLine(
        "gh", "release", "download", contractRelease,
        "--repo", contractRepository,
        "--pattern", openApiAsset,
        "--pattern", asyncApiAsset,
        "--dir", contractDirectory.get().asFile,
        "--clobber"
    )
}

val httpTemplates = layout.buildDirectory.dir("codegen/templates")
val prepareHttpTemplates = tasks.register("prepareHttpTemplates") {
    inputs.files(openApiGenerator)
    inputs.file("config/codegen/http-api-client-header.mustache")
    outputs.dir(httpTemplates)
    doLast {
        val template = ZipFile(openApiGenerator.singleFile).use { archive ->
            archive.getInputStream(
                archive.getEntry(
                    "kotlin-client/libraries/jvm-okhttp/infrastructure/ApiClient.kt.mustache"
                )
            ).bufferedReader().use { it.readText() }
        }
        val output = httpTemplates.get().file("infrastructure/ApiClient.kt.mustache").asFile
        output.parentFile.mkdirs()
        output.writeText(
            file("config/codegen/http-api-client-header.mustache").readText() + template
        )
    }
}

val generateServiceHttp = tasks.register<JavaExec>("generateServiceHttp") {
    dependsOn(downloadServiceContracts, prepareHttpTemplates)
    classpath = openApiGenerator
    mainClass.set("org.openapitools.codegen.OpenAPIGenerator")
    inputs.file(contractConfig)
    inputs.file(contractDirectory.map { it.file(openApiAsset) })
    inputs.file("config/codegen/openapi.json")
    inputs.dir(httpTemplates)
    inputs.property("generatorVersion", libs.versions.openapi.generator.get())
    val output = layout.buildDirectory.dir("generated/service/http")
    outputs.dir(output)
    args(
        "generate", "-g", "kotlin",
        "-i", contractDirectory.get().file(openApiAsset).asFile,
        "-c", file("config/codegen/openapi.json"),
        "-t", httpTemplates.get().asFile,
        "-o", output.get().asFile
    )
    doFirst {
        val target = output.get().asFile.canonicalFile
        check(target.toPath().startsWith(layout.buildDirectory.get().asFile.canonicalFile.toPath()))
        delete(target)
    }
    doLast {
        for (source in listOf(
            "api/SystemApi.kt",
            "model/CredentialBundle.kt",
            "model/UploadResponse.kt",
            "infrastructure/Serializer.kt"
        )) {
            val generated = output.get().asFile.resolve(
                "src/main/kotlin/com/buddylabs/pinglide/generated/http/$source"
            )
            check(generated.isFile && generated.length() > 0) {
                "HTTP generation produced missing/empty source: $source"
            }
        }
    }
}

val generateServiceWss = tasks.register<Exec>("generateServiceWss") {
    dependsOn(downloadServiceContracts, npmCi)
    inputs.file(contractConfig)
    inputs.file(contractDirectory.map { it.file(asyncApiAsset) })
    inputs.dir("scripts/codegen")
    inputs.file("package-lock.json")
    outputs.dir(layout.buildDirectory.dir("generated/service/wss"))
    commandLine("node", "scripts/codegen/generate-wss.mjs")
}

tasks.register("generateServiceClients") {
    dependsOn(generateServiceHttp, generateServiceWss)
}

spotless {
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    kotlin {
        target("app/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
    kotlinGradle {
        target("*.gradle.kts", "app/*.gradle.kts", "gradle/**/*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
}

detekt {
    toolVersion = libs.versions.detekt.get()
    config.setFrom(files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = false
    source.setFrom(
        fileTree(rootDir) {
            include("*.gradle.kts", "app/**/*.kt", "app/**/*.gradle.kts", "gradle/**/*.gradle.kts")
            exclude("**/build/**", "**/.gradle/**")
        }
    )
}

tasks.register<Exec>("checkFileSize") {
    group = "verification"
    description = "Checks the 500-physical-line limit for handwritten Kotlin and Gradle scripts."
    commandLine("node", "scripts/quality/check-file-size.mjs")
}

tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs the four agreed static checks."
    dependsOn("spotlessCheck", "checkFileSize", "detekt", ":app:lintDebug", ":app:lintRelease")
}

tasks.named("check") {
    dependsOn("qualityCheck")
}
