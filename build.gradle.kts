import groovy.json.JsonSlurper
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
}

val openApiGenerator = configurations.create("openApiGenerator")
val qqSdk = layout.buildDirectory.file("dependencies/qq/open_sdk_3.5.19_lite.jar")
tasks.register("downloadQqSdk") {
    val sdkUrl = "https://tangram-1251316161.file.myqcloud.com/files/20260414/" +
        "143310b667ece8922594fbe866dabdef.zip"
    inputs.property("url", sdkUrl)
    outputs.file(qqSdk)
    doLast {
        val archive = layout.buildDirectory.file("dependencies/qq/sdk.zip").get().asFile
        archive.parentFile.mkdirs()
        URI(sdkUrl).toURL().openStream().use { input ->
            archive.outputStream().use { input.copyTo(it) }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(archive.readBytes())
            .joinToString("") { "%02x".format(it) }
        check(digest == "6272d4123d8327400a655c48f1fb7eb24b83fd5beeb527951ed100b219192b9b") {
            "QQ SDK archive checksum mismatch"
        }
        ZipFile(archive).use { zip ->
            zip.getInputStream(zip.getEntry("open_sdk_3.5.19_r9483ffc7_lite.jar")).use { input ->
                qqSdk.get().asFile.outputStream().use { prepareQqSdk(input, it) }
            }
        }
    }
}
fun prepareQqSdk(input: InputStream, output: OutputStream) {
    ZipInputStream(input).use { source ->
        ZipOutputStream(output).use { target ->
            var entry = source.nextEntry
            while (entry != null) {
                target.putNextEntry(ZipEntry(entry.name))
                val bytes = source.readBytes()
                target.write(
                    when (entry.name) {
                        "com/tencent/open/log/SLog.class" -> prepareQqClass(bytes, session = false)

                        "com/tencent/connect/auth/QQToken.class" -> prepareQqClass(
                            bytes,
                            session = true
                        )

                        else -> bytes
                    }
                )
                target.closeEntry()
                entry = source.nextEntry
            }
        }
    }
}

fun prepareQqClass(bytes: ByteArray, session: Boolean): ByteArray {
    val output = ClassWriter(ClassWriter.COMPUTE_MAXS)
    var removed = 0
    ClassReader(bytes).accept(
        object : ClassVisitor(Opcodes.ASM9, output) {
            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?
            ): MethodVisitor? {
                val method = super.visitMethod(access, name, descriptor, signature, exceptions)
                if (session) {
                    if (name != "saveSession") return method
                    check(descriptor == "(Lorg/json/JSONObject;)Z")
                } else {
                    if (name !in listOf("v", "d", "i", "w", "e", "u")) return method
                    check(access and Opcodes.ACC_STATIC != 0 && descriptor.endsWith(")V"))
                }
                method.visitCode()
                if (session) method.visitInsn(Opcodes.ICONST_1)
                method.visitInsn(if (session) Opcodes.IRETURN else Opcodes.RETURN)
                method.visitMaxs(0, 0)
                method.visitEnd()
                removed++
                return null
            }
        },
        0
    )
    check(removed == if (session) 1 else 12) { "QQ SDK API changed; review privacy preparation" }
    return output.toByteArray()
}
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
