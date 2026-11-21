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
  E3.5 [x] Keyboard panel, pushed (CI to check): model/grid/format (50 JUnit tests, CI-run), `view/ClipboardPanelView` (Canvas), `SearchablePanel` (EmojiPanelView implements it too), host in MonsoonIme (`clipHost`: paste, copy again with EXTRA_NO_HISTORY, share, edit via `ClipEditActivity`, settings = SetupActivity for now), `emojiSearch` renamed `panelSearch` (+ `shownPanel`), strip clipboard icon -> `onClipboardKey`. Not device-tested yet: the owner's test of the build is the first run.
  E3.6 [ ] App: Clipboard screens (bottom nav) + settings page in plain Views; this touches claude-g's SetupActivity/SettingsPageActivity: append a Warning to status/claude-g.md first.
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
Check CI for the E3.5 push, give the owner the test checklist (below), then E3.6: app Clipboard screens (bottom nav History/Pinned/Folders/Shortcuts, gear -> clipboard settings page) in plain Views. Warn claude-g first (SetupActivity / SettingsPageActivity). Before every push run `tools/compile_check.sh` (new, ~30 s).

## Shared code facts (condensed from other Claudes' warnings; re-pull and read before editing KeyboardView, Keys.kt, Layouts.kt, EmojiPanelView, MonsoonIme)
- Switch languages only through `MonsoonIme.setLanguage`. The strip has an arrow and shows suggestions instead of the icons (D23, D41: `Kind.STRIP` keys are hidden while suggestions show).
- KeyboardView has `setTypingFeel`, `setAppearance` (rows via `rowSlots()` / `hintOf(k)`), `desiredHeight()` and a public `palette` (the emoji panel sizes and colors itself from them); a held hint is typed on release (G1.6);
  `Layouts.withEmojiKey` draws a bottom-row emoji key that calls `onEmojiKey`; EmojiPanelView has a keyboard button (`topTap` -> `onClose`). Keys.kt/Layouts.kt got `Key.digit`, `Key.bnHint`, `Key.drawable` (L5/L6/T11).

## Half-done files / known bugs
E2 known limits: a hardware keyboard types into the app while the emoji search bar is open; Recents refresh only when the panel opens. Ideas not built (ask first): GIF/kaomoji tabs, emoji in the strip, "clear recents" in settings.
Side branch `e1-integrate` is merged (can be deleted); `e1-feasibility` is history only.
LESSON (mine): always re-read a notes file before rewriting it, other Claudes (and my other sessions) append to it.

## Test checklist for my latest build (owner)
E3.5 clipboard panel (my commit is the `[claude-e] E3.5` one; wait for CI green, then take the APK at the `latest` link):
1. Copy a few different texts in other apps (while Monsoon is the active keyboard), then tap the clipboard icon in the strip: the panel opens with cards, newest first.
2. Tap a card: its text is typed. Long-press a card: selection starts; tap more cards; try pin / delete / move to folder from the top bar.
3. 3 dots on a card: Pin, Edit (opens an edit page), Copy again, Share, Delete.
4. Chips: All / Pinned / Text / Images / Shortcuts. Top-right toggle: 2 columns <-> 1 column. Search: tap the search icon, type (also in Avro): the panel shrinks above the keys and filters.
5. Copy something in a password field or from KeePassDX (if installed): it must NOT appear in the history.
6. Emoji button in the bottom bar goes to the emoji panel; ABC returns to the keys. Tell me anything that looks wrong, jumps, or is too slow.
