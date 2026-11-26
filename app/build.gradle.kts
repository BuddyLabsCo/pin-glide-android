import com.android.build.api.dsl.ApplicationBuildType
import java.net.URI
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.serialization)
}

fun signingInputs(prefix: String) = listOf(
    "STORE_FILE",
    "STORE_PASSWORD",
    "KEY_ALIAS",
    "KEY_PASSWORD"
).associateWith { providers.environmentVariable("${prefix}SIGNING_$it").orNull }

fun serviceOrigin(value: String, debug: Boolean): String {
    if (value.isEmpty()) return value
    val origin = runCatching { URI(value) }.getOrNull()
    val schemes = if (debug) setOf("http", "https") else setOf("https")
    require(
        origin != null && origin.scheme in schemes && origin.host != null &&
            (origin.port == -1 || origin.port in 1..65535) &&
            '%' !in origin.host && !origin.rawAuthority.endsWith(
                ":"
            ) && origin.rawUserInfo == null &&
            origin.rawPath in setOf("", "/") && origin.rawQuery == null &&
            origin.rawFragment == null
    ) {
        "Service URL must be a valid ${schemes.joinToString("/")} origin without credentials, " +
            "non-root paths, query parameters or fragments"
    }
    return value.removeSuffix("/")
}

fun qqAppId(prefix: String): String {
    val value = providers.environmentVariable("${prefix}QQ_APP_ID").orElse("").get()
    require(value.all { it in '0'..'9' }) { "QQ App ID must contain only digits" }
    return value
}

val devSigningInputs = signingInputs("PINGLIDE_DEV_")
val releaseSigningInputs = signingInputs("PINGLIDE_")
if ((devSigningInputs.values + releaseSigningInputs.values).any { it != null }) {
    check(
        !gradle.startParameter.isConfigurationCacheRequested &&
            !gradle.startParameter.isBuildCacheEnabled
    ) {
        "Signing inputs require --no-configuration-cache --no-build-cache."
    }
}
val devDistribution =
    providers.gradleProperty("pinglide.devDistribution").orNull?.toBooleanStrict() ?: false
val devQqAppId = qqAppId("PINGLIDE_DEV_")
val releaseQqAppId = qqAppId("PINGLIDE_")
val devServiceUrl = serviceOrigin(
    providers.environmentVariable("PINGLIDE_DEV_SERVICE_URL").orElse("").get(),
    debug = true
)
val releaseServiceUrl = serviceOrigin(
    providers.environmentVariable("PINGLIDE_SERVICE_URL").orElse("").get(),
    debug = false
)
val fixedDevSigning = devSigningInputs.values.none { it.isNullOrBlank() }

fun ApplicationBuildType.serviceConfiguration(qqAppId: String, serviceUrl: String) {
    buildConfigField("String", "QQ_APP_ID", "\"$qqAppId\"")
    buildConfigField("String", "SERVICE_URL", "\"$serviceUrl\"")
    manifestPlaceholders["qqAuthScheme"] = "tencent${qqAppId.ifEmpty { "unconfigured" }}"
}

android {
    namespace = "com.buddylabs.pinglide"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.buddylabs.pinglide"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseSigningInputs["STORE_FILE"]?.let { file(it) }
            storePassword = releaseSigningInputs["STORE_PASSWORD"]
            keyAlias = releaseSigningInputs["KEY_ALIAS"]
            keyPassword = releaseSigningInputs["KEY_PASSWORD"]
        }
        if (fixedDevSigning) {
            create("dev") {
                storeFile = file(checkNotNull(devSigningInputs["STORE_FILE"]))
                storePassword = devSigningInputs["STORE_PASSWORD"]
                keyAlias = devSigningInputs["KEY_ALIAS"]
                keyPassword = devSigningInputs["KEY_PASSWORD"]
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            serviceConfiguration(devQqAppId, devServiceUrl)
            if (fixedDevSigning) signingConfig = signingConfigs.getByName("dev")
        }
        release {
            serviceConfiguration(releaseQqAppId, releaseServiceUrl)
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].kotlin {
        directories.add(
            rootProject.layout.buildDirectory.dir(
                "generated/service/http/src/main/kotlin"
            ).get().asFile.path
        )
        directories.add(
            rootProject.layout.buildDirectory.dir("generated/service/wss").get().asFile.path
        )
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        checkTestSources = true
        lintConfig = rootProject.file("config/lint/lint.xml")
    }
}

tasks.named("preBuild") {
    dependsOn(rootProject.tasks.named("generateServiceClients"))
}

fun validateDistribution(
    prefix: String,
    signing: Map<String, String?>,
    serviceUrl: String,
    qqAppId: String
) {
    check(serviceUrl.isNotBlank() && qqAppId.isNotBlank()) {
        "Distribution requires ${prefix}SERVICE_URL and ${prefix}QQ_APP_ID. See docs/release.md."
    }
    val missing = signing.filterValues { it.isNullOrBlank() }.keys
    check(missing.isEmpty()) {
        "Missing signing inputs: ${missing.joinToString {
            "${prefix}SIGNING_$it"
        }}. See docs/release.md."
    }
}

val validateDebugPackaging = tasks.register("validateDebugPackaging") {
    doLast {
        if (devDistribution || devSigningInputs.values.any { it != null }) {
            validateDistribution("PINGLIDE_DEV_", devSigningInputs, devServiceUrl, devQqAppId)
        }
    }
}
val validateReleasePackaging = tasks.register("validateReleasePackaging") {
    doLast {
        validateDistribution("PINGLIDE_", releaseSigningInputs, releaseServiceUrl, releaseQqAppId)
    }
}
tasks.matching { it.name == "packageDebug" || it.name == "signDebugBundle" }.configureEach {
    dependsOn(validateDebugPackaging)
}
tasks.matching { it.name == "packageRelease" || it.name == "signReleaseBundle" }.configureEach {
    dependsOn(validateReleasePackaging)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation(
        files(rootProject.layout.buildDirectory.file("dependencies/qq/open_sdk_3.5.19_lite.jar")) {
            builtBy(rootProject.tasks.named("downloadQqSdk"))
        }
    )
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.okhttp)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)
    implementation(libs.bouncycastle)
    implementation(libs.camera.view)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.mlkit)
    implementation(libs.barcode.scanning)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
