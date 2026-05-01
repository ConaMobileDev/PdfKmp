# CLAUDE.md

Repository conventions and build commands for AI agents (Claude Code, etc.) **working on the PdfKmp codebase itself**.

> Reading PdfKmp to **use** it as a library? Check [AGENTS.md](AGENTS.md) (universal AI guide) or [`.claude/skills/pdfkmp/SKILL.md`](.claude/skills/pdfkmp/SKILL.md) (Claude Code slash command) instead.
>
> Looking for the human-facing tour? See [README.md](README.md).

---

## Project

`PdfKmp` is an open-source Kotlin Multiplatform PDF generator targeting Android and iOS. Published to Maven Central as `io.github.conamobiledev:pdfkmp`.

The library is built around a single closed sealed `PdfNode` tree authored via a Compose-style DSL. Layout is performed in commonMain (`MeasuredNode`), and rendering is dispatched to platform-specific `PdfCanvas` implementations:

- **Android backend** — `android.graphics.pdf.PdfDocument` + `Canvas` (`AndroidPdfCanvas`).
- **iOS backend** — `UIGraphicsBeginPDFContextToData` + Core Graphics (`IosPdfCanvas`).
- **Common test backend** — `FakePdfDriver` / `FakePdfCanvas` in `commonTest` records every draw call as a sealed `DrawCall` so the entire pipeline can be exercised end-to-end without native APIs.

Every text glyph and shape is emitted as a vector path — no rasterisation. Output stays sharp at any zoom level.

## Build commands

```bash
# Canonical test surface — exercises every backend through the common-test FakePdfDriver
./gradlew :pdfkmp:iosSimulatorArm64Test

# Library — all platform artefacts
./gradlew :pdfkmp:assemble

# Library — single-platform builds
./gradlew :pdfkmp:linkDebugFrameworkIosArm64
./gradlew :pdfkmp:linkDebugFrameworkIosSimulatorArm64

# Sample apps
./gradlew :sample:installDebug                     # Android, on connected device
# iOS sample: open iosApp/iosApp.xcodeproj in Xcode and Run

# Publishing — all three publishable modules ship together; release them in lock-step
./gradlew :pdfkmp:publishToMavenLocal :pdfkmp-compose-resources:publishToMavenLocal :pdfkmp-viewer:publishToMavenLocal              # local install
./gradlew :pdfkmp:publishAndReleaseToMavenCentral :pdfkmp-compose-resources:publishAndReleaseToMavenCentral :pdfkmp-viewer:publishAndReleaseToMavenCentral  # Maven Central (requires signing creds)
```

JDK 21 recommended (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)` on macOS).

## Module layout

- `:pdfkmp` — KMP library, Android (`aar`) + iOS framework `PdfKmp` (static, baseName `PdfKmp`). Publishable. Compose-free.
- `:pdfkmp-compose-resources` — opt-in KMP integration that maps Compose Multiplatform `DrawableResource` references onto the core PdfKmp DSL (`toVectorImage()`, `toBytes()`). Depends on `:pdfkmp` + `org.jetbrains.compose.components:components-resources`. Publishable as a separate artifact (`io.github.conamobiledev:pdfkmp-compose-resources`).
- `:pdfkmp-viewer` — opt-in Compose Multiplatform `PdfViewer` composable that renders any PdfKmp document on Android (`PdfRenderer`) and iOS (`PDFKit.PDFDocument` + `thumbnailOfSize`) and surfaces an optional share sheet (`Intent.ACTION_SEND` via `FileProvider` on Android, `UIActivityViewController` on iOS). Depends on `:pdfkmp` + Compose Multiplatform runtime/foundation/ui/material3. Android resources are turned on via `androidResources { enable = true }` in the KMP library DSL because the FileProvider needs `res/xml/pdfkmp_viewer_file_paths.xml`. Publishable as `io.github.conamobiledev:pdfkmp-viewer`.
- `:sample` — Compose Android sample app, structured as a **single-target KMP module** (`androidTarget()` only) so Compose Multiplatform Resources can be loaded from `commonMain/composeResources/`. The Activity and other Android-specific code live in `src/androidMain/kotlin/`, the resource-driven demo lives in `src/commonMain/kotlin/`. Depends on `:pdfkmp`, `:pdfkmp-compose-resources`, and `:pdfkmp-viewer`. Requires the `android.newDsl=false` + `android.builtInKotlin=false` shims in root `gradle.properties` for AGP 9 compatibility — see the comment in that file.
- `iosApp/` — SwiftUI / PDFKit sample app. Build phase calls `:pdfkmp:embedAndSignAppleFrameworkForXcode`.

## Where things live in the library

| What | Where |
|---|---|
| DSL entry points (`pdf { … }`, `column`, `row`, `text`, …) | `:pdfkmp/src/commonMain/kotlin/com/conamobile/pdfkmp/dsl/` |
| Sealed node hierarchy (`PdfNode`, `TextNode`, `ColumnNode`, …) | `:pdfkmp/src/commonMain/.../node/` |
| Layout engine (`measure(...)` → `MeasuredNode`) | `:pdfkmp/src/commonMain/.../layout/LayoutEngine.kt` |
| Renderer / page placement | `:pdfkmp/src/commonMain/.../render/DocumentRenderer.kt` |
| Platform canvas implementations | `:pdfkmp/src/androidMain/.../render/AndroidPdfCanvas.kt`, `:pdfkmp/src/iosMain/.../render/IosPdfCanvas.kt` |
| Test backend | `:pdfkmp/src/commonTest/kotlin/.../test/FakePdfBackend.kt` |
| Worked-example documents | `:pdfkmp/src/commonMain/.../samples/Samples.kt` |
| Smoke tests for samples | `:pdfkmp/src/commonTest/.../samples/SamplesSmokeTest.kt` |

## Adding a new feature

A feature normally touches **all four** layers, in this order:

1. **DSL** — add a function on `ContainerScope` (or a fitting receiver) in `dsl/`.
2. **Node** — add a sealed `PdfNode` variant in `node/`.
3. **Layout** — extend `LayoutEngine.measure(...)` and add a `MeasuredNode` variant.
4. **Render** — extend `DocumentRenderer.place(...)` and add a draw method on `PdfCanvas`.
5. **Platforms** — implement on `AndroidPdfCanvas`, `IosPdfCanvas`, and `FakePdfCanvas`.
6. **Sample** — add or extend a function in `Samples.kt`.
7. **Test** — add to `SamplesSmokeTest.kt` so the new path runs end-to-end on iOS Simulator.

## Conventions

- Base packages: `com.conamobile.pdfkmp` (library), `com.conamobile.pdfkmp.sample` (Android sample).
- `explicitApi()` is on for `:pdfkmp` — every new declaration must declare visibility (`public` / `internal`). Sample apps don't have this constraint.
- **Never use fully qualified class names inline** — add an `import` and use the short name.
- Publishing metadata (`GROUP`, `VERSION_NAME`, `POM_*`) lives in `gradle.properties`. Don't hardcode it in `build.gradle.kts`.
- Maven Central publishing is wired via the [Vanniktech `gradle-maven-publish-plugin`](https://github.com/vanniktech/gradle-maven-publish-plugin) (`com.vanniktech.maven.publish`) — uses the new Sonatype Central Portal flow.
- Signing credentials and Sonatype tokens never live in the repo — they're read from `~/.gradle/gradle.properties` (`signingInMemoryKey`, `mavenCentralUsername`, etc.) or GitHub Secrets in CI.
- KDoc on every `public` declaration; comments explain WHY, never WHAT.
- Coordinates are in PDF points with a top-left origin (Y grows downward). Both Android and iOS backends translate to native conventions internally.

## Testing

The canonical test surface is **iOS Simulator** because it exercises both common code AND the platform layer:

```bash
./gradlew :pdfkmp:iosSimulatorArm64Test
```

The pure-common surface uses `FakePdfDriver` so layout and rendering decisions can be asserted without launching a simulator. Every public sample in `Samples.kt` has a smoke test in `SamplesSmokeTest.kt` that verifies the output starts with the `%PDF-` magic bytes.

## Publishing checklist

All three publishable modules — `:pdfkmp`, `:pdfkmp-compose-resources`, `:pdfkmp-viewer` — share the same `VERSION_NAME` in root `gradle.properties` and are released together. Never ship one without the others, otherwise consumers pulling a companion artifact will get a version mismatch against the core. The runtime [`PdfKmp.VERSION`][version] constant is generated from `VERSION_NAME` by the `generatePdfKmpVersion` Gradle task, so a release bump only requires editing `gradle.properties` once.

[version]: pdfkmp/src/commonMain/kotlin/com/conamobile/pdfkmp/Pdf.kt

When cutting a release:

1. Update `VERSION_NAME` in `gradle.properties` (drop `-SNAPSHOT`, e.g. `0.2.0` or `0.2.0-alpha01`).
2. Run `./gradlew :pdfkmp:iosSimulatorArm64Test :pdfkmp-viewer:iosSimulatorArm64Test` and `./gradlew :pdfkmp:assemble :pdfkmp-compose-resources:assemble :pdfkmp-viewer:assemble` locally.
3. `git tag v0.2.0 && git push --tags` (or use GitHub Releases UI).
4. Maven Central publish: `./gradlew :pdfkmp:publishAndReleaseToMavenCentral :pdfkmp-compose-resources:publishAndReleaseToMavenCentral :pdfkmp-viewer:publishAndReleaseToMavenCentral --no-configuration-cache` (or trigger the GitHub Actions `publish.yml` workflow by publishing a Release — it ships all three modules in one run).
5. Verify all three artifacts landed: `https://repo1.maven.org/maven2/io/github/conamobiledev/pdfkmp/<version>/`, `.../pdfkmp-compose-resources/<version>/`, and `.../pdfkmp-viewer/<version>/` should all return 200.
6. Bump `VERSION_NAME` to the next `-SNAPSHOT` (e.g. `0.3.0-SNAPSHOT`) and commit.

Versions follow [semver](https://semver.org). Pre-1.0 minor versions may break API; alpha tags (`-alpha0N`) signal an actively settling surface.
