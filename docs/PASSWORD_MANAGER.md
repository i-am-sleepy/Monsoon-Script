# Password manager inside Monsoon Script (task E1, claude-e)

Status: DESIGN DISCUSSION with the owner. No code, no project-structure changes until the owner signs off on the design (see "Stages").
The owner's original brief is in docs/PASSWORD_MANAGER_BRIEF.md (verbatim, do not edit). Decisions go to DECISIONS.md (D25 onward, one line each).
This file holds the verified facts, the stage tracker and a parking lot.

## Decided so far
- D25 The whole app is GPL-3.0 (LICENSE file and headers only after the feasibility check passes).
- D26 Engine = KeePassDX `:database` + `:crypto` behind our own thin interface, feasibility check first; fallback KeePassJava2.

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
Alternative engine: KeePassJava2 (github.com/jorabin/KeePassJava2): Apache-2.0, Maven Central, KDBX 3 and 4 with Argon2, "Android compatible", pure Java,
single maintainer, history of neglect and "back in development" since May 2023. NOT yet verified: current maintenance, Argon2 speed on phones, KDBX 4.1, TOTP/attachments/custom fields API.
Android autofill and keyboards (developer.android.com "Integrate autofill with IMEs and autofill services"):
- From Android 11 (API 30) an autofill service can show its suggestions inside the keyboard's strip. The IME opts in (`supportsInlineSuggestions` in its XML,
  `InputMethodService.onCreateInlineSuggestionsRequest`, `onInlineSuggestionsResponse`). Without support on either side (or before Android 11) a dropdown menu is shown.
- The keyboard cannot see what an inline suggestion contains until the user taps it.
Avro license check: only `AvroPhoneticConverter.kt` was read: standard MPL-2.0 notice, no "Incompatible With Secondary Licenses" marker. Check every Avro file
when the LICENSE file is added.

## Stages (brief section 6). Each: explain, recommend, ask, wait for the answer, record a D-line in DECISIONS.md.
1 [x] Integration approaches (D25, D26)
2 [~] How it meets the keyboard: 3 ways a login gets into a field (keyboard picker, Autofill service with save prompt, inline suggestions in the strip);
    asked: how to open the picker, picker first or autofill first (STATUS Needs user #5)
3 [ ] UI, navigation, vault access, credential picking   4 [ ] Database create/open/unlock/save/lock   5 [ ] Security options and trade-offs
6 [ ] Reuse vs own code   7 [ ] Integration and update strategy   8 [ ] User journey (switch apps, lock phone, close keyboard) + phasing
Then: owner signs off -> inspect our project and the upstream source -> feasibility spike -> build.

## Parking lot (raise at the right stage)
- Vault engine in its own Android process so the IME process stays light (D1, performance rules); security benefit too. (stage 5)
- Android's biometric dialog needs an Activity: unlocking from the keyboard probably means a brief see-through screen. Verify. (stage 4/5)
- Cloud sync without network code: let the user pick the .kdbx through Android's file picker (works with Drive/Nextcloud/Syncthing folders); no INTERNET needed
  for the vault. Ties to D10 and Needs user #4. (stage 4/5)
- "Save this login?" prompt and secure autofill need an Android Autofill Service (a separate component from the keyboard, enabled once by the user in Android settings). (stage 2/6)
- Risky build changes (submodule, AGP bump, NDK) could turn main red for the other three Claudes: ask the owner whether to try them on a side branch. (stage 7)
- A key icon in the strip touches KeyboardView strip code that claude-l is changing (T8b.4, D23): warn claude-l before editing it (implementation phase).
