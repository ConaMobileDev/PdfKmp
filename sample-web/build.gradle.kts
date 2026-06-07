plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    // Browser executable — `./gradlew :sample-web:wasmJsBrowserDevelopmentRun`
    // starts a webpack dev server and opens the sample in the default browser.
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "sample-web.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":pdfkmp"))
            // Demonstrates the markdown(text) → PDF path on the web backend.
            implementation(project(":pdfkmp-markdown"))
        }
    }
}
