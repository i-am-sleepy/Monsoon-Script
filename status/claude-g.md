# claude-g notes (only claude-g edits this file; others only append lines under "Warnings")
Updated: 2026-11-21, session 3 (chat told to "act as claude-g"; commits carry [claude-g]). Focus: settings and saved preferences. G1 and G2-G4 done and owner-tested; nothing open.

## Warnings from other Claudes (read first; delete a line when handled)
(none)

## Tasks and steps
G1 Settings pages [x] Done and owner-tested (2026-11-21: "everything looks fine"). Details: ARCHITECTURE.md (Prefs, settings/, Appearance, Typing feel, strip toggles).
G2 Volume buttons move the cursor [x] Typing > "Volume buttons move the cursor" (Off / up = left, down = right / up = right, down = left; default Off). `MonsoonIme.onKeyDown/onKeyUp` handle only the two volume keys, only when the
   setting is on, the keyboard is shown over a real editor and no emoji search is typing. Owner: "volume keys okay" (2026-11-21).
G3 Open the keyboard automatically [x] Typing > "Open the keyboard automatically" (default on). Off = `onShowInputRequested` refuses implicit shows, a tap still opens it. Owner turned it off and reported no problem (2026-11-21).
   Known limit: an app that asks for the keyboard explicitly by itself looks like a tap and still opens it; reopen this task if the owner names such an app.
G4 Strip flicker [x] Icon row no longer flashes for a few frames after a language switch or after a space following a Bangla word (old candidates stay painted until the new answer, max 400 ms, not tappable meanwhile). Owner: "so far not seeing any flashes" (2026-11-21), so reopen if one shows up.
   Cause was `dropSuggestions()` clearing the strip, then the async prediction answer arriving a few frames later. Hold logic: `dropSuggestions(keepShown)`, `stripHeld`, `heldClear`, `HELD_MS`. CI #36 (ee09d9b) green.

## Next action
None. Wait for the owner's next request or bug report. No new features without the owner's go-ahead.
Chat-session tooling: the sandbox has no git login. Either the GitHub connector's `push_files` (full file contents, JSON-escape backslashes; read the files back with `git fetch origin main && git diff origin/main` afterwards to catch typos) or a token the owner pastes in the chat (use it only for one push command via `git -c http.extraheader=...`, never save it in files, memory or git config; tell the owner to revoke it afterwards). The token pasted on 2026-11-21 answered 403 (no write access), so G2-G4 went out with `push_files`.
How to add a setting: `Prefs` key + `ui.switchRow(...)` / `ui.choiceRow(title, key, options, default)` / `ui.sliderRow(title, key, min, max, default, format)` (or navRow/buttonRow/infoRow/note/tryField in `SettingsUi`), then read it in `applyPrefs()`.

## Half-done files / known bugs
ARCHITECTURE.md is up to date for G2-G4. Shared files I touched: MonsoonIme (onShowInputRequested, onKeyDown/onKeyUp, dropSuggestions/setSuggestions/onSuggestion/switchLanguage), Prefs, SettingsPageActivity, and a NEW `res/values/strings_behavior.xml` (the 9 new strings; strings.xml itself was not touched, so nobody has to merge it). claude-s was warned (onKeyDown now exists for the volume keys).
ARCHITECTURE.md typo from my re-push: two lines (the Avro dictionary line "precomposed ... (U+09DF)" and the WM data line "precomposed ... (same as Avro)") now hold the decomposed form (letter + nukta U+09BC) instead of the precomposed characters U+09DF, U+09DC, U+09DD. Restore them the next time that file is edited.
If a flicker shows up again: it is a drop that is not followed by a request (hard drop, shows the icon row at once on purpose), or an answer that takes longer than 400 ms (first Bangla prediction loads the pack).

## Test checklist for my latest build (owner)
(none pending)
