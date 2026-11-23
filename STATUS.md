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
- [x] G1 (claude-g, owner: "everything looks fine" 2026-11-21 after testing G1.8; earlier explicit OKs: settings pages, hold-hint bubble, long-press delay, Appearance preview/theme/height/gap; vibration retune and built-in click were not confirmed one by one) Settings pages and extra settings (D55): main list with sub-pages (Typing, Suggestions, Appearance, About and licenses); prefs helper; language key/swipe, learn switch +
      clear button, suggestion toggles, vibration/sound/long-press, theme, number row, height + key-gap sliders with live preview; later: hint bubble for held top-row digits. Includes T10 and L2.5.
      G1.1 prefs + G1.2 main list/Typing/Suggestions/About pages DONE (63f95a7, owner-tested OK 2026-11-19); G1.3 suggestion toggles done (CI green, owner saw the switches 2026-11-20); G1.4 typing feel pushed (2b499bf, CI #23 green) G1.5 Appearance (20464dd, CI #24 green) and G1.6 hold-hint bubble pushed (73307d4, CI #25 green); owner tests G1.3 to G1.6 together; G1.7 optional built-in key click added after the owner's report (CI #28 green); G1.8 emoji key on the bottom row + keyboard button in the emoji panel pushed on the owner's request (447a1f9, CI #31 green). Steps in status/claude-g.md
- [x] G2-G4 (claude-g, owner-tested 2026-11-21: volume keys okay, auto-open turned off as expected, no flashes so far) Typing page: "Volume buttons move the cursor" (G2), "Open the keyboard automatically" off = only a tap opens it (G3);
      the strip no longer flashes the icon row on a language switch or after a space (G4). Pushed (4e3ff3a, ee09d9b), CI #36 green. Reopen G4 if a flash shows up. Details in status/claude-g.md.
- [~] G5 (claude-g) Voice typing (owner request 2026-11-21, design D66 + D76 + D78, spec docs/VOICE_TYPING_SPEC.md): the strip's mic key: HOLD = record and type at the cursor, TAP = panel with an editable text box (NOT built yet); engine per language (Settings > Voice typing).
      2026-11-23: local ENGLISH (Parakeet) works on the owner's phone (his test: runs well). Local BANGLA (Moonshine, 76 MB, hosted in release `voice-models`) pushed in CI #63, AWAITING THE OWNER'S PHONE TEST. Next: tap panel (G5.3b), Voice test page + Wi-Fi-only (G5.4). Google hold-to-talk (G5.3a) parked: does nothing on his phone. Steps in status/claude-g.md
- [x] T11 (claude-l) Bangla digits (D18, D61). TAKEN OVER from claude-e on the owner's order 2026-11-21 (claude-e: remove it from your list). Built as a Settings > Typing choice "Digits in Bangla mode: English / Bangla" that swaps typed digit and hint on every digit key; pushed in 65133d1, CI #29 green, owner-tested OK (2026-11-21)
- [x] E2 (claude-e) Emoji system: DONE FOR NOW (owner 2026-11-21: it works; more improvements are postponed). Offline picker, Unicode 17 data, bundled Noto Color Emoji, search in English and Bangla, recents, skin tones (D56). Ideas not built: see status/claude-e.md
- [~] E1 (claude-e) Password manager in the keyboard (KeePassDX engine, picker in the keyboard, full vault screens in the app). CHECKPOINT 2026-11-21 (owner: pause E1, build the clipboard E3 now). Design SIGNED OFF (D53).
      Phase 0 DONE (docs/PASSWORD_MANAGER_PHASE0.md); the engine FOUNDATION is on main (merge 9330477, D64): submodule pinned to KeePassDX 4.5.8, `:vault` adapter + 14 emulator tests, engine workflow, AGP 8.13.2 / Gradle 8.14.2.
      The app does not use it yet (APK unchanged). Resume with Phase 1 (D51) = core vault in the app (create/open/unlock, entries and folders, auto-lock, safe saving); steps in status/claude-e.md
- [~] E3 (claude-e) Clipboard (owner spec 2026-11-21 in docs/CLIPBOARD_SPEC.md, design answered D65): keyboard panel on the existing strip clipboard icon, history store, search, pinned, folders, multi-select, text shortcuts (user-made), E3.13 (D79): Auto fit switch (on by default) + lines slider 1-10, cards as tall as their text in one column; built, CI #67 green, owner test pending.
- [~] E4 (claude-e) Owner request 2026-11-23: space bar highlight (the Enter key's color for 400 ms after a language switch, and for as long as the space bar is held in the cursor drag mode). Built, CI + owner test pending.
      app screens (History / Pinned / Folders / Shortcuts) + settings page, local-only, password fields / KeePassDX secrets never recorded (D43). Built: store, capture, keyboard panel (E3.1-E3.5, owner-tested OK 2026-11-21, CI #39). E3.6 app screens + settings page built (D67; CI + owner test pending). E3.7 shortcut candidates in the strip pushed (D68; CI green, owner test pending). E3.8 clipboard images built (D70; CI green, owner test pending). E3.9 lock + export/import + automatic backup built (D71; CI #50 green, owner test pending). E3.10 (D77, owner request 2026-11-23): direct shortcut insertion on Space (switch), recent copy chip in the strip, share several images; built, CI #52 green; E3.7-E3.10 tested by the owner 2026-11-23 (shortcuts, images, lock, backup OK); E3.11/E3.12 (keyboard + panel return after the unlock prompt, lock sync) built, owner test pending. Steps in status/claude-e.md
- [~] L2 (claude-l) Offline prediction for English / Bangla / Banglish from WM Keyboard Data (wasi-master/wmkeyboard-data): prefix autocomplete + next-word
      (uni/bi/trigram + learned usage), compact binary tables, async. Built: engine + data (L2.3), English/Bangla strip, arrow, learning (L2.4, CI green up to #13); owner device test OK (2026-11-18); L2.5 (settings switch, clear button, licenses screen) is done by claude-g in G1.2 (63f95a7); steps in status/claude-l.md
- [x] L3 (claude-l) Banglish prediction quality (D54, owner-tested OK 2026-11-18, CI #15): bridge from Banglish words to the Bangla pair data for next-word, lean Banglish for 1-3 letter prefixes, typed word as first strip choice, no repeat of the word just typed. Steps in status/claude-l.md
- [x] L4 (claude-l) প্রভাত (Probhat) and জাতীয় (National) fixed Bangla layouts as generated data (also usable by claude-s's T6), typing through the layouts, language-key long-press popup (D19).
      Taken over from claude-e's T9 (T9.1, T9.2, T9.4) on the owner's order 2026-11-20. Design answered (D58-D60), built in 46d05d1 (CI #26 green), owner-tested: works (2026-11-20). Steps in status/claude-l.md
- [x] L5 (claude-l) Vowel-sign keys drawn without the dotted circle (D62, owner request after the L4 test). Pushed in 65133d1, CI #29 green, owner-tested OK (2026-11-21). Steps in status/claude-l.md
- [x] L6 (claude-l) The signs left out of L4 (॥ ৲ ৴-৹ ৺ ৄ ৢ ৣ ঽ) as long-press hints on the ?123 pages, Bangla layouts only (D63). Pushed in 65133d1, CI #29 green, owner-tested OK (2026-11-21). Steps in status/claude-l.md
- [~] L1 (claude-l) Multi-Claude workflow (D20-D22): CLAUDE.md rules, owner tags, `status/` notes files, new paste command in README
- [x] L7 (claude-l) Trace of the features nobody works on (owner request 2026-11-22, read-only, nothing built): the unowned list, the idle claims (T6, T9.3, E1) and stale notes are in status/claude-l.md (L7). The owner picked auto-correction first (L8)
- [~] L8 (claude-l) Auto-correction and typo fixes (owner pick 2026-11-22, design D69): Settings > Suggestions switch "Auto-correction" (off by default), English only, auto-fix on space + Backspace undo, fix first in bold and typed word second in quotes on the strip. Pushed in 7acebe7 (CI #47 green); the owner found it too weak and D73 makes it stronger (two slips, rare listed words, context), pushed in 085e217 (CI #51 green), owner test pending. Steps in status/claude-l.md
- [~] L9 (claude-l) Space-bar cursor drag (owner go-ahead 2026-11-22, design D72): hold the space bar, then slide moves the cursor; a quick slide still switches language; Settings > Typing > Space bar (switch, hold time, speed); a tick per character. Pushed in 874ef94, CI #49 green; owner test pending. Steps in status/claude-l.md
- [~] L10 (claude-l) Themes (owner request 2026-11-22; no theme store): complete freedom to customize the look (colors, shape, text, more). Design answered (D75). Step 1 (colors + presets) in progress; then background picture, wallpaper colors + share/import, key shape + text. Steps in status/claude-l.md
- [x] L11 (claude-l) LICENSE file (owner order 2026-11-22, taken over from claude-e's E1.13(e)): GNU GPL v3 text in `LICENSE`, README License section, Avro MPL headers checked (D74)

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
3. (answered 2026-11-17: whole app is GPL-3.0, see D25. The LICENSE file was added on 2026-11-22, see D74.)
4. (answered 2026-11-21: one APK with the INTERNET permission for the voice model download, no offline-only flavor, see D66; Offline mode of D10 blocks the download)
5. (answered 2026-11-21: E1 GO and how the engine enters main, see D64)
6. (answered 2026-11-17: prediction data, see D41 and D42)
7. (answered 2026-11-18: Banglish prediction quality, see D54)
8. (answered 2026-11-19: emoji layout, skin tones, Bangla search names, hide undrawable emoji, see D56)

9. (answered 2026-11-20: the "Show word suggestions" switch does not touch Avro spelling choices, see D57)
10. (answered 2026-11-20: Probhat/National phone design, see D58-D60)
11. (answered 2026-11-21: Bangla digits as a setting, hints only in Bangla layouts, see D61-D63)
12. (answered 2026-11-21: clipboard 4th tab, card layout toggle, shortcut trigger, what is never recorded, see D65)
13. (answered 2026-11-21: voice typing design: code in the APK, model download + import, mic hold/tap panel, engine per language, Voice test page, build order, see D66)
14. (answered 2026-11-22: the English local model is parakeet-tdt_ctc-110m Q4_K_M GGUF, about 90 MB, from the transcribe.cpp catalogue)
15. (answered 2026-11-22: start with auto-correction and typo fixes, see L8)
16. (answered 2026-11-22: auto-correction is a switch, off by default; auto-fix on space with Backspace undo; English only; fix first in bold, typed word second in quotes, see D69)
17. (answered 2026-11-22: hold then slide moves the cursor, quick slide still switches language, hold time and speed sliders, a tick per character, see D72)
18. (answered 2026-11-22: themes order colors+presets, background picture, wallpaper colors+share/import, shape+text; one theme for day and one for night; simple 5 colors first, Advanced for all 11, see D75)

## Known notes
Long-press on the language pill does nothing until T9 adds a second Bangla layout (claude-e). Launcher icon and setup screen are placeholders.
Language choice is saved (T10). Avro choices of T8a are listed in `status/claude-g.md`.
