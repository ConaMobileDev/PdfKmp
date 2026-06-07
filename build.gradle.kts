plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKmpLibrary) apply false
    alias(libs.plugins.dokka)
}

// Aggregated API reference: `./gradlew :dokkaGenerate` collects every
// published module's KDoc into one HTML site (build/dokka/html), which the
// docs workflow serves under /api on GitHub Pages.
dependencies {
    dokka(project(":pdfkmp"))
    dokka(project(":pdfkmp-compose-resources"))
    dokka(project(":pdfkmp-viewer"))
    dokka(project(":pdfkmp-markdown"))
}

dokka {
    moduleName.set("PdfKmp")
}
