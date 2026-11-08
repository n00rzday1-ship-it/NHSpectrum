plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.noorzzz.nhspectrum"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.noorzzz.nhspectrum"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.core:core-ktx:1.12.0")
    
    // Location (Required for GPS)
    implementation("com.google.android.gms:play-services-location:21.1.0")
    
    // OpenStreetMap (Free Map)
    implementation("org.osmdroid:osmdroid-android:6.1.18")
    implementation("androidx.preference:preference-ktx:1.2.1")
    
    // ESP32 Serial
    implementation("com.github.mik3y:usb-serial-for-android:3.8.1")
}
