# Getting started

## Repository setup

PdfKmp is published to **Maven Central**. Make sure it's in your repository list:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
```

## Artifact coordinates

All publishable modules share the same version and are released in lock-step.

| Artifact | Coordinate | What it is |
|---|---|---|
| Core (KMP) | `io.github.conamobiledev:pdfkmp` | The DSL + layout + renderer. Compose-free. |
| Compose Resources | `io.github.conamobiledev:pdfkmp-compose-resources` | Bridges `Res.drawable.*` into the DSL (opt-in). |
| Markdown | `io.github.conamobiledev:pdfkmp-markdown` | Renders CommonMark-lite through the DSL (opt-in). |
| Viewer | `io.github.conamobiledev:pdfkmp-viewer` | Compose Multiplatform PDF viewer screen (opt-in, not on web). |

Platform-specific variants (`-android`, `-jvm`, `-wasm-js`) exist for plain
non-KMP projects. KMP projects depend on the base coordinate only — Gradle
resolves the right variant automatically for every target.

!!! note "Paths are mutually exclusive"
    KMP projects depend on `pdfkmp` only (**including for Android and Desktop targets**).
    Plain Android projects use `pdfkmp-android`; plain JVM/Desktop projects use `pdfkmp-jvm`.
    Do **not** add both the base and the platform artifact.

### Kotlin Multiplatform

```toml
# gradle/libs.versions.toml
[versions]
pdfkmp = "1.2.0"

[libraries]
pdfkmp = { module = "io.github.conamobiledev:pdfkmp", version.ref = "pdfkmp" }
pdfkmp-compose-resources = { module = "io.github.conamobiledev:pdfkmp-compose-resources", version.ref = "pdfkmp" } # optional
pdfkmp-viewer = { module = "io.github.conamobiledev:pdfkmp-viewer", version.ref = "pdfkmp" } # optional
```

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.pdfkmp)
            implementation(libs.pdfkmp.compose.resources) // optional
            implementation(libs.pdfkmp.viewer)            // optional
        }
    }
}
```

### Android-only

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("io.github.conamobiledev:pdfkmp-android:1.2.0")
    implementation("io.github.conamobiledev:pdfkmp-viewer:1.2.0") // optional
}
```

### Desktop / JVM-only

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.conamobiledev:pdfkmp-jvm:1.2.0")
    implementation("io.github.conamobiledev:pdfkmp-viewer-jvm:1.2.0") // optional
}
```

The Desktop backend is built on Apache PDFBox (pulled in transitively) and is
pure-Java, so the same artifact runs on macOS, Windows, and Linux with no native
libraries to bundle.

### Requirements

- JDK 17+
- Android Gradle Plugin 8.x, `compileSdk` 34+ (Android targets)
- Xcode 16+ when targeting iOS via Kotlin Multiplatform
- A desktop JRE 17+ when targeting Desktop / JVM

!!! note "R8 / ProGuard"
    Fully supported — no additional keep rules required.

## Hello world

```kotlin
import com.conamobile.pdfkmp.pdf
import com.conamobile.pdfkmp.storage.StorageLocation
import com.conamobile.pdfkmp.storage.save
import com.conamobile.pdfkmp.style.PdfColor
import com.conamobile.pdfkmp.unit.sp

val document = pdf {
    metadata { title = "Hello, PdfKmp" }
    page {
        text("Hello, world!") {
            fontSize = 24.sp
            bold = true
            color = PdfColor.Blue
        }
    }
}

val bytes: ByteArray = document.toByteArray()              // raw bytes
val saved = document.save(StorageLocation.Cache, "hello.pdf")  // returns SavedPdf (synchronous)
println(saved.path)  // absolute filesystem path you can hand to a viewer / share intent
```

## `pdf {}` vs `pdfAsync {}`

`pdfAsync` is `pdf` with a one-shot suspend preflight pass tacked onto the front.
The DSL inside is **identical** — only the top-level entry differs.

| | `pdf { }` | `pdfAsync { }` |
|---|---|---|
| Function shape | non-suspend | `suspend` |
| `drawable / vector / image (Res.drawable.X)` | throws at render time | works |
| All other DSL features | identical | identical |
| When to reach for it | document is built from in-memory bytes or pre-parsed `VectorImage`s | the tree contains one or more `Res.drawable.*` references |

Reach for `pdfAsync` only when you use the typed `Res.drawable.*` overloads from
the [Compose Resources](guides/compose-resources.md) companion. Existing `pdf { }`
code keeps working with zero changes.

```kotlin
suspend fun buildReport(): PdfDocument = pdfAsync {
    page {
        drawable(Res.drawable.logo, width = 64.dp, tint = PdfColor.Blue)
    }
}
```

## Save & share per platform

`save(...)` is a regular (non-suspend) function returning a `SavedPdf` with `path`
(the absolute filesystem path) and `uri` (the Android MediaStore content URI on
public writes, `null` elsewhere). Typed [storage locations](#available-storage-locations)
resolve inside the platform sandbox.

```kotlin
val saved = doc.save(StorageLocation.Downloads, filename = "report.pdf")
println(saved.path)     // absolute path
```

=== "Android"
    Display with the system `PdfRenderer`, or share via a `FileProvider` +
    `Intent.ACTION_SEND`. The simplest path is the [viewer](guides/viewer.md):
    `KmpPdfLauncher.open(saved.path, title = "Invoice")`.

=== "iOS"
    `PDFKit.PDFView` displays the document as vector. Share via
    `UIActivityViewController`, or hand bytes to UIKit with `doc.toNSData()`.

=== "Desktop"
    Locations resolve against the user's home (`~/Downloads`, `~/Documents`).
    Open the PDF in the OS default handler via `java.awt.Desktop.open`.

=== "Web"
    Browsers ship excellent PDF viewers — hand them the bytes:
    ```kotlin
    doc.openInNewTab()                                  // browser's own viewer
    doc.save(StorageLocation.Downloads, "hello.pdf")    // browser download
    ```

### Available storage locations

| Location | Android | iOS | Desktop (JVM) | Use case |
|---|---|---|---|---|
| `Cache` | app cache | `Library/Caches/` | `java.io.tmpdir` | temporary preview |
| `AppFiles` | app files | `Library/Application Support/` | `~/.pdfkmp` | persistent, app-private |
| `AppExternalFiles` | scoped external | app `Documents/` | `~/.pdfkmp` | larger app-scoped files |
| `Downloads` | shared via MediaStore | app `Documents/` (no public Downloads) | `~/Downloads` | user-visible |
| `Documents` | shared `Documents/` | app `Documents/` | `~/Documents` | user documents |
| `Temp` | `cacheDir/tmp/` | `NSTemporaryDirectory()` | `java.io.tmpdir` | one-shot temp |
| `Custom("/abs/path")` | any writable dir | any writable dir | any writable dir | full control |

!!! note "Android permissions"
    `Cache`, `AppFiles`, `AppExternalFiles`, `Temp`, and the MediaStore-backed
    `Downloads` / `Documents` (Android 10+) need **no permission**. For `Custom`
    paths outside your sandbox you supply your own permission handling.

## Troubleshooting

PdfKmp recovers gracefully (and silently) from a few conditions — an undecodable
image, a glyph missing from the active font, an unsupported feature on a given
backend. If a generated PDF doesn't look the way you expect, install a `PdfLog`
logger to surface those swallowed warnings:

```kotlin
import com.conamobile.pdfkmp.PdfLog

PdfLog.logger = { message -> println("PdfKmp: $message") }
```

See [Diagnostics — PdfLog](guides/forms-security.md#diagnostics-pdflog) for details.

## Where next

- [Text & typography](guides/text.md)
- [Pages, headers & watermarks](guides/page-setup.md)
- [PDF viewer (Compose)](guides/viewer.md)
- [Platform parity](guides/platform-parity.md)
