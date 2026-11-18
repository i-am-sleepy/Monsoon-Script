# Password manager inside Monsoon Script (task E1, claude-e)

Status: DESIGN TALK DONE (stages 1-8 answered by the owner, D25-D52). Waiting for the owner's SIGN-OFF of the summary below. No code and no project-structure changes before sign-off.
The owner's original brief is in docs/PASSWORD_MANAGER_BRIEF.md (verbatim, do not edit). DECISIONS.md is the single source of what is decided (D25-D40 and D43-D52 are E1; D41-D42 are claude-l's).

## Agreed design (sign-off summary, 2026-11-17)
- Foundation: KeePassDX `:database` + `:crypto` as an UNEDITED git submodule pinned to a release tag (D26, D47) behind a thin interface of our own; app is GPL-3.0 (D25). Fallback if the
  feasibility check fails: KeePassJava2 (D26). Reuse of KeePassDX `:app` code = judgment per case, listed in docs/UPSTREAM_COPIES.md (D46).
- Updates: weekly watcher of KeePassDX releases, 7-day wait, test branch with build + compatibility tests, the owner decides (D48). First integration on a side branch (D49).
- Process: vault engine in its own Android process (D44); extra libraries allowed there, D1 still rules the typing process (D45).
- Keyboard: key icon in the strip (D27); panel replaces the keys (D29); first view = matches for this app, then recent (D32); tap entry = chips + warning before typing a password into a
  non-password field (D40); mini add form (D31); unlock via a brief see-through screen (D37).
- App: Vault card + launcher shortcut (D34); one list screen (D33); keyboard's own look (D30).
- File: the owner picks it with Android's file picker (D35); one vault at first (D52); automatic safe saving with backups (D36); file changed elsewhere = ask the owner every time (D50).
- Security: auto-lock on screen off + 5 minutes idle (D38); fingerprint is a convenience, master password after restart / 3 days / new fingerprint (D39); clipboard 30 s, never in the
  keyboard's clipboard history, no screenshots, masked passwords, no network, no telemetry (D43).
- Build order (D51): Phase 0 feasibility check (side branch) -> Phase 1 core vault in the app -> Phase 2 keyboard picker -> Phase 3 fingerprint, backup/restore screens, attachments,
  one-time codes -> Phase 4 Autofill + save prompt + inline suggestions.
- NOT yet verified (Phase 0 must prove or disprove): the two modules build in our CI (native code, NDK/CMake, compileSdk 36 vs our 35, probably a newer AGP); they open test .kdbx files
  from KeePassXC/KeePassDX; APK size and RAM; one-time codes, attachments, custom fields and merge work for our use; how KeePassDX stores app links for matching (D32); the platform
  BiometricPrompt from a service; Android 13+ sensitive-clipboard flag; Keystore rules of D39; whether compatibility tests need an emulator in CI; Dependabot's tag handling (we use our own
  workflow anyway).
- Details settled at build time and reported to the owner: wording and choices of the file-changed question (D50) and where pending changes are held meanwhile; which Android versions get
  which features; final APK size.

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
  database file was changed from outside = relevant to D36/D50).
- Dependabot supports `gitsubmodule` (GitHub docs list it). A project's own dependabot.yml comment (citing dependabot-core issue 1639) says it cannot update only to tags or follow
  semver, i.e. it follows the tracked branch's newest commit. NOT yet confirmed by us. Hence D48 uses our own workflow that watches KeePassDX RELEASES.
Our project (ARCHITECTURE.md): AGP 8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 26, D1 = no libraries beyond AndroidX core (D45: exception for the vault), no Compose in the IME.
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

## Design details not covered by a single D-line
- PRINCIPLE: the keyboard never records what the owner types into other apps to build entries. New logins from websites come from the Autofill save prompt (later, D28).
- Creating a vault = in-app wizard (name, master password with confirmation and strength hint, optional key file, location), upstream-default encryption (KDBX 4; read the real
  defaults from the source and pick settings that open in about a second on the owner's phone), a clear "no recovery if the master password is forgotten" warning. Lock = keys and the
  opened database wiped from memory. Honest limit: the JVM cannot guarantee that every copy of a password string is wiped from memory; keep lifetimes short.
- Journeys: first setup = Vault card, create or open a vault (file picker), master password, optional fingerprint. Everyday = field, arrow if suggestions show, key icon; locked ->
  Unlock -> see-through screen -> back to the picker; tap entry -> chips; the panel stays on the same entry when moving to the next field in the same app. Switching apps or hiding the
  keyboard closes the panel but the vault stays unlocked until screen-off or 5 minutes idle (D38). Phone restart = locked, master password once (D39). Android may kill the vault process
  when memory is low: that simply locks the vault (safe). Unlock cancelled = picker stays locked.

## Stages (brief section 6)
1-8 [x] all answered (D25-D52). Next: owner sign-off -> inspect our project and the upstream source -> Phase 0 feasibility check on a side branch (D49) -> build in phases (D51).

## Parking lot (raise at the right moment)
- claude-l's D41/D42: suggestions now show whenever there are any and hide the icon row (arrow button swaps back). The key icon lives in the icon row: visible in password fields
  (no suggestions there), otherwise one tap on the arrow first. Check this feels right when the picker is built.
- Prediction/learning (D42) must NEVER learn text the vault types (picker output via commitText) or text typed in the panel's typing mode (search, add form). Tell claude-l
  at implementation time (or make the picker bypass the learning pipeline).
- CI on the side branch (D49): the existing workflow publishes the rolling release and updates `ci-status`; the side branch must be excluded from both. Check the workflow triggers first.
- File-changed question (D50) while only the keyboard panel is open: the panel cannot show the full question; it should show "file changed, tap to review" and open the app. Pending changes
  must survive until answered (possibly a safety copy next to the file). Design at build time.
- Unlock screen: keep the master-password field a proper password field; FLAG_SECURE (D43).
- Heavy key-derivation settings in someone else's database (e.g. very high Argon2 memory) can be slow or fail on a low-RAM phone: show a clear message. (implementation)
- A key icon in the strip touches KeyboardView strip code that claude-l is changing (T8b.4, D23, D41): warn claude-l before editing it (implementation phase).
