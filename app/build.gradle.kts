plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.gitactionbuild"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.gitactionbuild"
        minSdk = 24; targetSdk = 35; versionCode = 2; versionName = "1.1"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

    buildTypes {
        release {
            // shrink code and resources; features stay the same
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // personal use: sign with the debug key so the release APK installs directly
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging {
        resources {
            excludes += setOf("META-INF/*.kotlin_module", "META-INF/versions/**", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    implementation("io.github.rosemoe:editor:0.24.6")
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
