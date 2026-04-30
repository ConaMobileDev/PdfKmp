plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.gms)
    alias(libs.plugins.firebase.crashlytics)
}

android {
    namespace = "com.conamobile.romchi2"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.conamobile.romchi2"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 53
        versionName = "6.2"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            pickFirsts += "/META-INF/NOTICE.md"
            pickFirsts += "/META-INF/LICENSE.md"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
        getByName("debug") {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }
}

base {
    archivesName = "romchi-${android.defaultConfig.versionName}"
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.core.remote)
    implementation(projects.data.model)
    implementation(projects.ui.designsystem)

    implementation(libs.androidx.activityCompose)
    implementation(libs.androidx.window)
    implementation(libs.androidx.startup.runtime)

    implementation(libs.coroutines.android)
    implementation(libs.koin.android)
    implementation(libs.koin.core)
    implementation(libs.koin.compose)
    implementation(libs.napier)

    debugImplementation(libs.compose.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.auth)

    implementation(libs.facebook.android)
    implementation(libs.installreferrer)

    implementation(libs.google.auth)
    implementation(libs.google.auth.phone)

    implementation(libs.oneSignalAndroid)

    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.integrity)

    implementation(libs.kmpnotifier)
    implementation(libs.kmp.device.info)
}
