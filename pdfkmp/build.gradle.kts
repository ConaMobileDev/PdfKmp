import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.dokka)
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

/**
 * Embeds every TTF in `pdfkmp/fonts/` as a base64-encoded Kotlin source file
 * under `com.conamobile.pdfkmp.font.bundled`. The generated code is added to
 * the `commonMain` source set so the bytes are available on every platform
 * without the need for platform resource APIs.
 *
 * The base64 string is split into chunks of 60_000 characters to stay below
 * the JVM constant-pool 65_535-byte limit on string literals.
 */
val generateBundledFonts = tasks.register("generateBundledFonts") {
    group = "build"
    description = "Generates Kotlin sources embedding bundled TTF fonts as base64 strings."

    val fontsDir = layout.projectDirectory.dir("fonts")
    val outputDir = layout.buildDirectory.dir("generated/sources/bundledFonts/commonMain/kotlin")

    inputs.dir(fontsDir).withPropertyName("fontsDir")
    outputs.dir(outputDir).withPropertyName("outputDir")

    doLast {
        val fontFiles = fontsDir.asFile.listFiles { _, name -> name.endsWith(".ttf") }
            ?: emptyArray()
        require(fontFiles.isNotEmpty()) { "No TTF files found in ${fontsDir.asFile}" }

        val pkgDir = outputDir.get().asFile.resolve("com/conamobile/pdfkmp/font/bundled")
        pkgDir.deleteRecursively()
        pkgDir.mkdirs()

        fontFiles.sortedBy { it.name }.forEach { ttf ->
            val objectName = ttf.nameWithoutExtension
                .split('-', '_')
                .joinToString("") { part -> part.replaceFirstChar { it.uppercase() } } + "Bytes"

            val bytes = ttf.readBytes()
            val base64 = Base64.getEncoder().encodeToString(bytes)
            val chunks = base64.chunked(60_000)

            val src = buildString {
                appendLine("@file:Suppress(\"LargeClass\", \"MaxLineLength\", \"unused\")")
                appendLine()
                appendLine("package com.conamobile.pdfkmp.font.bundled")
                appendLine()
                appendLine("// GENERATED — do not edit by hand. Source: pdfkmp/fonts/${ttf.name}")
                appendLine()
                appendLine("internal object $objectName {")
                chunks.forEachIndexed { i, chunk ->
                    appendLine("    private const val CHUNK_$i: String = \"$chunk\"")
                }
                appendLine()
                appendLine("    internal val chunks: Array<String> = arrayOf(")
                chunks.indices.forEach { i ->
                    appendLine("        CHUNK_$i,")
                }
                appendLine("    )")
                appendLine("}")
            }
            pkgDir.resolve("$objectName.kt").writeText(src)
        }
    }
}

/**
 * Single-source-of-truth for [com.conamobile.pdfkmp.PdfKmp.VERSION] — the
 * runtime constant is generated from the `VERSION_NAME` Gradle property so a
 * release bump in `gradle.properties` cannot drift from what consumers see
 * at runtime.
 */
val generatePdfKmpVersion = tasks.register("generatePdfKmpVersion") {
    group = "build"
    description = "Generates a Kotlin file with the PdfKmp.VERSION constant from gradle.properties."

    val versionName = providers.gradleProperty("VERSION_NAME").get()
    val outputDir = layout.buildDirectory.dir("generated/sources/version/commonMain/kotlin")

    inputs.property("versionName", versionName)
    outputs.dir(outputDir).withPropertyName("outputDir")

    doLast {
        val pkgDir = outputDir.get().asFile.resolve("com/conamobile/pdfkmp")
        pkgDir.deleteRecursively()
        pkgDir.mkdirs()
        pkgDir.resolve("PdfKmpVersion.kt").writeText(
            """
            package com.conamobile.pdfkmp

            // GENERATED — do not edit by hand. Source: gradle.properties / VERSION_NAME

            internal const val PDFKMP_VERSION_NAME: String = "$versionName"
            """.trimIndent() + "\n"
        )
    }
}

kotlin {
    jvmToolchain(17)

    explicitApi()

    android {
        namespace = "com.conamobile.pdfkmp"
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

    // JVM / Desktop target (macOS, Windows, Linux). The backend builds on
    // Apache PdfBox, a pure-Java PDF engine, so the same artifact runs on
    // every desktop OS without bundling native libraries.
    jvm()

    // iosX64 (Intel-Mac simulator) was dropped in 1.1.0: Compose Multiplatform
    // 1.11.0 removed the target from its own modules, and Intel Macs are EOL.
    // Apple-Silicon simulators use iosSimulatorArm64; devices use iosArm64.
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = "PdfKmp"
            isStatic = true
        }
    }

    // Web (Kotlin/Wasm). Browsers expose no PDF-writing API to Wasm, so this
    // target renders through the pure-Kotlin `kmpwriter` backend (Standard-14
    // Helvetica, vector everything, JPEG/PNG passthrough). The published klib
    // is environment-agnostic — consumers use it from browser apps; nodejs()
    // here only selects where the library's own tests execute (Node downloads
    // automatically, no Chrome/karma needed on CI or Windows).
    wasmJs {
        nodejs()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBundledFonts)
            kotlin.srcDir(generatePdfKmpVersion)
            dependencies {
                implementation(libs.coroutines.core)
                implementation(libs.kotlinx.io.core)
            }
        }

        androidMain.dependencies {
            implementation(libs.coroutines.android)
            implementation(libs.androidx.startup.runtime)
        }

        jvmMain.dependencies {
            implementation(libs.pdfbox)
            // BouncyCastle is compileOnly: the keystore-based PdfSigner.sign(...)
            // path is built against it, but the ~9 MB jar is NOT bundled into the
            // published artifact. Callers who use that overload bring their own BC
            // at runtime; the callback-based overload needs no BC at all.
            compileOnly(libs.bouncycastle.bcpkix)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmTest.dependencies {
            // BC is a test-only dependency here: the signing tests generate a
            // self-signed certificate and exercise the keystore-based signer.
            implementation(libs.bouncycastle.bcpkix)
        }

        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }
}

// Make every Kotlin compile task depend on the codegen so the generated
// sources are present before compilation begins.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateBundledFonts)
    dependsOn(generatePdfKmpVersion)
}

// PdfBox image embedding goes through java.awt (BufferedImage / ImageIO),
// which must run in headless mode on CI and to avoid spawning a Dock/taskbar
// icon during the JVM test run.
tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
}

// Maven Central publishing via Vanniktech's plugin — handles sources and
// Javadoc jars, GPG signing, and the new Sonatype Central Portal upload
// flow (the legacy OSSRH staging repo is deprecated for fresh
// namespaces).
//
// Credentials live in `~/.gradle/gradle.properties`:
//   mavenCentralUsername=<central portal user token>
//   mavenCentralPassword=<central portal user token password>
//   signingInMemoryKeyId=<GPG key id>
//   signingInMemoryKeyPassword=<GPG passphrase or empty>
//   signingInMemoryKey=<armored private key, single line with \n separators>
mavenPublishing {
    // Vanniktech 0.30+ defaults to Sonatype Central Portal — no `SonatypeHost`
    // argument needed. `automaticRelease = true` skips the manual approval step.
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
    // GROUP, POM_ARTIFACT_ID, VERSION_NAME are read straight from
    // gradle.properties by the Vanniktech plugin — no explicit
    // `coordinates()` call needed.

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
