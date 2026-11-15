# STATUS (the baton: rewrite it, keep it under one page)

Updated: 2026-11-16, session 1 (Claude Sonnet 5.5). Code: none yet. CI: none yet. Latest APK: none.

## Now
Milestone **v0.1**: a bare English keyboard that installs from a CI-built APK and never interferes with physical keyboards.

## Next step (do this first)
T1: create the Gradle Android project skeleton (see T1). Then T2 so every push produces a downloadable APK.

## Tasks (the only place tasks live)  [ ] todo  [~] in progress  [x] done  [?] blocked on user
- [x] T0 Handoff docs, Avro reference files (`third_party/avro-reference/`), requirements spec
- [ ] T1 Project skeleton: Kotlin, minSdk 26, package `com.monsoonscript.keyboard`, app name "Monsoon Script",
      `InputMethodService` stub + `method.xml`, launcher/setup activity (guides enabling the IME). Gradle wrapper committed.
- [ ] T2 GitHub Actions: build the debug APK on push to main, publish it to a rolling pre-release `latest` (downloadable
      from a phone without logging in). Needs a stable signing key so updates install over old builds. See D9: signing key lives in GitHub
      Secrets (KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD). Generate it with keytool, then give the user exact
      phone-browser steps to add the 4 secrets (tell them the key was visible in chat; regenerating means one reinstall).
      Note: agent sessions may not be able to read Actions logs (the gh CLI token is invalid in the sandbox). So make the
      workflow push the last ~80 lines of a failed build log to a `ci-status` branch (file `last-build.txt`) that agents can
      `git fetch`; the user then never has to paste logs.
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
