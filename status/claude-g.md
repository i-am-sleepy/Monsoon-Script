# claude-g notes (only claude-g edits this file; others only append lines under "Warnings")
Updated: 2026-11-21, session 3 (chat told to "act as claude-g"; commits carry [claude-g]). Focus: settings and saved preferences (G1 done, owner-tested), now G2-G4.

## Warnings from other Claudes (read first; delete a line when handled)
(none)

## Tasks and steps
G1 Settings pages [x] Done and owner-tested (2026-11-21: "everything looks fine"). Details: ARCHITECTURE.md (Prefs, settings/, Appearance, Typing feel, strip toggles).
G2 Volume buttons move the cursor [~] Typing > "Volume buttons move the cursor" (Off / up = left, down = right / up = right, down = left; default Off). `MonsoonIme.onKeyDown/onKeyUp` handle only the two volume keys, only when the
   setting is on, the keyboard is shown over a real editor and no emoji search is typing. UNTESTED: whether the phone really hands the volume key to the IME first (it does on most phones; if the volume panel still shows, tell me the model).
G3 Open the keyboard automatically [~] Typing > "Open the keyboard automatically" (default on). Off = `onShowInputRequested` refuses implicit shows, a tap still opens it. UNTESTED. Limit: an app that asks for the keyboard explicitly by itself looks like a tap and still opens it.
G4 Strip flicker [~] Icon row no longer flashes for a few frames after a language switch or after a space following a Bangla word (old candidates stay painted until the new answer, max 400 ms, not tappable meanwhile). UNTESTED.
   Cause was `dropSuggestions()` clearing the strip, then the async prediction answer arriving a few frames later. Hold logic: `dropSuggestions(keepShown)`, `stripHeld`, `heldClear`, `HELD_MS`.

## Next action
Read the ci-status branch for the G2-G4 commits (4e3ff3a, ee09d9b); fix compile errors first (NOT compiled locally, CI is the compiler). Then fix whatever the owner reports from the checklist below. No new features without the owner's go-ahead.
Chat-session tooling: the sandbox has no git login. Either the GitHub connector's `push_files` (full file contents, JSON-escape backslashes; read the files back with `git fetch origin main && git diff origin/main` afterwards to catch typos) or a token the owner pastes in the chat (use it only for one push command via `git -c http.extraheader=...`, never save it in files, memory or git config; tell the owner to revoke it afterwards). The token pasted on 2026-11-21 answered 403 (no write access), so G2-G4 went out with `push_files`.
How to add a setting: `Prefs` key + `ui.switchRow(...)` / `ui.choiceRow(title, key, options, default)` / `ui.sliderRow(title, key, min, max, default, format)` (or navRow/buttonRow/infoRow/note/tryField in `SettingsUi`), then read it in `applyPrefs()`.

## Half-done files / known bugs
ARCHITECTURE.md is up to date for G2-G4. Shared files I touched: MonsoonIme (onShowInputRequested, onKeyDown/onKeyUp, dropSuggestions/setSuggestions/onSuggestion/switchLanguage), Prefs, SettingsPageActivity, and a NEW `res/values/strings_behavior.xml` (the 9 new strings; strings.xml itself was not touched, so nobody has to merge it). claude-s was warned (onKeyDown now exists for the volume keys).
If the flicker is still visible in some flow: it is a drop that is not followed by a request (hard drop, shows the icon row at once on purpose), or an answer that takes longer than 400 ms (first Bangla prediction loads the pack).

## Test checklist for my latest build (owner)
1. Settings > Typing > "Volume buttons move the cursor": pick "Up = left, down = right". In a text field with the keyboard open, volume up moves the cursor left, down moves it right, holding repeats; the volume panel does not appear and the volume does not change. Also try it in the middle of an Avro word (the word is finished, then the cursor moves). Close the keyboard: the buttons change the volume again. Try the other direction option and Off.
2. Settings > Typing > "Open the keyboard automatically" OFF: open a screen or page that focuses a text field by itself (a search page): the keyboard stays down; tap the field and it opens. Back hides it; rotating with the keyboard open keeps it open. ON again: the old behaviour. Tell me which app still pops it up.
3. Flicker: in English then Avro, type a few words, switch language with the pill or the space swipe while suggestions show: the icon row must not flash. In Avro, type a word and press space: the next-word suggestions replace the word suggestions without the icon row flashing. Tell me which flow still flashes.
