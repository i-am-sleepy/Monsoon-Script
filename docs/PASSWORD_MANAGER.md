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
- Folder names read on GitHub (names only, code NOT yet read):
  `:database` = `com/kunzisoft/keepass/` { database { crypto, element, exception, file, merge, search }, hardware, model, otp, stream, tasks, utils }.
  `database/element` has Attachment, CustomData, CustomDataItem, Field, Entry, Group, Tags, CompositeKey, MasterCredential, DateInstant, DeletedObject plus folders
  binary, entry, group, icon, node, security, template. `otp` has OtpElement, OtpEntryFields, TokenCalculator.
  `:app` = `com/kunzisoft/keepass/` { activities, adapters, app, backup, biometric, credentialprovider (autofill, magikeyboard, passkey, activity, viewmodel), database,
  education, icons, model, password, receivers, services, settings, tasks, timeout, utils, view, viewmodels }.
  So: autofill, Magikeyboard, passkeys, biometric unlock, backup, auto-lock timeout and all screens are in `:app` (verified by folder names), NOT in the reusable modules.
  One-time codes, attachments, custom fields, tags, templates, search and merge exist in `:database` by name; that they work for our use is NOT yet verified.
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
- D43 clipboard and exposure rules (30 s clear, never in the keyboard's clipboard history, sensitive flag, no screenshots, masked passwords, no network/telemetry).
  D44 vault engine in its own Android process.
- PRINCIPLE: the keyboard never records what the owner types into other apps to build entries. New logins from websites come from the Autofill save prompt (later, D28).
- PLAN (not asked): creating a vault = in-app wizard (name, master password with confirmation and strength hint, optional key file, location), upstream-default encryption
  (KDBX 4; read the real defaults from the source and pick settings that open in about a second on the owner's phone), a clear "no recovery if the master password is forgotten" warning.
  Lock = keys and the opened database wiped from memory. Honest limit: the JVM cannot guarantee that every copy of a password string is wiped from memory; keep lifetimes short.
- PROPOSAL (asked, stage 6) reuse map. REUSE untouched through the modules: KDBX read/write, encryption, entry/group model, attachments, custom fields, tags, templates,
  one-time codes, search, merge (to see whether it fits D36). WRITE OURSELVES: vault service in its own process, our thin interface, keyboard panel + typing mode, the app screens,
  unlock screen + Keystore/fingerprint + 3-day rule, auto-lock timers, file picker + safe saving + backups, clipboard handling, later the Autofill service + save prompt + inline suggestions.
  RULE: no copying of KeePassDX `:app` code (reading it for guidance is fine); any copy needs the owner's OK, case by case.
- PROPOSAL (asked, stage 6): D1 keeps applying to the keyboard/typing process; the vault process and vault screens may use the libraries they need (KeePassDX modules, BouncyCastle,
  Joda-Time, commons-io, AndroidX biometric + fragment). Without this exception the vault would have to be rebuilt without them: far more work and risk.

## Stages (brief section 6). Each: explain, recommend, ask, wait for the answer, record a D-line in DECISIONS.md.
1 [x] Integration approaches (D25, D26)   2 [x] How it meets the keyboard (D27, D28)   3 [x] UI (D29-D34)   4 [x] Database lifecycle (D35-D37)
5 [x] Security (D38-D40, D43, D44)
6 [~] Reuse vs own code: D1 exception and the reuse rule asked (STATUS Needs user #5)
7 [ ] Integration and update strategy   8 [ ] User journey (switch apps, lock phone, close keyboard) + phasing (feature priorities: one-time codes, attachments, custom fields, backup/export)
Then: owner signs off -> inspect our project and the upstream source -> feasibility spike -> build.

## Parking lot (raise at the right stage)
- claude-l's D41/D42: suggestions now show whenever there are any and hide the icon row (arrow button swaps back). The key icon lives in the icon row: visible in password fields
  (no suggestions there), otherwise one tap on the arrow first. Check this feels right when the picker is built.
- Prediction/learning (D42) must NEVER learn text the vault types (picker output via commitText) or text typed in the panel's typing mode (search, add form). Tell claude-l
  at implementation time (or make the picker bypass the learning pipeline).
- Cloud sync without network code is settled by D35. Merge behavior when the file changed elsewhere: stage 7/8.
- Unlock screen: keep the master-password field a proper password field; FLAG_SECURE (D43).
- Heavy key-derivation settings in someone else's database (e.g. very high Argon2 memory) can be slow or fail on a low-RAM phone: show a clear message. (stage 8 / implementation)
- Risky build changes (submodule, AGP bump, NDK) could turn main red for the other three Claudes: ask the owner whether to try them on a side branch. (stage 7)
- A key icon in the strip touches KeyboardView strip code that claude-l is changing (T8b.4, D23, D41): warn claude-l before editing it (implementation phase).
