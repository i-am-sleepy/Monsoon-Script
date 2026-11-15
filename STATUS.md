# STATUS (the baton: rewrite it, keep it under one page)

Updated: 2026-11-16, session 1 (Claude Sonnet 5.5). Code: skeleton pushed, first CI run NOT yet verified. Latest APK: none verified.

## Now
Milestone **v0.1**: a bare English keyboard that installs from a CI-built APK and never interferes with physical keyboards.

## Next step (do this first)
1. Verify the first CI run: `git fetch origin ci-status && git show origin/ci-status:last-build.txt` (wait ~4-8 min after a code push).
   If `result: failure`, fix the build before anything else.
2. Signing key (T2b): generate the keystore, give the user the 4 secret values and phone steps (see T2b).
3. Then T3 after asking the user the layout question (Needs user #1).

## Tasks (the only place tasks live)  [ ] todo  [~] in progress  [x] done  [?] blocked on user
- [x] T0 Handoff docs, Avro reference files (`third_party/avro-reference/`), requirements spec
- [~] T1 Project skeleton written: Kotlin, minSdk 26, `com.monsoonscript.keyboard`, IME stub (placeholder panel, no key
      overrides), `method.xml`, setup activity (Enable / Choose keyboard). Placeholder launcher icon. No Gradle wrapper
      (CI uses setup-gradle 8.10.2); no local build is possible in the sandbox. Mark [x] once CI is green.
- [~] T2 `.github/workflows/build.yml` written: release APK on push to main (docs-only pushes skipped), rolling pre-release
      `latest` (APK link in ARCHITECTURE.md), result and log tail pushed to branch `ci-status`. Mark [x] once verified.
- [ ] T2b Signing (D9): create PKCS12 keystore with openssl (alias `monsoon`), give the user the 4 GitHub Secrets
      (KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD) with phone-browser steps. Tell them the key was visible
      in chat and that builds made before the secrets exist (temporary debug key) need one uninstall/reinstall.
- [ ] T3 Minimal soft keyboard: Canvas-drawn QWERTY, shift/caps, backspace with repeat, enter (honor IME action),
      space, symbols page, key popups. Layout look is a design decision: show options to the user first (Needs user #1).
- [ ] T4 Hardware keyboard pass-through (Enter/Shift/Ctrl/Alt/arrows/Tab untouched; soft view hides when a physical
      keyboard is attached, with a toggle). Add the test checklist here once built.
- [ ] T5 In-keyboard switch to Gboard/other IME (`switchToNextInputMethod` / IME picker key)

## Roadmap after v0.1 (details in docs/REQUIREMENTS.md)
v0.2 Bangla: Avro phonetic port, Provat, Bijoy, in-keyboard language switch. v0.3 Emoji (full set, search, recents) and
clipboard with one-tap access. v0.4 Suggestions/autocorrect/next-word. v0.5 Themes, resizing, one-handed, split, number row.
v0.6 Edit/cursor panel and number pad, space-bar cursor drag. v0.7 Polish, performance pass, settings.

## Needs user (ask with the question tool; record answers in DECISIONS.md)
1. UI: main layout and look (Gboard-style). Show 2-3 options before building T3.
2. Bijoy: Avro's pack has Probhat, National (Jatiya), Bornona, Munir Optima and Avro Easy but NOT Bijoy Classic. Which
   Bijoy layout should be the target?
3. License for the non-Avro parts of the app (MIT / Apache-2.0 / GPL-3.0 / none).
4. When the first network feature appears: also ship an offline-only APK flavor with no INTERNET permission? (see D10)

## Known bugs / notes
None yet.
