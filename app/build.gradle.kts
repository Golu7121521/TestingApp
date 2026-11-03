plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.linkplayer.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.linkplayer.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // debug key se sign hoga taaki release APK seedha install ho sake
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Media3 (ExoPlayer) - sabhi major formats/protocols
    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")          // MP4, MKV, WebM, TS, MP3, AAC, FLAC, OGG, WAV ...
    implementation("androidx.media3:media3-exoplayer-hls:$media3")      // HLS (.m3u8)
    implementation("androidx.media3:media3-exoplayer-dash:$media3")     // DASH (.mpd)
    implementation("androidx.media3:media3-exoplayer-smoothstreaming:$media3") // SmoothStreaming
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3")     // RTSP
    implementation("androidx.media3:media3-datasource-okhttp:$media3")  // custom HTTP headers, redirects
    implementation("androidx.media3:media3-ui:$media3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
