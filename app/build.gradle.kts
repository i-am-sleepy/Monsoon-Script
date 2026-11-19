import java.util.TreeMap
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// CI sets KEYSTORE_PATH (+ KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD) from GitHub Secrets.
// Without them the release build falls back to the debug key (installs, but cannot update over a differently signed build).
val releaseKeystore: String? = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.monsoonscript.keyboard"
    compileSdk = 36

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

    // E1: BouncyCastle ships about 1.2 MB of post-quantum data files (org/bouncycastle/pqc/legacy) and some message
    // bundles that a password vault never uses. These are Java RESOURCES (the classes are untouched).
    packaging {
        resources {
            excludes += setOf("org/bouncycastle/pqc/**", "org/bouncycastle/**/CertPathReviewerMessages*.properties")
        }
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

    // E1 Phase 0 (side branch): the vault engine adapter module, which brings in the KeePassDX engine (D26, D47).
    implementation(project(":vault"))
}

// E1 Phase 0 (side branch only, never for main): after assembleRelease print what is inside the APK, grouped, so the
// engine's real size cost is exact. The lines start with APKPARTS so they are easy to find in the CI log.
val reportApkParts by tasks.registering {
    doLast {
        val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        if (!apk.exists()) {
            println("APKPARTS no apk at $apk")
            return@doLast
        }
        val raw = TreeMap<String, Long>()
        val stored = TreeMap<String, Long>()
        val detail = TreeMap<String, Long>()
        ZipFile(apk).use { zip ->
            for (e in zip.entries()) {
                val n = e.name
                val key = when {
                    n.endsWith(".dex") -> "dex"
                    n.startsWith("lib/") -> n.substringBeforeLast('/')
                    n.startsWith("assets/") -> "assets"
                    n.startsWith("res/") -> "res"
                    n.startsWith("META-INF/") -> "META-INF"
                    else -> n.substringBefore('/')
                }
                raw.merge(key, e.size) { x, y -> x + y }
                stored.merge(key, e.compressedSize) { x, y -> x + y }
                if (key != "dex" && key != "assets" && !key.startsWith("lib/")) {
                    val d = n.split('/').take(4).joinToString("/")
                    detail.merge(d, e.compressedSize) { x, y -> x + y }
                }
            }
        }
        println("APKPARTS part: bytes stored in the apk / bytes uncompressed")
        for (k in raw.keys) println("APKPARTS %-24s %12d / %12d".format(k, stored[k], raw[k]))
        println("APKPARTS total apk bytes: ${apk.length()}")
        println("APKPARTS biggest folders outside dex/assets/lib (stored bytes):")
        for ((k, v) in detail.entries.sortedByDescending { it.value }.take(8)) println("APKPARTS   %-60s %10d".format(k, v))
    }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(reportApkParts) }

// E1 Phase 0 (side branch only): smoke-test the SHRUNK release build on the connected emulator. Shrinking can break native
// code or reflection in ways that only show at run time. Installs the release APK (debug-signed in CI), sends a broadcast
// to the spike receiver, and waits for its result line in the log. Runs after the emulator tests (see vault/build.gradle.kts).
val smokeRelease by tasks.registering {
    dependsOn("installRelease")
    doLast {
        val sdk = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        val adb = if (sdk != null) "$sdk/platform-tools/adb" else "adb"
        fun run(vararg a: String): String {
            val p = ProcessBuilder(listOf(adb) + a).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            return out
        }
        run("logcat", "-c")
        val sent = run(
            "shell", "am", "broadcast", "--include-stopped-packages",
            "-n", "com.monsoonscript.keyboard/com.monsoonscript.vault.VaultSmokeReceiver"
        )
        println("SMOKE broadcast: " + sent.trim().replace("\n", " | "))
        var result = "no result line within 90 s"
        for (i in 1..45) {
            val line = run("logcat", "-d", "-s", "VaultSmoke:I").lines().lastOrNull { it.contains("RESULT:") }
            if (line != null) {
                result = line.substringAfter("RESULT:").trim()
                break
            }
            Thread.sleep(2000)
        }
        println("SMOKE release build result: $result")
        if (!result.startsWith("ok")) throw GradleException("release smoke test failed: $result")
    }
}
