# STATUS (the baton: rewrite it, keep it under one page)

Updated: 2026-11-16, session 1 (Claude Sonnet 5.5). CI run #1 green (commit 25899ab, temporary debug key). APK is published
but NOT for installing yet: wait for the release-signed build (T2b).

## Now
Milestone **v0.1**: a bare English keyboard that installs from a CI-built APK and never interferes with physical keyboards.

## Next step (do this first)
T2b: the user is adding the 4 GitHub Secrets (they will say "secrets added"). Then push any small code change (e.g. bump
`versionName` in app/build.gradle.kts) so CI builds a release-signed APK, confirm `signed: release key` in
`git fetch origin ci-status && git show origin/ci-status:last-build.txt`, and tell the user to install it and run the
checklist below. After that: ask the layout question (Needs user #1), then T3.

## Test checklist for the skeleton build (user)
1. Download https://github.com/i-am-sleepy/Monsoon-Script/releases/download/latest/MonsoonScript.apk and install it
   (allow "install unknown apps" for the browser if asked).
2. Open Monsoon Script, tap "1. Enable keyboard", switch Monsoon Script on, go back.
3. Tap "2. Choose keyboard", pick Monsoon Script. The status line should say it is active.
4. Open any text field: a dark placeholder panel appears (no keys yet; this build only proves install and enable).
5. Report anything odd (crash, keyboard not listed, status line wrong).

## Tasks (the only place tasks live)  [ ] todo  [~] in progress  [x] done  [?] blocked on user
- [x] T0 Handoff docs, Avro reference files (`third_party/avro-reference/`), requirements spec
- [x] T1 Project skeleton (Kotlin, minSdk 26, `com.monsoonscript.keyboard`, IME stub with no key overrides, setup activity,
      placeholder launcher icon). No Gradle wrapper (CI uses setup-gradle 8.10.2). Builds green in CI.
- [x] T2 CI: `.github/workflows/build.yml` builds the release APK on push to main, publishes rolling pre-release `latest`,
      pushes result and log tail to branch `ci-status`. Verified working (run #1).
- [?] T2b Signing (D9): keystore generated (PKCS12, alias `monsoon`) and the 4 values sent to the user in chat; waiting for
      them to add KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD at
      github.com/i-am-sleepy/Monsoon-Script/settings/secrets/actions. The key was visible in chat; if lost or regenerated the
      user must reinstall once. The key is NOT stored in the repo or memory; only GitHub Secrets hold it now.
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
None yet. Placeholder launcher icon and setup screen are unstyled on purpose (design input needed).
