import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(17)

    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":pdfkmp"))
            implementation(project(":pdfkmp-viewer"))

            // Compose for Desktop — pulls in the Skiko native libs for the
            // current OS plus the runtime/foundation/ui/material3 desktop
            // artifacts and the `application { Window { … } }` host APIs.
            implementation(compose.desktop.currentOs)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)

            implementation(libs.coroutines.core)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.conamobile.pdfkmp.sampledesktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "PdfKmpDesktopSample"
            packageVersion = "1.0.0"
        }
    }
}
