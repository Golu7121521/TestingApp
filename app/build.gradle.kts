plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.playvid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.playvid"
        minSdk = 26            // Adaptive icons + Picture-in-Picture available on every supported device
        targetSdk = 35
        versionCode = 40
        versionName = "3.6.0"
        // UI strings are English-only; this drops every other language's strings that the
        // libraries (AndroidX, Media3, ...) otherwise bundle for their own internal UI bits.
        resourceConfigurations += setOf("en")
    }

    // One APK per CPU architecture instead of one fat APK carrying all four native-library sets
    // (arm64-v8a, armeabi-v7a, x86_64, x86) — each download is a quarter of the native-lib weight.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    // Release signing: CI passes these through environment variables (see .github/workflows/build.yml)
    signingConfigs {
        create("release") {
            val path = System.getenv("KEYSTORE_PATH")
            if (!path.isNullOrBlank()) {
                storeFile = file(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // An unsigned APK can't be installed at all. Until a real release keystore secret is
            // added, sign with the debug key instead so this (minified, split, much smaller)
            // build is still directly installable; switches to the real key automatically once
            // KEYSTORE_PATH is set.
            signingConfig = if (!System.getenv("KEYSTORE_PATH").isNullOrBlank()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true // NewPipeExtractor needs java.nio/time APIs on minSdk < 33
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Media3 marks parts of its API @UnstableApi; opt in once for the whole module.
        freeCompilerArgs += "-opt-in=androidx.media3.common.util.UnstableApi"
    }
    buildFeatures { compose = true }
    lint { checkReleaseBuilds = false }
    packaging { resources { excludes += listOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES", "META-INF/INDEX.LIST") } }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.hls)
    implementation(libs.media3.dash)
    implementation(libs.media3.session)
    implementation(libs.androidx.media)
    implementation(libs.coil.compose)
    implementation(libs.media3.ui)
    implementation(libs.media3.okhttp)
    implementation(libs.media3.transformer)
    implementation(libs.media3.effect)
    implementation(libs.okhttp)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore)

    // YouTube link support (personal use): resolves a youtube.com / youtu.be link to a playable stream.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
}
