# claude-l notes (only claude-l edits this file; others only append lines under "Warnings")
Updated: 2026-11-17, session 4. Focus: Avro typing, T8a fixes, T8b suggestions, workflow setup (L1).

## Warnings from other Claudes (read first; delete a line when handled)
(none)

## Tasks and steps
L1 Multi-Claude workflow [~]
  L1.1 [x] Design agreed with the owner (D20-D22).
  L1.2 [x] CLAUDE.md rules, STATUS.md owner tags and hand-offs, status/ notes files, README paste command.
  L1.3 [x] Pushed to main.
  L1.4 [ ] Owner opens chats for claude-e/g/s with the new command; fix the rules if anything is unclear.
T8a fixes [ ] Wait for the owner's device-test report (checklist below), then fix what they report.
T8b Suggestion strip + dictionary search [~]
  T8b.1 [x] Owner decided the strip layout (D23) and the colon behaviour (D24).
  T8b.2 [x] Engine (pure Kotlin): generated regex tables + `AvroRegexBuilder` + `AvroSuggester` (bucket search, suffixes, Levenshtein
            ranking) + assets `assets/avro/dict/<bucket>.txt`, `suffixes.tsv`; 9 new unit tests (26 total). CI #7 (bae17eb) green incl. tests.
  T8b.3 [x] `ime/SuggestionWorker` (HandlerThread, coalescing) + `ime/AssetWordSource` (LRU 12 buckets; letter 'a' needs 7); `MonsoonIme` asks
            after each composing change. CI #8 (3fbce27) green.
  T8b.4 [~] Strip UI (KeyboardView.setSuggestions / drawSuggestions / touch routing, MonsoonIme.onSuggestion). CI #9 (cbb5d34) green; waiting for the
            owner's device test (checklist below).
T8c Smart colon (D24) [~]
  T8c.1 [x] `PhoneticComposer`: ':' joins the word after a letter; a trailing ':' is shown plain, a letter after it makes it visarga; 5 tests + 1 search test.
  T8c.2 [x] `MonsoonIme.showComposing`: no dictionary request while the word ends with a plain colon (only the word is shown).
  T8c.3 [ ] Owner device test (checklist below), then fix what they report.

## Next step
CI #9 (cbb5d34, T8b.4) is green. Check ci-status for the T8c commit, then wait for the owner's report on the T8b + T8c checklists below and fix what
they report (T8a bugs first). Nothing else is open on my side; do not start new features without the owner's go-ahead.

## Half-done files / known bugs
None. Avro choices I made without asking (confirm in testing): '.' right after a Bangla word gives the dari (।); ':' stays a literal colon
(Avro Windows would make it visarga ঃ); '^' after a word gives chandrabindu; capitals are never auto-applied in Bangla.

## Test checklist for the T8a build (CI #6, commit bef4011) (owner)
1. Install the APK from the `latest` link over the old one.
2. Top strip has a small pill "EN" right after the globe. Tap it: it turns into "বাং" and the space bar says "বাংলা (Avro)".
3. In Bangla mode type: ami (underlined আমি, space commits it), tumi, bangla, Dhaka (Shift then D, h...), bhalO. Try "ami." (gives আমি।).
4. Backspace while a word is underlined removes one Latin letter at a time and the Bangla updates. After the word is committed it deletes normally.
5. Swipe left or right on the space bar: language switches and no space is typed.
6. Mid-word, tap somewhere else in the text and keep typing: a fresh word must start (the old word must not reappear).
7. Password / URL / e-mail fields: letters come out as typed (no Bangla), even in Bangla mode.
8. English mode must feel exactly as before. Report lag, wrong conversions (compare with Ridmik/Avro), anything ugly or crashing.

## Test checklist for the T8b build (suggestion strip) (owner)
1. Install the APK from the `latest` link over the old one, switch to Bangla (বাং pill).
2. Type "ami": the strip's icons disappear and three columns appear: আমি (bold) first, then other words (e.g. আমী). The word is underlined in the text too.
3. Type "bangla", "kothay", "bhalobasha", "amader", "sonar": the right word should be among the 3 columns (e.g. কোথায়, ভালোবাসা, সোনার). Say which ones were wrong or missing.
4. Tap a suggestion: it replaces the underlined word and a space follows; the icons come back.
5. Space and Enter still commit the first (underlined) word as before. Backspace shortens the word and the columns update.
6. Type fast ("amar sonar bangla ..."): no lag, no flicker of wrong words, no crash. Report how long a suggestion takes to appear after the first word (once, cold).
7. English mode: the strip keeps its icons and nothing changes. Password/URL fields: no suggestions.
Smart colon (T8c, D24), same build: 8. Type "du" then the symbols page ":" then back to letters "kho": it must become দুঃখ (visarga). 9. Type "ami" then ":" and
stop: the text shows আমি: with a normal colon (and a space or Enter keeps it). 10. "ami" ":" "5" (like a time) keeps the plain colon.
