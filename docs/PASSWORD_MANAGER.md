# Password manager inside Monsoon Script (task E1, claude-e)

Status: DESIGN DISCUSSION with the owner. No code, no project-structure changes until the owner signs off on the design (see "Stages").
The owner's original brief is in docs/PASSWORD_MANAGER_BRIEF.md (verbatim, do not edit). Decisions are in DECISIONS.md (D25-D40 and D43 onward are E1; D41-D42 are claude-l's;
that file is the single source of what is decided). This file holds verified facts, design proposals, the stage tracker and a parking lot.

## Verified facts (2026-11-17; re-verify before relying on them)
KeePassDX (github.com/Kunzisoft/KeePassDX):
- License: GPL-3.0 (README and repo metadata; file headers say GPL v3 or later). Using its code makes the distributed app a GPL-3.0 work.
- Gradle modules (settings.gradle.kts): `:app`, `:database`, `:crypto`, `:icon-pack` (+ classic, material). Repos used: google, mavenCentral, jitpack.
- `:database` (namespace com.kunzisoft.keepass.database): Android library, compileSdk 36, minSdk 19, Java/Kotlin 17. Depends on Joda-Time, Apache commons-io
  and `api(project(":crypto"))`. Its build file has no publishing setup (no maven-publish), so it is consumed as source, not as a downloaded library.
- `:crypto` (namespace com.kunzisoft.encrypt): Android library with NATIVE code (CMake at `src/main/jni`, ndkVersion 25.2.9519653) and BouncyCastle (pkix).
  `:database` and `:crypto` live in one repo and one release, so they are updated together, never separately.
- Folder names read on GitHub (names only, code NOT yet read):
  `:database` = `com/kunzisoft/keepass/` { database { crypto, element, exception, file, merge, search }, hardware, model, otp, stream, tasks, utils }.
  `database/element` has Attachment, CustomData, CustomDataItem, Field, Entry, Group, Tags, CompositeKey, MasterCredential, DateInstant, DeletedObject plus folders
  binary, entry, group, icon, node, security, template. `otp` has OtpElement, OtpEntryFields, TokenCalculator.
  `:app` = `com/kunzisoft/keepass/` { activities, adapters, app, backup, biometric, credentialprovider (autofill, magikeyboard, passkey, activity, viewmodel), database,
  education, icons, model, password, receivers, services, settings, tasks, timeout, utils, view, viewmodels }.
  So: autofill, Magikeyboard, passkeys, biometric unlock, backup, auto-lock timeout and all screens are in `:app` (verified by folder names), NOT in the reusable modules.
  One-time codes, attachments, custom fields, tags, templates, search and merge exist in `:database` by name; that they work for our use is NOT yet verified.
- Release rhythm (GitHub releases, read 2026-11-17): 4.5.3 on Sep 4, 4.5.4 Sep 10, 4.5.5 Sep 25, 4.5.6 Oct 17, 4.5.7 Oct 31 (titled "Unstable"), 4.5.8 Oct 31 (replaces 4.5.7
  the same day). Roughly one release every 1-3 weeks. Notes mention fixes that sound engine-level (duplicate IDs, one-time-code fields, deleting history, an error message when the
  database file was changed from outside = relevant to D36).
- Dependabot supports `gitsubmodule` (GitHub docs list it). A project's own dependabot.yml comment (citing dependabot-core issue 1639) says it cannot update only to tags or follow
  semver, i.e. it follows the tracked branch's newest commit. NOT yet confirmed by us. If true, use our own scheduled GitHub Actions workflow that watches KeePassDX RELEASES instead.
Our project (ARCHITECTURE.md): AGP 8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 26, D1 = no libraries beyond AndroidX core (D45: exception for the vault), no Compose in the IME.
Gap to check: upstream modules want compileSdk 36 (probably a newer AGP), NDK/CMake in CI, BouncyCastle + Joda-Time + commons-io in our dependency list.
Alternative engine (fallback in D26): KeePassJava2 (github.com/jorabin/KeePassJava2): Apache-2.0, Maven Central, KDBX 3 and 4 with Argon2, "Android compatible",
pure Java, single maintainer, history of neglect and "back in development" since May 2023. NOT yet verified: current maintenance, Argon2 speed on phones,
KDBX 4.1, TOTP/attachments/custom fields API.
Android autofill and keyboards (developer.android.com "Integrate autofill with IMEs and autofill services"):
- From Android 11 (API 30) an autofill service can show its suggestions inside the keyboard's strip. The IME opts in (`supportsInlineSuggestions` in its XML,
  `InputMethodService.onCreateInlineSuggestionsRequest`, `onInlineSuggestionsResponse`). Without support on either side (or before Android 11) a dropdown menu is shown.
- The keyboard cannot see what an inline suggestion contains until the user taps it.
Android biometrics (developer.android.com, androidx.biometric.BiometricPrompt): the constructors take a FragmentActivity or a Fragment, so the system fingerprint dialog
needs an Activity. The usual workaround for non-activity callers is a transparent (translucent) Activity shown over the current app. The platform
(non-AndroidX) BiometricPrompt on API 28+ was NOT checked. The AndroidX biometric library is an extra dependency (D45).
Avro license check: only `AvroPhoneticConverter.kt` was read: standard MPL-2.0 notice, no "Incompatible With Secondary Licenses" marker. Check every Avro file
when the LICENSE file is added.
NOT yet verified (from general knowledge, check before relying on it): Android 13+ lets an app mark copied text as sensitive (`ClipDescription.EXTRA_IS_SENSITIVE`) so the
clipboard preview is hidden; Keystore keys can be bound to biometric authentication and invalidated on new fingerprint enrollment; KeePassDX's default encryption settings.

## Design. Items with a D-line are decided; the rest are proposals.
- D29 picker = panel replacing the key rows (same height), typing mode feeds the panel's field only. D30 full screens in the keyboard's style. D31 mini form for adding.
- D32 first view = matches for this app, then recent (app id from EditorInfo; entries added from the keyboard store it; how existing KeePassDX databases store app
  links: verify in source). D33 one list screen with a menu. D34 Vault card + launcher long-press shortcut.
- D35 vault file picked with Android's file picker. D36 automatic safe saving with backups, no silent overwrite. D37 unlock via a brief see-through screen.
- D38 auto-lock = screen off + 5 min idle, Lock button, not on keyboard close. D39 fingerprint = convenience only; master password after restart / 3 days / new fingerprint.
  D40 entry tap = chips + ask before typing into a non-password field.
- D43 clipboard and exposure rules. D44 vault engine in its own Android process. D45 library exception for the vault. D46 reuse rule = judgment per case, engine untouched,
  copies listed in docs/UPSTREAM_COPIES.md.
- PRINCIPLE: the keyboard never records what the owner types into other apps to build entries. New logins from websites come from the Autofill save prompt (later, D28).
- PLAN (not asked): creating a vault = in-app wizard (name, master password with confirmation and strength hint, optional key file, location), upstream-default encryption
  (KDBX 4; read the real defaults from the source and pick settings that open in about a second on the owner's phone), a clear "no recovery if the master password is forgotten" warning.
  Lock = keys and the opened database wiped from memory. Honest limit: the JVM cannot guarantee that every copy of a password string is wiped from memory; keep lifetimes short.
- PROPOSAL (asked, stage 7) how KeePassDX is built in. A: git submodule in `third_party/keepassdx` pinned to a RELEASE TAG, untouched; our Gradle includes `:database` and `:crypto` from
  it; all our code talks to the engine only through our thin interface (D26) in one package, so an upstream API change means fixing one adapter. B: copy the two modules into our repo
  (we own every diff, updates are manual, upstream history lost). C: our own fork of the two modules published as a library (cleanest for the app, but a fork + publishing pipeline to maintain).
  To test in the feasibility check: Gradle composite build (`includeBuild`) of the upstream project as a variant of A.
- PROPOSAL (asked, stage 7) updates. A scheduled GitHub Actions workflow (weekly) looks at KeePassDX RELEASES, ignores any release younger than 7 days (4.5.7 -> 4.5.8 shows why), then opens a
  branch that moves the pin, builds, and runs the compatibility tests; the result is reported and the owner decides to merge. Later, auto-merge when all tests pass, once the tests are trusted.
  Compatibility tests: a set of small .kdbx test files (fake passwords) made by KeePassXC, KeePassDX and others (different ciphers and key derivations, groups, attachments, custom fields,
  one-time codes, key file, recycle bin); open, read, edit, save, reopen, compare. Because the engine has native code, these may need an emulator in CI (to verify). Tests are an early
  warning, NOT a guarantee of compatibility.
- PROPOSAL (asked, stage 7) the first integration (submodule, build-tool upgrade, native build) is done on a side branch until CI is green, then merged, so main stays green for the other three
  Claudes. This departs from D2 (everyone commits straight to main) for this one piece of work.

## Stages (brief section 6). Each: explain, recommend, ask, wait for the answer, record a D-line in DECISIONS.md.
1 [x] Integration approaches (D25, D26)   2 [x] How it meets the keyboard (D27, D28)   3 [x] UI (D29-D34)   4 [x] Database lifecycle (D35-D37)
5 [x] Security (D38-D40, D43, D44)   6 [x] Reuse vs own code (D45, D46)
7 [~] Integration and update strategy: how it is built in, how updates are checked, side branch for the first integration (STATUS Needs user #5)
8 [ ] User journey (switch apps, lock phone, close keyboard) + phasing (feature priorities: one-time codes, attachments, custom fields, backup/export; merge behavior for D36)
Then: owner signs off -> inspect our project and the upstream source -> feasibility spike -> build.

## Parking lot (raise at the right stage)
- claude-l's D41/D42: suggestions now show whenever there are any and hide the icon row (arrow button swaps back). The key icon lives in the icon row: visible in password fields
  (no suggestions there), otherwise one tap on the arrow first. Check this feels right when the picker is built.
- Prediction/learning (D42) must NEVER learn text the vault types (picker output via commitText) or text typed in the panel's typing mode (search, add form). Tell claude-l
  at implementation time (or make the picker bypass the learning pipeline).
- Merge behavior when the file changed elsewhere (D36): stage 8; KeePassDX has a `merge` folder in `:database` to evaluate.
- Unlock screen: keep the master-password field a proper password field; FLAG_SECURE (D43).
- Heavy key-derivation settings in someone else's database (e.g. very high Argon2 memory) can be slow or fail on a low-RAM phone: show a clear message. (stage 8 / implementation)
- A key icon in the strip touches KeyboardView strip code that claude-l is changing (T8b.4, D23, D41): warn claude-l before editing it (implementation phase).
