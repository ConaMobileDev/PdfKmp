# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

`PdfKmp` is an open-source Kotlin Multiplatform PDF generator library targeting Android and iOS. The repository is currently a skeleton — modules and Gradle wiring are in place, the public API will be added next.

## Build commands

```bash
# Build everything
./gradlew build

# Library
./gradlew :pdfkmp:assemble
./gradlew :pdfkmp:linkDebugFrameworkIosArm64

# Sample apps
./gradlew :sample:installDebug

# Publishing
./gradlew :pdfkmp:publishToMavenLocal
```

## Module layout

- `:pdfkmp` — KMP library, Android (`aar`) + iOS framework `PdfKmp`. Publishable via `maven-publish`.
- `:sample` — Android sample app that depends on `:pdfkmp`.
- `iosApp/` — Xcode project consuming the `PdfKmp` framework. The build phase calls `:pdfkmp:embedAndSignAppleFrameworkForXcode`.

## Conventions

- Base package: `com.conamobile.pdfkmp` (library), `com.conamobile.pdfkmp.sample` (sample).
- `explicitApi()` is on for the library; everything new in `:pdfkmp` must declare visibility (`public`/`internal`).
- Never use fully qualified class names inline — add an `import` and use the short name.
- Publishing metadata lives in `gradle.properties` (`GROUP`, `VERSION_NAME`, `POM_*`). Don't hardcode it in `build.gradle.kts`.
