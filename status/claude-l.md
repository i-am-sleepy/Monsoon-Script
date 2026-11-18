# claude-l notes (only claude-l edits this file; others only append lines under "Warnings")
Updated: 2026-11-18, session 5. Focus: Avro typing + suggestions (T8a/b/c all done and owner-tested), workflow setup (L1).

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
  L2.3 [x] `tools/pack_wm_data.py` -> `assets/predict/{en,bn,rom}.mslm` (11 MB) + SOURCES.md/NOTICE.md; pure-Kotlin `predict/` (LangPack, Predictor, UserStats,
           WordTracker, TextNorm); 41 JUnit tests on the real packs, passing locally (kc.sh + JUnitCore). Pushed with the next commit; CI result below.
  L2.4 [~] Android side, in three pushes (each CI green before the owner gets a checklist):
    L2.4a [x] English autocomplete + next-word in the strip: PackLoader (mmap, noCompress), PredictionWorker, WordTracker hooks in MonsoonIme, strip arrow in KeyboardView. CI #12 (5e51504) green.
    L2.4b [~] Learning: `ime/UserStore` (SQLite), `ime/LearnSettings` (prefs contract for claude-g), `WordTracker.onWord` -> `PredictionWorker.learn`, gating (password/e-mail/URL,
              NO_PERSONALIZED_LEARNING, NO_SUGGESTIONS, `learn_enabled` default ON), flush on idle/close. Fixed a UserStats bug on the way (word rows were not restored). Pushed, waiting for CI.
    L2.4c [~] Bangla: tracker fed from Avro commits, next-word after a Bangla word (bn.mslm), Banglish pairs learned (English mode), bn frequency tie-break for Avro suggestions
              (`AvroSuggester` rank param + test). Pushed, waiting for CI. Then: owner checklist.
  L2.5 [ ] claude-g: learning switch + clear button + Licenses screen (warning sent); in-app credits text is `assets/licenses/prediction-data.txt`.
  Facts: use only v1 `en_bigrams/trigrams` and `bn_bigrams/trigrams` (Leipzig CC BY 4.0, Tatoeba); NOT `*_v2/_v3` pairs (OpenSubtitles non-commercial);
  `bn_rom*` was merged with the data owner's private Facebook chat counts (see scripts/import_facebook_chats.py), license unstated.
  Bengali files use decomposed য় (U+09AF U+09BC); Avro commits precomposed U+09DF.

## L3 steps (D54, owner answered 2026-11-18)
  L3.1 [x] bridge: `Bridge` (predict/), `assets/predict/rom2bn.bin` built by `BridgeBuilder` (test sources) + sync test, Predictor uses it in Banglish context, worker loads it lazily
  L3.2 [x] short-prefix lean Banglish (prior 0.4 for 1-3 letters, tuned with probes) + never repeat w1 after a space
  L3.3 [~] typed word as first strip choice while a word is typed (PredictionWorker). Pushed (966928a), waiting for CI; then send the owner the expected results
  Reminder: pure-Kotlin parts are tested locally (kc.sh); Android parts only by CI.

## Next step
L2.4c is pushed (451d34c). Once its CI run is green, send the owner the checklist below with the APK link, then wait for their report. Open: L2.5 (claude-g wires the learning
switch, Clear button and Licenses screen using `ime/LearnSettings` and `assets/licenses/prediction-data.txt`; warning sent), L1.4 (owner starting claude-e/g/s) and the Avro
choices below. Do not start other new features without the owner's go-ahead.

## Half-done files / known bugs
None known. Not verified on a device yet: everything in L2.4 (Android code is only compiled by CI). Things to watch in the owner's report: strip arrow touch handling, English tap-to-complete
replacing the right text in odd apps, backspace resync cost, cold start of the first prediction (packs map lazily), APK size (+~11 MB because the packs are stored uncompressed).
Avro choices I made without asking (the owner tested T8a without objecting; revisit only if they complain): '.' right after a Bangla word gives the dari (।); '^' after a word gives
chandrabindu; capitals are never auto-applied in Bangla. ':' follows D24 (smart colon).

## Test checklist for my latest build (owner)
Build = the newest APK after commit 451d34c (L2.4a + b + c together; the learning switch is not in Settings yet, learning is simply ON).
1. English, any text field: type "hel" -> the strip shows completions (e.g. help, hello) next to a small arrow at the left; tap one -> the typed letters are replaced and a space follows.
2. English: type "how " -> next words appear (e.g. to, much); tap one. Type "I " -> e.g. don't, think.
3. Tap the small arrow at the left of the strip: the icons (globe, EN, emoji, clipboard, mic) show; tap it again (or type) -> suggestions return. With nothing to suggest (empty field) the strip is just the icons, no arrow.
4. English: type "ami " then "tum" -> Banglish words (tumi ...) should appear; type a few of your own names/words twice; later they should show up for their first letters.
5. Capitals: type "He" -> suggestions start with a capital; at the start of a sentence a suggested word is capitalized.
6. বাংলা (Avro): type "ami" -> the usual Bangla words (common words first now); tap one -> after the space the strip shows likely next words (e.g. আমার, ...); tap one.
7. Password / e-mail / URL / number fields: no suggestions, icons only.
8. Backspace a word and retype; move the cursor into the middle of a word and type: suggestions should follow the word at the cursor, nothing should be deleted wrongly when you tap one.
9. Speed: suggestions should show about as fast as the Bangla ones did; tell me if typing ever stutters.
10. APK is about 11 MB bigger than before (the data is stored uncompressed so it is not copied). Tell me if you want it smaller.
