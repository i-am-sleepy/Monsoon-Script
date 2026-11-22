# claude-e notes (only claude-e edits this file; others only append lines under "Warnings")
Updated: 2026-11-21, session 3. Focus: E3 CLIPBOARD (owner spec: docs/CLIPBOARD_SPEC.md). E1 is CHECKPOINTED (foundation merged to main), E2 emoji is done for now.
T9 and T11 were taken over by claude-l (L4-L6); only T9.3 Bijoy Classic (+ the owner key-map check, STATUS Needs user #2) is still mine, paused.

## Warnings from other Claudes (read first; delete a line when handled)
(none; the older ones are condensed under "Shared code facts" below)

## Tasks and steps
E3 Clipboard [~] Owner rule for this task: ask him every implementation decision first and give my suggestions first; then build. Answered (D65): 4 tabs History/Pinned/Folders/Shortcuts + gear; cards in 2-column (default) AND 1-column with a toggle; shortcuts = strip suggestion + panel list, no auto-expand; never record password fields / sensitive-flag clips / vault copies, no guessing.
  E3.1 [x] Read spec + repo. Facts: strip icon `Codes.CLIPBOARD` is drawn but does nothing (`KeyboardView` `else -> Unit` near the strip tap handler); ARCHITECTURE.md plans a `clipboard/` package;
           D43 = a copied secret is never saved in the history and is cleared after 30 s; Android only lets the active keyboard read the clipboard, so history records only while Monsoon is the active keyboard.
  E3.2 [x] Questions answered, recorded as D65 (SQLite from the framework, defaults, build order below stand: the owner did not object).
  E3.3 [x] Store: pure Kotlin `clipboard/ClipModels|ClipDb|ClipStore` (limit on loose clips, dedupe, auto-delete, pin, folders, search, shortcuts) + `SqliteClipDb` (Android, background writes); 21 JUnit tests pass locally (kc.sh trick); pushed, CI to check.
  E3.4 [x] Capture: `ClipPrefs` (file monsoon_clipboard), `ClipHub` (one shared store, loads DB in background), `ClipCapture` (listener; skips sensitive flag, our own `EXTRA_NO_HISTORY` marker for the vault (D43), password/incognito fields); hooks in MonsoonIme (onCreate/onStartInputView/onFinishInput/onDestroy). Pushed, CI to check. NOTE for E1: the vault must put `ClipCapture.EXTRA_NO_HISTORY` (and the Android 13 sensitive flag) on every secret it copies.
  E3.5 [x] Keyboard panel, pushed (CI to check): model/grid/format (50 JUnit tests, CI-run), `view/ClipboardPanelView` (Canvas), `SearchablePanel` (EmojiPanelView implements it too), host in MonsoonIme (`clipHost`: paste, copy again with EXTRA_NO_HISTORY, share, edit via `ClipEditActivity`, settings = SetupActivity for now), `emojiSearch` renamed `panelSearch` (+ `shownPanel`), strip clipboard icon -> `onClipboardKey`. Owner-tested on device 2026-11-21 (CI #39, c94d41e): works.
  E3.6 [~] App screens (D67 answered: 'Clipboard' row on the main screen -> ClipboardActivity with 4 bottom tabs History/Pinned/Folders/Shortcuts + gear; simple list rows; date groups on by default; ONE settings page with sections). Touches claude-g's SetupActivity: append a Warning to status/claude-g.md in the same push.
    E3.6.1-E3.6.6 [x] pushed together (CI + owner test pending): `clipboard/ClipListModel` (+13 JUnit tests, 63 clipboard tests pass), `clipboard/app/` = ClipboardActivity (4 tabs + gear), ClipListScreen (History/Pinned/folder), FolderScreen, ShortcutScreen, ClipActions, ClipboardSettingsActivity; "Clipboard" row in SetupActivity; panel gear opens the settings page; SettingsUi got a `prefs` parameter and public helpers.
  E3.7 [~] Shortcuts in the strip (D68 answered). E3.7.1 [ ] pure `TriggerRun` (+ tests) and `ClipFormat.stripLabel`; E3.7.2 [ ] IME hooks (setSuggestions merge, requestPrediction start, onText/onBackspace/enter/tab feeds, acceptShortcut); E3.7.3 [ ] warn claude-l, notes, push, owner test.
  E3.7 [ ] Text shortcuts (user-made only). E3.8 [ ] Images (stored as private files, pasted with commitContent). E3.9 [ ] Biometric lock + export/import (encrypted backup = hook only, later).
E1 Password manager [~] CHECKPOINT 2026-11-21 (owner: pause, clipboard first). Docs: docs/PASSWORD_MANAGER.md (design), PASSWORD_MANAGER_PHASE0.md (results), PASSWORD_MANAGER_BRIEF.md.
  E1.1-E1.11 [x] Design D25-D53 (signed off), Phase 0 feasibility on branch `e1-feasibility` (green run #12, GO).
  E1.12 [x] Foundation on main (merge 9330477, D64): submodule `third_party/keepassdx` @ 4.5.8, `:vault` + 14 emulator tests, `engine.yml`, build.yml scoped to `:app`, AGP 8.13.2 / Gradle 8.14.2. The app does not use it yet.
            (A previous session wrote "merged" in these notes while the work was still on branch `e1-integrate`; this session did the real merge after both runs on 9a2512d were green.)
  E1.13 [ ] Resume here: Phase 1 (D51): (a) `VaultEngine` -> thin interface + one serialized executor; (b) `:vault` service in its own process (D44), app depends on `:vault` (compileSdk 36, drop the conditional include,
            shrunk-build smoke test in CI); (c) app screens in the keyboard's style (D30, D33, D34), file picker (D35), unlock, list/search/view/add/edit/delete, generator; (d) auto-lock + FLAG_SECURE (D38, D43);
            (e) LICENSE GPL-3.0 (D25, check every Avro header). Then Phase 2 (picker in the keyboard). Ask before any UI decision. Weekly watcher (D48) when convenient.
After every owner answer: add a D-line to DECISIONS.md (re-read it first; next free id D65) and update the docs.

## Next step
Check CI for the E3.6 push, give the owner the test checklist (below). Then E3.7 text shortcuts in the keyboard (strip suggestion when a trigger is typed: touches claude-l's suggestion strip, warn first), E3.8 images, E3.9 lock/export/import. Before every push run `tools/compile_check.sh` (~30 s).

## Shared code facts (condensed from other Claudes' warnings; re-pull and read before editing KeyboardView, Keys.kt, Layouts.kt, EmojiPanelView, MonsoonIme)
- Switch languages only through `MonsoonIme.setLanguage`. The strip has an arrow and shows suggestions instead of the icons (D23, D41: `Kind.STRIP` keys are hidden while suggestions show).
- KeyboardView has `setTypingFeel`, `setAppearance` (rows via `rowSlots()` / `hintOf(k)`), `desiredHeight()` and a public `palette` (the emoji panel sizes and colors itself from them); a held hint is typed on release (G1.6);
  `Layouts.withEmojiKey` draws a bottom-row emoji key that calls `onEmojiKey`; EmojiPanelView has a keyboard button (`topTap` -> `onClose`). Keys.kt/Layouts.kt got `Key.digit`, `Key.bnHint`, `Key.drawable` (L5/L6/T11).

## Half-done files / known bugs
E2 known limits: a hardware keyboard types into the app while the emoji search bar is open; Recents refresh only when the panel opens. Ideas not built (ask first): GIF/kaomoji tabs, emoji in the strip, "clear recents" in settings.
Side branch `e1-integrate` is merged (can be deleted); `e1-feasibility` is history only.
LESSON (mine): always re-read a notes file before rewriting it, other Claudes (and my other sessions) append to it.

## Test checklist for my latest build (owner)
E3.6 clipboard app screens (my commit is the `[claude-e] E3.6` one; wait for CI green, then take the APK at the `latest` link):
1. Open the Monsoon app: a new "Clipboard" row. It opens a page with tabs History / Pinned / Folders / Shortcuts and a gear at the top right.
2. History: your copied clips as rows under Pinned / Today / Yesterday / Earlier. Search field on top. Tap a row: Copy, Pin, Edit, Share, Move to folder, Delete. Long-press a row: selection mode (count on top, Pin / Move / Share / Delete at the bottom, All, X).
3. Pinned: only pinned clips. Folders: "New folder", tap one to see its clips, three dots = Rename / Delete folder; move clips in from History (Move).
4. Shortcuts: "New shortcut" (trigger like ;sig + text), tap one to edit or delete. Then open the keyboard's clipboard panel, chip Shortcuts: your shortcuts are there; tap one to type its text.
5. Gear: the clipboard settings (history on/off, limit, auto-delete, keep pinned, clear buttons, pinned on top, group by date, compact, preview lines, time). Try a few and see the keyboard panel follow (compact, lines, time, columns). The gear in the keyboard panel opens the same page.
Tell me what looks wrong or feels clumsy.
