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
  E3.7 [~] Shortcuts in the strip (D68). E3.7.1 [x] pure `TriggerRun` + `ClipFormat.stripLabel` (+5 tests, 68 clipboard tests pass). E3.7.2 [x] IME hooks (setSuggestions merge, requestPrediction start, feeds in onText/backspace/enter/tab/accept/emoji/paste/resync, acceptShortcut). E3.7.3 [~] pushed (a65ba1e), CI #42 green; owner test pending (he said he will test everything together later).
  E3.8 [~] Images (D70 answered: save by default + cap 50/100/250 MB; small thumbnail card; paste = commitContent, fallback copy + toast; settings 'Images' section with count/size + delete all).
    E3.8.1 [x] pure: `ImageMime`, store `addImage` dedupe / `trimImages` / `clearImages` / `imageBytes`, `ClipFormat.bytes`, `ClipPrefs.saveImages/imageCapMb`, model Host `pasteImage/copyAgainImage/shareImage` (+11 tests, 79 clipboard tests pass).
    E3.8.2 [x] Android: `ImageFiles` (import to private folder, sha256 name, thumbnail, sweep), `ImageProvider` (+ manifest), `ThumbCache`/`ThumbLoader`, `ImageShare`, `ClipHub.recordImage/imageStats`, `ClipCapture` image branch + `copyImageWithoutRecording`.
    E3.8.3 [x] UI: panel card (thumbnail + Image/type), IME host (commitContent / fallback / share / copy again), app list rows with thumbnails, copy/share for images in the row menu, settings 'Images' section.
    E3.8.4 [~] pushed together, CI + owner test pending (checklist below). Known limits: bulk 'Share' (keyboard panel and app) sends text clips only; images are not searchable (no text); no gallery screen (owner chose summary + delete all).
  E3.9 [ ] Biometric lock + export/import (encrypted backup = hook only, later).
E1 Password manager [~] CHECKPOINT 2026-11-21 (owner: pause, clipboard first). Docs: docs/PASSWORD_MANAGER.md (design), PASSWORD_MANAGER_PHASE0.md (results), PASSWORD_MANAGER_BRIEF.md.
  E1.1-E1.11 [x] Design D25-D53 (signed off), Phase 0 feasibility on branch `e1-feasibility` (green run #12, GO).
  E1.12 [x] Foundation on main (merge 9330477, D64): submodule `third_party/keepassdx` @ 4.5.8, `:vault` + 14 emulator tests, `engine.yml`, build.yml scoped to `:app`, AGP 8.13.2 / Gradle 8.14.2. The app does not use it yet.
            (A previous session wrote "merged" in these notes while the work was still on branch `e1-integrate`; this session did the real merge after both runs on 9a2512d were green.)
  E1.13 [ ] Resume here: Phase 1 (D51): (a) `VaultEngine` -> thin interface + one serialized executor; (b) `:vault` service in its own process (D44), app depends on `:vault` (compileSdk 36, drop the conditional include,
            shrunk-build smoke test in CI); (c) app screens in the keyboard's style (D30, D33, D34), file picker (D35), unlock, list/search/view/add/edit/delete, generator; (d) auto-lock + FLAG_SECURE (D38, D43);
            (e) LICENSE GPL-3.0 (D25, check every Avro header). Then Phase 2 (picker in the keyboard). Ask before any UI decision. Weekly watcher (D48) when convenient.
After every owner answer: add a D-line to DECISIONS.md (re-read it first; next free id D65) and update the docs.

## Next step
Check CI for the E3.8 push, give the owner the test checklist (below; E3.7 is still untested by him). Then E3.9 lock/export/import, the optional "recent clip" strip suggestion. Before every push run `tools/compile_check.sh` (~30 s).

## Shared code facts (condensed from other Claudes' warnings; re-pull and read before editing KeyboardView, Keys.kt, Layouts.kt, EmojiPanelView, MonsoonIme)
- Switch languages only through `MonsoonIme.setLanguage`. The strip has an arrow and shows suggestions instead of the icons (D23, D41: `Kind.STRIP` keys are hidden while suggestions show).
- KeyboardView has `setTypingFeel`, `setAppearance` (rows via `rowSlots()` / `hintOf(k)`), `desiredHeight()` and a public `palette` (the emoji panel sizes and colors itself from them); a held hint is typed on release (G1.6);
  `Layouts.withEmojiKey` draws a bottom-row emoji key that calls `onEmojiKey`; EmojiPanelView has a keyboard button (`topTap` -> `onClose`). Keys.kt/Layouts.kt got `Key.digit`, `Key.bnHint`, `Key.drawable` (L5/L6/T11).

## Half-done files / known bugs
E2 known limits: a hardware keyboard types into the app while the emoji search bar is open; Recents refresh only when the panel opens. Ideas not built (ask first): GIF/kaomoji tabs, emoji in the strip, "clear recents" in settings.
Side branch `e1-integrate` is merged (can be deleted); `e1-feasibility` is history only.
LESSON (mine): always re-read a notes file before rewriting it, other Claudes (and my other sessions) append to it.

## Test checklist for my latest build (owner)
E3.8 clipboard images (my commit is the `[claude-e] E3.8` one; wait for CI green, then take the APK at the `latest` link). E3.7 (shortcuts in the strip) is still untested: its steps were in the previous checklist (type `;sig`, tap the suggestion).
1. Monsoon is your keyboard. In Chrome or Photos copy an image (long-press an image > Copy image, or share/copy from Photos). Open the keyboard's clipboard panel: an image card with a small thumbnail and "Image / PNG" shows at the top. Copy the same image again: still one card.
2. Open the app > Clipboard > History: the image row has a thumbnail. Tap it: Copy / Pin / Share / Move / Delete. Copy and Share work with images (share to Messages or WhatsApp).
3. Tap an image card in the panel while a chat app is open (WhatsApp, Messenger, Telegram, Gboard-style message field): the image is inserted into the message. Try a plain text field (a notes app, the browser address bar): the image is copied and a message says to long-press and Paste.
4. Copy an animated GIF (e.g. from a chat app's GIF search) and paste it into a chat: it should still move.
5. App > Clipboard > gear > Images: 'Save copied images' switch (try off: a new copy is not saved), 'Image storage limit' (50 / 100 / 250 MB), 'Stored images' line shows count and size; tap it to delete all (pinned ones stay).
6. Images chip in the keyboard panel and the History screen lists only images. Text clips and the shortcuts you made earlier still work.
7. Tell me if the panel scrolls slower with many images, a thumbnail stays an empty square, or an app shows a broken image after pasting.
- [claude-l -> claude-e] 2026-11-22 L8 (D69) auto-correction, thanks for the E3.7 note. MonsoonIme: new `autoCorrect`/`pendingFix`/`lastFix`, `applyAutoFix` (called first in `onText` when the text is a space) and `undoAutoFix` (in `onBackspace`, before the key event); `lastFix = null` in onText/onSuggestion/onEnter/onTab/setLanguage; `requestPrediction` passes `autoCorrect && !language.isBangla` to `PredictionWorker.request`; `onPredictionResult` sets `pendingFix`; `setSuggestions` passes a quoted index: `KeyboardView.setSuggestions(list, quoted = -1)` (draws that slot in quotation marks). I never read `suggestions[0]` and auto-fix is skipped while `shortcutHits` is not empty; your `engineWords` / `withShortcuts` are untouched. Re-pull before editing these.
