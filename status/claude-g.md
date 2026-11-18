# claude-g notes (only claude-g edits this file; others only append lines under "Warnings")
Updated: 2026-11-17, not started. Focus: settings screen and saved preferences (T10).

## Warnings from other Claudes (read first; delete a line when handled)
(none)

## Tasks and steps
T10 Settings activity + SharedPreferences [ ]
  T10.1 [ ] `prefs/` helper over SharedPreferences (small, no allocations on the touch/draw path; read once, cache).
  T10.2 [ ] Settings activity: two switches, "language key" and "space-bar swipe" (both default ON, D15). Wire to `KeyboardView.setLanguageOptions`.
  T10.3 [ ] Persist the chosen language by its enum NAME (string); an unknown name falls back to EN (claude-e adds new values).
  T10.4 [ ] Setup screen is still a placeholder: only touch it if the settings entry needs it; ask the owner before changing how it looks (UI decision).

## Next step
Read the ci-status branch, then T10.1. Ask the owner before any visual decision for the settings screen (question tool, record in DECISIONS.md).

## Half-done files / known bugs
None.

## Test checklist for my latest build (owner)
(none yet)
- [claude-l -> claude-g] 2026-11-17 cbb5d34: view/KeyboardView.kt gained setSuggestions plus suggestion touch routing (strip icons hide while a Bangla word is composed). Re-pull before editing KeyboardView (setLanguageOptions etc.); nothing else to adapt.
- [claude-l -> claude-g] 2026-11-17 D42: offline word prediction is coming (L2). T10 settings should have room for: a switch "Learn from my typing" (default ON, key e.g. learn_enabled) and a button "Clear learned words" (I will expose predict-side functions for both and tell you the names when they exist), plus a "Licenses" screen that shows third_party/wmkeyboard-data/NOTICE.md (CC BY / CC BY-SA credits are required in the app). No change needed yet; just keep these in mind.
- [claude-l -> claude-g] 2026-11-18 L2.4b: the learning switch and the clear button are ready to wire. Use `ime/LearnSettings` (object, pure Android, no other dependency): `LearnSettings.enabled(ctx)` / `setEnabled(ctx, on)` for the "Learn from my typing" switch (default ON, key `learn_enabled`) and `LearnSettings.clearLearnedWords(ctx)` for the "Clear learned words" button (also tell the user it cannot be undone). If your prefs helper uses another SharedPreferences file than `monsoon_prefs`, change the one constant `LearnSettings.PREFS` (the IME reads the same file) and keep the key names. A Licenses screen should show `assets/licenses/prediction-data.txt` (plain text, read with `assets.open`).
