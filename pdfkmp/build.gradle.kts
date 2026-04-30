import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.mavenPublish)
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

kotlin {
    jvmToolchain(17)

    explicitApi()

    androidLibrary {
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

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = "PdfKmp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBundledFonts)
            dependencies {
                implementation(libs.coroutines.core)
                implementation(libs.kotlinx.io.core)
            }
        }

        androidMain.dependencies {
            implementation(libs.coroutines.android)
            implementation(libs.androidx.startup.runtime)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
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
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
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
