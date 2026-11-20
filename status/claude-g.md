# claude-g notes (only claude-g edits this file; others only append lines under "Warnings")
Updated: 2026-11-19, session 2 (chat told to "act as claude-g"; commits carry [claude-g]; pushed with the GitHub connector). Owner tested 63f95a7 (G1.1+G1.2): all checklist items OK. CI #19 (38ff5ef, includes 63f95a7) green.
Focus: settings screen and saved preferences (T10, G1). Owner decisions: D55.

## Warnings from other Claudes (read first; delete a line when handled)
(none) The three claude-l warnings (KeyboardView suggestions, learn switch + clear button, licenses screen) are handled by G1.2.

## Tasks and steps
G1 Settings pages [~]  (T10 = G1.1 + G1.2, L2.5 = G1.2)
  G1.1 [x] `prefs/Prefs` (file `monsoon_prefs`, keys, typed getters); language + last Bangla layout saved by enum NAME; IME restores them in onCreate and calls
           `KeyboardView.setLanguageOptions` through `MonsoonIme.applyPrefs()` on every pref change (listener)
  G1.2 [x] SetupActivity = main list (status, 2 setup steps, rows Typing / Suggestions / About and licenses); `SettingsPageActivity` (extra `page`) holds the three pages;
           `LicensesActivity` shows assets/licenses/{app,avro,prediction-data}.txt. Owner-tested OK (2026-11-19).
  G1.3 [~] Suggestion toggles pushed in 3 commits (e08984c prefs/switches/PredictionWorker, 78e7a92 MonsoonIme, then docs); claude-l warned. NOT compiled (no local compiler): check CI for the newest commit. Avro dictionary choices are NOT hidden by the switch (Needs user #9).
  G1.4 [ ] Typing feel: vibration Off/Light/Normal/Strong (declare VIBRATE, D55), key sound on/off, long-press delay Short/Normal/Long; "try it" text field on the page
  G1.5 [ ] Appearance: theme (system/light/dark), number row switch, height sliders portrait + landscape, key-gap slider (limits so keys never overlap or get too small), live preview (a real KeyboardView); then add the "Appearance" row to SetupActivity
  G1.6 [ ] (low priority) hold-hint bubble for top-row digits: show the digit while held, type it on release

## Next action
0. FIRST: `git fetch origin ci-status && git show FETCH_HEAD:last-build.txt`. If the newest commit failed, read only `--- errors ---` and fix (likely a Kotlin typo in MonsoonIme/PredictionWorker/Prefs); if green, give the owner the checklist below and continue.
G1.4 Typing feel: Prefs keys vibration (Off/Light/Normal/Strong), key sound, long-press delay; declare VIBRATE in AndroidManifest (D55); choice-row helper in `settings/SettingsUi.kt`; "try it" text field on the Typing page; read the values in `applyPrefs()`/KeyboardView (find the current haptic and long-press code first). Warn claude-e if KeyboardView key handling changes.
Chat-session tooling: no git credentials in the sandbox. Clone anonymously, edit, then push with the GitHub connector's `push_files` (full file contents; JSON-escape backslashes), and check with `git fetch origin main && git diff origin/main` that main equals the local files (also catches typos).
How to add a setting: `Prefs` key + `ui.switchRow(title, summary, key, default)` (or navRow/buttonRow/infoRow/note in `SettingsUi`), then read it in `applyPrefs()`.

## Half-done files / known bugs
ARCHITECTURE.md owes a G1.3 update (do it with the next push of that file): in the `prefs/Prefs.kt` line add the strip toggle keys `suggest_show` / `suggest_next_word` / `suggest_typed_first`; in the Learning bullet add one sentence (showSuggestions / nextWord / typedFirst are cached in `MonsoonIme.applyPrefs`; the tracker and learning keep running; Avro choices are unaffected; `typedFirst` is the last parameter of `PredictionWorker.request`).
Not compiled locally (CI only). Appearance row is deliberately absent until G1.5. Hand-offs kept: language has ONE entry point `MonsoonIme.setLanguage`, saved by enum NAME, unknown falls back to EN.
Shared files I touch: MonsoonIme, KeyboardView (palette, sizes, later), AndroidManifest (VIBRATE in G1.4), strings.xml; warn claude-e (layouts, number row) and claude-l (strip/prediction toggles) when I change them.

## Test checklist for my latest build (owner) - G1.3 suggestion toggles (UNTESTED, compile unconfirmed until CI says so)
1. Settings > Suggestions: new section "Suggestion strip" with three switches (all ON by default) above "Personal words".
2. "Show word suggestions" off: typing English or Banglish shows no completions or next words in the strip (icon row stays). Bangla Avro typing still shows its own dictionary choices. On again: suggestions return.
3. "Next-word suggestions" off: after a space the strip shows nothing; while a word is being typed completions still show.
4. "Show my typed word first" off: while typing the first choice is a completion, not your typed word (one more suggestion shown). On: your typed word is first and bold.
5. "Learn from my typing" still works with the strip off (type new words, turn the strip back on, they appear later).

- [claude-e -> claude-g] 2026-11-19 E2.3: LicensesActivity.FILES got a 4th entry "licenses/emoji.txt" (Unicode data + Noto font credits); app/build.gradle.kts got `noCompress += "ttf"` next to "mslm". Keep both when you edit those files. Later E2 work (picker) will also touch MonsoonIme, KeyboardView and strings.xml; I will warn again with the functions.

- [claude-e -> claude-g] 2026-11-19 E2.5: ime/MonsoonIme.kt now returns a vertical LinearLayout from onCreateInputView (KeyboardView is its child; `keyboard` still points at the KeyboardView) and has an emoji block (showEmoji, closeEmoji, typeEmoji, search typing: onText/onBackspace/onEnter/onTab/onSuggestion check `emojiSearch` first, commitComposingIfAny flushes the Avro composer into the query while searching). KeyboardView got `setStripVisible`, `desiredHeight`, public `Palette` + `palette`, `Listener.onEmojiKey`. Re-pull before editing either file; keep these hooks. New strings are not needed (the panel draws its few labels itself); if you move to string resources, EmojiPanelView has constants HINT_SEARCH etc.
