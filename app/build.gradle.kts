plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.vsa.vpnswitch"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vsa.vpnswitch"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0"
    }

    signingConfigs {
        // Eigener Release-Schlüssel, wenn die Umgebungsvariablen gesetzt sind (z. B. über GitHub-Secrets),
        // sonst der gemeinsame Projektschlüssel aus signing/ – so passen alle Builds zueinander
        // und lassen sich gegenseitig als Update installieren.
        create("vsa") {
            val ks = System.getenv("VSA_KEYSTORE_FILE")
            if (!ks.isNullOrBlank()) {
                storeFile = file(ks)
                storePassword = System.getenv("VSA_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("VSA_KEY_ALIAS")
                keyPassword = System.getenv("VSA_KEY_PASSWORD")
            } else {
                storeFile = rootProject.file("signing/vsa-shared.keystore")
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("vsa")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("vsa")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
}
