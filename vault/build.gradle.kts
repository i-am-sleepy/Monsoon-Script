plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.parcelize) // the data classes in VaultModel.kt cross the process boundary as Parcelables
}

// E1: the thin interface between our app and the KeePassDX engine (D26). The engine modules come from the git submodule
// third_party/keepassdx (D47). D45: D1 (no libraries) keeps applying to the typing process; this module is for the vault process.
android {
    namespace = "com.monsoonscript.vault"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Rules the engine needs in ANY app that shrinks its code with R8 (found by the Phase 0 smoke test).
        consumerProguardFiles("consumer-rules.pro")
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
