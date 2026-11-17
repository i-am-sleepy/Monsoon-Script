# claude-e notes (only claude-e edits this file; others only append lines under "Warnings")
Updated: 2026-11-17, session 1. Focus: E1 password-manager DESIGN TALK with the owner (owner priority, no code), then T9, T11.

## Warnings from other Claudes (read first; delete a line when handled)
(none)

## Tasks and steps
E1 Password manager design with the owner [~] (facts + stages: docs/PASSWORD_MANAGER.md, brief: docs/PASSWORD_MANAGER_BRIEF.md). NO code, NO project changes until sign-off.
  E1.1 [x] Read brief and repo docs; CI #8 green; upstream facts verified (KeePassDX GPL-3.0, its modules, KeePassJava2, Android inline autofill, BiometricPrompt needs an Activity).
  E1.2 [x] Stage 1 integration approaches: GPL-3.0 for the app (D25), KeePassDX modules behind our own interface, feasibility check first (D26).
  E1.3 [x] Stage 2 how it meets the keyboard: key icon in the strip (D27); picker first, autofill + save prompt later (D28).
  E1.4 [x] Stage 3 UI: panel replaces the keys (D29), full screens in the keyboard's style (D30), mini form for adding (D31), first view (D32), one list screen (D33), card + shortcut (D34).
  E1.5 [x] Stage 4 database lifecycle: file picker location (D35), automatic safe saving with backups (D36), see-through unlock screen (D37).
  E1.6 [x] Stage 5a security: auto-lock (D38), fingerprint policy (D39), entry tap behavior (D40).
  E1.7 [~] Stage 5b security: asked clipboard clear timeout and vault in its own process; announced the rest of the plan (clipboard history, screenshots, hidden passwords, no network). Waiting.
  E1.8 [ ] Stage 6 reuse vs own code (incl. D1 exception for the vault process).  E1.9 [ ] Stage 7 integration and update strategy.
  E1.10 [ ] Stage 8 user journey (switch apps, lock phone, close keyboard) + phasing.
  E1.11 [ ] Owner signs off -> inspect our project and the upstream source, feasibility spike, then LICENSE file (D25), then build.
  After every owner answer: add a D-line to DECISIONS.md (next free id: D41) and update docs/PASSWORD_MANAGER.md.
T9 Fixed layouts as data + language-key popup [ ] waits behind E1 (owner priority)
  T9.1 [ ] Layout data format (key maps as data, not code) and loader; Provat and National from `third_party/avro-reference/layouts` (images stripped).
  T9.2 [ ] Add enum values to `Language` (Provat, National, Bijoy); typing through the layout engine; check English and Avro still behave exactly as before.
  T9.3 [ ] Bijoy Classic from the standard chart. Then ask the owner to verify the key map (STATUS Needs user #2).
  T9.4 [ ] Language-key long-press popup to pick Avro / Provat / Bijoy / National (D19). Tap = English <-> last-used Bangla layout.
T11 Bangla digit hints [ ] (after T9) English digits typed, Bangla digits as hints and on long-press (D18).

## Next step
Read the owner's answers to the stage-5b questions, write them as D41+ in DECISIONS.md (also record the announced plan items unless the owner objects), then stage 6. Check ci-status first (last read: CI #8 green).
After E1 is paused or done: T9.1. Switch languages only through `MonsoonIme.setLanguage` (see STATUS "Hand-offs").

## Half-done files / known bugs
None. (E1 so far only added docs and decisions; no source files touched.)

## Test checklist for my latest build (owner)
(none: no code change yet)
- [claude-l -> claude-e] 2026-11-17 D41: the strip gets a small arrow button at the left (suggestions <-> icons, Gboard style) and next-word suggestions after a space, in every language. I will change view/KeyboardView.kt (strip layout and touch routing) and keep Layouts.stripLeft/stripRight as they are; your password key icon (D27) and the language popup keep working. Re-pull before editing KeyboardView's strip code.
