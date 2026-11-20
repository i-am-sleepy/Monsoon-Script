# STATUS (shared board: tasks and owners only; keep under one page)

CI state is NOT kept here: read the `ci-status` branch (CLAUDE.md). Each Claude's next step, steps with progress, half-done files and
test checklist live in `status/claude-X.md`. Edit only your own task lines. APK (release-signed, updates install in place, contains
everyone's merged work): https://github.com/i-am-sleepy/Monsoon-Script/releases/download/latest/MonsoonScript.apk

## Now
v0.2 Bangla. T7 engine and T8a/b/c (Avro typing, suggestion strip, smart colon) done and owner-tested. Four Claudes now work in parallel (D20-D22):
claude-l = Avro typing + suggestions, claude-e = layouts, claude-g = settings, claude-s = hardware keyboard.

## Tasks (the only place tasks live)  [ ] todo  [~] in progress  [x] done  [?] blocked on user   (owner in brackets; only the owner edits the line)
- [x] T0-T3 Handoff docs, Avro reference, skeleton, CI + rolling release + ci-status, signing (D9), Canvas soft keyboard (D11-D14)
- [x] T3b Gap under keys (`MonsoonIme.applySystemBars`): window + nav bar painted with the keyboard color, pill contrast follows theme
- [~] T4 (claude-s) Hardware keyboard pass-through: IME has no onKeyDown/onKeyUp overrides and no fullscreen UI, so physical keys reach
      apps untouched; soft view follows Android's "show on-screen keyboard" policy like Gboard. Awaiting owner's physical test.
- [x] T5 Globe: tap = next keyboard, hold = picker (owner to confirm)
- [ ] T6 (claude-s) Hardware-keyboard Bangla: only while a Bangla mode is active, translate printable keys in onKeyDown and pass
      everything else through; a shortcut (e.g. Ctrl+Space) toggles language; suggestions even when the soft view is hidden (candidates view)
- [x] T7 Avro Phonetic engine in `lang/avro/*` + JUnit tests + CI test step + `assets/avro/autodict.dct` (CI #5 green)
- [~] T8 Language model in the IME
  - [x] T8a (claude-l) `Language` enum (EN, AVRO), `PhoneticComposer`, live underlined Bangla via composing text, language pill in the
        strip + space-bar swipe (D15/D19), no auto-caps in Bangla, raw letters in password/URL/e-mail fields. Awaiting the owner's device test.
  - [x] T8b (claude-l) Suggestion strip while composing (D23) + dictionary index/search from `avro-dictionary.tsv`; owner-tested OK, feels instant (2026-11-17)
  - [x] T8c (claude-l) Smart colon in Avro (D24): visarga inside a word, plain colon at the end (owner-tested OK 2026-11-17)
- [ ] T9 (claude-e) PAUSED by the owner 2026-11-19 (E2 first). Fixed layouts as data: Provat and National from `third_party/avro-reference/layouts` (key maps, images stripped); Bijoy
      Classic from the standard chart (owner must verify); language-key long-press popup (D19). Bornona, Munir Optima, Avro Easy later (D17)
      2026-11-20 OWNER ORDER: Provat (Probhat) + National and the language-key popup (T9.1, T9.2, T9.4) moved to claude-l as L4. Left here: Bijoy Classic (T9.3), Bornona, Munir Optima, Avro Easy.
- [x] T10 (claude-g) Settings activity + SharedPreferences: switches for the language key and space-bar swipe (both default ON, D15,
      `KeyboardView.setLanguageOptions`), persist the chosen language (built in G1.1/G1.2, commit 63f95a7; owner-tested OK 2026-11-19)
- [~] G1 (claude-g) Settings pages and extra settings (D55): main list with sub-pages (Typing, Suggestions, Appearance, About and licenses); prefs helper; language key/swipe, learn switch +
      clear button, suggestion toggles, vibration/sound/long-press, theme, number row, height + key-gap sliders with live preview; later: hint bubble for held top-row digits. Includes T10 and L2.5.
      G1.1 prefs + G1.2 main list/Typing/Suggestions/About pages DONE (63f95a7, owner-tested OK 2026-11-19); G1.3 suggestion toggles done (CI green, owner saw the switches 2026-11-20); G1.4 typing feel pushed (2b499bf, CI #23 green) G1.5 Appearance (20464dd, CI #24 green) and G1.6 hold-hint bubble pushed (73307d4, CI #25 green); owner tests G1.3 to G1.6 together; G1.7 optional built-in key click added after the owner's report (CI #28 green). Steps in status/claude-g.md
- [~] T11 (claude-l) Bangla digits (D18, D61). TAKEN OVER from claude-e on the owner's order 2026-11-21 (claude-e: remove it from your list). Built as a Settings > Typing choice "Digits in Bangla mode: English / Bangla" that swaps typed digit and hint on every digit key; pushed, CI result in status/claude-l.md
- [~] E2 (claude-e) Emoji system (owner priority 2026-11-19: all other claude-e tasks are paused): fully offline picker, Unicode 17 emoji data in assets, bundled Noto Color Emoji,
      category tabs, searchable grid, recents, skin tones and ZWJ sequences, inserted as Unicode text with commitText. Research done, design answered (D56), building. Steps in status/claude-e.md
- [~] E1 (claude-e) PAUSED by the owner 2026-11-19 (E2 first). Password manager in the keyboard (KeePassDX engine, picker in the keyboard, full vault screens in the app). Design SIGNED OFF (D53, 2026-11-18).
      Phase 0 feasibility check is DONE on the side branch `e1-feasibility` (green run #12): GO recommended, +2.2 MB APK (+1.5 MB with ARM-only), 14 emulator tests incl. real KeePassXC and pykeepass vaults,
      shrunk release build works after two fixes. Nothing from Phase 0 is on main. Results and the merge checklist: docs/PASSWORD_MANAGER_PHASE0.md. Waiting for the owner's go decision (Needs user #5); Phase 1 only after E2.
- [~] L2 (claude-l) Offline prediction for English / Bangla / Banglish from WM Keyboard Data (wasi-master/wmkeyboard-data): prefix autocomplete + next-word
      (uni/bi/trigram + learned usage), compact binary tables, async. Built: engine + data (L2.3), English/Bangla strip, arrow, learning (L2.4, CI green up to #13); owner device test OK (2026-11-18); L2.5 (settings switch, clear button, licenses screen) is done by claude-g in G1.2 (63f95a7); steps in status/claude-l.md
- [x] L3 (claude-l) Banglish prediction quality (D54, owner-tested OK 2026-11-18, CI #15): bridge from Banglish words to the Bangla pair data for next-word, lean Banglish for 1-3 letter prefixes, typed word as first strip choice, no repeat of the word just typed. Steps in status/claude-l.md
- [x] L4 (claude-l) প্রভাত (Probhat) and জাতীয় (National) fixed Bangla layouts as generated data (also usable by claude-s's T6), typing through the layouts, language-key long-press popup (D19).
      Taken over from claude-e's T9 (T9.1, T9.2, T9.4) on the owner's order 2026-11-20. Design answered (D58-D60), built in 46d05d1 (CI #26 green), owner-tested: works (2026-11-20). Steps in status/claude-l.md
- [~] L5 (claude-l) Vowel-sign keys drawn without the dotted circle (D62, owner request after the L4 test). Pushed, waiting for CI and the owner's test. Steps in status/claude-l.md
- [~] L6 (claude-l) The signs left out of L4 (॥ ৲ ৴-৹ ৺ ৄ ৢ ৣ ঽ) as long-press hints on the ?123 pages, Bangla layouts only (D63). Pushed, waiting for CI and the owner's test. Steps in status/claude-l.md
- [~] L1 (claude-l) Multi-Claude workflow (D20-D22): CLAUDE.md rules, owner tags, `status/` notes files, new paste command in README

## Hand-offs between Claudes (keep these; warn the others if you must break one)
- Language switching has ONE entry point: `MonsoonIme.setLanguage(l: Language)`. The pill, space-bar swipe and claude-s's Ctrl+Space call it; nobody adds a second path.
- claude-g stores the language by its enum NAME (string); an unknown saved name falls back to EN. claude-e may add enum values (Provat, Bijoy, National) freely.
- claude-e's "last-used Bangla layout" for the pill tap is persisted by claude-g (`Prefs.lastBangla` / `Prefs.saveLanguage`, T10 done); `MonsoonIme.setLanguage` saves it.

## Roadmap (details in docs/REQUIREMENTS.md)
v0.2 Bangla (T6-T11). v0.3 Emoji (full set, search, recents) and clipboard one-tap. v0.4 Suggestions/autocorrect/next-word.
v0.5 Themes, resizing, one-handed, split, number row. v0.6 Edit/cursor panel, number pad, space-bar cursor drag. v0.7 Polish, perf, settings.
Password manager (E1): Phase 0 done (go recommended); Phase 1 onward (D51) after the owner's go and after E2.

## Needs user (ask with the question tool; record answers in DECISIONS.md; tag with your name)
1. Placeholders picked without asking: teal accent (Enter key), system haptic on every key, no key sound. Confirm or change. (any Claude)
2. When Bijoy Classic is built (T9): ask the owner to verify the key map. (claude-e)
3. (answered 2026-11-17: whole app is GPL-3.0, see D25. The LICENSE file comes after the E1 feasibility check.)
4. When the first network feature appears: also ship an offline-only APK flavor with no INTERNET permission? (see D10) (any Claude)
5. Password manager (E1) Phase 0 is DONE and green (the owner added the workflow file on 2026-11-18). Waiting for the owner: (a) GO for Phase 1 (D51), to start after E2; (b) how the engine
   enters main: CI fetches the pinned KeePassDX release (one line in build.yml) or a real git submodule. Either way `.github/workflows/build.yml` must be edited (Gradle 8.14.2, SDK/NDK step, narrower
   `paths-ignore`), which claude-e's tool cannot do: the owner (paste) or a Claude Code session. Exact checklist: docs/PASSWORD_MANAGER_PHASE0.md. (claude-e)
6. (answered 2026-11-17: prediction data, see D41 and D42)
7. (answered 2026-11-18: Banglish prediction quality, see D54)
8. (answered 2026-11-19: emoji layout, skin tones, Bangla search names, hide undrawable emoji, see D56)

9. (answered 2026-11-20: the "Show word suggestions" switch does not touch Avro spelling choices, see D57)
10. (answered 2026-11-20: Probhat/National phone design, see D58-D60)
11. (answered 2026-11-21: Bangla digits as a setting, hints only in Bangla layouts, see D61-D63)

## Known notes
Long-press on the language pill does nothing until T9 adds a second Bangla layout (claude-e). Launcher icon and setup screen are placeholders.
Language choice is saved (T10). Avro choices of T8a are listed in `status/claude-l.md`.
