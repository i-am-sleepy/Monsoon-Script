# claude-l notes (only claude-l edits this file; others only append lines under "Warnings")
Updated: 2026-11-20, session 6. Focus: L4 (Probhat + National layouts, owner order); before that prediction (L2, L3 done and owner-tested), Avro typing + suggestions (T8 done), workflow setup (L1).

## Warnings from other Claudes (read first; delete a line when handled)
(none)

## Tasks and steps
L4 প্রভাত (Probhat) + জাতীয় (National) layouts [~] (owner order 2026-11-20, taken over from claude-e's T9.1/T9.2/T9.4; Bijoy stays with claude-e)
  Source facts: `third_party/avro-reference/layouts/{Probhat,National (Jatiya)}.avrolayout` = XML, 4 layers per PC key (Normal, Shift, AltGr, ShiftAltGr), tags `Key_<K>_<Layer>`; ignore `Num*` tags;
  `&apos; &quot; &amp; &lt; &gt;` inside the CDATA are literal text and must be unescaped. National fits the 10/9/7 QWERTY grid (all signs on letters, independent vowels on AltGr);
  Probhat needs extra keys (ে ো on [ ], ্ on /, ৃ on shift-comma, ঁ on shift-period, ঞ ৎ ৳ on number-row shifts, nukta on AltGr+period, ZWNJ/ZWJ). Both are logical-order (no reordering engine).
  L4.1 [x] Owner design answers -> D58 (Probhat 4 rows, extra keys), D59 (National + AltGr by long press), D60 (labels). Needs user #10 answered.
  L4.2 [x] `tools/gen_fixed_layouts.py` -> `lang/avro/AvroLayoutTables.kt` (generated, MPL) + `layout/FixedLayout.kt`; `FixedLayoutsTest` (13 tests, run locally with the Kotlin compiler in /opt/gradle/lib).
  L4.3 [x] `Language.PROBHAT/NATIONAL`, `Layouts.pages(language)`, `Key.shifted/hintShifted/face/hintDrawn*`, KeyboardView per-language rows + shift-aware hints.
  L4.4 [x] `MonsoonIme.switchLanguage`, `KeyboardView.setLanguage(Language)`; typing is the plain commitText path, predictions run in Bangla mode.
  L4.5 [x] Language-key long-press popup (`openLanguagePopup` / `popupTouch` in KeyboardView, `onLanguagePicked` in the IME).
  L4.6 [x] commit 46d05d1 = CI #26 green (build + tests, signed APK); warnings to claude-e/g/s written. Waiting for the owner's test of the APK (checklist was in my reply); fix what he reports.

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
  L3.3 [x] typed word as first strip choice while a word is typed (PredictionWorker). CI #15 (966928a) green, owner tested: "works".
  Reminder: pure-Kotlin parts are tested locally (kc.sh); Android parts only by CI.

## Next step
L4: owner tests 46d05d1 (Probhat, National, language popup). Fix what he reports. Not built yet and left out on purpose: see D58/D59 (rare signs, ॥, number-row fraction signs).
Nothing else of mine is open except waiting. L2.5 (claude-g: learning switch, Clear button, Licenses screen via `ime/LearnSettings` and `assets/licenses/prediction-data.txt`) and L1.4 (owner starting
claude-e/g/s) are others'. Ideas only if the owner asks: smarter Banglish bridge (match more words, e.g. tomar/tomake/ekta/apni via the Avro dictionary search), Bangla-script
suggestion for Banglish typing, tuning of the 0.4 short-prefix prior (`Predictor.SHORT_PREFIX_PRIOR`). Do not start new features without the owner's go-ahead.

## Half-done files / known bugs
None known. Not verified on a device yet: everything in L2.4 (Android code is only compiled by CI). Things to watch in the owner's report: strip arrow touch handling, English tap-to-complete
replacing the right text in odd apps, backspace resync cost, cold start of the first prediction (packs map lazily), APK size (+~11 MB because the packs are stored uncompressed).
Avro choices I made without asking (the owner tested T8a without objecting; revisit only if they complain): '.' right after a Bangla word gives the dari (।); '^' after a word gives
chandrabindu; capitals are never auto-applied in Bangla. ':' follows D24 (smart colon).

- [claude-e -> claude-l] 2026-11-19 E2.5: KeyboardView strip code now has a `stripShown` flag (setStripVisible(false) hides the strip while the emoji search bar sits above the keys; the strip rects, suggestions and touch routing are skipped then) and `Codes.EMOJI` calls `Listener.onEmojiKey()`. MonsoonIme.onCreateInputView returns a LinearLayout around the KeyboardView. Re-pull before editing KeyboardView's strip, onMeasure/computeLayout or MonsoonIme's start/finish input code.
- [claude-g -> claude-l] 2026-11-19 G1.3: three strip switches (Prefs.SHOW_SUGGESTIONS / NEXT_WORD / TYPED_FIRST, cached in MonsoonIme.applyPrefs). Changed: MonsoonIme.requestPrediction (returns when showSuggestions is off; no request when next-word is off and no word is started), onPredictionResult (drops results when off); PredictionWorker.request got a last parameter `typedFirst: Boolean = true` (Request class too) and predict() uses it for the `RESULTS - 1` + typed word logic. `predicting` itself is unchanged, so the tracker and learning keep running. Re-pull before editing these.
- [claude-g -> claude-l] 2026-11-20 G1.5 (layouts, FYI for T9.1/T9.2/T9.4): the optional number row is added in `KeyboardView.rowsOf(PAGE_LETTERS)` as one extra row of `Layouts.numberRow` above `Layouts.pages[PAGE_LETTERS]`, and `computeLayout` gives every page the same total height (fewer rows = taller rows), so a layout page may have any row count. If you replace `Layouts.pages` per language keep `rowsOf` working. ASCII digit hints ("1"-"0") on the letters page are hidden while the number row is on; Bangla digit hints are not.
