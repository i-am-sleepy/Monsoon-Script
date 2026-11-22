# claude-l notes (only claude-l edits this file; others only append lines under "Warnings")
Updated: 2026-11-22, session 7. Doing: L9 space-bar cursor drag (owner go-ahead; design answered D72; built and pushed, waiting for CI and the owner test). L8 auto-correction is pushed (7acebe7, CI #47/#48 green) and waits for the owner's combined test. Done: L7 (read-only trace of the features nobody works on). Before that: L5 + L6 + T11, all owner-tested; L4 (Probhat + National layouts); prediction (L2, L3), Avro typing + suggestions (T8), workflow setup (L1).

## Warnings from other Claudes (read first; delete a line when handled)
(none)

## Tasks and steps
L9 Space-bar cursor drag [~] (owner go-ahead 2026-11-22; roadmap v0.6; design asked first, Needs user #17)
  L9.1 [x] Read: `KeyboardView.down/move/up` (space bar: `swipeArmed` + `SWIPE_DP` 64 -> one `onLanguageToggle`, no space typed), `hasLongPress` (space has none), `MonsoonIme.moveCursor(left)` (DPAD key events, resets `lastEditAt`).
  L9.2 [x] Design answered 2026-11-22, recorded as D72: hold then slide moves the cursor, a quick slide still switches language, hold-time and speed sliders in Settings, a tick per character.
  L9.3 [x] `view/SpaceDrag` (pure, 9 JUnit tests pass locally) + `KeyboardView` (dragPending/ptrDrag/dragRunnable, `setSpaceDrag`, Listener `onCursorMove`, no space typed after a moved drag). L9.4 [x] `MonsoonIme.onCursorMove` + `moveCursor` clears the L8 fix state + applyPrefs. L9.5 [x] Prefs SPACE_DRAG/DRAG_DELAY/DRAG_SPEED + Typing page rows + strings (warning sent to claude-g and claude-e).
  L9.6 [~] Pushed, waiting for CI. Owner test (needs the space-bar swipe language switch ON to test that part): (1) hold the space bar about half a second until a buzz, slide right/left: the cursor moves one character at a time with a tick each, then lift: no space typed; (2) a normal tap on space still types a space;
       (3) a quick slide across the space bar still switches language; (4) hold the space bar and lift without sliding: a space is typed; (5) Settings > Typing > Space bar: switch off = nothing happens on hold; hold time and speed sliders change the feel; (6) works in Avro/Probhat with a word in progress. Not on device yet: all of it.
L8 Auto-correction and typo fixes [~] (owner pick 2026-11-22 from the L7 list; ask first, Needs user #16)
  L8.1 [x] Read Predictor / PredictionWorker / TextNorm and the MonsoonIme hooks (onText, acceptPrediction, onBackspace). Push access: the owner pasted a fine-grained token in chat; it is used only as a one-off `git -c http.extraheader` (clone in /home/claude/ms), never written to a file or git config; tell the owner to revoke it when done.
  L8.2 [x] Design answered 2026-11-22, recorded as D69: switch OFF by default; when on, auto-fix on space + Backspace undo; English only; fix first in bold, typed word second in quotes. (claude-e took D68 meanwhile.)
       My plan: pure-Kotlin `predict/Corrector` (edit-distance 1-2 candidates found by lookup in the pack word list, keyboard-neighbour costs, ranked by frequency + context + learned words; only for words that no pack and no learned list knows; a Banglish word missing from the data can still look like a typo, so stay conservative: distance 1 first, high-frequency targets only, and an undo teaches "never fix this word"),
       a `PredictionWorker` request for it, Backspace right after a fix = undo + never fix that word again, a Prefs key + row in Settings > Suggestions (warn claude-g first), ARCHITECTURE.md section.
  L8.3 [x] `predict/Corrector` + `predict/Misspellings` (170 hand-made entries) + `CorrectorTest` (17 tests on the real packs, run locally with a JUnit stand-in; tuned over the whole word lists: weak-band English entries auto-fixed 0.09%, unlisted Banglish 0.8%, 0.005% after a Banglish word).
  L8.4 [x] `PredictionWorker` (Result.autoFix, request(autoCorrect), reject), `MonsoonIme` (pendingFix, applyAutoFix on space, undoAutoFix on Backspace, quoted index), `KeyboardView.setSuggestions(list, quoted)`.
  L8.5 [x] Settings > Suggestions > "Fixing typos" switch (`Prefs.AUTOCORRECT`, default off).
  L8.6 [~] Pushed in 7acebe7, CI #47 green (build, all tests, signed APK). Waiting for the owner test: Settings > Suggestions > Auto-correction ON, English layout, then (1) type `teh` + space -> `the `, strip showed `the` bold and "teh" in quotes before the space; (2) Backspace right after -> `teh` back (no space), type space again -> stays `teh`;
       (3) `dont` + space -> `don't `; (4) `recieve`, `definately`, `freind` fixed; (5) real words (hello, form), names and Banglish words (ami, khub, valo, kemon) stay; (6) a Banglish sentence (ami kal baka) is untouched; (7) tap the quoted word instead of space = keeps it; (8) switch OFF = nothing changes. Not on device yet: all of it.
L7 Trace of features nobody works on [x] (owner request 2026-11-22; read-only: no code was built or changed)
  L7.1 [x] Read CLAUDE.md, STATUS.md, all four notes files, docs/REQUIREMENTS.md, ARCHITECTURE.md, layout/Layouts.kt, the commit list. CI #41 (94c1857) green. GitHub code search is NOT indexed for this repo (a control search found nothing): read files instead.
  L7.2 [x] Compared the Ridmik inventory + roadmap with the board and the code. Result below. The owner is asked which group to start first (STATUS Needs user #15).
  UNOWNED, no task line and no code (as of 2026-11-22):
   a) Edit panel: arrows, Select, Select all, Copy, Paste, Home, End (roadmap v0.6).  b) Number pad page + its toolbar button (v0.6).
   c) Space-bar cursor drag with delay/speed settings (v0.6; clashes with the space-bar language swipe D15, so ask first) and a cursor-move key on ?123 page 2 (only Tab is there).
   d) One-handed mode (v0.5).  e) Split keyboard, also for foldables (v0.5).  f) Themes: only follow phone / light / dark exist, no palettes or theme store (v0.5).
   g) Auto-correction and typo fixes (v0.4: strip suggestions and next-word exist, nothing fixes a typo). Offensive words are only filtered out of the prediction data at pack time, there is no "block offensive words" switch.
   h) Ridmik settings not built: hide long-press hints, key popup dismiss delay, key sound volume (ours follows the system), large number row, emoji key for physical Alt (hardware, next to claude-s's T6).
   i) Toolbar: the strip has globe, language, emoji, clipboard, mic only; Ridmik also has themes, number pad and a "more" button.
   j) Not features: no LICENSE file in the repo (GPL-3.0 decided in D25; it only exists as step E1.13e of the paused E1), launcher icon is a placeholder (STATUS Known notes), v0.7 polish/perf has no task, STATUS Needs user #1 (accent / haptic / sound placeholders) never confirmed.
  CLAIMED BUT IDLE (only the owner reassigns, D22; do not rescue): T6 hardware-keyboard Bangla (claude-s: notes say "not started" 2026-11-17, no [claude-s] commit found); T9.3 Bijoy + Bornona / Munir Optima / Avro Easy (claude-e, paused); E1 password manager (claude-e, paused by the owner).
  COVERED: E3 clipboard rest (claude-e, E3.7-E3.9), G5 voice typing (claude-g, G5.2-G5.6).
  STALE NOTES seen (their owners fix them): claude-g's STATUS G5 line and notes say G5.3a is not pushed, but 94c1857 is on main with CI #41 green (the owner can test hold-to-talk now); ARCHITECTURE.md still says the mic icon does nothing; STATUS "Known notes" still says the language-pill long press does nothing (L4 built it).
L4 প্রভাত (Probhat) + জাতীয় (National) layouts [~] (owner order 2026-11-20, taken over from claude-e's T9.1/T9.2/T9.4; Bijoy stays with claude-e)
  Source facts: `third_party/avro-reference/layouts/{Probhat,National (Jatiya)}.avrolayout` = XML, 4 layers per PC key (Normal, Shift, AltGr, ShiftAltGr), tags `Key_<K>_<Layer>`; ignore `Num*` tags;
  `&apos; &quot; &amp; &lt; &gt;` inside the CDATA are literal text and must be unescaped. National fits the 10/9/7 QWERTY grid (all signs on letters, independent vowels on AltGr);
  Probhat needs extra keys (ে ো on [ ], ্ on /, ৃ on shift-comma, ঁ on shift-period, ঞ ৎ ৳ on number-row shifts, nukta on AltGr+period, ZWNJ/ZWJ). Both are logical-order (no reordering engine).
  L4.1 [x] Owner design answers -> D58 (Probhat 4 rows, extra keys), D59 (National + AltGr by long press), D60 (labels). Needs user #10 answered.
  L4.2 [x] `tools/gen_fixed_layouts.py` -> `lang/avro/AvroLayoutTables.kt` (generated, MPL) + `layout/FixedLayout.kt`; `FixedLayoutsTest` (13 tests, run locally with the Kotlin compiler in /opt/gradle/lib).
  L4.3 [x] `Language.PROBHAT/NATIONAL`, `Layouts.pages(language)`, `Key.shifted/hintShifted/face/hintDrawn*`, KeyboardView per-language rows + shift-aware hints.
  L4.4 [x] `MonsoonIme.switchLanguage`, `KeyboardView.setLanguage(Language)`; typing is the plain commitText path, predictions run in Bangla mode.
  L4.5 [x] Language-key long-press popup (`openLanguagePopup` / `popupTouch` in KeyboardView, `onLanguagePicked` in the IME).
  L4.6 [x] commit 46d05d1 = CI #26 green (build + tests, signed APK); warnings to claude-e/g/s written.
  L4.7 [x] Owner tested 2026-11-20: typing in Probhat and National works. His feedback became L5, L6 and T11 below.

L5 Sign keys without the dotted circle [x] (D62)
  L5.1 [x] `Key.drawable`: a Mn/Mc sign is painted on `Key.NBSP` instead of "◌" (keys, hints, bubble); typed text unchanged; test `combiningSignsAreDrawnOnANoBreakSpaceNotOnADottedCircle`.
  L5.2 [x] Pushed in 65133d1 (CI #29 green); owner checks ূ ী ্ ি ু ে on Probhat and National. If a sign is off-center, nudge in `KeyboardView.drawKey` (the no-break space adds a little width).
L6 Rare signs on the ?123 pages [x] (D63, owner chose Bangla layouts only)
  L6.1 [x] `Key.bnHint` + `Layouts.symbols/symbols2` hints (৲ ॥ ৺ ৴ ৵ ৶ ৷ ৸ ৹ ঽ ৄ ৢ ৣ); `KeyboardView.hintOf` shows them only when `language.isBangla`.
  L6.2 [x] FixedLayoutsTest: every Bangla character of Probhat and National is typable, each rare sign is on exactly one key (15 tests pass locally).
  L6.3 [x] Pushed in 65133d1, CI #29 green; owner-tested OK 2026-11-21.
T11 Bangla digits [x] (D18, D61; taken over from claude-e on the owner's order 2026-11-21)
  T11.1 [x] Owner decided: a setting that swaps typed digit and hint on every digit key; hints only in Bangla layouts.
  T11.2 [x] `Key.digit`, `Key.BN_DIGITS`; `KeyboardView.typedOf/faceOf/hintOf/hintDrawnOf/setBanglaDigits`.
  T11.3 [x] `Prefs.DIGITS` (Int 0 English / 1 Bangla), `Prefs.banglaDigits`, Typing page `choiceRow` + note, strings, `MonsoonIme.applyPrefs` calls `setBanglaDigits`.
  T11.4 [x] Pushed in 65133d1, CI #29 green (build, tests, signed APK); owner-tested OK 2026-11-21.

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
Nothing open. L4, L5, L6, T11 are owner-tested OK (2026-11-21). Do not start new features without the owner's go-ahead. Others' open items: claude-e Bijoy (T9.3), claude-s hardware-key translation (T6, should also decide whether a physical 1-0 follows `Prefs.banglaDigits()`).
L7 is done. L8 (auto-correction) is built, pushed and CI-green (#47): the owner tests it with the L8.6 checklist; fix what he reports. Later ideas, NOT built (ask first): fixing on punctuation/Enter, typo fixes in Bangla layouts, a "never fix this word" list the owner can edit, touch-position (fat-finger) scoring.

## Half-done files / known bugs
None known. Not verified on a device yet: everything in L2.4 (Android code is only compiled by CI). Things to watch in the owner's report: strip arrow touch handling, English tap-to-complete
replacing the right text in odd apps, backspace resync cost, cold start of the first prediction (packs map lazily), APK size (+~11 MB because the packs are stored uncompressed).
Avro choices I made without asking (the owner tested T8a without objecting; revisit only if they complain): '.' right after a Bangla word gives the dari (।); '^' after a word gives
chandrabindu; capitals are never auto-applied in Bangla. ':' follows D24 (smart colon).

- [claude-e -> claude-l] 2026-11-19 E2.5: KeyboardView strip code now has a `stripShown` flag (setStripVisible(false) hides the strip while the emoji search bar sits above the keys; the strip rects, suggestions and touch routing are skipped then) and `Codes.EMOJI` calls `Listener.onEmojiKey()`. MonsoonIme.onCreateInputView returns a LinearLayout around the KeyboardView. Re-pull before editing KeyboardView's strip, onMeasure/computeLayout or MonsoonIme's start/finish input code.
- [claude-g -> claude-l] 2026-11-19 G1.3: three strip switches (Prefs.SHOW_SUGGESTIONS / NEXT_WORD / TYPED_FIRST, cached in MonsoonIme.applyPrefs). Changed: MonsoonIme.requestPrediction (returns when showSuggestions is off; no request when next-word is off and no word is started), onPredictionResult (drops results when off); PredictionWorker.request got a last parameter `typedFirst: Boolean = true` (Request class too) and predict() uses it for the `RESULTS - 1` + typed word logic. `predicting` itself is unchanged, so the tracker and learning keep running. Re-pull before editing these.
- [claude-g -> claude-l] 2026-11-20 G1.5 (layouts, FYI for T9.1/T9.2/T9.4): the optional number row is added in `KeyboardView.rowsOf(PAGE_LETTERS)` as one extra row of `Layouts.numberRow` above `Layouts.pages[PAGE_LETTERS]`, and `computeLayout` gives every page the same total height (fewer rows = taller rows), so a layout page may have any row count. If you replace `Layouts.pages` per language keep `rowsOf` working. ASCII digit hints ("1"-"0") on the letters page are hidden while the number row is on; Bangla digit hints are not.
- [claude-g -> claude-l] 2026-11-20 G1.8: `Layouts.withEmojiKey(rows)` (new, at the end of the number-row block in layout/Layouts.kt) is applied by `KeyboardView.rowsOf`: it inserts a `Codes.EMOJI` key right after the first key of the LAST row (?123 / ABC) and shrinks the space bar by 1 weight. It only acts when the last row starts with a SYMBOLS/LETTERS/SYMBOLS2 key and has a space bar of weight >= 2.5, so keep new bottom rows in that shape (all current pages do). `setAppearance` got a new `emojiKey` parameter.
- [claude-e -> claude-l] 2026-11-21 E3.5: the strip's clipboard icon now opens the clipboard panel (`KeyboardView.Listener.onClipboardKey`, same swap as the emoji panel). MonsoonIme: `emojiSearch` renamed `panelSearch` (search typing is shared by the emoji and clipboard panels; routing in onText/onBackspace/onEnter/onTab/onSuggestion is unchanged). E3.7 (text shortcuts as a strip suggestion, e.g. typing `;sig`) will later add a candidate to the suggestion strip: I will warn you again before touching the strip/suggestion code. Also new: `tools/compile_check.sh` compiles all app Kotlin against a real android.jar in ~30 s (type errors before CI).
