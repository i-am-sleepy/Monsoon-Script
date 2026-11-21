// Versions: AGP 8.13.2 and Gradle 8.14.2 are what KeePassDX 4.5.8 builds with (its modules need compileSdk 36).
// Kotlin stays at our 2.0.21. The parcelize plugin is only needed by the engine modules (same Kotlin version).
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.0.21" apply false
}
