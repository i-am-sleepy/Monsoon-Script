# E1 Phase 0 log: feasibility check of the KeePassDX engine (claude-e)

Started 2026-11-18 after the owner's sign-off (D53). Ran on the side branch `e1-feasibility` (D49). NOTHING from Phase 0 is on main.
Spike pin: KeePassDX 4.5.8 (published Oct 31; the unstable 4.5.7 is skipped). Final green run: #12, commit 8b6ace1 (branch `e1-feasibility`).

## MERGED INTO MAIN on 2026-11-21 (D64): submodule pinned to 4.5.8, `:vault` adapter + the 14 emulator tests, `engine.yml`, AGP 8.13.2 / Gradle 8.14.2. The checklist at the end of this file is DONE except the
## items that need Phase 1 (the app depends on `:vault`, the `:vault` process, weekly update watcher, LICENSE file). Branch `e1-feasibility` is kept only as history.

## RESULT: GO (the owner approved: D64)
Every item the design listed as "NOT yet verified" for the engine is now verified, except the few listed under "Not verified yet" below.

### What was proven (all in OUR build and on an Android 14 x86_64 emulator, KeePassDX 4.5.8 used UNEDITED)
1. The upstream modules `:database` and `:crypto` compile inside our project with AGP 8.13.2, Gradle 8.14.2, Kotlin 2.0.21 (override of upstream's 2.0.0), compileSdk 36, NDK 25.2.9519653,
   CMake 3.22.1. The native code (AES and Argon2) builds for all four CPU types. Upstream's own JVM unit tests pass inside our build.
2. 14 instrumented tests on the emulator pass:
   - native crypto loads; create, save, reload a KDBX with AES-KDF and with Argon2id, each with AES, Twofish and ChaCha20 (the KDBX header really is version 4);
   - content survives save + reload: username, password with Bangla (Unicode) text, URL, multi-line notes, a protected custom field (the protected flag is kept), KeePassDX's app-link field
     `AndroidApp` (understood by `getAppOrigin()`, so D32 can read existing KeePassDX databases), a KeePassXC-style `otp` field (a 6-digit code is produced);
   - TOTP codes equal the official RFC 6238 test values for SHA-1, SHA-256 and SHA-512;
   - merging two diverged copies of a vault gives the union of the entries (`mergeData`, for D50);
   - THIRD-PARTY files: a KDBX 4 vault made by pykeepass 4.2.0 (independent implementation; Argon2d + AES-256) with nested folders, Bangla password, custom + protected fields, one-time code,
     tags, history, attachment: opens, reads correctly, and after our engine adds an entry and saves, EVERYTHING survives (no data loss). A password + key-file vault opens; without the key file it is refused;
     a wrong password is refused;
   - a REAL KeePassXC 2.7.6 vault (KDBX 3.1, AES-KDF with about 33 million rounds, nested folders, a gzip-compressed attachment, Bangla title and password): opens, reads, survives a resave;
     a KeePassXC vault with password + XML v2 key file opens, and is refused without the key file.
3. The SHRUNK release build works. Our R8 shrinker broke the engine at first (two real findings, both fixed with rules in `app/proguard-rules.pro`, see below). After the fix, the release APK installed on the
   emulator creates, saves and reloads vaults with AES-KDF and with Argon2id x AES/Twofish/ChaCha20 (smoke test by broadcast).
4. Size cost (release APK, spike run #12, total 14,064,386 bytes): native libraries 1,422,576 bytes for four CPU types (712,032 for arm64-v8a + armeabi-v7a only), code (dex) +513,887 bytes,
   Joda-Time time-zone data 120,505 bytes: about 2.2 MB, about 1.5 MB if only the two ARM types are shipped. Excluding BouncyCastle's unused post-quantum data files saved a further 1.2 MB
   (they were 1,214,815 bytes). Do not compare with the "latest" APK on main: it grew past 22 MB because of other Claudes' prediction data; compare by parts (the status file prints them).

### Real problems found and how they were solved (these are the value of Phase 0)
- R8 error "Missing class org.joda.convert.FromString/ToString": Joda-Time refers to annotations that are not shipped. Fix: `-dontwarn org.joda.convert.FromString` and `ToString`.
- Shrunk build failed at run time with "NoSuchAlgorithmException: Blowfish KeyGenerator not available": the engine encrypts its attachment cache with Blowfish (`LoadedKey.kt`) through BouncyCastle,
  which registers its algorithms by class-name strings; R8 removed those classes. Fix: keep rules for `BouncyCastleProvider`, `jcajce.provider.config/util`, `symmetric.util`, and only the algorithm families
  Blowfish, Twofish and ChaCha (keeping all of BouncyCastle would cost far more space). Also keep `com.kunzisoft.encrypt.**` (the JNI bridge looks classes up by name).
- The engine's `DatabaseException` keeps the real cause in its own field `mThrowable`, NOT in the standard `cause`: our adapter must unwrap it (the smoke receiver shows how).
- KDBX 3.1 attachments are gzip-compressed: read them with `BinaryData.getUnGzipInputDataStream(binaryCache)`, never `getInputDataStream` (my first test did, and the 0x1F gzip byte even crashed Gradle's
  test-report parser).
- `-dontobfuscate` is on in the spike (needed to read the error names). Decide at merge: for an open-source app it costs little and makes crash reports readable.

### Engine facts to design Phase 1 with (read from the 4.5.8 source, then confirmed by the tests)
- API is stream based: `Database.createData`, `loadData(InputStream, MasterCredential, challengeRetriever, readOnly, allowUserVerification, cacheDirectory, limits, fixDuplicateUUID, progress)`,
  `saveData(cacheFile, () -> OutputStream, MasterCredential?, retriever, limits)` (writes a cache file first, then copies), `reloadData`, `mergeData`, `createEntry`, `addEntryTo`, `getChildEntries/Groups`,
  `kdfEngine` and `encryptionAlgorithm` (settable). Entry has plain properties (`title`, `username`, `password` as CharArray, `url`, `notes`), `putExtraField`, `getExtraFields`, `getOtpElement`, `getAppOrigin`,
  `getHistory`, `tags`, `getAttachments(db.attachmentPool)`. A key file is passed as its raw bytes in `MasterCredential(password, keyFileBytes)` (the engine understands KeePassXC's XML v2 format).
- New databases default to AES-KDF; Argon2 is native-only (no Java fallback), so KDBX 4 with Argon2 only works on a device or emulator, never in a plain JVM test.
- The KDF engines are shared singletons: serialize ALL vault operations (one executor in the vault process). `Limits` refuses KDF settings that need more than 3/4 of free memory instead of crashing.
- `Database` is `open`; `DatabaseInfo` extends it. KeePassDX stores an app link as the custom field `AndroidApp` (package name, optionally `AndroidApp Signature`) plus the URL field: this is exactly what D32 needs.

## Where the spike is (branch `e1-feasibility`, never to be merged as it is)
- `settings.gradle.kts` (upstream's version catalog with Kotlin overridden to 2.0.21; includes `:crypto` and `:database` from `third_party/keepassdx/`, which CI clones at tag 4.5.8),
  root `build.gradle.kts` (catalog aliases), `app/build.gradle.kts` (compileSdk 36, `implementation(project(":vault"))`, the BouncyCastle resource exclusion, and SPIKE-ONLY tasks `reportApkParts` and
  `smokeRelease`), `app/proguard-rules.pro` (the rules above), module `vault/` (adapter `VaultSpike.kt`, `VaultSpikeService` in process `:vault`, SPIKE-ONLY exported `VaultSmokeReceiver`, 14 emulator tests in 4 classes,
  test vaults as base64 assets in `vault/src/androidTest/assets/fixtures/`), `.github/workflows/e1-feasibility.yml` (added by the owner) and `tools/e1-feasibility.workflow.yml` (copy).
- Results are written by the job to the branches `e1-status-build` and `e1-status-emulator` (file `status.txt`). The unauthenticated GitHub API is rate-limited from my sandbox; read raw files instead.

## Not verified yet (honest list)
- The vault engine running inside the real `:vault` PROCESS and called over a bound service (the smoke test ran in the main process); RAM of that process; timing on a real phone (the owner's phone is slower
  than the emulator host; Argon2 settings of real-world vaults should be timed there).
- A file written by OUR engine opened by KeePassXC/pykeepass (the other direction). Needs a workflow change to move the file off the emulator, or a host-side step.
- The Gradle composite-build variant and a real git submodule (D47); CI clones the pinned tag instead. attachments larger than a few bytes, history restore, the `search` package, templates, passkeys (not planned).
- Android-side pieces that are not engine questions: BiometricPrompt from the vault process, the Android 13+ sensitive-clipboard flag, Keystore rules (D39), see-through unlock screen (D37).

## What bringing this onto main needs (nobody has done any of it; it touches shared files, so only after the owner says go)
- `.github/workflows/build.yml` (my tool cannot edit workflow files; the owner or a Claude Code session must): Gradle 8.14.2 instead of 8.10.2; a step that fetches KeePassDX at the pinned tag into `third_party/keepassdx`
  (clone as in the spike, or `submodules: recursive` once a real submodule exists); `sdkmanager "platforms;android-36" "ndk;25.2.9519653" "cmake;3.22.1"` (continue-on-error); `paths-ignore: third_party/**` must be narrowed
  to the folders that really are docs/data (otherwise a KeePassDX version bump would not start a build); optionally a separate emulator job running `:vault:connectedDebugAndroidTest`.
- Repo files: the settings/root/app Gradle changes above (compileSdk 36 for the app; targetSdk stays 34), the `:vault` module without the spike-only pieces, the proguard rules, the packaging exclusion.
  The build still ends with the same release APK path, signing and rolling release; the other Claudes' source needs no change (they may see new lint/deprecation warnings at compileSdk 36).
- The real submodule (a gitlink) cannot be created with my tool either: a Claude Code session or the owner's git must run `git submodule add` at tag 4.5.8. Until then the CI-clone approach works and keeps the pin in one line.
- D48's weekly watcher needs a workflow file too (same limitation).
