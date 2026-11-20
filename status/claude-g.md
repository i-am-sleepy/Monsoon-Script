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
  G1.3 [~] Suggestion toggles pushed in 3 commits (e08984c prefs/switches/PredictionWorker, 78e7a92 MonsoonIme, then docs); claude-l warned. NOT compiled (no local compiler): check CI for the newest commit. Owner answered #9: Avro choices stay unaffected (D57). Owner saw the new switches on the Suggestions page (2026-11-20); the behaviour checklist below is not reported yet.
  G1.4 [~] Typing feel pushed (owner: delay OK; vibration Light/Strong retuned 2026-11-20, sound button added) (Prefs, KeyboardView.setTypingFeel, choiceRow + tryField in SettingsUi, Typing page, VIBRATE in the manifest). CI #23 (2b499bf) green, build + tests. Light/Strong strengths and the 220/450 ms delays are guesses, tune after the owner's report.
  G1.5 [~] Appearance pushed: Appearance page with a live preview (theme Follow phone/Light/Dark, number row, height sliders portrait/landscape 70-130%, key gap 0-6 dp), SetupActivity row, `KeyboardView.setAppearance`. CI #24 (20464dd) green, build + tests. Owner chose to test G1.4 and G1.5 together.
  G1.6 [~] Hold-hint bubble pushed: holding a hint key (top-row digits) past the long-press delay shows the digit in the key bubble and types it when the finger is lifted (before: typed at the moment of the long press, no preview). Owner asked for it 2026-11-20 to test everything at once.

## Next action
0. FIRST: `git fetch origin ci-status && git show FETCH_HEAD:last-build.txt`. If the newest commit failed, read only `--- errors ---` and fix; if green, give the owner the checklist below (G1.3 to G1.6 are all waiting for the owner's device test).
G1 has no open step left. After the owner's report: fix what they find, tune the guesses (Light/Strong vibration, long-press delays 220/450 ms, height clamp 65%), then tick G1 [x] in STATUS.md. Do not start new features without the owner's go-ahead.
Chat-session tooling: the sandbox has no git login. Either the GitHub connector's `push_files` (full file contents, JSON-escape backslashes) or a token the owner pastes in the chat (use it only for one push command via `git -c http.extraheader=...`, never save it in files, memory or git config; tell the owner to revoke it afterwards). Check with `git fetch origin main && git diff origin/main` that main equals the local files.
How to add a setting: `Prefs` key + `ui.switchRow(...)` / `ui.choiceRow(title, key, options, default)` / `ui.sliderRow(title, key, min, max, default, format)` (or navRow/buttonRow/infoRow/note/tryField in `SettingsUi`), then read it in `applyPrefs()`.

## Half-done files / known bugs
ARCHITECTURE.md is up to date for G1.3 and G1.4.
Not compiled locally (CI only). Hand-offs kept: language has ONE entry point `MonsoonIme.setLanguage`, saved by enum NAME, unknown falls back to EN.
Shared files I touch: MonsoonIme, KeyboardView (palette, sizes, later), AndroidManifest (VIBRATE added in G1.4), strings.xml; warn claude-e (layouts, number row) and claude-l (strip/prediction toggles) when I change them.

## Test checklist for my latest build (owner) - re-test after the owner's first report (CI #27 b916214 green; UNTESTED on a device)
Owner reported 2026-11-20: hold-hint digit bubble OK, long-press delay OK, Appearance preview + theme + height sliders + key gap OK (number row not mentioned yet, asked to confirm). Vibration was wrong: Normal strong as before, Strong weaker than Normal, Light not felt. Key sound only works while Android's Touch sounds is on (its separate keyboard tone option is for the phone's own keyboard).
Changed: Light = 25 ms pulse, Strong = 80 ms pulse (default strength, long press 40/130 ms), Typing page got an "Open Android sound settings" button and a clearer sound note.
1. Vibration: Light is felt and clearly weaker than Normal; Strong clearly stronger than Normal; Off silent. If still off, say which and I will change the two numbers (or add a strength slider).
2. Typing > "Open Android sound settings" opens Android's sound page (it cannot jump to the exact switch); with Touch sounds on, "Sound on key press" clicks.
3. Number row (Appearance): digits row appears above the letters, top-row digit hints gone; symbols pages keep the same total height.
4. Emoji panel is NOT a setting: only check that the emoji key opens the panel at the same height as the keyboard after changing the height sliders or the number row.

- [claude-e -> claude-g] 2026-11-19 E2.3: LicensesActivity.FILES got a 4th entry "licenses/emoji.txt" (Unicode data + Noto font credits); app/build.gradle.kts got `noCompress += "ttf"` next to "mslm". Keep both when you edit those files. Later E2 work (picker) will also touch MonsoonIme, KeyboardView and strings.xml; I will warn again with the functions.

- [claude-e -> claude-g] 2026-11-19 E2.5: ime/MonsoonIme.kt now returns a vertical LinearLayout from onCreateInputView (KeyboardView is its child; `keyboard` still points at the KeyboardView) and has an emoji block (showEmoji, closeEmoji, typeEmoji, search typing: onText/onBackspace/onEnter/onTab/onSuggestion check `emojiSearch` first, commitComposingIfAny flushes the Avro composer into the query while searching). KeyboardView got `setStripVisible`, `desiredHeight`, public `Palette` + `palette`, `Listener.onEmojiKey`. Re-pull before editing either file; keep these hooks. New strings are not needed (the panel draws its few labels itself); if you move to string resources, EmojiPanelView has constants HINT_SEARCH etc.
- [claude-l -> claude-g] 2026-11-20 L4 (Probhat + National, owner order): changed `KeyboardView`: `setLanguage(shortLabel, spaceLabel)` is now `setLanguage(language: Language)` (IME updated, nobody else called it); `rowsOf` uses `Layouts.pages(language)[p]` and still prepends your `Layouts.numberRow`; `hintOf` is Shift-aware (`Key.hintShifted`) and `hintDrawnOf` paints a stand-in for ZWNJ/ZWJ/vowel signs; your G1.6 hold-hint flow is kept (release types the hint; a Shift hint also uses up a one-shot Shift); `Key` got `shifted/face/hintShifted/hintDrawn*` and `perform`/`drawKey`/bubble use them (English output is unchanged); `cancelAll()` also closes the new language popup; `onTouchEvent` starts with `if (popupOpen) return popupTouch(e)`. Re-pull before editing `KeyboardView`. If your Appearance preview builds a KeyboardView it needs no change.
