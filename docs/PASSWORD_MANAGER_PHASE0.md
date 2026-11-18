# E1 Phase 0 log: feasibility check of the KeePassDX engine (claude-e)

Started 2026-11-18 after the owner's sign-off (D53). Runs on the side branch `e1-feasibility` (D49), which branched from main at 95bfdbc. Nothing here is on main.
Spike pin: KeePassDX 4.5.8 (published Oct 31, 18 days old on 2026-11-18; 4.5.7 was marked unstable and is skipped).
Goal (docs/PASSWORD_MANAGER.md, "NOT yet verified"): does the engine build in OUR CI, package into OUR shrunk APK, and create/save/reload a KDBX with native Argon2?
Result so far: NOTHING BUILT YET. The CI job cannot start until the owner moves one file (STATUS Needs user #5). Everything below is read from the real 4.5.8 source.

## Read from the real source (4.5.8), not yet run
- Engine API is stream based (no Uri or file knowledge): `Database.createData(name, rootName, templateGroupName)`, `loadData(InputStream, MasterCredential, challengeRetriever, readOnly,
  allowUserVerification, cacheDirectory, limits, fixDuplicateUUID, progressTaskUpdater)`, `saveData(cacheFile, () -> OutputStream?, MasterCredential?, challengeRetriever, limits)`
  (it writes to a cache file first, then copies to the stream), `reloadData`, `mergeData(InputStream, MasterCredential?, ...)`, `addEntryTo`, `updateEntry`, `removeEntryFrom`,
  `getEntryById`, `rootGroup`, `kdfEngine` (settable). This fits D36 (our adapter does the atomic swap and backups) and D50 (`reloadData`/`mergeData` exist).
- `MasterCredential` keeps the password as a CharArray that can be wiped (better than Strings for D43's memory hygiene).
- Argon2 is NATIVE ONLY (JNI, `NativeArgon2KeyTransformer`; no Java fallback). AES-KDF has a Java fallback. So proving real KDBX 4 (Argon2) needs an emulator in CI. A new database
  defaults to AES-KDF, so the spike selects Argon2id explicitly.
- `Limits` guards KDF memory (a request may use at most 3/4 of available memory, else the engine refuses instead of crashing): helps the low-RAM-phone concern.
- The KDF engines (`KdfFactory.aesKdf`, `argon2idKdf`) are shared singletons with mutable parameters: ALL vault operations must be serialized (one executor in the vault process).
- Upstream build: AGP 8.13.2, Gradle 8.14.2, Kotlin 2.0.0, compileSdk 36, NDK 25.2.9519653, CMake build of AES + Argon2 for ALL ABIs (no filters), `database` also needs Joda-Time,
  commons-io, BouncyCastle (via crypto). Upstream does NOT shrink its release build (`isMinifyEnabled = false`, no proguard rules); we do (R8): JNI/BouncyCastle/Parcelize under R8 is an
  open risk. Upstream's own tests: JVM Robolectric unit tests in `:database` (no KDBX file opening), emulator tests for crypto (AES, Argon2, signature).
- Our build today: AGP 8.7.3, Gradle 8.10.2 (set in `.github/workflows/build.yml`, no wrapper), Kotlin 2.0.21, compileSdk 35, R8 + resource shrinking on.

## What the spike contains (branch `e1-feasibility`, commit 7f7bbbe + the workflow file)
- `settings.gradle.kts`: imports upstream's `libs.versions.toml` as our catalog (Kotlin overridden to 2.0.21) and includes upstream's `:crypto` and `:database` from `third_party/keepassdx/`.
  In the spike that folder is a plain `git clone --depth 1 --branch 4.5.8` made by CI; the real design (D47) is a git submodule.
- Root `build.gradle.kts` uses catalog aliases (AGP 8.13.2 + Kotlin 2.0.21 + parcelize). `app/build.gradle.kts`: compileSdk 36 and `implementation(project(":vault"))`.
- New module `vault/` (namespace `com.monsoonscript.vault`): `VaultSpike.kt` (the only file touching the engine API: create, save, reload with AES-KDF and Argon2id),
  `VaultSpikeService.kt` (declared with `android:process=":vault"`, D44, so R8 keeps the whole path), and an emulator test `VaultSpikeTest.kt` (native libs load; both round trips).
- `tools/e1-feasibility.workflow.yml`: the CI job (two jobs: release APK build + upstream unit tests; emulator test on API 34 x86_64). Own concurrency group, no release, no `ci-status`.
  It reports to the branches `e1-status-build` and `e1-status-emulator` (file `status.txt`), read with the GitHub tool like `ci-status`.

## Blocker and what is needed
- claude-e's GitHub tool is refused (403) when creating files under `.github/workflows/`. The workflow therefore sits in `tools/`. The owner (or a Claude Code session) must move it to
  `.github/workflows/e1-feasibility.yml` on the branch `e1-feasibility`. A push to that branch then starts the job by itself. (Do NOT use the "Run workflow" button on the main workflow:
  it would publish a release and overwrite `ci-status`.)

## Things Phase 0 must still prove or disprove (in this order)
1. The two upstream modules compile inside our build (AGP 8.13.2 / Gradle 8.14.2 / compileSdk 36, NDK + CMake in the CI image).
2. Our shrunk release APK still contains working engine code and native libs (R8 keep rules may be needed; check missing-class errors in the status file). Measure the APK size growth
   against the latest main APK (the status file prints both) and consider `abiFilters` (arm64-v8a + armeabi-v7a) to avoid shipping four copies of the native libs.
3. On an emulator: native crypto loads, create/save/reload works for AES-KDF and Argon2id.
4. Then, as time allows: real files from KeePassXC (`keepassxc-cli` in CI) with groups, custom fields, attachments and one-time codes; edit, save, and re-open them with KeePassXC itself;
   `mergeData`; a smoke test of the SHRUNK release build on the emulator; RAM of the `:vault` process; whether the Gradle composite-build variant (D47) works.

## Consequences for the rest of the project (to handle when this merges)
- `.github/workflows/build.yml` uses Gradle 8.10.2: it must become 8.14.2 (AGP 8.13.2 needs it). It also has `paths-ignore: third_party/**`, which would stop a KeePassDX submodule bump from
  starting a build (D48): that ignore needs a narrower pattern. It has a shared concurrency group `build-apk` with cancel-in-progress: side-branch jobs must keep their own group.
  Workflow edits need the owner or a Claude Code session (my tool cannot).
- Our `app` moves to compileSdk 36 (needed for the engine modules; targetSdk stays 34). Other Claudes: no source changes are needed, but re-check lint/deprecation warnings after the merge.
- Module ids `:crypto` and `:database` are fixed by upstream (`project(":crypto")`); our own module is `:vault`.
