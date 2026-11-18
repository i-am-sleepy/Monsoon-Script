plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

// E1: the thin interface between our app and the KeePassDX engine (D26). Phase 0 holds only a spike.
// D45: D1 (no libraries) keeps applying to the typing process; this module runs in the ":vault" process.
android {
    namespace = "com.monsoonscript.vault"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(project(":database"))

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.junit)
}
