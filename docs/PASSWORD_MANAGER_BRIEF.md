# Owner's brief for the password manager (task E1)

Verbatim from the owner, received 2026-11-17. Do not edit. Verified facts, stages and decisions: docs/PASSWORD_MANAGER.md and DECISIONS.md.

I now want to integrate a fully featured password manager directly into the keyboard application, allowing users to manage passwords and securely insert credentials without switching between apps.

## 1. My overall goals
- Keep my existing keyboard implementation intact. Do not unnecessarily rewrite or restructure it.
- Add a complete password manager as an integrated feature of the existing application.
- Reuse as much reliable, existing open-source password-manager functionality as possible instead of reinventing everything.
- Prioritize security, privacy, reliability and long-term maintainability.
- Minimize the amount of custom code I need to maintain.
- Prefer solutions that can automatically track upstream improvements with minimal manual intervention.

## 2. Preferred foundation
My current leading candidate is KeePassDX. I am particularly interested in reusing its:
- Database implementation.
- Cryptographic implementation.
- KDBX handling.
- Relevant password-management functionality.
I want to investigate whether its database and crypto modules can be integrated independently, without incorporating its entire Android application.

## 3. Required functionality
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

## 4. Security requirements
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

## 5. Upstream integration and maintenance
I strongly prefer reusing upstream source code rather than maintaining a heavily modified fork. Investigate:
- Git submodules or other suitable source-integration strategies.
- Whether upstream modules can be updated independently.
- How to minimize conflicts when upstream code changes.
- Automated update detection and compatibility testing.
- Whether CI can build the application and run database interoperability tests after upstream updates.
The ideal result is a small integration layer around largely untouched upstream components.
Do not promise automatic compatibility without verifying it.

## 6. IMPORTANT: Discuss the design with me FIRST
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

## 7. Development workflow
Only after we have agreed on the architecture and user experience:
- Inspect my existing project structure and source code.
- Examine the actual upstream KeePassDX repository and its current module dependencies.
- Verify API compatibility instead of guessing.
- Implement existing keyboard functionality.
- Test KDBX compatibility, security-sensitive operations and credential insertion.
- Avoid unnecessary dependencies and modifications.
- Do not invent APIs, Gradle configurations or repository structures. Verify them against the actual source code.
