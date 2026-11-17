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
  E1.5 [~] Stage 4 database lifecycle: asked where the vault file lives, how saving works, how unlocking from the keyboard works (STATUS Needs user #5). Waiting.
  E1.6 [ ] Stage 5 security options and trade-offs.  E1.7 [ ] Stage 6 reuse vs own code (incl. D1 exception for the vault process).
  E1.8 [ ] Stage 7 integration and update strategy.  E1.9 [ ] Stage 8 user journey (switch apps, lock phone, close keyboard) + phasing.
  E1.10 [ ] Owner signs off -> inspect our project and the upstream source, feasibility spike, then LICENSE file (D25), then build.
  After every owner answer: add a D-line to DECISIONS.md (next free id: D35) and update docs/PASSWORD_MANAGER.md.
T9 Fixed layouts as data + language-key popup [ ] waits behind E1 (owner priority)
  T9.1 [ ] Layout data format (key maps as data, not code) and loader; Provat and National from `third_party/avro-reference/layouts` (images stripped).
  T9.2 [ ] Add enum values to `Language` (Provat, National, Bijoy); typing through the layout engine; check English and Avro still behave exactly as before.
  T9.3 [ ] Bijoy Classic from the standard chart. Then ask the owner to verify the key map (STATUS Needs user #2).
  T9.4 [ ] Language-key long-press popup to pick Avro / Provat / Bijoy / National (D19). Tap = English <-> last-used Bangla layout.
T11 Bangla digit hints [ ] (after T9) English digits typed, Bangla digits as hints and on long-press (D18).

## Next step
Read the owner's answers to the stage-4 questions, write them as D35+ in DECISIONS.md, then stage 5 (security). Check ci-status first (last read: CI #8 green).
After E1 is paused or done: T9.1. Switch languages only through `MonsoonIme.setLanguage` (see STATUS "Hand-offs").

## Half-done files / known bugs
None. (E1 so far only added docs and decisions; no source files touched.)

## Test checklist for my latest build (owner)
(none: no code change yet)
