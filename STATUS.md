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
- [x] G5 (claude-g) Voice typing DONE 2026-11-25 (owner-tested: local English + Bangla, Google voice after allowing the mic for Google's speech service, tap panel with caret editing, waveform, edge-pinned language/mic keys, speech detector, Bangla punctuation help; design D66 + D76 + D77 + D81 + D82 + D88, spec docs/VOICE_TYPING_SPEC.md). Dropped by the owner: Wi-Fi-only check, Voice test page. Details in status/claude-g.md
- [x] T11 (claude-l) Bangla digits (D18, D61). TAKEN OVER from claude-e on the owner's order 2026-11-21 (claude-e: remove it from your list). Built as a Settings > Typing choice "Digits in Bangla mode: English / Bangla" that swaps typed digit and hint on every digit key; pushed in 65133d1, CI #29 green, owner-tested OK (2026-11-21)
- [x] E2 (claude-e) Emoji system: DONE FOR NOW (owner 2026-11-21: it works; more improvements are postponed). Offline picker, Unicode 17 data, bundled Noto Color Emoji, search in English and Bangla, recents, skin tones (D56). Ideas not built: see status/claude-e.md
- [~] E1 (claude-e) Password manager in the keyboard (KeePassDX engine, picker in the keyboard, full vault screens in the app). Phase 1 (E1.13) DONE and owner-tested (2026-11-25). Phase 2 (E1.14, the picker in the keyboard) is built; the unlock card had two bugs (keyboard opening by itself, back to home screen) and is fixed / instrumented, waiting for the owner's re-test (Needs user #22). Phase 3 step 1 (E1.15, fingerprint unlock) is built, waiting for the owner's phone test (Needs user #23); then backup/restore, attachments, one-time codes, Phase 4 Autofill. Steps in status/claude-e.md. Design SIGNED OFF (D53).
      Phase 0 DONE (docs/PASSWORD_MANAGER_PHASE0.md); the engine FOUNDATION is on main (merge 9330477, D64): submodule pinned to KeePassDX 4.5.8, `:vault` adapter + 14 emulator tests, engine workflow, AGP 8.13.2 / Gradle 8.14.2.
      The app does not use it yet (APK unchanged). Resume with Phase 1 (D51) = core vault in the app (create/open/unlock, entries and folders, auto-lock, safe saving); steps in status/claude-e.md
- [x] E3 (claude-e) Clipboard DONE 2026-11-23, owner-tested (E3.1-E3.13; spec docs/CLIPBOARD_SPEC.md; D65 D67 D68 D70 D71 D77 D79): keyboard panel on the strip clipboard icon (search, chips, 2-column / 1-column / Auto fit cards, multi-select, menu), history store, pinned, folders, user-made text shortcuts (strip candidates or direct insertion), images, recent copy chip, biometric lock, backup + automatic backup, app screens + settings. Password fields / KeePassDX secrets are never recorded (D43). Later idea (owner, ask first): an encryption option for the history like KeePassDX. Steps in status/claude-e.md
- [x] E4 (claude-e) Space bar highlight DONE, owner-tested: the Enter key's color for 400 ms after a language switch and while the cursor drag mode is held.
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
- [~] L10 (claude-l) Themes (D75, no theme store): STEP 1 colors + presets DONE (bd26704, CI #64 green, owner: works); day and night theme, editor with live preview, color picker. Still to do (the app revamp L12 is done; design questions for step 2 asked 2026-11-25): step 2 background picture, step 3 wallpaper colors + share/import as text, step 4 key shape (roundness, border, shadow) + text size. Steps in status/claude-l.md STEP 2 (background picture, D89) is pushed and waits for the owner's test (Needs user #21).
- [x] L11 (claude-l) LICENSE file (owner order 2026-11-22, taken over from claude-e's E1.13(e)): GNU GPL v3 text in `LICENSE`, README License section, Avro MPL headers checked (D74)
- [x] L12 (claude-l) App revamp (owner request 2026-11-23; design D83, changed by D85 and D86; R1 theme engine + edge-to-edge + restyled pages, R2 icon + logo + start screen + new home + search box, R3 floating bubbles: Try it everywhere, a look-only palette preview in the theme editor) DONE, owner 2026-11-25: "everything seems to be fine for now". CI #86 green. Details in status/claude-l.md.

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
19. (answered 2026-11-24, D83: app revamp design) -> L12.
20. (answered 2026-11-25: owner tested the app revamp, "everything seems to be fine for now", see L12, D85, D86)
21. (claude-l, 2026-11-25) Owner test of the theme BACKGROUND PICTURE (latest release build, D89): open a custom theme in the theme editor (or copy one), tap Background picture > Choose a picture, pick a photo (also try a tall phone photo and a screenshot); check it shows behind the letter keys, then the emoji panel, the clipboard panel and voice typing; move Fade and Key opacity (the keyboard preview and your real keyboard change at once); set it as day or night theme; copy the theme (the copy keeps the picture); remove the picture. Say if the fade / key defaults, the see-through look or the picker feel wrong. ALSO (D90): in the editor open Background picture > Position and zoom: drag and pinch the picture inside the frame, switch Portrait / Landscape (each has its own placement), check your real keyboard in both orientations, and the reset button.
 22. (claude-e, 2026-11-26) Owner re-test of the PASSWORD PICKER in the keyboard (Phase 2, E1.14; latest release build, includes the unlock-card fix of my commit after 523c30b; Phase 1 passed on 2026-11-25). In a login screen of any app: (1) the strip has a new key icon right after the clipboard icon; tap it: the picker replaces the keys. (2) Locked: it shows "Unlock"; tap it: a see-through card (the app stays visible behind a dim layer) asks for the master password; Cancel / the dim area / Back close it and the normal keys come back (the picker must NOT pop up again). (3) Type the password + Unlock: the card closes, the picker opens by itself over the same field. (4) Chips This app / Recent / All; tap a folder, "back"; the search icon: type with the keyboard's keys, results show above them, Enter keeps the query. (5) Tap an entry: Username / Password chips open under it; Username types it and the picker stays; Password types it and the picker closes; in a field that is NOT a password field it asks "Type the password here anyway?". (6) Lock icon locks the vault, gear opens the app's vault screen. (7) THE TWO BUGS: after unlock / cancel, does the keyboard still pop up by itself or in another app? Does the card ever send you to the home screen? If yes: Settings > Voice typing > Problem report, copy the text to me and tell me which app was behind.
 23. (claude-e, 2026-11-26) Owner test of FINGERPRINT UNLOCK (Phase 3 step 1, E1.15; latest release build, my commit titled "E1.15 fingerprint unlock"; needs Android 9+ and an enrolled fingerprint). (1) Vault settings (gear in the vault app): under Security there is a switch "Unlock with fingerprint"; turn it on: the line below says it finishes at the next master password unlock. (2) Lock the vault, unlock it by TYPING the master password (in the app's unlock page or the keyboard's card): the phone's fingerprint prompt "Set up fingerprint unlock" appears once; touch the sensor; the switch line now says "Set up". "Not now" skips it (it is then not asked again until the next restart). (3) Lock the vault again and open it (keyboard key icon > Unlock, or the vault app): the fingerprint prompt shows at once, WITHOUT the soft keyboard; touch the sensor and the vault opens. (4) In that prompt press "Master password": the keyboard comes up and typing works as before; a wrong finger several times also falls back to typing. (5) Restart the phone, open the vault: it asks for the master password with a line "The phone restarted..."; after typing it, the set-up prompt appears again (fingerprint works afterwards). (6) Add a new fingerprint in the phone's settings, open the vault: it asks for the master password with a line about changed fingerprints (and sets up again). (7) Switch the setting off: fingerprint is not offered any more. (8) The "after 3 days" rule cannot be tried quickly, tell me if you want a short test button. Report anything odd (prompt twice, keyboard missing, going to the home screen).

## Known notes
Long-press on the language pill does nothing until T9 adds a second Bangla layout (claude-e). Launcher icon and setup screen are placeholders.
Language choice is saved (T10). Avro choices of T8a are listed in `status/claude-g.md`.
