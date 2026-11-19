plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.fileforge.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fileforge.app"
        minSdk = 26
        // targetSdk 29 + requestLegacyExternalStorage = full /sdcard access on Android 10
        // (Redmi 9 Prime). On Android 11+ the app asks for "All files access".
        targetSdk = 29
        versionCode = 1
        versionName = "1.0"

        // Only 64-bit ARM (Redmi 9 Prime etc.)
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/release.jks")
            storePassword = "fileforge123"
            keyAlias = "fileforge"
            keyPassword = "fileforge123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // Zip / unzip
    implementation("net.lingala.zip4j:zip4j:2.11.5")
    // Root shell
    implementation("com.github.topjohnwu.libsu:core:6.0.0")
}
