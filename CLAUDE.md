# CLAUDE.md

Repository conventions and build commands for AI agents (Claude Code, etc.) **working on the PdfKmp codebase itself**.

> Reading PdfKmp to **use** it as a library? Check [AGENTS.md](AGENTS.md) (universal AI guide) or [`.claude/skills/pdfkmp/SKILL.md`](.claude/skills/pdfkmp/SKILL.md) (Claude Code slash command) instead.
>
> Looking for the human-facing tour? See [README.md](README.md).

---

## Project

`PdfKmp` is an open-source Kotlin Multiplatform PDF generator targeting Android, iOS, and Desktop (JVM — macOS, Windows, Linux). Published to Maven Central as `io.github.conamobiledev:pdfkmp`.

The library is built around a single closed sealed `PdfNode` tree authored via a Compose-style DSL. Layout is performed in commonMain (`MeasuredNode`), and rendering is dispatched to platform-specific `PdfCanvas` implementations:

- **Android backend** — `android.graphics.pdf.PdfDocument` + `Canvas` (`AndroidPdfCanvas`).
- **iOS backend** — `UIGraphicsBeginPDFContextToData` + Core Graphics (`IosPdfCanvas`).
- **JVM / Desktop backend** — Apache PdfBox (`JvmPdfCanvas` / `JvmPdfDriver`). Pure-Java, no native libraries; embeds subset TrueType fonts for vector text, builds axial/radial PDF shadings for gradients, and writes real link annotations + a populated info dictionary. The PdfBox dependency lives only in the `jvmMain` source set.
- **Common test backend** — `FakePdfDriver` / `FakePdfCanvas` in `commonTest` records every draw call as a sealed `DrawCall` so the entire pipeline can be exercised end-to-end without native APIs.

Every text glyph and shape is emitted as a vector path — no rasterisation. Output stays sharp at any zoom level.

## Build commands

```bash
# Canonical test surface — exercises common + the iOS platform layer
./gradlew :pdfkmp:iosSimulatorArm64Test

# JVM / Desktop test surface — exercises common + the PdfBox backend
# end-to-end (SamplesSmokeTest re-renders every sample; JvmBackendTest
# re-parses + rasterises the output). Fast; no simulator needed.
./gradlew :pdfkmp:jvmTest

# Library — all platform artefacts (Android aar + iOS frameworks + JVM jar)
./gradlew :pdfkmp:assemble

# Library — single-platform builds
./gradlew :pdfkmp:linkDebugFrameworkIosArm64
./gradlew :pdfkmp:linkDebugFrameworkIosSimulatorArm64
./gradlew :pdfkmp:jvmJar

# Sample apps
./gradlew :sample:installDebug                     # Android, on connected device
# iOS sample: open iosApp/iosApp.xcodeproj in Xcode and Run

# Publishing — all four publishable modules ship together; release them in lock-step.
# Maven Central releases normally go through GitHub: publishing a Release triggers the
# publish.yml workflow, which runs the command below for you on a macOS runner. Run it by
# hand ONLY as a local/fallback path, and NEVER for a version you also publish a GitHub
# Release for — both call publishAndReleaseToMavenCentral, so the second fails on a
# duplicate version. See the Publishing checklist below.
./gradlew :pdfkmp:publishToMavenLocal :pdfkmp-compose-resources:publishToMavenLocal :pdfkmp-viewer:publishToMavenLocal :pdfkmp-markdown:publishToMavenLocal              # local install
./gradlew :pdfkmp:publishAndReleaseToMavenCentral :pdfkmp-compose-resources:publishAndReleaseToMavenCentral :pdfkmp-viewer:publishAndReleaseToMavenCentral :pdfkmp-markdown:publishAndReleaseToMavenCentral  # Maven Central (fallback; CI does this on Release)
```

JDK 21 recommended (`export JAVA_HOME=$(/usr/libexec/java_home -v 21)` on macOS).

## Module layout

- `:pdfkmp` — KMP library, Android (`aar`) + iOS framework `PdfKmp` (static, baseName `PdfKmp`) + JVM/Desktop (`jar`, depends on Apache PdfBox in `jvmMain` only). Publishable. Compose-free.
- `:pdfkmp-compose-resources` — opt-in KMP integration that maps Compose Multiplatform `DrawableResource` references onto the core PdfKmp DSL (`toVectorImage()`, `toBytes()`). Pure common, so the `jvm` target needs no platform code. Depends on `:pdfkmp` + `org.jetbrains.compose.components:components-resources`. Publishable as a separate artifact (`io.github.conamobiledev:pdfkmp-compose-resources`).
- `:pdfkmp-viewer` — opt-in Compose Multiplatform `PdfViewer` composable that renders any PdfKmp document on Android (`PdfRenderer`), iOS (`PDFKit.PDFDocument` + `thumbnailOfSize`), and Desktop (PdfBox `PDFRenderer`), and surfaces an optional share affordance (`Intent.ACTION_SEND` via `FileProvider` on Android, `UIActivityViewController` on iOS, `java.awt.Desktop.open` on Desktop). The Desktop `KmpPdfLauncher` hosts the viewer in a Swing `JFrame` + Compose `ComposePanel`. Depends on `:pdfkmp` + Compose Multiplatform runtime/foundation/ui/material3. Android resources are turned on via `androidResources { enable = true }` in the KMP library DSL because the FileProvider needs `res/xml/pdfkmp_viewer_file_paths.xml`. Publishable as `io.github.conamobiledev:pdfkmp-viewer`.
- `:pdfkmp-markdown` — opt-in KMP integration that renders a CommonMark-lite subset (headings, emphasis, inline code, lists, fenced code, blockquotes, pipe tables, links, horizontal rules) straight into the core PdfKmp DSL via the `markdown(text, theme)` extension on `ContainerScope`. Pure common (parser + DSL walker), so the `jvm` target needs no platform code; targets Android + iOS (`iosArm64` / `iosSimulatorArm64`) + JVM like the other companions. Depends on `:pdfkmp` only. Publishable as `io.github.conamobiledev:pdfkmp-markdown`. Compose-free.
- `:sample-shared` — KMP library (`com.android.kotlin.multiplatform.library`, Android target only) holding the resource-driven slice of the Android sample: a `commonMain/composeResources/` tree and `ComposeResourcesDemo`, which feeds typed `Res.drawable.*` references into `pdfAsync { drawable(...) }`. It exists because AGP 9 forbids applying the Kotlin Multiplatform plugin alongside `com.android.application`; extracting the Compose-Resources code into this KMP library is what lets `:sample` be a plain Android app. Depends on `:pdfkmp` + `:pdfkmp-compose-resources`. Not published.
- `:sample` — Compose Android sample app, a **plain `com.android.application` module** (no KMP plugin). Sources use the standard `src/main/` layout; depends on `:sample-shared` (Compose-Resources demo + generated `Res`), `:pdfkmp`, and `:pdfkmp-viewer`. Compose is wired by the `org.jetbrains.kotlin.plugin.compose` compiler plugin + `buildFeatures { compose = true }`; under AGP 9's built-in Kotlin the standalone `kotlin-android` plugin is intentionally **not** applied (it would clash with built-in Kotlin). No `gradle.properties` shims required.
- `iosApp/` — SwiftUI / PDFKit sample app. Build phase calls `:pdfkmp:embedAndSignAppleFrameworkForXcode`.
- `:sample-desktop` — Compose for Desktop (JVM) sample app (`jvm()` target + `compose.desktop.application`). A master/detail window: a scrollable list of every `Samples.*` document, each opening in `KmpPdfViewer`. Run with `./gradlew :sample-desktop:run`. Depends on all three publishable modules; proves the Desktop backend + viewer + the Compose-Resources (`Res.drawable.*` → `pdfAsync { drawable(...) }`) path end-to-end (a `composeResources/drawable/` asset is bundled and verified at startup). (macOS trackpad pinch-zoom needs no launch flag — the viewer opens `java.desktop/com.apple.eawt.event` reflectively at runtime.)
- `docs/` — non-code documentation and assets: `docs/screenshots/` holds the README hero images, and design / feasibility notes live alongside them (e.g. `streaming-and-memory.md`, `wasm-feasibility.md`).

## Where things live in the library

| What | Where |
|---|---|
| DSL entry points (`pdf { … }`, `column`, `row`, `text`, …) | `:pdfkmp/src/commonMain/kotlin/com/conamobile/pdfkmp/dsl/` |
| Sealed node hierarchy (`PdfNode`, `TextNode`, `ColumnNode`, …) | `:pdfkmp/src/commonMain/.../node/` |
| Layout engine (`measure(...)` → `MeasuredNode`) | `:pdfkmp/src/commonMain/.../layout/LayoutEngine.kt` |
| Renderer / page placement | `:pdfkmp/src/commonMain/.../render/DocumentRenderer.kt` |
| Platform canvas implementations | `:pdfkmp/src/androidMain/.../render/AndroidPdfCanvas.kt`, `:pdfkmp/src/iosMain/.../render/IosPdfCanvas.kt`, `:pdfkmp/src/jvmMain/.../render/JvmPdfCanvas.kt` (+ `JvmPdfDriver`, `JvmFontRegistry`, `JvmFontMetrics`, `JvmShading`) |
| Test backend | `:pdfkmp/src/commonTest/kotlin/.../test/FakePdfBackend.kt` |
| Worked-example documents | `:pdfkmp/src/commonMain/.../samples/Samples.kt` |
| Smoke tests for samples | `:pdfkmp/src/commonTest/.../samples/SamplesSmokeTest.kt` |

## Adding a new feature

A feature normally touches **all four** layers, in this order:

1. **DSL** — add a function on `ContainerScope` (or a fitting receiver) in `dsl/`.
2. **Node** — add a sealed `PdfNode` variant in `node/`.
3. **Layout** — extend `LayoutEngine.measure(...)` and add a `MeasuredNode` variant.
4. **Render** — extend `DocumentRenderer.place(...)` and add a draw method on `PdfCanvas`.
5. **Platforms** — implement on `AndroidPdfCanvas`, `IosPdfCanvas`, `JvmPdfCanvas`, and `FakePdfCanvas`.
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

All four publishable modules — `:pdfkmp`, `:pdfkmp-compose-resources`, `:pdfkmp-viewer`, `:pdfkmp-markdown` — share the same `VERSION_NAME` in root `gradle.properties` and are released together. Never ship one without the others, otherwise consumers pulling a companion artifact will get a version mismatch against the core. The runtime [`PdfKmp.VERSION`][version] constant is generated from `VERSION_NAME` by the `generatePdfKmpVersion` Gradle task, so a release bump only requires editing `gradle.properties` once.

[version]: pdfkmp/src/commonMain/kotlin/com/conamobile/pdfkmp/Pdf.kt

When cutting a release:

1. Set `VERSION_NAME` in `gradle.properties` to the release version (e.g. `1.2.0` or `1.2.0-alpha01`). This project does **not** use `-SNAPSHOT` dev versions — `VERSION_NAME` stays at the last released version between releases and is only changed when cutting the next one. (Do not bump it back to a `-SNAPSHOT` afterwards.)
2. Run the tests on both canonical surfaces — iOS Simulator AND JVM (the JVM/PdfBox backend has its own correctness gate): `./gradlew :pdfkmp:iosSimulatorArm64Test :pdfkmp-viewer:iosSimulatorArm64Test :pdfkmp:jvmTest :pdfkmp-viewer:jvmTest :pdfkmp-markdown:jvmTest` and `./gradlew :pdfkmp:assemble :pdfkmp-compose-resources:assemble :pdfkmp-viewer:assemble :pdfkmp-markdown:assemble` locally.
3. Add the version's section to `CHANGELOG.md`, then commit + push the `VERSION_NAME` bump and changelog.
4. Tag the release commit and push the tag: `git tag v1.2.0 && git push origin v1.2.0`.
5. **Publish a GitHub Release** for that tag (title + notes from the CHANGELOG). Publishing the Release triggers the `publish.yml` workflow (`on: release: published`), which runs `publishAndReleaseToMavenCentral` for all four modules on a macOS runner and ships them to Maven Central in one go — this is the **canonical** publish path. The manual `./gradlew …publishAndReleaseToMavenCentral` is a fallback only; never run it for a version you also publish a GitHub Release for, or the CI run will fail on the duplicate version.
6. Verify all four artifacts landed (allow ~10–30 min for the Central Portal to propagate to the public mirror): `https://repo1.maven.org/maven2/io/github/conamobiledev/pdfkmp/<version>/`, `.../pdfkmp-compose-resources/<version>/`, `.../pdfkmp-viewer/<version>/`, and `.../pdfkmp-markdown/<version>/` should all return 200.

Versions follow [semver](https://semver.org). Pre-1.0 minor versions may break API; alpha tags (`-alpha0N`) signal an actively settling surface.
