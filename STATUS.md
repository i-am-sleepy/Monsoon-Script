# STATUS (shared board: tasks and owners only; keep under one page)

CI state is NOT kept here: read the `ci-status` branch (CLAUDE.md). Each Claude's next step, steps with progress, half-done files and
test checklist live in `status/claude-X.md`. Edit only your own task lines. APK (release-signed, updates install in place, contains
everyone's merged work): https://github.com/i-am-sleepy/Monsoon-Script/releases/download/latest/MonsoonScript.apk

## Now
v0.2 Bangla. T7 engine done, T8a done and green (CI #6) but not yet tried on the owner's device. Four Claudes now work in parallel (D20-D22):
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
  - [ ] T8b (claude-l) Suggestion strip while composing + compact dictionary index from `avro-dictionary.tsv` (needs the owner's layout decision first)
- [ ] T9 (claude-e) Fixed layouts as data: Provat and National from `third_party/avro-reference/layouts` (key maps, images stripped); Bijoy
      Classic from the standard chart (owner must verify); language-key long-press popup (D19). Bornona, Munir Optima, Avro Easy later (D17)
- [ ] T10 (claude-g) Settings activity + SharedPreferences: switches for the language key and space-bar swipe (both default ON, D15,
      `KeyboardView.setLanguageOptions`), persist the chosen language, later more settings
- [ ] T11 (claude-e, after T9) Bangla digit hints on digit keys (English typed, Bangla long-press, D18)
- [~] L1 (claude-l) Multi-Claude workflow (D20-D22): CLAUDE.md rules, owner tags, `status/` notes files, new paste command in README

## Hand-offs between Claudes (keep these; warn the others if you must break one)
- Language switching has ONE entry point: `MonsoonIme.setLanguage(l: Language)`. The pill, space-bar swipe and claude-s's Ctrl+Space call it; nobody adds a second path.
- claude-g stores the language by its enum NAME (string); an unknown saved name falls back to EN. claude-e may add enum values (Provat, Bijoy, National) freely.
- claude-e's "last-used Bangla layout" for the pill tap uses claude-g's persistence once T10 exists; until then keep it in memory only.

## Roadmap (details in docs/REQUIREMENTS.md)
v0.2 Bangla (T6-T11). v0.3 Emoji (full set, search, recents) and clipboard one-tap. v0.4 Suggestions/autocorrect/next-word.
v0.5 Themes, resizing, one-handed, split, number row. v0.6 Edit/cursor panel, number pad, space-bar cursor drag. v0.7 Polish, perf, settings.

## Needs user (ask with the question tool; record answers in DECISIONS.md; tag with your name)
1. Placeholders picked without asking: teal accent (Enter key), system haptic on every key, no key sound. Confirm or change. (any Claude)
2. When Bijoy Classic is built (T9): ask the owner to verify the key map. (claude-e)
3. License for the non-Avro parts of the app (MIT / Apache-2.0 / GPL-3.0 / none). (any Claude)
4. When the first network feature appears: also ship an offline-only APK flavor with no INTERNET permission? (see D10) (any Claude)
5. T8b: while composing, does the strip show suggestions across the full width and hide the icons, like Gboard? (claude-l, asks at the start of T8b)

## Known notes
Long-press on the language pill does nothing until T9 adds a second Bangla layout (claude-e). Launcher icon and setup screen are placeholders.
Language choice is not persisted until T10 (claude-g). Avro choices of T8a are listed in `status/claude-l.md`.
