# claude-s notes (only claude-s edits this file; others only append lines under "Warnings")
Updated: 2026-11-17, not started. Focus: physical keyboards (T4 follow-ups, T6 Bangla from a hardware keyboard).

## Warnings from other Claudes (read first; delete a line when handled)
(none)

## Tasks and steps
T4 Hardware keyboard pass-through [~] Code is done; waiting for the owner's physical-keyboard test. Fix what they report.
T6 Hardware-keyboard Bangla [ ]
  T6.1 [ ] Read ARCHITECTURE.md "Hardware keyboard rules". Add `onKeyDown` handling ONLY while a Bangla mode is active; translate printable keys,
           pass everything else through untouched (Enter, Shift, Ctrl/Alt/Meta combos, arrows, Tab are never swallowed).
  T6.2 [ ] Ctrl+Space (or similar) toggles language by calling `MonsoonIme.setLanguage` (the same path as the pill and space swipe).
  T6.3 [ ] Suggestions when the soft view is hidden (candidates view). Needs claude-l's T8b index: check status/claude-l.md first.
  T6.4 [ ] Test checklist for the owner, with a physical keyboard.

## Next step
Read the ci-status branch, then T6.1. English typing with a physical keyboard must stay exactly as before.

## Half-done files / known bugs
None.

## Test checklist for my latest build (owner)
(none yet)
- [claude-l -> claude-s] 2026-11-17 cbb5d34: MonsoonIme now runs a suggestion worker (ime/SuggestionWorker) and updates the strip in showComposing(). If your T6 hardware-key path feeds letters into the PhoneticComposer, go through showComposing (not setComposingText directly) so the strip follows, and call commitComposingIfAny when a hardware key ends the word. Dictionary search is AvroSuggester (lang/avro), main-thread use is not allowed (tens of ms).
