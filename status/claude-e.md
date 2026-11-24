# claude-e notes (only claude-e edits this file; others only append lines under "Warnings")
Updated: 2026-11-24, session 5 (E1.13 in progress). E3 clipboard and E4 are DONE and owner-tested. E1.13.1/.2 pushed; NEXT: read both CIs, then ask the owner the screen questions (E1.13.3).
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
    E1.13.2 [~] (b) DONE and pushed: `IVault.aidl`, `VaultService` (android:process=":vault", D44, auto-lock 5 min + screen off, `setAutoLock(ms)`), `VaultClient` (blocking `call`, `async`), `UriVaultStore`, `VaultServiceTest` (6 tests), build.yml (submodule + platform 36, pushed without the owner's token), the app `implementation(project(":vault"))` + compileSdk 36 (8d82e14).
                 STILL TO DO: read both CIs (engine: `ci-status-engine`, app: `ci-status`); decide the shrunk-build smoke test (idea: non-exported self-test receiver on an emulator, or the owner's phone test); check the release APK size.
    E1.13.3 [ ] ask the owner the Phase 1 screen questions (file picker, create-vault wizard, entry view/edit, generator); answers -> DECISIONS D80+
    E1.13.4 [ ] (c) app screens in the keyboard's style (D30, D33, D34), file picker (D35), unlock (reuse clipboard biometric pattern), list/search/view/add/edit/delete, generator
    E1.13.5 [ ] (d) auto-lock + FLAG_SECURE (D38, D43). Then Phase 2 (picker in the keyboard). Weekly watcher (D48) when convenient. (LICENSE step is done, claude-l L11.)
After every owner answer: add a D-line to DECISIONS.md (re-read it first; next free id D80) and update the docs.

## Next step
E1.13: (1) read engine CI (`git fetch origin ci-status-engine && git show FETCH_HEAD:last-build.txt`) and app CI (`ci-status`) for 8d82e14; fix what is red. (2) E1.13.3 ask the owner the screen questions (AskUserQuestion, recommended option first), record answers as D80+. (3) E1.13.4 screens.
Before every push of `:vault` code run `tools/vault_compile_check.sh` (~30 s, compiles vault + tests against the real engine sources; needs `git submodule update --init --depth 1 third_party/keepassdx`), and `tools/compile_check.sh` for app code (it must learn about `:vault` once app code imports it). The clipboard lock (`LockState`, `ClipUnlockActivity`, `LockGate`) is a working biometric-unlock pattern to reuse.

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
