# claude-g notes (only claude-g edits this file; others only append lines under "Warnings")
Updated: 2026-11-19, session 1 (a chat the owner told to "act as claude-g"; commits carry [claude-g]). CHECKPOINT after G1.1+G1.2 (commit 63f95a7); the owner continues in a NEW chat from here.
Focus: settings screen and saved preferences (T10, G1). Owner decisions: D55.

## Warnings from other Claudes (read first; delete a line when handled)
(none) The three claude-l warnings (KeyboardView suggestions, learn switch + clear button, licenses screen) are handled by G1.2.

## Tasks and steps
G1 Settings pages [~]  (T10 = G1.1 + G1.2, L2.5 = G1.2)
  G1.1 [x] `prefs/Prefs` (file `monsoon_prefs`, keys, typed getters); language + last Bangla layout saved by enum NAME; IME restores them in onCreate and calls
           `KeyboardView.setLanguageOptions` through `MonsoonIme.applyPrefs()` on every pref change (listener)
  G1.2 [x] SetupActivity = main list (status, 2 setup steps, rows Typing / Suggestions / About and licenses); `SettingsPageActivity` (extra `page`) holds the three pages;
           `LicensesActivity` shows assets/licenses/{app,avro,prediction-data}.txt. Pushed; CI run #16 (63f95a7) was still building at checkpoint time, so compile is UNCONFIRMED (code written without a local compiler). Then the owner's device test (checklist below).
  G1.3 [ ] Suggestion toggles: show suggestions, next-word, typed word first
  G1.4 [ ] Typing feel: vibration Off/Light/Normal/Strong (declare VIBRATE, D55), key sound on/off, long-press delay Short/Normal/Long; "try it" text field on the page
  G1.5 [ ] Appearance: theme (system/light/dark), number row switch, height sliders portrait + landscape, key-gap slider (limits so keys never overlap or get too small), live preview (a real KeyboardView); then add the "Appearance" row to SetupActivity
  G1.6 [ ] (low priority) hold-hint bubble for top-row digits: show the digit while held, type it on release

## Next action
0. FIRST: `git fetch origin ci-status && git show FETCH_HEAD:last-build.txt`. If run #16 / commit 63f95a7 failed, fix it before anything else (read only `--- errors ---`; most likely a Kotlin typo in `settings/*.kt` or `prefs/Prefs.kt`), push, then continue. If green, tell the owner and wait for their checklist result.
G1.3 (after step 0):
1. Add keys to `prefs/Prefs.kt`: `SHOW_SUGGESTIONS` ("suggest_show", default true), `NEXT_WORD` ("suggest_next_word", true), `TYPED_FIRST` ("suggest_typed_first", true) with getters.
2. Three `switchRow`s at the top of the Suggestions page in `settings/SettingsPageActivity.suggestions()` (new header "Strip"), above "Personal words".
3. Wire in `ime/MonsoonIme.kt`: `predicting` (line ~85, `get() = predictionAllowed`) gets `&& showSuggestions` (cache the three values in `applyPrefs()`); when next-word is off, skip the empty-prefix request in
   `resyncAndPredict` (line ~301-308); typed-word-first lives in `ime/PredictionWorker.kt` (~line 172-175, `RESULTS - 1`): pass a flag in `Request` or filter in MonsoonIme.
   These are claude-l's files: append a Warning to `status/claude-l.md` (what changed, which functions) in the same push. Avro (Bangla typing) dictionary suggestions (SuggestionWorker) are NOT part of "show suggestions" unless the owner says so; ask if unsure.
4. Update STATUS.md G1 line, this file, ARCHITECTURE.md; push; one-shot CI check; give the owner a checklist.
How to add a setting: `Prefs` key + `ui.switchRow(title, summary, key, default)` (or navRow/buttonRow/infoRow/note in `settings/SettingsUi.kt`), then read it in `applyPrefs()`. Sliders/choice rows (G1.4, G1.5) need new helpers in SettingsUi.

## Half-done files / known bugs
None. Not compiled locally (CI only). Appearance row is deliberately absent until G1.5. Hand-offs kept: language has ONE entry point `MonsoonIme.setLanguage`, saved by enum NAME, unknown falls back to EN.
Shared files I touch: MonsoonIme, KeyboardView (palette, sizes, later), AndroidManifest (VIBRATE in G1.4), strings.xml; warn claude-e (layouts, number row) and claude-l (strip/prediction toggles) when I change them.

## Test checklist for my latest build (owner) - commit 63f95a7 (G1.1 + G1.2)
1. Open the Monsoon Script app: title, setup status, "1. Enable keyboard", "2. Choose keyboard", then rows Typing, Suggestions, About and licenses. Light and dark phone theme both readable.
2. Typing: turn "Language key" off, open the keyboard (the EN / বাং key in the icon row disappears); on again (it returns). Same for "Swipe the space bar": off = swiping does nothing, on = switches language.
3. Switch to Bangla, close the keyboard app completely and open it again: it comes back in Bangla (same for English).
4. Suggestions: "Learn from my typing" off, type a few new words, they must not show up as suggestions later; on again, learning resumes.
5. "Clear learned words": a confirm dialog appears (cannot be undone); Cancel keeps words, Clear shows "Learned words cleared" and your own words stop being suggested.
6. About and licenses: version shown; "Licenses and credits" lists the app GPL-3.0 note, the Avro MPL-2.0 note and the word data credits; text scrolls and can be selected.
7. System Back returns from a sub-page to the main list; the arrow in the page title does the same.
- [claude-e -> claude-g] 2026-11-19 E2.3: LicensesActivity.FILES got a 4th entry "licenses/emoji.txt" (Unicode data + Noto font credits); app/build.gradle.kts got `noCompress += "ttf"` next to "mslm". Keep both when you edit those files. Later E2 work (picker) will also touch MonsoonIme, KeyboardView and strings.xml; I will warn again with the functions.
