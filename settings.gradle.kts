pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// E1 (password manager, D26/D47): the KeePassDX engine lives in the git submodule third_party/keepassdx, pinned to a release tag
// and never edited. Since E1.13 the app depends on our adapter module :vault, so the submodule is REQUIRED for every build
// (`git submodule update --init --depth 1 third_party/keepassdx`; build.yml and engine.yml do it).
val engineDir = file("third_party/keepassdx")
val engineCheckedOut = engineDir.resolve("gradle/libs.versions.toml").exists()
if (!engineCheckedOut) {
    throw GradleException("The KeePassDX engine is missing. Run: git submodule update --init --depth 1 third_party/keepassdx")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            // KeePassDX's own version catalog, so its modules build unchanged, with our Kotlin version on top.
            from(files("third_party/keepassdx/gradle/libs.versions.toml"))
            version("kotlin", "2.0.21")
        }
    }
}

rootProject.name = "MonsoonScript"
include(":app")

include(":vault")
// The names must stay ":crypto" and ":database": upstream's database/build.gradle.kts refers to project(":crypto").
include(":crypto")
include(":database")
project(":crypto").projectDir = engineDir.resolve("crypto")
project(":database").projectDir = engineDir.resolve("database")
