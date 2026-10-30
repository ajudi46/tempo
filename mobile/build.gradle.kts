plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ajudi46.tempo.mobile"
    compileSdk = 36

    defaultConfig {
        // Must match the watch app's applicationId (and be signed with the same key)
        // for the Wearable Data Layer to connect the two apps.
        applicationId = "com.ajudi46.tempo"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.01"))
    implementation("androidx.compose.ui:ui")
    // Expressive APIs (MaterialExpressiveTheme, LoadingIndicator, MaterialShapes...) are public in the 1.5 alphas
    implementation("androidx.compose.material3:material3:1.5.0-alpha04")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
}
