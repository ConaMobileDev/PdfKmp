import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.mavenPublish)
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

kotlin {
    jvmToolchain(17)

    explicitApi()

    android {
        namespace = "com.conamobile.pdfkmp.viewer"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        // KMP Android library modules disable resource processing by
        // default (unlike `com.android.library`). Turn it on so the
        // FileProvider's `res/xml/pdfkmp_viewer_file_paths.xml` gets
        // packaged into the AAR — without it, AAPT fails to resolve
        // `@xml/pdfkmp_viewer_file_paths` referenced from the manifest.
        androidResources {
            enable = true
        }

        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
        }
    }

    // JVM / Desktop target (macOS, Windows, Linux). Compose for Desktop
    // hosts the viewer UI; PdfBox's PDFRenderer rasterises pages for the
    // on-screen preview, mirroring Android's PdfRenderer and iOS's PDFKit.
    jvm()

    // iosX64 dropped in 1.1.0 — Compose Multiplatform 1.11.0 removed the
    // Intel-Mac-simulator target from its own modules (this module depends on
    // Compose), so it can no longer be published here either.
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = "PdfKmpViewer"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":pdfkmp"))

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.material3)

            implementation(libs.coroutines.core)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core)
            implementation(libs.androidx.startup.runtime)
            // KmpPdfLauncher hosts KmpPdfViewer in an internal
            // `ComponentActivity`; activity-compose pulls in the
            // `setContent` extension and edge-to-edge helpers.
            implementation(libs.androidx.activityCompose)
            implementation(libs.coroutines.android)
        }

        jvmMain.dependencies {
            // Rasterises PDF pages to BufferedImage for the Desktop preview.
            implementation(libs.pdfbox)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }
}

mavenPublishing {
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    pom {
        name.set(providers.gradleProperty("POM_NAME"))
        description.set(providers.gradleProperty("POM_DESCRIPTION"))
        url.set(providers.gradleProperty("POM_URL"))
        inceptionYear.set(providers.gradleProperty("POM_INCEPTION_YEAR"))

        licenses {
            license {
                name.set(providers.gradleProperty("POM_LICENSE_NAME"))
                url.set(providers.gradleProperty("POM_LICENSE_URL"))
                distribution.set(providers.gradleProperty("POM_LICENSE_DIST"))
            }
        }
        developers {
            developer {
                id.set(providers.gradleProperty("POM_DEVELOPER_ID"))
                name.set(providers.gradleProperty("POM_DEVELOPER_NAME"))
                url.set(providers.gradleProperty("POM_DEVELOPER_URL"))
            }
        }
        scm {
            url.set(providers.gradleProperty("POM_SCM_URL"))
            connection.set(providers.gradleProperty("POM_SCM_CONNECTION"))
            developerConnection.set(providers.gradleProperty("POM_SCM_DEV_CONNECTION"))
        }
    }
}
