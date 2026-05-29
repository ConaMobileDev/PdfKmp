// Pure Android application. AGP 9 forbids applying the Kotlin Multiplatform
// plugin alongside `com.android.application`, so the resource-driven, common
// slice of this sample lives in the `:sample-shared` KMP library and the app
// just depends on it. Under AGP 9 defaults (newDsl=true, builtInKotlin=true)
// `com.android.application` compiles Kotlin itself — do NOT add the standalone
// `org.jetbrains.kotlin.android` plugin; it clashes with built-in Kotlin.
plugins {
    alias(libs.plugins.androidApplication)

    // Compose compiler — still required on every Compose module; built-in
    // Kotlin replaces only the kotlin-android plugin, never this one.
    alias(libs.plugins.compose.compiler)

    // Supplies the `compose {}` extension and the org.jetbrains.compose.*
    // dependency wiring this app's UI consumes.
    alias(libs.plugins.composeMultiplatform)
}

android {
    namespace = "com.conamobile.pdfkmp.sample"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.conamobile.pdfkmp.sample"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    // Required for Compose tooling integration even though the Compose compiler
    // itself is wired by the `compose.compiler` plugin above.
    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

dependencies {
    // The KMP library that owns the Compose-Resources demo + generated `Res`.
    implementation(project(":sample-shared"))
    // PdfDocument + the bundled Samples.* documents the list renders.
    implementation(project(":pdfkmp"))
    // KmpPdfViewer / KmpPdfLauncher / PdfViewerTopBar — the integration point.
    implementation(project(":pdfkmp-viewer"))

    implementation(libs.androidx.activityCompose)

    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)

    implementation(libs.coroutines.android)

    debugImplementation(libs.compose.ui.tooling)
}
