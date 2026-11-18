# claude-e notes (only claude-e edits this file; others only append lines under "Warnings")
Updated: 2026-11-17, session 1. Focus: E1 password-manager DESIGN TALK with the owner (owner priority, no code), then T9, T11.

## Warnings from other Claudes (read first; delete a line when handled)
(none)

## Tasks and steps
E1 Password manager design with the owner [~] (facts + stages: docs/PASSWORD_MANAGER.md, brief: docs/PASSWORD_MANAGER_BRIEF.md). NO code, NO project changes until sign-off.
  E1.1 [x] Read brief and repo docs; CI #8 green; upstream facts verified (KeePassDX GPL-3.0, its modules and folder layout, release rhythm, KeePassJava2, Android inline autofill, BiometricPrompt needs an Activity).
  E1.2 [x] Stage 1 integration approaches: GPL-3.0 for the app (D25), KeePassDX modules behind our own interface, feasibility check first (D26).
  E1.3 [x] Stage 2 how it meets the keyboard: key icon in the strip (D27); picker first, autofill + save prompt later (D28).
  E1.4 [x] Stage 3 UI: panel replaces the keys (D29), full screens in the keyboard's style (D30), mini form for adding (D31), first view (D32), one list screen (D33), card + shortcut (D34).
  E1.5 [x] Stage 4 database lifecycle: file picker location (D35), automatic safe saving with backups (D36), see-through unlock screen (D37).
  E1.6 [x] Stage 5 security: auto-lock (D38), fingerprint policy (D39), entry tap behavior (D40), clipboard and exposure rules (D43), vault in its own process (D44).
  E1.7 [x] Stage 6 reuse vs own code: D1 exception for the vault (D45), judgment rule for KeePassDX code (D46).
  E1.8 [x] Stage 7 integration and update strategy: git submodule pinned to a release (D47), weekly release check + test branch (D48), side branch for the first integration (D49).
  E1.9 [~] Stage 8 user journeys + build order + merge behavior + one vault or several: asked (STATUS Needs user #5). Waiting.
  E1.10 [ ] Sign-off: write a one-page summary of everything agreed (docs/PASSWORD_MANAGER.md) and ask the owner to approve it.
  E1.11 [ ] After sign-off: inspect our project and the upstream source, feasibility spike on a side branch (D49), then LICENSE file (D25), then build in phases.
  After every owner answer: add a D-line to DECISIONS.md (next free id: D50; re-read the file first, claude-l also adds lines) and update docs/PASSWORD_MANAGER.md.
T9 Fixed layouts as data + language-key popup [ ] waits behind E1 (owner priority)
  T9.1 [ ] Layout data format (key maps as data, not code) and loader; Provat and National from `third_party/avro-reference/layouts` (images stripped).
  T9.2 [ ] Add enum values to `Language` (Provat, National, Bijoy); typing through the layout engine; check English and Avro still behave exactly as before.
  T9.3 [ ] Bijoy Classic from the standard chart. Then ask the owner to verify the key map (STATUS Needs user #2).
  T9.4 [ ] Language-key long-press popup to pick Avro / Provat / Bijoy / National (D19). Tap = English <-> last-used Bangla layout.
T11 Bangla digit hints [ ] (after T9) English digits typed, Bangla digits as hints and on long-press (D18).

## Next step
Read the owner's answers to the stage-8 questions, write them as D50+ in DECISIONS.md, then the sign-off summary. Check ci-status first (last read: CI #8 green).
After E1 design is signed off: ask the owner whether the feasibility spike (E1 phase 0) starts now or T9 goes first. Switch languages only through `MonsoonIme.setLanguage` (see STATUS "Hand-offs").
Strip layout changed by claude-l's D41: re-read KeyboardView before touching the strip.

## Half-done files / known bugs
None. (E1 so far only added docs and decisions; no source files touched.)

## Test checklist for my latest build (owner)
(none: no code change yet)
