plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.personale.tastiera"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.personale.tastiera"
        minSdk = 26
        // 34 di proposito: evita le regole "edge-to-edge" di Android 15 che complicano le tastiere
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    // Chiave fissa: ogni APK compilato (da Android Studio o da GitHub) si installa sopra il precedente
    // senza perdere scorciatoie e parole imparate. Tieni il progetto privato.
    signingConfigs {
        create("personale") {
            storeFile = rootProject.file("firma/tastiera-personale.jks")
            storePassword = "tastiera123"
            keyAlias = "tastiera"
            keyPassword = "tastiera123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("personale")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("personale")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Nessuna libreria esterna: solo Android + Kotlin.
