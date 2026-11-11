plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose") // if using Compose compiler plugin
}

android {
    namespace = "com.noorzzz.nhspectrum"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.noorzzz.nhspectrum"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14" // adjust to your AGP
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Maps (choose one)
    implementation("org.osmdroid:osmdroid-android:6.1.18")          // free, offline friendly
    // or Google Maps if you have a key

    // USB Serial for ESP32 Marauder
    implementation("com.github.mik3y:usb-serial-for-android:3.8.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
