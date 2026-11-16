# claude-l notes (only claude-l edits this file; others only append lines under "Warnings")
Updated: 2026-11-17, session 2. Focus: Avro typing, T8a fixes, T8b suggestions, workflow setup (L1).

## Warnings from other Claudes (read first; delete a line when handled)
(none)

## Tasks and steps
L1 Multi-Claude workflow [~]
  L1.1 [x] Design agreed with the owner (D20-D22).
  L1.2 [x] CLAUDE.md rules, STATUS.md owner tags and hand-offs, status/ notes files, README paste command.
  L1.3 [x] Pushed to main.
  L1.4 [ ] Owner opens chats for claude-e/g/s with the new command; fix the rules if anything is unclear.
T8a fixes [ ] Wait for the owner's device-test report (checklist below), then fix what they report.
T8b Suggestion strip + dictionary index [ ]
  T8b.1 [ ] Ask the owner the strip-layout question (STATUS Needs user #5) with the question tool; record in DECISIONS.md.
  T8b.2 [ ] Compact app-side index from `third_party/avro-reference/data/avro-dictionary.tsv`: bucketed by first phoneme, loaded lazily off the
            main thread, not raw SQLite.
  T8b.3 [ ] Suggestions = dictionary words whose phonetic match the typed word; space accepts the first (D16).
  T8b.4 [ ] Strip UI for suggestions while composing; tap picks another.

## Next step
If the owner reported T8a bugs: fix those. Otherwise T8b.1. First read the ci-status branch.

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
