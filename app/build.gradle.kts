import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.serialization)
}

val releaseSigningInputs = listOf(
    "PINGLIDE_SIGNING_STORE_FILE",
    "PINGLIDE_SIGNING_STORE_PASSWORD",
    "PINGLIDE_SIGNING_KEY_ALIAS",
    "PINGLIDE_SIGNING_KEY_PASSWORD"
).associateWith { providers.environmentVariable(it).orNull }
val missingReleaseSigningInputs = releaseSigningInputs.filterValues { it.isNullOrBlank() }.keys
val qqAppId = providers.environmentVariable("PINGLIDE_QQ_APP_ID").orElse("").get()
val serviceUrl = providers.environmentVariable("PINGLIDE_SERVICE_URL").orElse("").get()
require(
    qqAppId.isEmpty() || qqAppId.all {
        it in '0'..'9'
    }
) { "QQ App ID must contain only digits" }
require(serviceUrl.isEmpty() || serviceUrl.matches(Regex("https://[a-zA-Z0-9.:-]+"))) {
    "Service URL must be an HTTPS origin without credentials, paths or query parameters"
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
        buildConfigField("String", "QQ_APP_ID", "\"$qqAppId\"")
        buildConfigField("String", "SERVICE_URL", "\"$serviceUrl\"")
        manifestPlaceholders["qqAuthScheme"] = "tencent${qqAppId.ifEmpty { "unconfigured" }}"
    }

    signingConfigs {
        create("release") {
            storeFile = releaseSigningInputs["PINGLIDE_SIGNING_STORE_FILE"]?.let { file(it) }
            storePassword = releaseSigningInputs["PINGLIDE_SIGNING_STORE_PASSWORD"]
            keyAlias = releaseSigningInputs["PINGLIDE_SIGNING_KEY_ALIAS"]
            keyPassword = releaseSigningInputs["PINGLIDE_SIGNING_KEY_PASSWORD"]
        }
    }

    buildTypes {
        release {
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

tasks.matching { it.name == "packageRelease" || it.name == "signReleaseBundle" }.configureEach {
    doFirst {
        check(missingReleaseSigningInputs.isEmpty()) {
            "Missing release signing inputs: ${missingReleaseSigningInputs.joinToString()}. " +
                "See docs/release.md."
        }
    }
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
    testImplementation(libs.junit)
}
