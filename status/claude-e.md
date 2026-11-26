# claude-e notes (only claude-e edits this file; others only append lines under "Warnings")
Updated: 2026-11-26, session 5. E1.13 (vault Phase 1) is DONE and owner-tested. E1.14 (Phase 2, the password picker in the keyboard) is built and pushed; the owner reported two problems with the unlock card (below), fixed in the commit after 523c30b, WAITING for his re-test. E1.15 (Phase 3 step 1, fingerprint unlock) is built and pushed, waiting for CI and his phone test. E3 clipboard and E4 are DONE.
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
    E1.13.5 [x] (d) FLAG_SECURE on every vault screen, idle lock per screen + service auto-lock + screen-off lock, setting 1/2/5/10/30 min (default 5, D38). Owner tested 2026-11-25: works (two fixes: Save/Unlock buttons moved above the keyboard).
  E1.14 [~] Phase 2 (D51, owner order 2026-11-25 "start on phase 2"): the picker in the keyboard. Answers D87 (chips open in place under the entry; picker closes after Password, stays after Username). Steps:
    E1.14.1 [x] `IVault.forApp(pkg)` + `summaries(ids)` (entries linked by the custom field AndroidApp[n]; recent list); 2 emulator tests (engine CI #10 green).
    E1.14.2 [x] `vault/VaultPickerModel` (pure Kotlin) + 14 JVM tests (run with kotlinc + JUnitCore, recipe: compile VaultModel without Parcelize + model + test, JUnitCore).
    E1.14.3 [x] `view/VaultPanelView` (Canvas, like ClipboardPanelView): toolbar, chips This app / Recent / All, list, chips in place, locked / no vault / conflict / error screens, search bar mode, "type here anyway?" overlay.
    E1.14.4 [x] wiring: key icon (`Codes.VAULT`, after the clipboard icon), `VaultUnlockActivity` (see-through master password card), typing without learning (`vaultLearnBlock`, D42), `VaultPrefs.recentIds`.
    E1.14.5 [~] owner report 2026-11-25 evening (build with 79f3041): (a) the keyboard / picker sometimes opened by itself after the unlock card, even when closed and with no text field; (b) sometimes the card "exits the app behind it, back to the home screen".
            Fix pushed: (a) the card tells the IME from `onDestroy`; the IME brings keyboard + picker back only for a field of the SAME app after the card is gone (`vaultEditorIsBack`), never asks the system to show the keyboard
            without an attached field (`editorAttached`, also for the clipboard flow), cancel = keys only, marks used up after use. (b) cause NOT proven: card is now `launchMode=singleInstance`, ends with `finishAndRemoveTask`, handles uiMode/density/etc.
            itself; `CrashLog.note` lines (card created / ended / destroyed, memory warnings, the IME's side) show in Settings > Voice typing > Problem report. If (b) happens again ask the owner for that report + what app was behind.
            Suspects left: low memory (Argon2 needs 64 MiB in the :vault process; LMK may kill the app behind a translucent card), the card recreated after process death (`recreated=true` in the log).
    E1.14.6 [ ] after the owner's re-test: docs/PASSWORD_MANAGER.md status (Phase 2 text).
  E1.15 [~] Phase 3 step 1 (D51 order: fingerprint, backup/restore, attachments, one-time codes + Code chip; owner "Continue with phase 3" 2026-11-26). Rules D39 + owner answers D91 + details D92.
    E1.15.1 [x] `FingerprintPolicy` (pure) + 10 JVM tests (kotlinc + JUnitCore recipe, all green); `FingerprintStore` (Keystore AES-GCM, availability, arm/read/disarm/skip); `FingerprintPrompt` (platform BiometricPrompt + CryptoObject).
    E1.15.2 [x] `FingerprintUnlock` flow (begin / passwordRejected / afterTyped / stop) + `SoftKeyboard.show`; hooked into `VaultUnlockActivity` (card) and `VaultMainActivity.showUnlock/unlock`; `VaultSettingsActivity` switch row; `VaultPrefs.fingerprintOn` + `forgetVault` turns it off; strings `fp_*`. `tools/compile_check.sh` clean.
    E1.15.3 [~] pushed, waiting for CI + the owner's phone test (only the owner's phone can prove the sensor path: prompt shows, set-up touch, restart / 3-day rules, adding a fingerprint). Test list in STATUS Needs user #23.
    E1.15.3b [x] owner's extra request (D95): "Use fingerprint" button under the password field on the card and the app unlock page.
    E1.15.4 [ ] after the test: fix what he finds, docs/PASSWORD_MANAGER.md status. Then E1.17 attachments, E1.18 one-time codes + Code chip (D40). Phase 4 Autofill (D28).
  E1.16 [~] Phase 3 step 2 backups screen (D51; owner's answers D94: list + Restore, Save a copy now, Restore from a file; row in Vault settings; confirm; keep current as backup; lock after).
    E1.16.1 [x] engine: `BackupInfo`, `VaultSession.backups/restoreBackup/restoreFromFile`, AIDL + service, 6 new androidTest cases in VaultSessionTest (run on the emulator in engine.yml; `tools/vault_compile_check.sh` clean).
    E1.16.2 [x] app: `VaultBackupsActivity`, Backups row in `VaultSettingsActivity`, manifest, strings `vault_backups_*`, `VaultHub.readFileIntent`; ARCHITECTURE updated. `tools/compile_check.sh` clean.
    E1.16.3 [~] pushed; waiting for CI (app + engine emulator tests on `ci-status-engine`) and the owner's phone test (STATUS Needs user #24).
    E1.16.4 [ ] after the test: fix findings; docs/PASSWORD_MANAGER.md status. Then E1.17 attachments, E1.18 one-time codes + Code chip (D40).
After every owner answer: add a D-line to DECISIONS.md (re-read it first; D87 is the last one I wrote, check the file for the next free id) and update the docs.

## Next step
E1.16.3: read `ci-status` and `ci-status-engine` for the E1.16 commit; wait for the owner's fingerprint test (STATUS Needs user #23) and backups test (#24) and the Phase 2 re-test (#22; if the card still goes to the home screen read Problem report `CrashLog.note` lines first). Next after that: E1.17 attachments (UI task: ask the owner the screen questions first, CLAUDE.md), then E1.18.
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
