plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val vc = (project.findProperty("vc") as String?)?.toIntOrNull() ?: 1
val minify = (project.findProperty("minify") as String?) == "true"
val vn = (project.findProperty("vn") as String?)?.removePrefix("v") ?: "1.0"

android {
    namespace = "com.example.universalplayer"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.universalplayer"
        minSdk = 26
        targetSdk = 34
        versionCode = vc
        versionName = vn
        // Only arm64 APK
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        // Fixed key: every build is signed the same way, so friends can update the app without uninstalling.
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "universalplayer"
            keyAlias = "friends"
            keyPassword = "universalplayer"
        }
    }

    buildTypes {
        release {
            // Smaller APK when built with -Pminify=true (see workflow input). Off by default = safest.
            isMinifyEnabled = minify
            isShrinkResources = minify
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    packaging {
        // Required by youtubedl-android (python/yt-dlp are shipped as native libs)
        jniLibs { useLegacyPackaging = true }
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    val media3 = "1.3.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-datasource:$media3")
    implementation("androidx.media3:media3-database:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-session:$media3")

    // Chromecast
    implementation("com.google.android.gms:play-services-cast-framework:21.4.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")

    implementation("io.coil-kt:coil-compose:2.6.0")

    // yt-dlp wrapper: supports 1000+ sites (YouTube, Vimeo, X, Instagram, Facebook, TikTok ...)
    implementation("io.github.junkfood02.youtubedl-android:library:0.17.2")
}
