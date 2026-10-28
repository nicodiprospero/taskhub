import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9 compiles Kotlin itself (built-in Kotlin) — no kotlin-android plugin.
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val glyphSdk = file("libs/GlyphMatrixSDK.aar")
if (!glyphSdk.exists()) {
    throw GradleException(
        "Missing ${glyphSdk.path}. Nothing's licence forbids redistributing the Glyph Matrix SDK, so it is " +
            "not in the repo. Download glyph-matrix-sdk-2.0.aar and copy it there — see app/libs/README.md.",
    )
}

android {
    namespace = "dev.nicodiprospero.glyphwave"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.nicodiprospero.glyphwave"
        minSdk = 34
        targetSdk = 37
        versionCode = 3
        versionName = "0.2.0-phase2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    implementation(files(glyphSdk))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
