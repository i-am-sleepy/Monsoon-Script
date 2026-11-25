# claude-e notes (only claude-e edits this file; others only append lines under "Warnings")
Updated: 2026-11-25, session 5. E1.13 (vault Phase 1) screens are built, pushed and green in CI; WAITING for the owner's phone test (checklist in STATUS Needs user). E3 clipboard and E4 are DONE.
T9 and T11 were taken over by claude-l (L4-L6); only T9.3 Bijoy Classic (+ the owner key-map check, STATUS Needs user #2) is still mine, paused.

## Warnings from other Claudes (read first; delete a line when handled)
(none; the older ones are condensed under "Shared code facts" below)

## Tasks and steps
E3 Clipboard [x] DONE 2026-11-23, every checklist item passed on the owner's phone (E3.7-E3.13). Docs: docs/CLIPBOARD_SPEC.md, DECISIONS D65 D67 D68 D70 D71 D77 D79, ARCHITECTURE.md 'Clipboard'. 125 pure-Kotlin tests (`/tmp/ktool/kc.sh` recipe: compile the `clipboard/*.kt` pure files + tests, run JUnitCore).
  Built: store/DB/hub/capture, keyboard panel (search, chips, 2 or 1 column cards, multi-select, 3-dot menu, shortcut strip D68 + direct insertion D77, recent copy chip D77, images D70), app screens + settings, lock (biometric, D71) with keyboard return after unlock, backup/auto backup (zip, keeps 5), Auto fit (D79).
  Known limits: backups are NOT encrypted (stated in settings); import only merges; images are not searchable; with Auto fit on the panel's 1/2 column button does nothing (owner chose that; could hide/dim it, ask first).
  IDEAS NOT BUILT (ask first): owner wants LATER an encryption option for the clipboard history 'like KeePassDX' (probably the same kind of key-derived encrypted store; start only after E1 Phase 1 exists, and ask him how: password vs. device lock, which data (text, images, backups), what the keyboard shows while locked); generalize 'keyboard returns by itself' to gear/edit/share flows.
E4 [x] space bar highlight DONE, owner-tested (`KeyboardView.spaceLit/flashSpace`, Enter key color for 400 ms after a language switch and while the cursor drag is held).
E1 Password manager [~] CHECKPOINT 2026-11-21 (owner: pause, clipboard first). Docs: docs/PASSWORD_MANAGER.md (design), PASSWORD_MANAGER_PHASE0.md (results), PASSWORD_MANAGER_BRIEF.md.
  E1.1-E1.11 [x] Design D25-D53 (signed off), Phase 0 feasibility on branch `e1-feasibility` (green run #12, GO).
  E1.12 [x] Foundation on main (merge 9330477, D64): submodule `third_party/keepassdx` @ 4.5.8, `:vault` + 14 emulator tests, `engine.yml`, build.yml scoped to `:app`, AGP 8.13.2 / Gradle 8.14.2. The app does not use it yet.
            (A previous session wrote "merged" in these notes while the work was still on branch `e1-integrate`; this session did the real merge after both runs on 9a2512d were green.)
  E1.13 [~] Phase 1 (D51), started 2026-11-24 on the owner's order ("integrate KeePassDX as agreed"). Steps:
    E1.13.1 [x] (a) in `:vault`: `VaultModel.kt` (parcelable data + VaultWire), `VaultStore` (+ backups), `VaultSession` (create/open/lock/list/search/entry CRUD/groups/save, D50 conflicts, KDF memory pre-check), `VaultSessionTest` (16 tests). Engine run #6 had 15/16 green; the failing one (memory limit) is fixed in 58153dd, awaiting the CI result.
    E1.13.2 [x] (b) `IVault.aidl`, `VaultService` (android:process=":vault", D44, auto-lock + screen off, `setAutoLock(ms)`), `VaultClient`, `UriVaultStore`, `VaultServiceTest`; build.yml (submodule + platform 36); the app depends on `:vault` (compileSdk 36). Engine CI #9 green (all emulator tests), app CI green with R8.
    E1.13.3 [x] asked 2026-11-24, answered: one-screen create, instant delete + Undo bar, characters-only generator, Recycle Bin as a folder row at the bottom (DECISIONS D81).
    E1.13.4 [~] (c) screens pushed (3c541b3, e42584f): `app/.../keyboard/vault/` (see ARCHITECTURE.md). NOT yet seen on a phone. Known gaps: restoring from the bin goes to the main folder (previous folder not exposed); moving an entry to another folder has no screen; entry history has no view/restore; release R8 behaviour (BouncyCastle/argon2 at runtime) is only proven by the owner's unlock test.
    E1.13.5 [~] (d) FLAG_SECURE on every vault screen, idle lock per screen + service auto-lock + screen-off lock, setting 1/2/5/10/30 min (default 5, D38). Awaiting the owner test. THEN: Phase 2 (picker in the keyboard, D51), fingerprint unlock (reuse ClipUnlockActivity pattern), weekly watcher (D48).
After every owner answer: add a D-line to DECISIONS.md (re-read it first; next free id D82) and update the docs.

## Next step
E1.13: (1) after the owner's test list: fix what he reports (his words go to DECISIONS if they change behaviour). (2) Phase 2 (keyboard picker) only after Phase 1 passes on the phone. (3) Update docs/PASSWORD_MANAGER.md status.
Before every push of `:vault` code run `tools/vault_compile_check.sh` (~30 s, needs `git submodule update --init --depth 1 third_party/keepassdx`) and `tools/compile_check.sh` for app code (it also compiles VaultModel + VaultClient). Pure vault logic: compile with kotlinc + a main() like /tmp/gentest if no unit-test runner is available.

## Shared code facts (condensed from other Claudes' warnings; re-pull and read before editing KeyboardView, Keys.kt, Layouts.kt, EmojiPanelView, MonsoonIme)
- Switch languages only through `MonsoonIme.setLanguage`. The strip has an arrow and shows suggestions instead of the icons (D23, D41: `Kind.STRIP` keys are hidden while suggestions show).
- KeyboardView has `setTypingFeel`, `setAppearance` (rows via `rowSlots()` / `hintOf(k)`), `desiredHeight()` and a public `palette` (the emoji panel sizes and colors itself from them); a held hint is typed on release (G1.6);
  `Layouts.withEmojiKey` draws a bottom-row emoji key that calls `onEmojiKey`; EmojiPanelView has a keyboard button (`topTap` -> `onClose`). Keys.kt/Layouts.kt got `Key.digit`, `Key.bnHint`, `Key.drawable` (L5/L6/T11).
- L8/L9 (claude-l, handled): MonsoonIme has `autoCorrect`/`pendingFix`/`lastFix` (`applyAutoFix` first in `onText` on a space, `undoAutoFix` in `onBackspace`); `KeyboardView.setSuggestions(list, quoted = -1)`; `Listener.onCursorMove(steps)` has a default body (space-bar cursor drag, D72, touch code in KeyboardView). Re-pull before editing those.
- L10 themes (claude-l, handled): `KeyboardView.setThemes(day, night)`, `Palette` is a data class, DARK/LIGHT come from `theme/Themes`; my panels still take `kb.palette` via `setPalette`. claude-l reuses `clipboard/MiniJson` (read + write): tell claude-l before changing its API. LICENSE (GPL-3.0) is done by claude-l (L11, D74), no step (e) for E1.

## Half-done files / known bugs
E2 known limits: a hardware keyboard types into the app while the emoji search bar is open; Recents refresh only when the panel opens. Ideas not built (ask first): GIF/kaomoji tabs, emoji in the strip, "clear recents" in settings.
Side branch `e1-integrate` is merged (can be deleted); `e1-feasibility` is history only.
LESSON (mine): always re-read a notes file before rewriting it, other Claudes (and my other sessions) append to it.
Clipboard: nothing half-done. No owner test is pending for my work.
- [claude-l -> claude-e] 2026-11-24 L12 R1 (CI #76 green) + R2: every app screen now extends `settings/ThemedActivity` (ClipboardActivity, ClipboardSettingsActivity, ClipEditActivity were switched, nothing else changed in them except: ClipboardActivity's back arrow is `ui.backButton { finish() }`, `LockGate` paints its overlay with `ui.bg`). `SettingsUi.row()` now returns a `RowView`: on a `ui.page()` content, rows that follow each other are drawn together in ONE rounded card (headers, notes and other views sit between cards); rows added to your own containers behave as before. Colors come from the keyboard theme in use (`AppColors`, default theme = High contrast), not from the system Material colors. `ui.page(title, logo = false) { onBack }` got an optional `logo` parameter before the lambda. Re-pull before editing SettingsUi or the clipboard screens' chrome.
