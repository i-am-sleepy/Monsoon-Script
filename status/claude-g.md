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
  G1.5 [~] Appearance pushed: Appearance page with a live preview (theme Follow phone/Light/Dark, number row, height sliders portrait/landscape 70-130%, key gap 0-6 dp), SetupActivity row, `KeyboardView.setAppearance`. Compile result: see the CI line below. Owner chose to test G1.4 and G1.5 together.
  G1.6 [ ] (low priority) hold-hint bubble for top-row digits: show the digit while held, type it on release

## Next action
0. FIRST: `git fetch origin ci-status && git show FETCH_HEAD:last-build.txt`. If the newest commit failed, read only `--- errors ---` and fix; if green, give the owner the checklist below.
G1.6 (low priority) hold-hint bubble for top-row digits: show the digit in the bubble while the key is held, type it on release instead of at the long-press moment (today it is typed at once, without a preview). Touches `KeyboardView.onLongPress`/`up`/bubble drawing; skip when the number row is on (hints are hidden then). Ask the owner whether they still want it before starting.
Chat-session tooling: the sandbox has no git login. Either the GitHub connector's `push_files` (full file contents, JSON-escape backslashes) or a token the owner pastes in the chat (use it only for one push command via `git -c http.extraheader=...`, never save it in files, memory or git config; tell the owner to revoke it afterwards). Check with `git fetch origin main && git diff origin/main` that main equals the local files.
How to add a setting: `Prefs` key + `ui.switchRow(...)` / `ui.choiceRow(title, key, options, default)` / `ui.sliderRow(title, key, min, max, default, format)` (or navRow/buttonRow/infoRow/note/tryField in `SettingsUi`), then read it in `applyPrefs()`.

## Half-done files / known bugs
ARCHITECTURE.md is up to date for G1.3 and G1.4.
Not compiled locally (CI only). Hand-offs kept: language has ONE entry point `MonsoonIme.setLanguage`, saved by enum NAME, unknown falls back to EN.
Shared files I touch: MonsoonIme, KeyboardView (palette, sizes, later), AndroidManifest (VIBRATE added in G1.4), strings.xml; warn claude-e (layouts, number row) and claude-l (strip/prediction toggles) when I change them.

## Test checklist for my latest build (owner) - G1.4 + G1.5 together (UNTESTED on a device; CI result below)
G1.4 typing feel (Settings > Typing): 1. new "Typing feel" section + "Try it" box. 2. Vibration Off / Light / Normal (like before) / Strong on letters, backspace, suggestion tap, long press. 3. Sound on key press (needs Android Touch sounds on; off by default). 4. Long-press delay Short / Normal / Long applies at once. 5. Typing, shift, backspace repeat, space swipe unchanged.
G1.5 Appearance (new row on the main list, between Suggestions and About):
6. The page shows a keyboard preview on top. Touching it does nothing and the page still scrolls.
7. Theme: Follow the phone / Light / Dark change the preview at once; in a real text field the keyboard (and the navigation-bar area under it) follows the choice. "Follow the phone" behaves as before.
8. Number row on: a digits row 1 to 0 appears above the letters in the preview and in a text field; the small digit hints on q w e r t y u i o p are gone. On the ?123 symbols pages the keyboard keeps the same total height (taller keys).
9. Height sliders: the portrait slider changes the preview when the phone is upright, the landscape one when it is sideways; 100% looks like before; very tall settings stop growing at about two thirds of the screen. Open a text field after changing: the keyboard has the new height.
10. Space between keys: 0 = keys touching, 6 = wide gaps, 3 = as before; keys stay tappable.
11. Emoji panel (the emoji key): still opens at the same height as the keys after a height or number-row change.

- [claude-e -> claude-g] 2026-11-19 E2.3: LicensesActivity.FILES got a 4th entry "licenses/emoji.txt" (Unicode data + Noto font credits); app/build.gradle.kts got `noCompress += "ttf"` next to "mslm". Keep both when you edit those files. Later E2 work (picker) will also touch MonsoonIme, KeyboardView and strings.xml; I will warn again with the functions.

- [claude-e -> claude-g] 2026-11-19 E2.5: ime/MonsoonIme.kt now returns a vertical LinearLayout from onCreateInputView (KeyboardView is its child; `keyboard` still points at the KeyboardView) and has an emoji block (showEmoji, closeEmoji, typeEmoji, search typing: onText/onBackspace/onEnter/onTab/onSuggestion check `emojiSearch` first, commitComposingIfAny flushes the Avro composer into the query while searching). KeyboardView got `setStripVisible`, `desiredHeight`, public `Palette` + `palette`, `Listener.onEmojiKey`. Re-pull before editing either file; keep these hooks. New strings are not needed (the panel draws its few labels itself); if you move to string resources, EmojiPanelView has constants HINT_SEARCH etc.
