plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// GitHub Actions ka run number versionCode banta hai, taaki har build update ki tarah install ho sake
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toIntOrNull() ?: 1

android {
    namespace = "com.gamerec.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gamerec.app"
        minSdk = 29          // Android 10+ (internal audio capture ke liye zaroori)
        targetSdk = 34
        versionCode = runNumber
        versionName = "1.0.$runNumber"

        // Sirf arm64 (aaj ke saare phones)
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("keystore/release.jks")
            storePassword = "gamerec123"
            keyAlias = "gamerec"
            keyPassword = "gamerec123"
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
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

// Jaan-bujh kar koi third-party library nahi: Android ka apna hardware MediaCodec
// sabse kam CPU/GPU leta hai, isse game ka FPS sabse kam girta hai.
dependencies {
}
