# ARCHITECTURE (map for agents; update in the same commit as any structural change)

Status: skeleton only. Exists: `ime/MonsoonIme.kt` (placeholder panel), `settings/SetupActivity.kt` (enable/choose screen),
`.github/workflows/build.yml`. Everything else below is planned; replace "planned" with real paths as you build.

## Build and CI
- No local Android build is possible in the cloud sandbox (Maven and Gradle hosts are blocked). CI is the compiler.
- Pinned: Gradle 8.10.2 (via setup-gradle, no wrapper), AGP 8.7.3, Kotlin 2.0.21, JDK 17, compileSdk 35, targetSdk 34, minSdk 26.
- Workflow `.github/workflows/build.yml`: builds `assembleRelease` on push to main (md/docs/third_party-only pushes skip it).
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
