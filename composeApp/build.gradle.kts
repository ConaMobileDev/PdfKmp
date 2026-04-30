import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinx.serialization)
}

kotlin {
    jvmToolchain(17)

    androidLibrary {
        namespace = "com.conamobile.romchi2.shared"
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

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            export(libs.kmpnotifier)
            export(project(":features:auth"))

            baseName = "ComposeApp"
            isStatic = true
            freeCompilerArgs += "-Xbinary=bundleId=com.blink.romchi"
        }
    }

    sourceSets {

        commonMain.dependencies {
            api(projects.features.auth)
            api(projects.core.remote)
            implementation(projects.features.orders)
            implementation(projects.ui.component)
            implementation(projects.data.repository)
            implementation(projects.features.home)
            implementation(projects.features.newOrder)
            implementation(projects.features.profile)
            implementation(projects.features.employees)
            implementation(projects.features.prices)
            implementation(projects.features.payment)
            implementation(projects.features.downloader)
            implementation(projects.features.dillersLoc)
            implementation(projects.ui.designsystem)
            implementation(projects.core.store)
            implementation(projects.core.analytics)
            implementation(projects.data.model)
            implementation(projects.navigation)
            implementation(projects.core.database)
            implementation(projects.core.datetime)
            implementation(projects.backendKmp)
            implementation(projects.core.connection)
            implementation(projects.core.sync)
            implementation(projects.core.cache)
            implementation(projects.features.company)
            implementation(projects.platform)
            implementation(projects.mapController)
            implementation(projects.features.warehouse)
            implementation(projects.features.aiChat)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.components.uiToolingPreview)
            implementation(libs.compose.navigation)

            api(libs.koin.compose)
            api(libs.koin.core)
            implementation(libs.koin.viewmodel)
            implementation(libs.koin.viewmodel.navigation)
            api(libs.napier)

            api(libs.kmpnotifier)
        }
    }
}
