pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            // E1 Phase 0 (side branch): KeePassDX's own version catalog, so its modules build unchanged (D47),
            // with our Kotlin version on top. The upstream checkout is made by the CI workflow (later: a git submodule).
            from(files("third_party/keepassdx/gradle/libs.versions.toml"))
            version("kotlin", "2.0.21")
        }
    }
}

rootProject.name = "MonsoonScript"
include(":app")
include(":vault")

// KeePassDX engine modules, used unchanged (D26, D46, D47). Names must stay ":crypto" and ":database":
// upstream's database/build.gradle.kts refers to project(":crypto").
include(":crypto")
include(":database")
project(":crypto").projectDir = file("third_party/keepassdx/crypto")
project(":database").projectDir = file("third_party/keepassdx/database")
