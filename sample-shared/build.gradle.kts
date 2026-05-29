import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Shared, resource-driven slice of the Android sample. Kept as a Kotlin
// Multiplatform library (`com.android.kotlin.multiplatform.library`) — NOT a
// `com.android.application` — so it can own a `commonMain/composeResources/`
// tree and have the Compose Multiplatform Resources plugin generate the typed
// `Res` accessor that `ComposeResourcesDemo` feeds into `pdfAsync { drawable(...) }`.
// The `:sample` app module (plain `com.android.application`) depends on this and
// calls `ComposeResourcesDemo.build()`. Splitting the Compose-Resources code out
// here is what lets `:sample` drop the KMP plugin entirely, which AGP 9 requires
// (KMP + `com.android.application` in one module is no longer allowed).
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)

    android {
        // Distinct from the app's `com.conamobile.pdfkmp.sample` namespace —
        // an Android library and the app that consumes it must not share one.
        namespace = "com.conamobile.pdfkmp.sample.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":pdfkmp"))
            // Brings the `drawable(...)` / `vector(...)` DSL and, transitively,
            // `components-resources` — the type the generated `Res` references.
            implementation(project(":pdfkmp-compose-resources"))

            // No @Composable lives here, but the Compose compiler plugin (applied
            // for Compose-Resources `Res` generation) refuses to run unless the
            // Compose runtime is on the classpath — declare it explicitly.
            implementation(libs.compose.runtime)

            implementation(libs.coroutines.core)
        }
    }
}

// Generate the typed `Res` accessor for `commonMain/composeResources/`. The
// package matches `ComposeResourcesDemo`'s imports; `publicResClass = false`
// keeps `Res` internal to this module — only the (public) `ComposeResourcesDemo`
// crosses the module boundary into the app.
compose.resources {
    publicResClass = false
    packageOfResClass = "com.conamobile.pdfkmp.sample.generated.resources"
    generateResClass = always
}
