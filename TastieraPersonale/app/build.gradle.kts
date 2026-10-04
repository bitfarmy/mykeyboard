import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * La chiave di firma NON sta nel repository. Viene letta, nell'ordine, da:
 * 1. variabili d'ambiente KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD (GitHub Actions);
 * 2. il file indicato da TASTIERA_KEYSTORE_PROPERTIES, oppure keystore.properties nella cartella
 *    del progetto, oppure ~/.android-chiavi/keystore.properties (sul tuo computer).
 * Il file .properties contiene: storeFile, storePassword, keyAlias, keyPassword.
 */
data class Firma(val file: File, val storePassword: String, val alias: String, val keyPassword: String)

fun leggiFirma(): Firma? {
    val env = System.getenv()
    val fileEnv = env["KEYSTORE_FILE"]
    if (!fileEnv.isNullOrBlank()) {
        return Firma(
            file(fileEnv),
            env["KEYSTORE_PASSWORD"] ?: error("Manca KEYSTORE_PASSWORD"),
            env["KEY_ALIAS"] ?: error("Manca KEY_ALIAS"),
            env["KEY_PASSWORD"] ?: error("Manca KEY_PASSWORD"),
        )
    }
    val candidati = listOfNotNull(
        env["TASTIERA_KEYSTORE_PROPERTIES"]?.let { File(it) },
        rootProject.file("keystore.properties"),
        File(System.getProperty("user.home"), ".android-chiavi/keystore.properties"),
    )
    val props = candidati.firstOrNull { it.isFile } ?: return null
    val p = Properties().apply { props.inputStream().use { load(it) } }
    return Firma(
        File(p.getProperty("storeFile")),
        p.getProperty("storePassword"),
        p.getProperty("keyAlias"),
        p.getProperty("keyPassword"),
    )
}

val firma = leggiFirma()

android {
    namespace = "com.personale.tastiera"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.personale.tastiera"
        minSdk = 26
        // 34 di proposito: evita le regole "edge-to-edge" di Android 15 che complicano le tastiere
        targetSdk = 34
        versionCode = 2
        versionName = "0.2"
    }

    signingConfigs {
        if (firma != null) {
            create("personale") {
                storeFile = firma.file
                storePassword = firma.storePassword
                keyAlias = firma.alias
                keyPassword = firma.keyPassword
            }
        }
    }

    buildTypes {
        // Senza chiave personale la build di debug usa la chiave di debug standard di Android.
        debug {
            if (firma != null) signingConfig = signingConfigs.getByName("personale")
        }
        release {
            isMinifyEnabled = false
            if (firma != null) signingConfig = signingConfigs.getByName("personale")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

// L'APK da installare deve essere firmato con la chiave personale, altrimenti non si aggiorna.
tasks.matching { it.name == "assembleRelease" }.configureEach {
    doFirst {
        if (firma == null) throw GradleException(
            "Chiave di firma non trovata: imposta i secret su GitHub o crea keystore.properties (vedi README).",
        )
    }
}

// Nessuna libreria esterna nell'app: solo Android + Kotlin. JUnit serve solo ai test sul computer.
dependencies {
    testImplementation("junit:junit:4.13.2")
}
