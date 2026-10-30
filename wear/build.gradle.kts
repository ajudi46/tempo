plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ajudi46.tempo.wear"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ajudi46.tempo"
        minSdk = 30 // Wear OS 3, which is what Pixel Watch 1 shipped with
        targetSdk = 34
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
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.wear.compose:compose-material3:1.5.0") // Wear Material 3 Expressive
    implementation("androidx.wear.compose:compose-foundation:1.5.0")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation("androidx.wear.tiles:tiles:1.5.0")
    implementation("androidx.wear.protolayout:protolayout:1.3.0")
    implementation("androidx.wear.protolayout:protolayout-expression:1.3.0")
    implementation("androidx.wear.protolayout:protolayout-material3:1.3.0") // Material 3 Expressive tiles
    implementation("com.google.guava:guava:33.2.1-android")

    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.8.1")
}
