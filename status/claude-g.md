# claude-g notes (only claude-g edits this file; others only append lines under "Warnings")
Updated: 2026-11-18, session 1 (a chat the owner told to "act as claude-g"; commits carry [claude-g]). Focus: settings screen and saved preferences (T10, G1).

## Warnings from other Claudes (read first; delete a line when handled)
(none)

## Tasks and steps
T10 = G1.1 + G1.2 (below). Owner decisions: D55.
G1 Settings pages [~]
  G1.1 [ ] `prefs/Prefs` helper (one SharedPreferences file `monsoon_prefs`, shared with `ime/LearnSettings`; read once, cached, listener); persist language + last Bangla layout by enum NAME;
           wire language key + space swipe to `KeyboardView.setLanguageOptions` (T10.1, T10.3)
  G1.2 [ ] Settings UI: SetupActivity becomes the main list (status + setup buttons + rows) with sub-pages Typing (language key, swipe), Suggestions (learn switch, clear button with confirm),
           About (licenses: Avro MPL-2.0 note, `assets/licenses/prediction-data.txt`, app GPL-3.0) (T10.2, L2.5). Plain views, light/dark. Push, CI, owner checklist 1.
  G1.3 [ ] Suggestion toggles: show suggestions, next-word, typed word first (IME + PredictionWorker)
  G1.4 [ ] Typing feel: vibration Off/Light/Normal/Strong (declare VIBRATE, D55), key sound on/off, long-press delay Short/Normal/Long; "try it" text field on the page
  G1.5 [ ] Appearance: theme (system/light/dark), number row switch, height sliders portrait + landscape, key-gap slider (limits so keys never overlap or get too small), live preview (a real KeyboardView)
  G1.6 [ ] (low priority) hold-hint bubble for top-row digits: show the digit while held, type it on release

## Next step
G1.1. Hand-offs to keep: language has ONE entry point `MonsoonIme.setLanguage`; saved by enum NAME, unknown falls back to EN. Shared files I touch: MonsoonIme (start/setLanguage), KeyboardView
(options, palette, sizes), AndroidManifest (VIBRATE, later), strings.xml; warn claude-e (layouts, number row) and claude-l (strip/prediction toggles) when I change them.

## Half-done files / known bugs
None.

## Test checklist for my latest build (owner)
(none yet)
- [claude-l -> claude-g] 2026-11-17 cbb5d34: view/KeyboardView.kt gained setSuggestions plus suggestion touch routing (strip icons hide while a Bangla word is composed). Re-pull before editing KeyboardView (setLanguageOptions etc.); nothing else to adapt.
- [claude-l -> claude-g] 2026-11-17 D42: offline word prediction is coming (L2). T10 settings should have room for: a switch "Learn from my typing" (default ON, key e.g. learn_enabled) and a button "Clear learned words" (I will expose predict-side functions for both and tell you the names when they exist), plus a "Licenses" screen that shows third_party/wmkeyboard-data/NOTICE.md (CC BY / CC BY-SA credits are required in the app). No change needed yet; just keep these in mind.
- [claude-l -> claude-g] 2026-11-18 L2.4b: the learning switch and the clear button are ready to wire. Use `ime/LearnSettings` (object, pure Android, no other dependency): `LearnSettings.enabled(ctx)` / `setEnabled(ctx, on)` for the "Learn from my typing" switch (default ON, key `learn_enabled`) and `LearnSettings.clearLearnedWords(ctx)` for the "Clear learned words" button (also tell the user it cannot be undone). If your prefs helper uses another SharedPreferences file than `monsoon_prefs`, change the one constant `LearnSettings.PREFS` (the IME reads the same file) and keep the key names. A Licenses screen should show `assets/licenses/prediction-data.txt` (plain text, read with `assets.open`).
