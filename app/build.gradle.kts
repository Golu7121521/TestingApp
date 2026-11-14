plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.gitactionbuild"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.gitactionbuild"
        minSdk = 24; targetSdk = 35; versionCode = 2; versionName = "1.1"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}

dependencies {
    implementation("io.github.rosemoe:editor:0.24.6")
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
