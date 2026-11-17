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

## Design (stages 3-4). Items with a D-line are decided; the rest are proposals.
- DECIDED D29: the picker is a panel that replaces the key rows at the same height; top strip stays, key icon highlighted, a way back to the keys.
  Panel header: lock state, search, add (+), expand. While the panel is in typing mode the keyboard's own keys feed the panel's field; nothing reaches the app.
- DECIDED D30: full vault screens are our own, in the keyboard's visual style. "Expand" opens the app's vault screen on the same entry/group/search;
  back returns to the app that was being typed in.
- DECIDED D31: adding an entry = mini form in the panel (title, username, password with generator, Save; "More fields" opens the app's add screen with the
  values carried over). First version may use the app's add screen alone as a stopgap.
- DECIDED D32: picker first view = matches for this app (entries storing the app id), then recent; folders one tap away. Browsers: recent + search.
  The keyboard gets the app id from EditorInfo; entries added from the keyboard store it. How existing KeePassDX databases store app links: verify in source.
- DECIDED D33: app vault screens = one list screen with a menu. DECIDED D34: vault reached by a card on the main screen + launcher long-press shortcut.
- PRINCIPLE: the keyboard never records what the owner types into other apps to build entries. New logins from websites come from the Autofill save prompt (later, D28).
- PROPOSAL (asked, stage 4): vault file location = the owner picks it with Android's file picker, both for creating and opening (.kdbx from KeePassDX/KeePassXC
  open in place; survives uninstall; any synced folder gives cloud sync without network code in our app). Alternatives: private app storage only (no sync, lost on
  uninstall or clear data), or offer both at creation.
- PROPOSAL (asked, stage 4): saving = automatic after every change, written safely (temp file, then swap) with the last few versions kept as backups; if the file changed
  elsewhere (synced from another device) never overwrite silently: detect, then merge or ask (design in stage 7/8). Alternative: manual Save button.
- PROPOSAL (asked, stage 4): unlocking from the keyboard = locked panel shows an Unlock button that opens a brief see-through unlock screen over the current app
  (fingerprint or master password), then returns to the picker. Alternative: master password typed in the panel, fingerprint still via the see-through screen.
- PLAN (not asked): creating a vault = in-app wizard (name, master password with confirmation and strength hint, optional key file, location), upstream-default encryption
  (KDBX 4), a clear "no recovery if the master password is forgotten" warning. Lock = keys and the opened database wiped from memory; WHEN it locks is stage 5.

## Stages (brief section 6). Each: explain, recommend, ask, wait for the answer, record a D-line in DECISIONS.md.
1 [x] Integration approaches (D25, D26)   2 [x] How it meets the keyboard (D27, D28)   3 [x] UI (D29-D34)
4 [~] Database create/open/unlock/save/lock: file location, saving, unlock path asked (STATUS Needs user #5)
5 [ ] Security options and trade-offs (biometric policy, auto-lock timing, clipboard, process isolation, field-type warnings)   6 [ ] Reuse vs own code
7 [ ] Integration and update strategy   8 [ ] User journey (switch apps, lock phone, close keyboard) + phasing
Then: owner signs off -> inspect our project and the upstream source -> feasibility spike -> build.

## Parking lot (raise at the right stage)
- Vault engine in its own Android process so the IME process stays light (D1, performance rules); security benefit too. (stage 5)
- D1 says no libraries beyond AndroidX core. The vault needs more (KeePassDX modules, BouncyCastle, Joda-Time, commons-io, AndroidX biometric + fragment). Propose: D1 keeps
  applying to the IME/typing process; the vault process and vault screens get a documented exception. Ask the owner. (stage 6/7)
- Cloud sync without network code: let the user pick the .kdbx through Android's file picker (works with Drive/Nextcloud/Syncthing folders); no INTERNET needed
  for the vault. Ties to D10 and Needs user #4. (stage 4/5)
- Tapping an entry: explicit chips (User / Pass / Code) vs one-tap fill; warn before typing a password into a field that is not a password field. (stage 5)
- Unlock screen: block screenshots/recents preview (FLAG_SECURE) and keep the master-password field a proper password field. (stage 5)
- Risky build changes (submodule, AGP bump, NDK) could turn main red for the other three Claudes: ask the owner whether to try them on a side branch. (stage 7)
- A key icon in the strip touches KeyboardView strip code that claude-l is changing (T8b.4, D23): warn claude-l before editing it (implementation phase).
