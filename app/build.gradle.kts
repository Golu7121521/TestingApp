plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.gitactionbuild"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.gitactionbuild"
        minSdk = 24; targetSdk = 29; versionCode = 1; versionName = "1.0"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("io.github.rosemoe:editor:0.23.7")
}
