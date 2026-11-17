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
Avro license check: only `AvroPhoneticConverter.kt` was read: standard MPL-2.0 notice, no "Incompatible With Secondary Licenses" marker. Check every Avro file
when the LICENSE file is added.

## Design (stage 3). Items with a D-line are decided; the rest are proposals.
- DECIDED D29: the picker is a panel that replaces the key rows at the same height; top strip stays, key icon highlighted, a way back to the keys.
  Panel header: lock state, search, add (+), expand. While the panel is in typing mode the keyboard's own keys feed the panel's field; nothing reaches the app.
- DECIDED D30: full vault screens are our own, in the keyboard's visual style. "Expand" opens the app's vault screen on the same entry/group/search;
  back returns to the app that was being typed in.
- PROPOSAL (open): adding an entry. Option A: mini form in the panel (title, username, password with generator, Save; "More fields" opens the app's add screen
  with the typed values carried over). Option B: "+" always opens the app's add screen. Trade-off: A = nicer flow (stay in the sign-up page), a second small
  form to maintain, reuses the typing mode needed for search anyway; B = less keyboard code, but leaving the page (a low-RAM phone may close the app in
  the background). Typing in the app's own fields also goes through our keyboard, so neither is safer for the typed text itself. Recommended: A, with B as the
  first stopgap while the panel typing mode is built.
- PRINCIPLE: the keyboard never records what the owner types into other apps to build entries. New logins from websites come from the Autofill save prompt (later, D28).

## Stages (brief section 6). Each: explain, recommend, ask, wait for the answer, record a D-line in DECISIONS.md.
1 [x] Integration approaches (D25, D26)   2 [x] How it meets the keyboard (D27, D28)
3a [~] UI: panel placement (D29) and look of full screens (D30) decided; add-entry flow explained, waiting (STATUS Needs user #5)
3b [ ] UI: what the picker shows first, search, navigation inside the app, how the vault is reached
4 [ ] Database create/open/unlock/save/lock   5 [ ] Security options and trade-offs   6 [ ] Reuse vs own code
7 [ ] Integration and update strategy   8 [ ] User journey (switch apps, lock phone, close keyboard) + phasing
Then: owner signs off -> inspect our project and the upstream source -> feasibility spike -> build.

## Parking lot (raise at the right stage)
- Vault engine in its own Android process so the IME process stays light (D1, performance rules); security benefit too. (stage 5)
- Android's biometric dialog needs an Activity: unlocking from the keyboard probably means a brief see-through screen. Verify. (stage 4/5)
- Cloud sync without network code: let the user pick the .kdbx through Android's file picker (works with Drive/Nextcloud/Syncthing folders); no INTERNET needed
  for the vault. Ties to D10 and Needs user #4. (stage 4/5)
- Tapping an entry: explicit chips (User / Pass / Code) vs one-tap fill; warn before typing a password into a field that is not a password field. (stage 5)
- Risky build changes (submodule, AGP bump, NDK) could turn main red for the other three Claudes: ask the owner whether to try them on a side branch. (stage 7)
- A key icon in the strip touches KeyboardView strip code that claude-l is changing (T8b.4, D23): warn claude-l before editing it (implementation phase).
