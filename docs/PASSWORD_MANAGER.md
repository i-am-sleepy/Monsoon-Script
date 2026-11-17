# Password manager inside Monsoon Script (task E1, claude-e)

Status: DESIGN DISCUSSION with the owner. No code, no project-structure changes until the owner signs off on the design (see "Stages").
The owner's original brief is in docs/PASSWORD_MANAGER_BRIEF.md (verbatim, do not edit). Decisions are in DECISIONS.md (D25 onward, one line each;
that file is the single source of what is decided). This file holds verified facts, design proposals, the stage tracker and a parking lot.

## Verified facts (2026-11-17; re-verify before relying on them)
KeePassDX (github.com/Kunzisoft/KeePassDX):
- License: GPL-3.0 (README and repo metadata; file headers say GPL v3 or later). Using its code makes the distributed app a GPL-3.0 work.
- Gradle modules (settings.gradle.kts): `:app`, `:database`, `:crypto`, `:icon-pack` (+ classic, material). Repos used: google, mavenCentral, jitpack.
- `:database` (namespace com.kunzisoft.keepass.database): Android library, compileSdk 36, minSdk 19, Java/Kotlin 17. Depends on Joda-Time, Apache commons-io
  and `api(project(":crypto"))`. Its build file has no publishing setup (no maven-publish), so it is consumed as source, not as a downloaded library.
- `:crypto` (namespace com.kunzisoft.encrypt): Android library with NATIVE code (CMake at `src/main/jni`, ndkVersion 25.2.9519653) and BouncyCastle (pkix).
- The module list suggests autofill, biometrics, Magikeyboard and the UI live in `:app`, not in `:database`/`:crypto`. NOT yet verified in the source.
Our project (ARCHITECTURE.md): AGP 8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 26, D1 = no libraries beyond AndroidX core, no Compose in the IME.
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
(non-AndroidX) BiometricPrompt on API 28+ was NOT checked. The AndroidX biometric library is an extra dependency (see parking lot, D1).
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
- PRINCIPLE: the keyboard never records what the owner types into other apps to build entries. New logins from websites come from the Autofill save prompt (later, D28).
- PLAN (not asked): creating a vault = in-app wizard (name, master password with confirmation and strength hint, optional key file, location), upstream-default encryption
  (KDBX 4; read the real defaults from the source and pick settings that open in about a second on the owner's phone), a clear "no recovery if the master password is forgotten" warning.
  Lock = keys and the opened database wiped from memory. Honest limit: the JVM cannot guarantee that every copy of a password string is wiped from memory; keep lifetimes short.
- PLAN (not asked, owner may object): the picker types credentials straight into the field, so the clipboard is not involved; vault screens' copy buttons are the only clipboard use;
  a copied secret must never land in the keyboard's own clipboard history (strip clipboard icon, v0.3); mark copies sensitive where Android supports it; block screenshots and the
  recents preview on vault and unlock screens (FLAG_SECURE); passwords shown masked, revealed only on tap, never shown in the picker; the vault code makes no network calls at all
  (a CI check; D10's Offline mode stays independent); no telemetry.
- PROPOSAL (asked, stage 5b): copied secrets are cleared from the clipboard after 30 seconds (only if the clipboard still holds our copy); timeout becomes a setting.
- PROPOSAL (asked, stage 5b): the vault engine runs in its own Android process. For: typing process stays as light and fast as today (heavy libraries load only when the vault is used);
  keys and the opened database live outside the keyboard process. Against: more plumbing (the keyboard talks to the vault through an Android bound service), more to test, some extra RAM
  while the vault is open. Alternative: one process (simpler, but the keyboard process carries BouncyCastle, native code and the engine). Can be revisited after the feasibility check.

## Stages (brief section 6). Each: explain, recommend, ask, wait for the answer, record a D-line in DECISIONS.md.
1 [x] Integration approaches (D25, D26)   2 [x] How it meets the keyboard (D27, D28)   3 [x] UI (D29-D34)   4 [x] Database lifecycle (D35-D37)
5a [x] Security: auto-lock, fingerprint policy, entry tap behavior (D38-D40)
5b [~] Security: clipboard timeout and process isolation asked; rest of the plan above announced (STATUS Needs user #5)
6 [ ] Reuse vs own code (incl. D1 exception for the vault process)   7 [ ] Integration and update strategy
8 [ ] User journey (switch apps, lock phone, close keyboard) + phasing
Then: owner signs off -> inspect our project and the upstream source -> feasibility spike -> build.

## Parking lot (raise at the right stage)
- D1 says no libraries beyond AndroidX core. The vault needs more (KeePassDX modules, BouncyCastle, Joda-Time, commons-io, AndroidX biometric + fragment). Propose: D1 keeps
  applying to the IME/typing process; the vault process and vault screens get a documented exception. Ask the owner. (stage 6/7)
- Cloud sync without network code is settled by D35. Merge behavior when the file changed elsewhere: stage 7/8.
- Unlock screen: keep the master-password field a proper password field; FLAG_SECURE (stage 5b plan).
- Heavy key-derivation settings in someone else's database (e.g. very high Argon2 memory) can be slow or fail on a low-RAM phone: show a clear message. (stage 8 / implementation)
- Risky build changes (submodule, AGP bump, NDK) could turn main red for the other three Claudes: ask the owner whether to try them on a side branch. (stage 7)
- A key icon in the strip touches KeyboardView strip code that claude-l is changing (T8b.4, D23): warn claude-l before editing it (implementation phase).
