# claude-l notes (only claude-l edits this file; others only append lines under "Warnings")
Updated: 2026-11-17, session 4. Focus: Avro typing + suggestions (T8a/b/c all done and owner-tested), workflow setup (L1).

## Warnings from other Claudes (read first; delete a line when handled)
(none)

## Tasks and steps
L1 Multi-Claude workflow [~]
  L1.1 [x] Design agreed with the owner (D20-D22).
  L1.2 [x] CLAUDE.md rules, STATUS.md owner tags and hand-offs, status/ notes files, README paste command.
  L1.3 [x] Pushed to main.
  L1.4 [ ] Owner opens chats for claude-e/g/s with the new command; fix the rules if anything is unclear.
T8a Avro typing [x] owner-tested OK on device.
T8b Suggestion strip + dictionary search [x] owner-tested OK on device, feels instant (2026-11-17)
  T8b.1 [x] Owner decided the strip layout (D23) and the colon behaviour (D24).
  T8b.2 [x] Engine (pure Kotlin): generated regex tables + `AvroRegexBuilder` + `AvroSuggester` (bucket search, suffixes, Levenshtein
            ranking) + assets `assets/avro/dict/<bucket>.txt`, `suffixes.tsv`; 9 new unit tests (26 total). CI #7 (bae17eb) green incl. tests.
  T8b.3 [x] `ime/SuggestionWorker` (HandlerThread, coalescing) + `ime/AssetWordSource` (LRU 12 buckets; letter 'a' needs 7); `MonsoonIme` asks
            after each composing change. CI #8 (3fbce27) green.
  T8b.4 [x] Strip UI (KeyboardView.setSuggestions / drawSuggestions / touch routing, MonsoonIme.onSuggestion). CI #9 (cbb5d34) green, owner-tested OK.
T8c Smart colon (D24) [x] owner-tested OK (2026-11-17), CI #10 (1edcadc) green
  T8c.1 [x] `PhoneticComposer`: ':' joins the word after a letter; a trailing ':' is shown plain, a letter after it makes it visarga; 5 tests + 1 search test.
  T8c.2 [x] `MonsoonIme.showComposing`: no dictionary request while the word ends with a plain colon (only the word is shown).
  T8c.3 [x] Owner device test passed.

L2 Offline prediction from WM Keyboard Data [~] decisions D41/D42 answered, building
  L2.1 [x] Research: license/attribution, file formats, sizes, quality (clone in /home/claude/wasi-master/wmkeyboard-data, not in the repo).
  L2.2 [x] Owner decisions recorded (D41, D42).
  L2.3 [ ] `tools/pack_wm_data.py` + third_party/wmkeyboard-data/ (NOTICE, source hashes) -> `assets/predict/{en,bn,rom}.mslm`; pure-Kotlin `predict/`
           (reader, stupid-backoff scorer + user boost, WordTracker, TextNorm, UserStats) + JUnit tests. Packs are mmap'd (needs noCompress for .mslm).
  L2.4 [ ] Android side: PredictionWorker (HandlerThread), SQLite user store, IME hooks (English word tracking, Bangla commit hook, learning), strip:
           left arrow toggle + suggestions for next-word (KeyboardView). Then the owner's test checklist.
  L2.5 [ ] claude-g: learning switch + clear button + Licenses screen (warning sent); credits text in `third_party/wmkeyboard-data/NOTICE.md`.
  Facts: use only v1 `en_bigrams/trigrams` and `bn_bigrams/trigrams` (Leipzig CC BY 4.0, Tatoeba); NOT `*_v2/_v3` pairs (OpenSubtitles non-commercial);
  `bn_rom*` was merged with the data owner's private Facebook chat counts (see scripts/import_facebook_chats.py), license unstated.
  Bengali files use decomposed য় (U+09AF U+09BC); Avro commits precomposed U+09DF.

## Next step
T8a, T8b and T8c are all owner-tested OK (CI #10, 1edcadc, green). Open: L1.4 (owner starting claude-e/g/s), L2 (waiting for the owner's answers) and the Avro
choices below. L2: read the owner's answers in chat/DECISIONS.md, then do L2.3. Do not start other new features without the owner's go-ahead.

## Half-done files / known bugs
None. Avro choices I made without asking (the owner tested T8a without objecting; revisit only if they complain): '.' right after a Bangla word gives
the dari (।); '^' after a word gives chandrabindu; capitals are never auto-applied in Bangla. ':' follows D24 (smart colon).
