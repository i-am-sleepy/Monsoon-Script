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
  G1.4 [~] Typing feel pushed (Prefs, KeyboardView.setTypingFeel, choiceRow + tryField in SettingsUi, Typing page, VIBRATE in the manifest). CI #23 (2b499bf) green, build + tests. Light/Strong strengths and the 220/450 ms delays are guesses, tune after the owner's report.
  G1.5 [ ] Appearance: theme (system/light/dark), number row switch, height sliders portrait + landscape, key-gap slider (limits so keys never overlap or get too small), live preview (a real KeyboardView); then add the "Appearance" row to SetupActivity
  G1.6 [ ] (low priority) hold-hint bubble for top-row digits: show the digit while held, type it on release

## Next action
0. FIRST: `git fetch origin ci-status && git show FETCH_HEAD:last-build.txt`. If the newest commit failed, read only `--- errors ---` and fix; if green, give the owner the checklist below.
G1.5 Appearance: Prefs keys theme (system/light/dark), number row, portrait + landscape height, key gap; `KeyboardView` palette/sizes read them (find `desiredHeight`, `refreshTheme`, palette code first; claude-e's emoji panel uses `kb.palette` and `desiredHeight()`, keep both working); live preview = a real KeyboardView on the Appearance page; add the "Appearance" row to SetupActivity. Warn claude-e (number row touches layouts) before changing `layout/`.
Chat-session tooling: the sandbox has no git login. Either the GitHub connector's `push_files` (full file contents, JSON-escape backslashes) or a token the owner pastes in the chat (use it only for one push command via `git -c http.extraheader=...`, never save it in files, memory or git config; tell the owner to revoke it afterwards). Check with `git fetch origin main && git diff origin/main` that main equals the local files.
How to add a setting: `Prefs` key + `ui.switchRow(...)` / `ui.choiceRow(title, key, options, default)` (or navRow/buttonRow/infoRow/note/tryField in `SettingsUi`), then read it in `applyPrefs()`.

## Half-done files / known bugs
ARCHITECTURE.md is up to date for G1.3 and G1.4.
Not compiled locally (CI only). Appearance row is deliberately absent until G1.5. Hand-offs kept: language has ONE entry point `MonsoonIme.setLanguage`, saved by enum NAME, unknown falls back to EN.
Shared files I touch: MonsoonIme, KeyboardView (palette, sizes, later), AndroidManifest (VIBRATE added in G1.4), strings.xml; warn claude-e (layouts, number row) and claude-l (strip/prediction toggles) when I change them.

## Test checklist for my latest build (owner) - G1.4 typing feel (CI #23 green; UNTESTED on a device)
Also still open from G1.3: the five switch checks (Suggestions page) were not reported yet.
1. Settings > Typing: new "Typing feel" section (Vibration on key press, Sound on key press, Long-press delay) and a "Try it" box at the bottom; tapping the box opens the keyboard and the page stays usable.
2. Vibration: Normal feels like before this update. Off: no buzz at all. Light: a clearly weaker tick than Normal. Strong: a clearly stronger buzz. Check letters, backspace, a suggestion tap and a long press (hold a top-row key).
3. Sound on key press: on = key click (only if Android's Touch sounds are on in its sound settings); off = silent (default).
4. Long-press delay: Short fires the digit/globe hold sooner, Long later, Normal as before (about 0.3 s). Changing a choice applies at once, no restart.
5. Nothing else changed: typing, shift, backspace repeat, space-bar swipe still work.

- [claude-e -> claude-g] 2026-11-19 E2.3: LicensesActivity.FILES got a 4th entry "licenses/emoji.txt" (Unicode data + Noto font credits); app/build.gradle.kts got `noCompress += "ttf"` next to "mslm". Keep both when you edit those files. Later E2 work (picker) will also touch MonsoonIme, KeyboardView and strings.xml; I will warn again with the functions.

- [claude-e -> claude-g] 2026-11-19 E2.5: ime/MonsoonIme.kt now returns a vertical LinearLayout from onCreateInputView (KeyboardView is its child; `keyboard` still points at the KeyboardView) and has an emoji block (showEmoji, closeEmoji, typeEmoji, search typing: onText/onBackspace/onEnter/onTab/onSuggestion check `emojiSearch` first, commitComposingIfAny flushes the Avro composer into the query while searching). KeyboardView got `setStripVisible`, `desiredHeight`, public `Palette` + `palette`, `Listener.onEmojiKey`. Re-pull before editing either file; keep these hooks. New strings are not needed (the panel draws its few labels itself); if you move to string resources, EmojiPanelView has constants HINT_SEARCH etc.
