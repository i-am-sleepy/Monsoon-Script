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

## Design. Items with a D-line are decided; the rest are proposals.
- D29 picker = panel replacing the key rows (same height), typing mode feeds the panel's field only. D30 full screens in the keyboard's style. D31 mini form for adding.
- D32 first view = matches for this app, then recent (app id from EditorInfo; entries added from the keyboard store it; how existing KeePassDX databases store app
  links: verify in source). D33 one list screen with a menu. D34 Vault card + launcher long-press shortcut.
- D35 vault file picked with Android's file picker. D36 automatic safe saving with backups, no silent overwrite. D37 unlock via a brief see-through screen.
- PRINCIPLE: the keyboard never records what the owner types into other apps to build entries. New logins from websites come from the Autofill save prompt (later, D28).
- PLAN (not asked): creating a vault = in-app wizard (name, master password with confirmation and strength hint, optional key file, location), upstream-default encryption
  (KDBX 4), a clear "no recovery if the master password is forgotten" warning. Lock = keys and the opened database wiped from memory.
- PLAN (stage 5b, not yet asked): a copied password must never land in the keyboard's own clipboard history (the strip has a clipboard icon, v0.3); auto-clear the clipboard
  after a short time; mark copied secrets as sensitive where Android supports it (Android 13+, verify); block screenshots and the recents preview on vault and unlock screens;
  the vault code makes no network calls (CI check, D10); vault engine in its own Android process (trade-off: lighter typing process and secrets outside it vs more plumbing and
  extra RAM while open); show passwords hidden by default, reveal only on tap.
- PROPOSAL (asked, stage 5a): auto-lock default = when the screen turns off AND after 5 minutes without use; manual lock button always available; never on "keyboard closed" (too
  frequent). Timeout becomes a setting.
- PROPOSAL (asked, stage 5a): fingerprint = convenience, never the root: master password first; fingerprint then opens a copy of the unlock secret wrapped by a hardware-backed
  Android Keystore key that needs a fingerprint check; master password required again after a phone restart, after 3 days, or after the phone's fingerprints change. Each rule must be
  confirmed enforceable before we rely on it.
- PROPOSAL (asked, stage 5a): tapping an entry shows chips (Username / Password / Code); tapping a chip types it; typing a password into a field that is not a password field asks first.
  One-tap fill (username, Tab, password) could be an optional setting later.

## Stages (brief section 6). Each: explain, recommend, ask, wait for the answer, record a D-line in DECISIONS.md.
1 [x] Integration approaches (D25, D26)   2 [x] How it meets the keyboard (D27, D28)   3 [x] UI (D29-D34)   4 [x] Database lifecycle (D35-D37)
5a [~] Security: auto-lock default, fingerprint policy, entry tap behavior asked (STATUS Needs user #5)
5b [ ] Security: clipboard, process isolation, network guard, screenshots, hidden passwords (PLAN above, to be explained and asked)
6 [ ] Reuse vs own code (incl. D1 exception for the vault process)   7 [ ] Integration and update strategy
8 [ ] User journey (switch apps, lock phone, close keyboard) + phasing
Then: owner signs off -> inspect our project and the upstream source -> feasibility spike -> build.

## Parking lot (raise at the right stage)
- D1 says no libraries beyond AndroidX core. The vault needs more (KeePassDX modules, BouncyCastle, Joda-Time, commons-io, AndroidX biometric + fragment). Propose: D1 keeps
  applying to the IME/typing process; the vault process and vault screens get a documented exception. Ask the owner. (stage 6/7)
- Cloud sync without network code is settled by D35. Merge behavior when the file changed elsewhere: stage 7/8.
- Unlock screen: keep the master-password field a proper password field; FLAG_SECURE (stage 5b).
- Heavy key-derivation settings in someone else's database (e.g. very high Argon2 memory) can be slow or fail on a low-RAM phone: show a clear message. (stage 8 / implementation)
- Risky build changes (submodule, AGP bump, NDK) could turn main red for the other three Claudes: ask the owner whether to try them on a side branch. (stage 7)
- A key icon in the strip touches KeyboardView strip code that claude-l is changing (T8b.4, D23): warn claude-l before editing it (implementation phase).
