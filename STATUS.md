# STATUS (the baton: rewrite it, keep it under one page)

Updated: 2026-11-16, session 1 (Claude Sonnet 5.5). CI #4 green (commit 4d171e6), release-signed APK published at the `latest`
link (updates install in place). The v0.1 typing keyboard is CONFIRMED working on the owner's phone. Always re-check ci-status first.

## Now
v0.1 is done except owner confirmation of build #4 (gap fix, globe long-press, physical keyboard). Starting **v0.2 Bangla**.

## Next step (do this first)
1. Confirm the newest build is green on ci-status; fix it first if not. Fix what the owner reports from the checklist below.
2. T7: Avro Phonetic engine as pure Kotlin (no Android classes) under `lang/avro/`, ported from `third_party/avro-reference/engine`
   (MPL-2.0 header on each ported file, list them in NOTICE.md), with JUnit tests run in CI (add a test step to build.yml that
   also reports to ci-status). Tests first: known words ("ami" -> আমি, etc.), then the UI. The suggestion dictionary is in the repo as TSV
   (`third_party/avro-reference/data/`, 159k words + 749 suffixes); turn it into a compact app-side index at T8 (not raw SQLite).

## Test checklist for build #4 (owner)
1. Install https://github.com/i-am-sleepy/Monsoon-Script/releases/download/latest/MonsoonScript.apk over the old one.
2. Open a text field: no see-through gap under the keys; the gesture bar line stays visible in dark AND light mode.
3. Hold the globe (top-left): keyboard picker opens. Tap it: jumps to your next keyboard.
4. Physical keyboard (if you have one): Enter, Shift, Ctrl+C/V/A, arrows, Tab, typing in several apps must all behave normally.
5. Report lag, wrong hits, anything ugly or crashing.

## Tasks (the only place tasks live)  [ ] todo  [~] in progress  [x] done  [?] blocked on user
- [x] T0-T3 Handoff docs, Avro reference, skeleton, CI + rolling release + ci-status, signing (D9), Canvas soft keyboard (D11-D14)
- [x] T3b Gap under keys (`MonsoonIme.applySystemBars`): window + nav bar painted with the keyboard color, pill contrast follows theme
- [~] T4 Hardware keyboard pass-through: IME has no onKeyDown/onKeyUp overrides and no fullscreen UI, so physical keys reach
      apps untouched; soft view follows Android's "show on-screen keyboard" policy like Gboard. Awaiting owner's physical test.
- [x] T5 Globe: tap = next keyboard, hold = picker (owner to confirm)
- [ ] T6 Hardware-keyboard Bangla: only while a Bangla mode is active, translate printable keys in onKeyDown and pass everything
      else through; a shortcut (e.g. Ctrl+Space) toggles language; suggestions even when the soft view is hidden (candidates view)
- [ ] T7 Avro Phonetic engine (pure Kotlin) + JUnit tests in CI (see Next step)
- [ ] T8 Language model in the IME: languages English, Bangla Avro, Provat, Bijoy Classic, National; per-language layout and engine;
      language key (EN / বাং) beside space and swipe on the space bar (D15); live underlined composing text + suggestions in the strip (D16)
- [ ] T9 Fixed layouts as data: Provat and National from `third_party/avro-reference/layouts` (key maps, images stripped); Bijoy Classic
      from the standard chart (owner must verify the map against their habits); Bornona, Munir Optima, Avro Easy later (D17)
- [ ] T10 Settings activity + SharedPreferences: switches for the language key and for space-bar swipe (both default ON, D15), later more
- [ ] T11 Bangla digit hints on digit keys (English typed, Bangla long-press, D18)

## Roadmap (details in docs/REQUIREMENTS.md)
v0.2 Bangla (T6-T11). v0.3 Emoji (full set, search, recents) and clipboard one-tap. v0.4 Suggestions/autocorrect/next-word.
v0.5 Themes, resizing, one-handed, split, number row. v0.6 Edit/cursor panel, number pad, space-bar cursor drag. v0.7 Polish, perf, settings.

## Needs user (ask with the question tool; record answers in DECISIONS.md)
1. Placeholders I picked without asking: teal accent (Enter key), system haptic on every key, no key sound. Confirm or change.
2. When Bijoy Classic is built (T9): ask the owner to verify the key map.
3. License for the non-Avro parts of the app (MIT / Apache-2.0 / GPL-3.0 / none).
4. When the first network feature appears: also ship an offline-only APK flavor with no INTERNET permission? (see D10)

## Known bugs / notes
Emoji, clipboard and mic strip icons are drawn but do nothing yet. Launcher icon and setup screen are placeholders.
