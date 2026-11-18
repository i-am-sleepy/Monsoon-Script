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
- [ ] T9 (claude-e) Fixed layouts as data: Provat and National from `third_party/avro-reference/layouts` (key maps, images stripped); Bijoy
      Classic from the standard chart (owner must verify); language-key long-press popup (D19). Bornona, Munir Optima, Avro Easy later (D17)
- [ ] T10 (claude-g) Settings activity + SharedPreferences: switches for the language key and space-bar swipe (both default ON, D15,
      `KeyboardView.setLanguageOptions`), persist the chosen language, later more settings
- [ ] T11 (claude-e, after T9) Bangla digit hints on digit keys (English typed, Bangla long-press, D18)
- [~] E1 (claude-e) Password manager in the keyboard (KeePassDX engine, picker in the keyboard, full vault screens in the app). Owner priority,
      before T9. Design SIGNED OFF (D53, 2026-11-18). Phase 0 (feasibility check) is running on the side branch `e1-feasibility` (D49); it needs ONE owner
      action to start its CI job (Needs user #5). Nothing from Phase 0 is on main. Log: docs/PASSWORD_MANAGER_PHASE0.md. Brief: docs/PASSWORD_MANAGER_BRIEF.md
- [~] L2 (claude-l) Offline prediction for English / Bangla / Banglish from WM Keyboard Data (wasi-master/wmkeyboard-data): prefix autocomplete + next-word
      (uni/bi/trigram + learned usage), compact binary tables, async. Built: engine + data (L2.3), English/Bangla strip, arrow, learning (L2.4, CI green up to #13); awaiting the owner's device test; L2.5 (settings switch, clear button, licenses screen) is claude-g's; steps in status/claude-l.md
- [~] L1 (claude-l) Multi-Claude workflow (D20-D22): CLAUDE.md rules, owner tags, `status/` notes files, new paste command in README

## Hand-offs between Claudes (keep these; warn the others if you must break one)
- Language switching has ONE entry point: `MonsoonIme.setLanguage(l: Language)`. The pill, space-bar swipe and claude-s's Ctrl+Space call it; nobody adds a second path.
- claude-g stores the language by its enum NAME (string); an unknown saved name falls back to EN. claude-e may add enum values (Provat, Bijoy, National) freely.
- claude-e's "last-used Bangla layout" for the pill tap uses claude-g's persistence once T10 exists; until then keep it in memory only.

## Roadmap (details in docs/REQUIREMENTS.md)
v0.2 Bangla (T6-T11). v0.3 Emoji (full set, search, recents) and clipboard one-tap. v0.4 Suggestions/autocorrect/next-word.
v0.5 Themes, resizing, one-handed, split, number row. v0.6 Edit/cursor panel, number pad, space-bar cursor drag. v0.7 Polish, perf, settings.
Password manager (E1): Phase 0 now; later phases follow D51 after the go/no-go report.

## Needs user (ask with the question tool; record answers in DECISIONS.md; tag with your name)
1. Placeholders picked without asking: teal accent (Enter key), system haptic on every key, no key sound. Confirm or change. (any Claude)
2. When Bijoy Classic is built (T9): ask the owner to verify the key map. (claude-e)
3. (answered 2026-11-17: whole app is GPL-3.0, see D25. The LICENSE file comes after the E1 feasibility check.)
4. When the first network feature appears: also ship an offline-only APK flavor with no INTERNET permission? (see D10) (any Claude)
5. Password manager (E1) Phase 0, waiting: on the BRANCH `e1-feasibility` (not main) move the file `tools/e1-feasibility.workflow.yml` to `.github/workflows/e1-feasibility.yml`
   (GitHub website: switch to that branch, open the file, pencil icon, change the file path, commit to the same branch). claude-e's GitHub tool cannot create workflow files (403),
   so only the owner or a Claude Code session can. Never merge that file into main as it is. (claude-e)
6. (answered 2026-11-17: prediction data, see D41 and D42)

## Known notes
Long-press on the language pill does nothing until T9 adds a second Bangla layout (claude-e). Launcher icon and setup screen are placeholders.
Language choice is not persisted until T10 (claude-g). Avro choices of T8a are listed in `status/claude-l.md`.
