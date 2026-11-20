pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// E1 (password manager, D26/D47): the KeePassDX engine lives in the git submodule third_party/keepassdx, pinned to a release tag
// and never edited. Its modules and our adapter module are part of the build ONLY when that folder has been checked out
// (`git submodule update --init --depth 1`), so a plain checkout still builds the keyboard exactly as before.
val engineDir = file("third_party/keepassdx")
val engineCheckedOut = engineDir.resolve("gradle/libs.versions.toml").exists()

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    if (engineCheckedOut) {
        versionCatalogs {
            create("libs") {
                // KeePassDX's own version catalog, so its modules build unchanged, with our Kotlin version on top.
                from(files("third_party/keepassdx/gradle/libs.versions.toml"))
                version("kotlin", "2.0.21")
            }
        }
    }
}

rootProject.name = "MonsoonScript"
include(":app")

if (engineCheckedOut) {
    include(":vault")
    // The names must stay ":crypto" and ":database": upstream's database/build.gradle.kts refers to project(":crypto").
    include(":crypto")
    include(":database")
    project(":crypto").projectDir = engineDir.resolve("crypto")
    project(":database").projectDir = engineDir.resolve("database")
}
