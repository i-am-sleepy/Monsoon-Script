# ARCHITECTURE (map for agents; update in the same commit as any structural change)

Status: v0.1 done, v0.2 in progress (Avro typing works on device; suggestion strip built, awaiting the owner's device test). Exists:
- `ime/MonsoonIme.kt`: commits text, backspace/enter/tab as key events, enter honors the editor's IME action, globe = next IME,
  auto-caps (asked only after space . ! ? and at start, because getCursorCapsMode blocks on the app), no fullscreen extract UI.
- `view/ClickSound.kt`: the built-in key click (G1.7), owned by KeyboardView.
- `view/KeyboardView.kt`: one Canvas view (strip icons + 4 rows), multi-touch (4 pointers), commit on release, finger sliding
  switches the pressed key, shift/caps lock, backspace repeat, digit long-press hints (after the long-press delay the key bubble shows the hint and the hint is typed when the finger is lifted, G1.6; `ptrHint`), key bubble, light/dark palette, no allocations in touch/draw.
- `layout/Keys.kt` + `layout/Layouts.kt`: key model and the letters / symbols / symbols2 pages as data, plus `Layouts.numberRow` (the optional digits row, G1.5).
  `Layouts.pages(language)` returns the page set of a language (same array every time; English and Avro share `Layouts.pages`, Probhat and National have their own
  letters page, the symbol pages are shared). `Key` also carries `shifted` (what Shift shows/types, any script), `hintShifted` (long press with Shift on) and the painted
  stand-ins `face` / `hintDrawn*` (a dotted circle before a vowel sign, "ZWNJ" / "ZWJ" by name). `KeyboardView` only paints and types those.
- Fixed Bangla layouts (L4, D58-D60): `layout/FixedLayout.kt` = one layout as a PC key table (`text(pcKey, layer)`, layers Normal / Shift / AltGr / Shift+AltGr, Avro's key
  names "A".."Z", "COMMA", "OEM4" ...). The tables of Probhat and National are GENERATED (MPL-2.0) into `lang/avro/AvroLayoutTables.kt` by `tools/gen_fixed_layouts.py` from
  `third_party/avro-reference/layouts`; `FixedLayoutsTest` fails when they drift from the XML or when a Bangla character becomes unreachable on the phone. The phone
  arrangement (which PC keys sit where, hints) is `Layouts.probhatLetters` / `nationalLetters` built with `fk(...)`: a long press types the AltGr layer (Shift on: Shift+AltGr),
  the top row keeps the digit hints. A hardware keyboard (T6) can map its key codes to the same PC key names and call `FixedLayout.text`. Bijoy (T9.3) adds its own table.
  Key faces (L5, D62): `Key.drawable` paints a vowel sign on a no-break space (`Key.NBSP`), never on a dotted circle. Bangla digits (T11, D61): `Key.digit` marks a plain digit key, `Key.BN_DIGITS` are
  the constants; `KeyboardView.typedOf` / `faceOf` / `hintOf` swap digit and hint when `Prefs.DIGITS` = Bangla and a Bangla layout is active (English never). `Key.bnHint` (L6, D63) is a hint that exists
  only in the Bangla layouts: the rare signs on the ?123 pages (`Layouts.symbols` / `symbols2`).
- `prefs/Prefs.kt`: the ONE SharedPreferences file `monsoon_prefs` (key constants + typed getters: language, last Bangla layout, language key, space swipe, learn, strip toggles `suggest_show` / `suggest_next_word` / `suggest_typed_first`, Bangla digits `typing_digits` (Int 0 English / 1 Bangla), keyboard behavior `typing_auto_show` (default on) / `typing_volume_cursor` (Int 0 Off / 1 up = left / 2 up = right), typing feel `typing_vibration` (Int 0-3) / `typing_key_sound` / `typing_long_press` (Int 0-2), appearance `appearance_theme` (Int 0-2) / `appearance_number_row` / `appearance_emoji_key` (default on) / `appearance_height_portrait` + `appearance_height_landscape` (percent 70-130) / `appearance_key_gap` (dp 0-6)).
  `ime/LearnSettings` uses the same file. The IME listens (`MonsoonIme.applyPrefs`) so settings take effect live.
- `settings/` (plain framework Views, no libraries, light/dark from the theme): `SetupActivity` = main list (setup status + steps, rows for the pages),
  `SettingsPageActivity` = Typing / Suggestions / Appearance / About pages chosen by the `page` extra (Appearance holds a live, touch-inert KeyboardView preview that follows the prefs), `LicensesActivity` (assets/licenses/*.txt), `SettingsUi` = row builders
  (page, header, switchRow, choiceRow (Int pref, dialog list), navRow, buttonRow, infoRow, note, tryField). A new setting = a Prefs key + a row + a read in `applyPrefs()`.
- `.github/workflows/build.yml`.
- `lang/avro/` (MPL-2.0 port, pure Kotlin, no Android classes, NOT thread-safe, one instance on the main thread):
  `AvroPhoneticConverter` (`convert(word)` -> Bangla, with Avro autocorrect + `joNukta` flag), `AvroAutoCorrect` (loads
  `assets/avro/autodict.dct`, a copy of the reference file), `AvroRules` (GENERATED rule tables for k g n c t d p b m l s h) and
  `AvroBanglaChars` (GENERATED constants). Hand-written parts: vowels, o/O, r/rri, j, dot, w/y/x, CutText, CorrectCase.
  The generated files come from `third_party/avro-reference/engine/*.pas`; if the tables ever need a change, fix the generator
  idea (parse `if Cnv('pat', expr) = True then`) or edit the Kotlin by hand and drop the GENERATED note.
- `lang/Language.kt` (ENGLISH, AVRO, PROBHAT, NATIONAL; labels for the strip pill and space bar, order = order of the language popup; Probhat/National type plain `commitText` like English, no composing, predictions run in Bangla mode) and `lang/PhoneticComposer.kt` (the raw Latin word being typed,
  `text()` = live Bangla, `accepts(ch)`, `externalMove(...)` = decide from onUpdateSelection whether the user moved the cursor). Pure Kotlin.
  Smart colon (D24): ':' joins a word after a letter; `text()` shows a trailing ':' as a plain colon and the engine's visarga once a letter follows.
- `MonsoonIme`: in Avro it shows the word with `setComposingText`; space, digits, punctuation, Enter, Tab, globe and language switch call
  `finishComposingText` first, so editor text always equals what is shown. Backspace edits the Latin buffer while composing.
  `onUpdateSelection` clears the buffer if the cursor leaves the word (ignores echoes within 250 ms of our own edit).
  Strip order: globe, language pill (`Codes.LANG`, text pill, 1.25 cells wide), emoji, clipboard ... mic. `KeyboardView.setLanguage(Language)` pushes the language in (labels + the letters page; a new
  layout resets Shift). Long press on the pill opens the language popup (D19, `openLanguagePopup` / `popupTouch`, drawn over the keys, takes every touch until closed, lifting over a row
  calls `Listener.onLanguagePicked` -> `MonsoonIme.switchLanguage`; it lists `Language.values()`, so a new layout shows up by adding it to the enum).
- Suggestion engine (T8b, `lang/avro/`, MPL-2.0, pure Kotlin, no Android classes, NOT thread-safe: use one instance per worker thread):
  `AvroSuggester(words: AvroWordSource, suffixes)`.`suggest(english, plainBangla, limit=12)`: `AvroRegexBuilder.convert(english)` makes a regex of
  every Bangla spelling the typed word could have; `AvroBucketMap.forLetter` picks the dictionary buckets for the first letter; each bucket's
  words are full-matched against the regex; then suffix forms (stem + suffix, with Avro's vowel/khandatta/anushar joining rules); results are
  sorted by Levenshtein distance to the plain conversion. `AvroWordSource.load(bucket)` is the only I/O: the IME implements it on top of
  `assets/avro/dict/<bucket>.txt` (`ime/AssetWordSource`, LRU of 12 buckets: one letter needs up to 7). Typed words longer than `MAX_ENGLISH_LENGTH` (50) give no suggestions.
  `AvroRegexRules`/`AvroRegexConstants` are GENERATED (`tools/gen_avro_regex.py`); `assets/avro/dict/*` and `suffixes.tsv` come from
  `tools/gen_avro_dict_assets.py` (source: `third_party/avro-reference/data/`). Dictionary words use precomposed য় (U+09DF); the converter does too.
  JVM timing with the real dictionary: 4-50 ms per word (cold bucket loads included); on a phone run it on a background thread only.
- `ime/SuggestionWorker`: one HandlerThread ("monsoon-suggest") owning the `AvroSuggester` (created lazily on its first request, so keyboard open
  stays fast). `request(raw, plain)` keeps only the newest pending request (AtomicReference) and answers on the main thread; `MonsoonIme.onSuggestionResult`
  drops answers whose raw word is no longer the one being composed. `MonsoonIme.suggestions` = [word as shown, plain conversion if different, dictionary words].
- Suggestion strip (D23): `KeyboardView.setSuggestions(list)` shows the first 3 as equal columns across the whole strip (first one bold) and HIDES every
  `Kind.STRIP` key (globe, language pill, emoji, clipboard, mic, any future strip icon) until the list is empty again. Touches in the strip while it shows
  suggestions go to the suggestion under the finger (`ptrInSug`/`ptrSug`, never to a hidden icon); release on it calls `Listener.onSuggestion(word)`
  (default no-op), `MonsoonIme.onSuggestion` commits `word + " "` in one `commitText`. Space/Enter/punctuation still commit the underlined word.
- Prediction (L2, `predict/`, pure Kotlin, no Android classes, NOT thread-safe: one prediction worker owns the packs and the stats). Data: WM Keyboard Data
  packed by `tools/pack_wm_data.py` into `assets/predict/{en,bn,rom}.mslm` (rom = Banglish words, English mode merges en + rom, D41); inputs, upstream commit and
  hashes in `third_party/wmkeyboard-data/SOURCES.md`, credits/licenses in `third_party/wmkeyboard-data/NOTICE.md` (+ in-app text `assets/licenses/prediction-data.txt`).
  Only the v1 pair lists are used (never `*_v2/_v3`); offensive words are removed at pack time. Format "MSLM" (layout in the packer's docstring): header, sorted word
  list (UTF-8), 1-byte log counts (`q = round(10*log2 c)`), 7-byte bigram and 10-byte trigram records sorted by word ids; read with binary search straight from a
  memory-mapped file (`LangPack(ByteBuffer)`), so only touched pages are loaded. Bangla words are stored with precomposed য় ড় ঢ় (same as Avro).
  - `TextNorm`: `key()` = lookup form (lowercase Latin, ’ -> ', nukta precomposed), `isWordChar`, `isSentenceEnd`, `matchCase` (restores capitals for the shown word).
  - `Predictor(user).predict(packs, prefix, w1, w2, limit)`: candidates = prefix range (top 48 by count) + words seen after the context + the owner's own words; score =
    stupid backoff (trigram share, else 0.4 x bigram share, else 0.16 x unigram), summed over packs x pack prior, then mixed with `UserStats` (weights grow with
    how much the owner typed). `Predictor.englishMix(en, rom, w1, w2)` picks the priors (Banglish words down-weighted unless a previous word is Banglish).
  - `Bridge` (D54): `assets/predict/rom2bn.bin` links every Banglish word to the Bangla word the plain Avro conversion makes of it (0 = no match, about half of everyday Banglish
    usage matches); `Predictor.englishMix(..., bridge)` attaches it to the Banglish pack only when a previous word is Banglish, so context words are looked up in the Bangla pairs
    and successors are shown as their most frequent Banglish spelling (`WeightedPack.bridge`, `Ctx.pairs/b1/b2`). The file is built by `BridgeBuilder` (test sources, run from `app/`
    after the packs change; `BridgeTest` fails when the committed file is stale). Other D54 rules: 1-3 letter prefixes give Banglish words prior 0.4 instead of 0.05 when no Banglish
    word came before; after a space the word just typed is never suggested again (unless the owner learned that pair). `PredictionWorker` loads the bridge lazily and, while a word is
    typed, puts the word as typed first in the strip (tapping it just adds the space).
  - `UserStats`: learned words and pairs with a 30-day half-life, prune at 30k words / 60k pairs, `takeChanges()` / `restore()` for the SQLite store (Android side).
  - `WordTracker`: follows the word in progress and the two before it from committed characters; sentence end forgets context; `reset(textBeforeCursor)` resyncs.
  Tests: `app/src/test/kotlin/.../predict/*Test.kt` (53; `Packs.kt` maps the real files from `src/main/assets/predict`, run from `app/`).
- Prediction in the IME (L2.4, English and Bangla): `ime/PackLoader` memory-maps `assets/predict/<name>.mslm` straight from the APK (`androidResources.noCompress += "mslm"` in
  app/build.gradle.kts; compressed assets would be read into the heap instead); `ime/PredictionWorker` (HandlerThread "monsoon-predict", coalescing like SuggestionWorker, owns the
  packs and the `Predictor`, loads packs lazily, answers on the main thread with the request's sequence number and ready-capitalized words via `TextNorm.matchCase`).
  `MonsoonIme` keeps a `WordTracker` fed by every committed character (onText, Backspace, Enter, Tab, a tapped suggestion); `resyncTracker()` rebuilds it from
  `getTextBeforeCursor(80)` at start of input (posted after the first frame), when Backspace crosses into an earlier word or deletes a selection, on a language switch and from
  `onUpdateSelection` when the update is older than `PhoneticComposer.STALE_MS` after our last edit (cursor moved, app changed the text, physical keyboard). `requestPrediction()`
  asks for completions of the word in progress, or the next word after a space; nothing is asked at the start of a field or sentence (the icon row stays). `predSeq` drops stale answers
  (`dropSuggestions()` bumps it). Tapping a prediction (`acceptPrediction`) checks that the text before the cursor equals the tracked word, then in one batch edit deletes it and commits
  `word + " "`. Only fields that allow composing and do not set `TYPE_TEXT_FLAG_NO_SUGGESTIONS` get predictions (`predictionAllowed`). In Bangla (Avro) the tracker is fed what is committed (the composed word joins it in
  `commitComposingIfAny` / `onSuggestion`, the dari and plain characters go through `onText`); while a word is composed the strip shows the Avro dictionary words (`requestPrediction`
  is skipped), right after it the next-word predictions come from `bn.mslm` (`PredictionWorker.Mode.BANGLA`, same worker, same learned stats). Avro suggestions that are equally
  close to the plain conversion are ordered by word frequency: `AvroSuggester(words, suffixes, rank)`, `rank` = `bn.mslm` count level (looked up for words within 2 edits of the best;
  `SuggestionWorker` maps its own `LangPack` because packs are single-thread objects).
- Learning (L2.4b, D42): `WordTracker.onWord(prev, word)` fires whenever a word ends (not while `reset` replays text); `MonsoonIme.learnWord` forwards it to
  `PredictionWorker.learn` when `learnAllowed` (field allows predictions, no `IME_FLAG_NO_PERSONALIZED_LEARNING`, and `LearnSettings.enabled`, default ON). The worker keeps a
  `UserStats` (faded counts, 30-day half-life, pruned at 30k words / 60k pairs; a word no dictionary knows needs 2 uses before it is suggested), loads it from `ime/UserStore`
  (SQLite `learned.db`, table `facts(w1, w2, n, last)`, word rows have w2 = "") on first use, and saves the changes 8 s after the last learn, at `flush()` (keyboard closes) and at `stop()`.
  Emoji key (G1.8): `KeyboardView.rowsOf` runs the rows through `Layouts.withEmojiKey`, which puts a `Codes.EMOJI` key (kind SPECIAL, drawn as a smiley by `drawSmiley`, handled by the existing `onEmojiKey`) right of the first key of the bottom row (?123 / ABC) and takes its width from the space bar; any layout whose last row starts with ?123 / ABC and has a wide space bar gets it, so new layouts need nothing.
  Appearance (G1.5, D55), pushed by `MonsoonIme.applyPrefs` into `KeyboardView.setAppearance(theme, numberRow, emojiKey, heightPortrait, heightLandscape, keyGapDp)` (no-op when nothing changed; also calls `applySystemBars` and re-colors the emoji panel): theme 0 follow the phone / 1 light / 2 dark replaces the uiMode check in `refreshTheme`; row height = standard (52 dp portrait, 42 dp landscape) x percent, clamped so the keyboard stays under 65% of the screen; `rowSlots()` is 4, or 5 with the number row, and `computeLayout` gives every page the same total height (a page with fewer rows gets taller rows); `rowsOf(p)` puts `Layouts.numberRow` above the letters page; `hintOf(k)` hides ASCII-digit hints on the letters page while the number row is on; key gaps are `gapX = gap dp`, `gapY = gap + 1 dp` (clamped to a fifth of a key).
  Typing feel (G1.4, D55), pushed by `MonsoonIme.applyPrefs` into `KeyboardView.setTypingFeel(vibration, keySound, longPressMs)`: vibration 0 Off / 1 Light (25 ms) / 3 Strong (80 ms) use cached `VibrationEffect` one-shots on the `Vibrator` at the device's default strength (needs the VIBRATE permission, declared), 2 Normal = `performHapticFeedback` as before; key sound = Android's click (`AudioManager.playSoundEffect`, plays only while the system Touch sounds switch is on; the Typing page has a button to Android's sound settings) or, with `typing_sound_source` = 1, `view/ClickSound` (three clicks synthesized in code, written as tiny WAVs to the cache folder, played through a SoundPool at the system sound volume, silent in silent/vibrate mode; created only while selected and attached); the long-press delay replaces the old fixed 300 ms. The Typing settings page ends with a "Try it" text field.
  Keyboard behavior (G2, G3), cached in `MonsoonIme.applyPrefs` (`autoShow` is also set in `onCreate`, the view does not exist yet when the first show is asked): `onShowInputRequested` returns false for a request without
  `InputMethod.SHOW_EXPLICIT` (the implicit show Android makes when a field or window gets focus) while "Open the keyboard automatically" is off; a tap passes the explicit flag, and `configChange` (rotation) is let through.
  `onKeyDown` / `onKeyUp` look ONLY at KEYCODE_VOLUME_UP/DOWN, and only when `typing_volume_cursor` is on, the input view is shown over a real editor and no emoji search is typing (`volumeKeyIsOurs`): the press moves the
  cursor with `sendDownUpKeyEvents(DPAD_LEFT/RIGHT)` after `commitComposingIfAny()` (repeats while held) and is consumed; everything else goes to `super` (claude-s: T6 adds its Bangla key path next to this, keep the early return).
  Strip hold (G4): `dropSuggestions(keepShown = true)` (used right before a request follows: space/punctuation after an Avro word, a tapped Avro suggestion, `switchLanguage`; only when `answerFollows`) empties the logical
  `suggestions` but leaves the old candidates painted (`stripHeld`) until `setSuggestions` gets the answer or `HELD_MS` (400 ms) passes (`heldClear`); `onSuggestion` ignores taps while `suggestions` is empty, and
  `requestPrediction` / `resyncAndPredict` end the hold at once when no answer will come. Other drops stay immediate.
  Strip toggles (G1.3, D57), cached in `MonsoonIme.applyPrefs`: `showSuggestions` off stops `requestPrediction` and drops late answers (the tracker and learning keep running; Avro dictionary choices are never affected), `nextWord` off = no request when no word is started, `typedFirst` is the last parameter of `PredictionWorker.request` (off = 3 predictions, no typed word first).
  Settings contract for claude-g: `ime/LearnSettings` holds the pref names (`PREFS`, `KEY_ENABLED`, `KEY_CLEAR_EPOCH`); the switch writes `learn_enabled`
  (`LearnSettings.setEnabled`), the button calls `LearnSettings.clearLearnedWords(context)` (empties the database and bumps an epoch that the running service hears via a
  SharedPreferences listener to empty its memory). Change `LearnSettings.PREFS` if the settings code uses another prefs file.
- Strip arrow (D41): while the strip has candidates, a chevron button sits at its left (`KeyboardView`, `arrowW`); tapping it swaps the candidates for the normal icon row (`iconsPinned`,
  chevron mirrored) until the candidates change; with no candidates the strip is the plain icon row without arrow. The left-hand strip icons are shifted right by the arrow width in place
  (`placeStrip`, `stripLeftCount`), so hit-testing needs no special case; touches on the arrow are tracked per pointer (`ptrArrow`, `ARROW_SLOT`).
- `tools/`: one-off generators (python3 -I tools/<name>.py from the repo root). Generated Kotlin files name their generator in the header.
- Tests: `app/src/test/kotlin/.../lang/avro/AvroPhoneticConverterTest.kt` + `app/src/test/resources/avro/golden.tsv`
  (1,200 golden cases from an independent Python translation of the Pascal; 40k random inputs matched the port exactly);
  `AvroSuggesterTest.kt` (uses the real assets, `src/main/assets/avro`, relative to the `app/` directory); `PhoneticComposerTest.kt`.
The strip's mic icon is drawn but does nothing yet. Everything else below is planned; replace "planned" with real paths as you build.

### Emoji (E2, D56)
- Data: `assets/emoji/emoji.tsv` (generated by `tools/gen_emoji_data.py` from `third_party/emoji-data/`: Unicode 17 emoji-test + CLDR en/bn names; 1,914 base emoji, 2,030 skin-tone variants,
  one line per base: `tab, emoji, en, bn, variants`) and `assets/emoji/NotoColorEmoji.ttf` (stored uncompressed, drawn only inside the picker). Credits: `assets/licenses/emoji.txt`.
- `emoji/` (pure Kotlin except the loader; unit tests in src/test/.../emoji): `EmojiData` (parse, search in English and Bangla, `baseOf`, `withoutUnsupported`), `EmojiStore` (recents, remembered skin tone per
  emoji; over a `TextStore`), `EmojiGrid` (section/row/column geometry and hit test), `EmojiLoader` (background thread: parse, Typeface from the asset, hide what the system font cannot draw via
  `Paint.hasGlyph`, cached per `Build.FINGERPRINT` in prefs file `monsoon_emoji`) and `PrefsTextStore`.
- `view/EmojiPanelView`: one Canvas view (no RecyclerView, nothing allocated per frame): search pill with a keyboard button at its right end (same as ABC: back to the typing keys), one scrolling grid (Recent + 8 tabs), bottom bar (ABC, tab icons, backspace), long-press skin-tone popup
  (two steps for couples). Emoji are typed as ONE complete string with `commitText` (MonsoonIme.typeEmoji); never images.
- `MonsoonIme`: `onCreateInputView` returns a vertical LinearLayout (emoji panel on top, KeyboardView below, one visible at a time). `onEmojiKey` -> `showEmoji` (keys GONE, panel at `KeyboardView.desiredHeight()`).
  Search typing (`panelSearch`, shared with the clipboard panel; the panel on screen is `shownPanel`, a `SearchablePanel`): panel shrinks to results row + query bar, `KeyboardView.setStripVisible(false)`, keys come back below; `onText`/`onBackspace`/`onEnter`/`onTab` are routed to the query
  (`searchBuf` + the Avro composer for the word in progress), never to the editor. `commitComposingIfAny` flushes the composer into the query while searching. Enter shows the results in the grid.
  Recents are not recorded in password fields or with IME_FLAG_NO_PERSONALIZED_LEARNING. Hardware keys are untouched (a physical keyboard types into the app, not into the search bar).

### Clipboard (E3, D65; spec: docs/CLIPBOARD_SPEC.md)
- `clipboard/` is split so the logic is pure Kotlin with JUnit tests (src/test/.../clipboard, `FakeClipDb`) and Android code stays thin. Pure: `ClipModels` (`ClipItem`, `ClipFolder`, `ClipShortcut`), `ClipDb` (storage
  interface), `ClipStore` (in-memory list newest first, written through `ClipDb`: limit and auto-delete age apply to "loose" clips only, pinned exempt while `keepPinned`, foldered always exempt; duplicate
  text moves to the top and keeps its pin/folder; folders; text shortcuts), `ClipFormat` ("2 min ago", preview text, export text), `CardGrid` (equal-size card geometry, 2 or 1 columns, hit test),
  `ClipPanelModel` (chips All/Pinned/Text/Images/Shortcuts + one per folder, search, selection, 3-dot menu, folder picker, bulk actions; talks to the keyboard through `ClipPanelModel.Host`).
  `ClipListModel` (the app's History / Pinned / folder list: rows under Pinned / Today / Yesterday / Earlier headers, search, multi-selection, bulk pin / move / delete / share text).
  Android: `SqliteClipDb` (`clipboard.db`, one writer thread, errors logged and swallowed), `ClipPrefs` (SharedPreferences `monsoon_clipboard`), `ClipHub` (process singleton: prefs + store; the IME, the panel and
  the app's screens share it), `ClipCapture` (OnPrimaryClipChangedListener; `copyWithoutRecording` puts a text back on the clipboard without it being recorded again), `ClipEditActivity` (plain-Views editor for one clip).
- App screens (E3.6, D67), package `clipboard/app/`, plain Views, framework `ListView` (no RecyclerView): the main screen's "Clipboard" row opens `ClipboardActivity` (top bar with back + gear, four bottom tabs). Each tab is a `Screen`:
  `ClipListScreen` (History / Pinned / one folder: search field, rows, long press = multi-select with a bottom action bar; it only shows `ClipListModel`), `FolderScreen` (folders with counts, "New folder", three dots = rename / delete,
  tap opens the folder's `ClipListScreen`), `ShortcutScreen` (the user's `;trigger` shortcuts, editor dialog). Only the visible screen listens to the store (`show()` / `hide()` from onStart / onStop). `ClipActions` holds the shared dialogs
  (clip menu, folder picker, delete confirm, name prompt). `ClipboardSettingsActivity` = one page with sections Management / Behavior / Appearance / Privacy; it uses `SettingsUi(this, prefs.sp)` (SettingsUi takes the SharedPreferences
  file to write to; default is `Prefs`). The keyboard panel's gear opens it too.
- Capture rules (D65): only while Monsoon is the active keyboard (Android lets nothing else read the clipboard in the background). Never recorded: history off, clip flagged sensitive
  (`EXTRA_IS_SENSITIVE`, which KeePassDX sets, or our own `ClipCapture.EXTRA_NO_HISTORY`, which the vault and "Copy again" must set), anything copied while a password / hidden / no-personalized-learning
  field has the keyboard (`MonsoonIme.clipCapture.fieldAllowed`). No guessing from content.
- `view/ClipboardPanelView`: one Canvas view like `EmojiPanelView` (toolbar, chip row, scrolling cards, bottom bar, popup for menu and folder picker); it only draws `ClipPanelModel` and forwards touches. One cached
  `StaticLayout` per card. Not Compose, no RecyclerView.
- Text shortcuts in the strip (E3.7, D68): `TriggerRun` (pure) holds the characters typed since the last space and is fed next to `WordTracker` (onText, backspace, enter/tab, accepted suggestions, emoji, paste; rebuilt in `resyncTracker`). `refreshShortcuts()` runs at the
  start of every `requestPrediction()`: `ClipStore.matchShortcuts` against the run -> `shortcutHits`; `setSuggestions` puts their one-line texts (`ClipFormat.stripLabel`) IN FRONT of what the word engines gave (`engineWords`), so a slow prediction answer
  cannot drop them. Not active in Avro mode or where the strip is off. A tap goes to `acceptShortcut` first: it replaces the typed trigger with the full text (checked against the editor), no space. The first entries of `suggestions` are shortcuts while `shortcutHits` is
  not empty: auto-correction must never use them.
- `MonsoonIme`: the strip's clipboard icon -> `onClipboardKey` -> `showClipboard` (same swap as the emoji panel). `clipHost` (a `ClipPanelModel.Host`) does paste (`commitText`, no learning), copy again
  (`setPrimaryClip` with `EXTRA_NO_HISTORY`), share (chooser), edit (`ClipEditActivity`) and settings. Panel search reuses the emoji mechanism (typed text goes to the query, not the editor).

## Build and CI
- No local Android build is possible in the cloud sandbox (Maven and Gradle hosts are blocked). CI is the compiler.
- Pinned: Gradle 8.14.2 (via setup-gradle, no wrapper), AGP 8.13.2, Kotlin 2.0.21, JDK 17, compileSdk 35 (app; the KeePassDX engine modules use 36), targetSdk 34, minSdk 26.
- Workflow `.github/workflows/build.yml`: runs `:app:testDebugUnitTest` (JUnit 4, pure JVM tests), then `:app:assembleRelease`, on push to main
  (md/docs/third_party-only pushes skip it). Failing tests stop the APK from being published; ci-status shows `tests:` and the failures.
- Local trick for pure-Kotlin engines: Gradle's own lib folder has a Kotlin 2.0.21 compiler, JUnit and hamcrest, so engine code can be
  compiled and tested in the sandbox without Maven (`java -cp /opt/gradle/lib/kotlin-compiler-embeddable-2.0.21.jar:... K2JVMCompiler`,
  see the classpath list in the jar names under `/opt/gradle/lib`). Android code: run `tools/compile_check.sh` before every push (compiles all of `app/src/main/kotlin` against a real android.jar
  from github.com/Sable/android-platforms, ~30 s; catches type errors and wrong API signatures; does not check resources, the manifest, R8 or lint, so CI stays the final judge).
- Result and log tail: `git fetch origin ci-status && git show origin/ci-status:last-build.txt` (first run takes ~4-8 min).
- APK (rolling pre-release): https://github.com/i-am-sleepy/Monsoon-Script/releases/download/latest/MonsoonScript.apk
- Signing: release key from GitHub Secrets (KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD); without them it
  falls back to a temporary debug key. versionCode = GitHub run number.

### Password manager engine (E1, D26/D44/D45/D46/D47/D64; plan and results: docs/PASSWORD_MANAGER.md, docs/PASSWORD_MANAGER_PHASE0.md)
- `third_party/keepassdx`: KeePassDX as a git SUBMODULE (shallow), pinned to release tag 4.5.8, NEVER edited. A fresh clone needs `git submodule update --init --depth 1`; without it everything else still builds
  (`settings.gradle.kts` includes `:vault`, `:crypto`, `:database` only when `third_party/keepassdx/gradle/libs.versions.toml` exists). Upstream's version catalog is imported as `libs` (Kotlin overridden to ours).
- `vault/` (`com.monsoonscript.vault`): our adapter. `VaultEngine.kt` is the ONLY file that talks to the engine (D26); it will grow into the thin vault interface and run in the `:vault` process (D44). `consumer-rules.pro` holds
  the shrinker rules the engine needs (each fixed a real failure). Tests: 14 emulator tests in `vault/src/androidTest` (all ciphers and key derivations, content, RFC 6238, merge, vaults made by pykeepass and real KeePassXC,
  fixtures as base64 text in `assets/fixtures/`). Not a dependency of `:app` yet, so the APK is unchanged until Phase 1.
- `.github/workflows/engine.yml`: only when `vault/**`, the submodule pointer, `.gitmodules`, the root Gradle files or the workflow itself change: engine unit tests, then the emulator tests (API 34 x86_64, KVM). Result on main:
  branch `ci-status-engine`, file `last-build.txt`. Branch runs are read through the Actions page/API.
- Updating KeePassDX = moving the submodule pointer to a newer RELEASE tag (never a random commit), on a test branch; `docs/UPSTREAM_COPIES.md` lists any KeePassDX code we copied (D46). A weekly watcher workflow (D48) is not built yet.

## Layout (planned)
```
app/src/main/kotlin/com/monsoonscript/keyboard/
  ime/        MonsoonIme (InputMethodService), InputConnection helpers, hardware key handling, IME switching
  view/       KeyboardView (Canvas), key model, touch/gesture handling, popups, themes
  layout/     Layout definitions as Kotlin data (QWERTY, symbols, numpad, edit panel). No XML inflation.
  lang/       LanguageEngine interface; english/, avro/ (MPL-2.0 port), provat/, bijoy/
  emoji/      emoji data model, loader, recents and skin tones (built, see "Emoji (E2)" above); the picker view is view/EmojiPanelView
  clipboard/  clipboard history store, panel model and capture (built, see "Clipboard (E3)" below); the panel view is view/ClipboardPanelView
  prefs/      settings (SharedPreferences)
  settings/   Settings + setup activity (plain Views)
third_party/avro-reference/   read-only reference (MPL-2.0 Pascal sources, layout XML, docs). Not compiled.
docs/REQUIREMENTS.md          target features and what to avoid
```

## Engine interface (planned)
`LanguageEngine` takes key input and the current composing text and returns edits for the InputConnection (commit /
set composing / delete). English is pass-through. Avro phonetic composes Latin keystrokes into Bangla. Provat and Bijoy are
fixed layouts that map physical/virtual keys to characters. Engines are switchable from the keyboard itself (no settings trip).

## Hardware keyboard rules (non-negotiable)
- In `onKeyDown/onKeyUp`, handle only events that must be translated (Bangla modes mapping a printable key) and, as an owner opt-in (G2), the volume keys while the keyboard is up. For everything
  else return `false` / `super` so the app gets them: Enter, Shift, Ctrl/Alt/Meta combos, arrows, Tab, Esc, function keys.
- Never rewrite `KeyEvent` meta state or send synthesized Enter. Respect `EditorInfo.imeOptions` only for the soft Enter key.
- With a physical keyboard attached, the soft view is hidden by default (toggle in settings); Avro and Bijoy still work from it.
- Gboard is the reference behavior: no interference first, then extras (shortcuts, suggestions) once pass-through is solid.

## Performance rules
- No allocation in touch handling or `onDraw`; pre-build paints, paths and text layouts; invalidate only dirty key rects.
- Load emoji, dictionary and layouts lazily and off the main thread; keep resident memory small (target phone: 2 GB RAM).
- Startup of the IME must be near instant; no network, no reflection, no background services beyond the IME itself.
