# ARCHITECTURE (map for agents; update in the same commit as any structural change)

Status: v0.1 done, v0.2 in progress (Avro typing works on device; suggestion engine done, strip UI not wired yet). Exists:
- `ime/MonsoonIme.kt`: commits text, backspace/enter/tab as key events, enter honors the editor's IME action, globe = next IME,
  auto-caps (asked only after space . ! ? and at start, because getCursorCapsMode blocks on the app), no fullscreen extract UI.
- `view/KeyboardView.kt`: one Canvas view (strip icons + 4 rows), multi-touch (4 pointers), commit on release, finger sliding
  switches the pressed key, shift/caps lock, backspace repeat, digit long-press hints, key bubble, light/dark palette, no allocations in touch/draw.
- `layout/Keys.kt` + `layout/Layouts.kt`: key model and the letters / symbols / symbols2 pages as data.
- `settings/SetupActivity.kt` (enable/choose screen), `.github/workflows/build.yml`.
- `lang/avro/` (MPL-2.0 port, pure Kotlin, no Android classes, NOT thread-safe, one instance on the main thread):
  `AvroPhoneticConverter` (`convert(word)` -> Bangla, with Avro autocorrect + `joNukta` flag), `AvroAutoCorrect` (loads
  `assets/avro/autodict.dct`, a copy of the reference file), `AvroRules` (GENERATED rule tables for k g n c t d p b m l s h) and
  `AvroBanglaChars` (GENERATED constants). Hand-written parts: vowels, o/O, r/rri, j, dot, w/y/x, CutText, CorrectCase.
  The generated files come from `third_party/avro-reference/engine/*.pas`; if the tables ever need a change, fix the generator
  idea (parse `if Cnv('pat', expr) = True then`) or edit the Kotlin by hand and drop the GENERATED note.
- `lang/Language.kt` (EN, AVRO; labels for the strip pill and space bar) and `lang/PhoneticComposer.kt` (the raw Latin word being typed,
  `text()` = live Bangla, `accepts(ch)`, `externalMove(...)` = decide from onUpdateSelection whether the user moved the cursor). Pure Kotlin.
- `MonsoonIme`: in Avro it shows the word with `setComposingText`; space, digits, punctuation, Enter, Tab, globe and language switch call
  `finishComposingText` first, so editor text always equals what is shown. Backspace edits the Latin buffer while composing.
  `onUpdateSelection` clears the buffer if the cursor leaves the word (ignores echoes within 250 ms of our own edit).
  Strip order: globe, language pill (`Codes.LANG`, text pill, 1.25 cells wide), emoji, clipboard ... mic. `KeyboardView.setLanguage` pushes labels in.
- Suggestion engine (T8b, `lang/avro/`, MPL-2.0, pure Kotlin, no Android classes, NOT thread-safe: use one instance per worker thread):
  `AvroSuggester(words: AvroWordSource, suffixes)`.`suggest(english, plainBangla, limit=12)`: `AvroRegexBuilder.convert(english)` makes a regex of
  every Bangla spelling the typed word could have; `AvroBucketMap.forLetter` picks the dictionary buckets for the first letter; each bucket's
  words are full-matched against the regex; then suffix forms (stem + suffix, with Avro's vowel/khandatta/anushar joining rules); results are
  sorted by Levenshtein distance to the plain conversion. `AvroWordSource.load(bucket)` is the only I/O: the IME implements it on top of
  `assets/avro/dict/<bucket>.txt` (`ime/AssetWordSource`, LRU of 12 buckets: one letter needs up to 7). Typed words longer than `MAX_ENGLISH_LENGTH` (50) give no suggestions.
  `AvroRegexRules`/`AvroRegexConstants` are GENERATED (`tools/gen_avro_regex.py`); `assets/avro/dict/*` and `suffixes.tsv` come from
  `tools/gen_avro_dict_assets.py` (source: `third_party/avro-reference/data/`). Dictionary words use precomposed য় (U+09DF); the converter does too.
  JVM timing with the real dictionary: 4-50 ms per word (cold bucket loads included); on a phone run it on a background thread only.
- `ime/SuggestionWorker`: one HandlerThread ("monsoon-suggest") owning the `AvroSuggester` (created lazily on its first request, so keyboard open
  stays fast). `request(raw, plain)` keeps only the newest pending request (AtomicReference) and answers on the main thread; `MonsoonIme.onSuggestionResult`
  drops answers whose raw word is no longer the one being composed. `MonsoonIme.suggestions` = [word as shown, plain conversion if different, dictionary words].
- `tools/`: one-off generators (python3 -I tools/<name>.py from the repo root). Generated Kotlin files name their generator in the header.
- Tests: `app/src/test/kotlin/.../lang/avro/AvroPhoneticConverterTest.kt` + `app/src/test/resources/avro/golden.tsv`
  (1,200 golden cases from an independent Python translation of the Pascal; 40k random inputs matched the port exactly);
  `AvroSuggesterTest.kt` (uses the real assets, `src/main/assets/avro`, relative to the `app/` directory); `PhoneticComposerTest.kt`.
Strip icons for emoji, clipboard and mic are drawn but do nothing yet. Everything else below is planned; replace "planned" with real paths as you build.

## Build and CI
- No local Android build is possible in the cloud sandbox (Maven and Gradle hosts are blocked). CI is the compiler.
- Pinned: Gradle 8.10.2 (via setup-gradle, no wrapper), AGP 8.7.3, Kotlin 2.0.21, JDK 17, compileSdk 35, targetSdk 34, minSdk 26.
- Workflow `.github/workflows/build.yml`: runs `testDebugUnitTest` (JUnit 4, pure JVM tests), then `assembleRelease`, on push to main
  (md/docs/third_party-only pushes skip it). Failing tests stop the APK from being published; ci-status shows `tests:` and the failures.
- Local trick for pure-Kotlin engines: Gradle's own lib folder has a Kotlin 2.0.21 compiler, JUnit and hamcrest, so engine code can be
  compiled and tested in the sandbox without Maven (`java -cp /opt/gradle/lib/kotlin-compiler-embeddable-2.0.21.jar:... K2JVMCompiler`,
  see the classpath list in the jar names under `/opt/gradle/lib`). Android code still can only be compiled by CI.
- Result and log tail: `git fetch origin ci-status && git show origin/ci-status:last-build.txt` (first run takes ~4-8 min).
- APK (rolling pre-release): https://github.com/i-am-sleepy/Monsoon-Script/releases/download/latest/MonsoonScript.apk
- Signing: release key from GitHub Secrets (KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD); without them it
  falls back to a temporary debug key. versionCode = GitHub run number.

## Layout (planned)
```
app/src/main/kotlin/com/monsoonscript/keyboard/
  ime/        MonsoonIme (InputMethodService), InputConnection helpers, hardware key handling, IME switching
  view/       KeyboardView (Canvas), key model, touch/gesture handling, popups, themes
  layout/     Layout definitions as Kotlin data (QWERTY, symbols, numpad, edit panel). No XML inflation.
  lang/       LanguageEngine interface; english/, avro/ (MPL-2.0 port), provat/, bijoy/
  emoji/      bundled compact emoji data, lazy loaded; recents, search
  clipboard/  clipboard history store and its panel
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
- In `onKeyDown/onKeyUp`, handle only events that must be translated (Bangla modes mapping a printable key). For everything
  else return `false` / `super` so the app gets them: Enter, Shift, Ctrl/Alt/Meta combos, arrows, Tab, Esc, function keys.
- Never rewrite `KeyEvent` meta state or send synthesized Enter. Respect `EditorInfo.imeOptions` only for the soft Enter key.
- With a physical keyboard attached, the soft view is hidden by default (toggle in settings); Avro and Bijoy still work from it.
- Gboard is the reference behavior: no interference first, then extras (shortcuts, suggestions) once pass-through is solid.

## Performance rules
- No allocation in touch handling or `onDraw`; pre-build paints, paths and text layouts; invalidate only dirty key rects.
- Load emoji, dictionary and layouts lazily and off the main thread; keep resident memory small (target phone: 2 GB RAM).
- Startup of the IME must be near instant; no network, no reflection, no background services beyond the IME itself.
