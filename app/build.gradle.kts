import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.serialization)
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
    }

    buildFeatures {
        compose = true
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

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}

dependencies {
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
