# STATUS (the baton: rewrite it, keep it under one page)

Updated: 2026-11-16, session 1 (Claude Sonnet 5.5). Release-signed APKs update in place. T3 typing keyboard CONFIRMED working on the owner's phone
(build #3). The newest build adds the fix for the see-through gap under the keys, globe long-press and hardware-key
notes; waiting for the owner's feedback. Always re-check ci-status first.

## Now
Milestone **v0.1**: a bare English keyboard that installs from a CI-built APK and never interferes with physical keyboards.

## Next step (do this first)
1. Confirm the newest build is green on ci-status; fix it first if not.
2. Fix whatever the user reports from the checklist below (gap under keys, physical keyboard results, feel).
3. Start v0.2 Bangla with the Avro phonetic engine as pure Kotlin (no Android classes) under `lang/avro/`, ported from
   `third_party/avro-reference/engine` (MPL-2.0 header, list in NOTICE.md), with JUnit tests run in CI (add a test step to
   build.yml that reports to ci-status) before any UI. The language-switch UI answers are in DECISIONS.md (D15+) if the
   owner has given them; otherwise ask first.

## Test checklist for the typing build (user)
1. Download https://github.com/i-am-sleepy/Monsoon-Script/releases/download/latest/MonsoonScript.apk and install it over
   the old one (no uninstall needed). Make sure Monsoon Script is the selected keyboard.
2. Open any text field. You should see a top strip (globe, emoji, clipboard icons left, mic right) and four rows of keys.
3. Type letters, space, comma, period. Check Shift: one tap = next letter capital; double-tap = caps lock (underline).
   After ". " the next letter should be capital automatically.
4. Backspace: tap deletes one, holding repeats and speeds up. Enter: new line, or search/send/go in fields that ask for it.
5. Hold q..p for a moment: the digit shown in the corner is typed. Tap ?123 and =\< for symbol pages, ABC to return.
   A number-only field should open on the symbols page.
6. Globe icon (top-left): tap switches to your next keyboard, hold opens the keyboard picker. Emoji, clipboard and mic
   icons do nothing yet.
7. Switch phone dark/light mode and rotate the phone: colors and size should adapt. Under the keys there must be no
   see-through gap, and the gesture bar line at the bottom must stay visible in both modes.
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
- [x] T3b Gap under the keys: IME window background and navigation bar painted with the keyboard color, gesture-pill
      contrast follows light/dark (`MonsoonIme.applySystemBars`). Pushed; owner to confirm on device.
- [~] T4 Hardware keyboard pass-through: MonsoonIme has no onKeyDown/onKeyUp overrides and no fullscreen UI, so physical
      keys reach the app untouched; soft view visibility follows Android's own policy (system "show on-screen keyboard"
      setting), like Gboard. Needs the owner's test with a physical keyboard: Enter, Shift, Ctrl+C/V/A, arrows, Tab, typing in
      several apps. In-app toggle and extras come later.
- [x] T5 Globe: tap = next keyboard, long-press = keyboard picker (owner to confirm).
- [ ] T6 (v0.2) Hardware-keyboard Bangla: only while a Bangla mode is active, translate printable keys (Avro/Provat/Bijoy)
      in onKeyDown and pass everything else through; a shortcut (e.g. Ctrl+Space) toggles language; suggestions strip
      even when the soft view is hidden (candidates view).

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
