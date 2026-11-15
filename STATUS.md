# STATUS (the baton: rewrite it, keep it under one page)

Updated: 2026-11-16, session 1 (Claude Sonnet 5.5). Release-signed APKs update in place. T3 (soft keyboard) is pushed; its CI
result is recorded on the ci-status branch (check it first: a red build blocks everything).

## Now
Milestone **v0.1**: a bare English keyboard that installs from a CI-built APK and never interferes with physical keyboards.

## Next step (do this first)
1. Confirm the T3 build is green on ci-status; fix it first if not.
2. Fix whatever the user reports from the checklist below (feel, wrong hits, visual bugs), then start T4 (hardware keyboard
   pass-through behavior and toggle), then T5 (IME picker on globe long-press).

## Test checklist for the typing build (user)
1. Download https://github.com/i-am-sleepy/Monsoon-Script/releases/download/latest/MonsoonScript.apk and install it over
   the old one (no uninstall needed). Make sure Monsoon Script is the selected keyboard.
2. Open any text field. You should see a top strip (globe, emoji, clipboard icons left, mic right) and four rows of keys.
3. Type letters, space, comma, period. Check Shift: one tap = next letter capital; double-tap = caps lock (underline).
   After ". " the next letter should be capital automatically.
4. Backspace: tap deletes one, holding repeats and speeds up. Enter: new line, or search/send/go in fields that ask for it.
5. Hold q..p for a moment: the digit shown in the corner is typed. Tap ?123 and =\< for symbol pages, ABC to return.
   A number-only field should open on the symbols page.
6. Globe icon (top-left) switches to your next keyboard. Emoji, clipboard and mic icons do nothing yet.
7. Switch phone dark/light mode and rotate the phone: colors and size should adapt.
8. Report: lag or missed taps, wrong keys hit, anything ugly, crashes. If you have a physical keyboard, try it and tell
   me what happens (hardware behavior is task T4).

## Tasks (the only place tasks live)  [ ] todo  [~] in progress  [x] done  [?] blocked on user
- [x] T0 Handoff docs, Avro reference files (`third_party/avro-reference/`), requirements spec
- [x] T1 Project skeleton (Kotlin, minSdk 26, `com.monsoonscript.keyboard`, IME stub with no key overrides, setup activity,
      placeholder launcher icon). No Gradle wrapper (CI uses setup-gradle 8.10.2). Builds green in CI.
- [x] T2 CI: `.github/workflows/build.yml` builds the release APK on push to main, publishes rolling pre-release `latest`,
      pushes result and log tail to branch `ci-status`. Verified working (run #1).
- [x] T2b Signing (D9): PKCS12 key (alias `monsoon`) is in GitHub Secrets (KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS,
      KEY_PASSWORD); build #2 confirmed `signed: release key`. The key was visible in chat once; if it is ever regenerated the
      user must uninstall/reinstall once. It is NOT in the repo or memory. Never change the secrets without telling the user.
- [~] T3 Minimal soft keyboard (D11-D14): written and pushed (Canvas KeyboardView, letters/symbols/symbols2 pages, shift and
      caps lock, auto-caps, backspace repeat, enter honoring IME action, tab, digit long-press hints, key bubble, light/dark).
      Mark [x] when CI is green AND the user's checklist passes. Emoji/clipboard/mic strip icons are placeholders (no action).
- [ ] T4 Hardware keyboard pass-through (Enter/Shift/Ctrl/Alt/arrows/Tab untouched; soft view hides when a physical
      keyboard is attached, with a toggle). Add the test checklist here once built.
- [ ] T5 In-keyboard switch to Gboard/other IME (`switchToNextInputMethod` / IME picker key)

## Roadmap after v0.1 (details in docs/REQUIREMENTS.md)
v0.2 Bangla: Avro phonetic port, Provat, Bijoy, in-keyboard language switch. v0.3 Emoji (full set, search, recents) and
clipboard with one-tap access. v0.4 Suggestions/autocorrect/next-word. v0.5 Themes, resizing, one-handed, split, number row.
v0.6 Edit/cursor panel and number pad, space-bar cursor drag. v0.7 Polish, performance pass, settings.

## Needs user (ask with the question tool; record answers in DECISIONS.md)
1. Placeholders I picked without asking: teal accent color (Enter key), system haptic on every key, no key sound. Confirm or
   change when the user reacts to the first typing build.
2. Bijoy: Avro's pack has Probhat, National (Jatiya), Bornona, Munir Optima and Avro Easy but NOT Bijoy Classic. Which
   Bijoy layout should be the target?
3. License for the non-Avro parts of the app (MIT / Apache-2.0 / GPL-3.0 / none).
4. When the first network feature appears: also ship an offline-only APK flavor with no INTERNET permission? (see D10)

## Known bugs / notes
None yet. Placeholder launcher icon and setup screen are unstyled on purpose (design input needed).
