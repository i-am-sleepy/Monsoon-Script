plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI sets KEYSTORE_PATH (+ KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD) from GitHub Secrets.
// Without them the release build falls back to the debug key (installs, but cannot update over a differently signed build).
val releaseKeystore: String? = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }

// The native voice engine (G5, D66: transcribe.cpp + a small JNI layer, arm64 only) is built only when its submodule is checked out,
// like the vault engine (D64): a plain checkout still builds the keyboard without it, and the app then offers no local voice model.
val withVoiceNative = File(rootDir, "third_party/transcribe.cpp/CMakeLists.txt").exists()

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
        if (withVoiceNative) {
            ndk { abiFilters += "arm64-v8a" }
        }
    }

    if (withVoiceNative) {
        ndkVersion = "25.2.9519653" // the NDK the engine workflow already installs (E1)
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
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
    // Same for the emoji font (E2): Typeface.createFromAsset maps an uncompressed asset instead of copying 10 MB into memory.
    androidResources {
        noCompress += "mslm"
        noCompress += "ttf"
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
