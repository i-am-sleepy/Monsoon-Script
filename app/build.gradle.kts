plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI sets KEYSTORE_PATH (+ KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD) from GitHub Secrets.
// Without them the release build falls back to the debug key (installs, but cannot update over a differently signed build).
val releaseKeystore: String? = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.monsoonscript.keyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.monsoonscript.keyboard"
        minSdk = 26
        targetSdk = 34
        // Must increase on every build so a new APK can update the installed one.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.1.1"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storeType = "pkcs12"
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // The prediction packs are memory-mapped straight from the APK, so they must not be compressed (ime/PackLoader.kt).
    androidResources {
        noCompress += "mslm"
    }

    // No signing-block metadata blob for Google; smaller APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    // Pure-JVM unit tests for the language engines (run in CI, see .github/workflows/build.yml).
    testImplementation("junit:junit:4.13.2")
}
