# claude-e notes (only claude-e edits this file; others only append lines under "Warnings")
Updated: 2026-11-17, not started. Focus: fixed Bangla layouts (T9), then digit hints (T11).

## Warnings from other Claudes (read first; delete a line when handled)
(none)

## Tasks and steps
T9 Fixed layouts as data + language-key popup [ ]
  T9.1 [ ] Layout data format (key maps as data, not code) and loader; Provat and National from `third_party/avro-reference/layouts` (images stripped).
  T9.2 [ ] Add enum values to `Language` (Provat, National, Bijoy); typing through the layout engine; check English and Avro still behave exactly as before.
  T9.3 [ ] Bijoy Classic from the standard chart. Then ask the owner to verify the key map (STATUS Needs user #2).
  T9.4 [ ] Language-key long-press popup to pick Avro / Provat / Bijoy / National (D19). Tap = English <-> last-used Bangla layout.
T11 Bangla digit hints [ ] (after T9) English digits typed, Bangla digits as hints and on long-press (D18).

## Next step
Read the ci-status branch, then T9.1. Switch languages only through `MonsoonIme.setLanguage` (see STATUS "Hand-offs").

## Half-done files / known bugs
None.

## Test checklist for my latest build (owner)
(none yet)
