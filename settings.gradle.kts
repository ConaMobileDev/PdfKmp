rootProject.name = "Romchi-Multiplatform"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            content { 
              	includeGroupByRegex("com\\.android.*")
              	includeGroupByRegex("com\\.google.*")
              	includeGroupByRegex("androidx.*")
              	includeGroupByRegex("android.*")
            }
        }
        gradlePluginPortal()
        mavenCentral()
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
              	includeGroupByRegex("com\\.android.*")
              	includeGroupByRegex("com\\.google.*")
              	includeGroupByRegex("androidx.*")
              	includeGroupByRegex("android.*")
            }
        }
        mavenCentral()
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
        maven("https://jitpack.io")  // for KMPLiquidGlass
    }
}
include(":composeApp")
include(":androidApp")
include(":data")
include(":data:model")
include(":data:repository")
include(":core:database")
include(":core:datetime")
include(":core:remote")
include(":core:store")
include(":core:analytics")
include(":core:connection")
include(":platform")
include(":navigation")
include(":features")
include(":ui")
include(":ui:designsystem")
include(":ui:component")
include(":features:auth")
include(":features:home")
include(":features:profile")
include(":features:payment")
include(":features:orders")
include(":features:prices")
include(":features:employees")
include(":features:newOrder")
include(":features:company")
include(":features:downloader")
include(":features:warehouse")
include(":core:storage")
include(":core:sync")
include(":core:cache")
include(":backend-kmp")
include(":mapController")
include(":features:dillersLoc")
include(":features:aiChat")
