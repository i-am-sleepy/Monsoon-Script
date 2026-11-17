# Password manager inside Monsoon Script (task E1, claude-e)

Status: DESIGN DISCUSSION with the owner. No code, no project-structure changes until the owner signs off on the design (see "Stages").
Decisions are recorded in DECISIONS.md (one line each). This file holds the brief, the verified facts, the stage tracker and a parking lot.

## Owner's brief (verbatim, received 2026-11-17)

I now want to integrate a fully featured password manager directly into the keyboard application, allowing users to manage passwords and securely insert credentials without switching between apps.

### 1. My overall goals
- Keep my existing keyboard implementation intact. Do not unnecessarily rewrite or restructure it.
- Add a complete password manager as an integrated feature of the existing application.
- Reuse as much reliable, existing open-source password-manager functionality as possible instead of reinventing everything.
- Prioritize security, privacy, reliability and long-term maintainability.
- Minimize the amount of custom code I need to maintain.
- Prefer solutions that can automatically track upstream improvements with minimal manual intervention.

### 2. Preferred foundation
My current leading candidate is KeePassDX. I am particularly interested in reusing its:
- Database implementation.
- Cryptographic implementation.
- KDBX handling.
- Relevant password-management functionality.
I want to investigate whether its database and crypto modules can be integrated independently, without incorporating its entire Android application.

### 3. Required functionality
The integrated password manager should eventually support:
- Creating new encrypted password databases.
- Opening existing .kdbx databases.
- Using existing KeePassDX-compatible databases without requiring migration.
- Viewing, searching, adding, editing and deleting credentials.
- Option to store the entered password/credential in a website appears automatically.
- Organizing credentials into groups.
- Securely autofilling usernames and passwords.
- A convenient credential picker accessible directly from the keyboard.
- Secure database unlocking and session management.
- Basically a full version of the picker lies in the keyboard app itself and only necessary stuff like password picker/adding details added in actual keyboard - everything should be seamless so that expanding from the keyboard's password manager section to the keyboard app's password manager module full interface feels connected and clean and seamless.
- Biometric authentication where appropriate through Android's built in option.
- Database backup and recovery.
- Support for important password-manager features such as TOTP, attachments and custom fields, where feasible.
Do not assume every feature must be implemented immediately. Help me prioritize them.

### 4. Security requirements
Security and privacy are extremely important. I want:
- Local-first operation without mandatory cloud accounts, but allow cloud syncing if someone wants as well.
- Strong encryption and secure key handling.
- Android Keystore integration where appropriate.
- Biometric authentication without treating biometrics as a replacement for the master password.
- Protection against accidental credential exposure.
- Secure handling of clipboard contents and sensitive data.
- No unnecessary network access or telemetry.
- Proper handling of database locking, backgrounding and application lifecycle events.
- Explain security decisions rather than silently implementing them.

### 5. Upstream integration and maintenance
I strongly prefer reusing upstream source code rather than maintaining a heavily modified fork. Investigate:
- Git submodules or other suitable source-integration strategies.
- Whether upstream modules can be updated independently.
- How to minimize conflicts when upstream code changes.
- Automated update detection and compatibility testing.
- Whether CI can build the application and run database interoperability tests after upstream updates.
The ideal result is a small integration layer around largely untouched upstream components.
Do not promise automatic compatibility without verifying it.

### 6. IMPORTANT: Discuss the design with me FIRST
Do not start coding, generate implementation files, modify project structure or prescribe a final architecture immediately.
First, work with me as a technical design partner. Proceed through discussion in stages:
1. Explain the possible integration approaches in understandable language.
2. Discuss how the password manager should interact with the existing keyboard.
3. Ask about my preferences for the user interface, navigation, vault access and credential selection.
4. Discuss how database creation, opening, unlocking, saving and locking should work.
5. Explain the security options and their trade-offs, then ask for my preferences.
6. Discuss how much KeePassDX functionality should be reused versus implemented separately.
7. Compare integration and update strategies, considering long-term maintenance.
8. Discuss the expected user experience, including what happens when a user switches apps, locks the phone or closes the keyboard.
Ask questions conversationally. Explain the implications of each decision and wait for my answers.
If there are multiple reasonable approaches, present the alternatives and recommend one with reasons, but let me make the final design decisions.
Keep track of the decisions we make so that the eventual implementation follows the agreed design.

### 7. Development workflow
Only after we have agreed on the architecture and user experience:
- Inspect my existing project structure and source code.
- Examine the actual upstream KeePassDX repository and its current module dependencies.
- Verify API compatibility instead of guessing.
- Implement existing keyboard functionality.
- Test KDBX compatibility, security-sensitive operations and credential insertion.
- Avoid unnecessary dependencies and modifications.
- Do not invent APIs, Gradle configurations or repository structures. Verify them against the actual source code.

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
Alternative engine: KeePassJava2 (github.com/jorabin/KeePassJava2): Apache-2.0, on Maven Central, KDBX 3 and 4 with Argon2, "Android compatible", pure Java,
single maintainer, history of neglect and "back in development" since May 2023. NOT yet verified: current maintenance, Argon2 speed on phones, KDBX 4.1, TOTP/attachments/custom fields API.

## Stages (brief section 6). Each: explain, recommend, ask, wait for the answer, record a D-line in DECISIONS.md.
1 [~] Integration approaches (asked: license + foundation, STATUS Needs user #5)
2 [ ] How it meets the keyboard   3 [ ] UI, navigation, vault access, credential picking   4 [ ] Database create/open/unlock/save/lock
5 [ ] Security options and trade-offs   6 [ ] Reuse vs own code   7 [ ] Integration and update strategy   8 [ ] User journey (switch apps, lock phone, close keyboard) + phasing
Then: owner signs off -> inspect our project and the upstream source -> feasibility spike -> build.

## Parking lot (raise at the right stage)
- Vault engine in its own Android process so the IME process stays light (D1, performance rules); security benefit too. (stage 5)
- Android's biometric dialog needs an Activity: unlocking from the keyboard probably means a brief see-through screen. Verify. (stage 4/5)
- Cloud sync without network code: let the user pick the .kdbx through Android's file picker (works with Drive/Nextcloud/Syncthing folders); no INTERNET needed
  for the vault. Ties to D10 and Needs user #4. (stage 4/5)
- "Save this login?" prompt and secure autofill need an Android Autofill Service (not part of the keyboard). Inline suggestions in the strip need Android 11+;
  minSdk is 26. (stage 2/6)
- Risky build changes (submodule, AGP bump, NDK) could turn main red for the other three Claudes: ask the owner whether to try them on a side branch. (stage 7)
- License of the whole app is tied to this decision (Needs user #3).
