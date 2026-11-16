# STATUS (the baton: rewrite it, keep it under one page)

Updated: 2026-11-16, session 1 (Claude Sonnet 5.5). Last confirmed green: CI #6 (bef4011: T8a build + unit tests), APK at the `latest` link, awaiting the owner's device test. Always re-check ci-status first. Release-signed APK at the `latest` link (updates install in place).

## Now
v0.2 Bangla. T7 engine done (CI #5). T8a pushed: language model, Avro live composing in the IME, language key (pill) in the strip next to
the globe (D19), space-bar swipe. CI #6 compiled and passed; nothing of T8a has been tried on a device yet.

## Next step (do this first)
1. `git fetch origin ci-status && git show FETCH_HEAD:last-build.txt` (a plain `origin/ci-status` ref may not exist after a one-branch
   fetch). If `tests:` or `result:` is not success, fix it first (the file lists the failing test / compile error).
2. Fix what the owner reports from the checklist below.
3. T8b: suggestion strip while composing (owner decision needed first: while composing the strip shows suggestions across the full
   width and hides the icons, like Gboard? ask with the question tool). Needs the dictionary index: build a compact app-side index from
   `third_party/avro-reference/data/avro-dictionary.tsv` (bucketed by first phoneme, loaded lazily off the main thread, not raw SQLite),
   then suggestions = dictionary words whose phonetic match the typed word; space accepts the first (D16).
4. T9 (more layouts + the language-key long-press popup, D19), T10 (settings: language key + space swipe switches, persist language), T11, T6.

## Test checklist for the next build (owner)
1. Install https://github.com/i-am-sleepy/Monsoon-Script/releases/download/latest/MonsoonScript.apk over the old one.
2. Top strip now shows a small pill "EN" right after the globe. Tap it: it turns into "বাং" and the space bar says "বাংলা (Avro)".
3. In Bangla mode type: ami (underlined আমি, space commits it), tumi, bangla, Dhaka (Shift then D, h...), bhalO. Try "ami." (should give আমি।).
4. Backspace while a word is underlined removes one Latin letter at a time and the Bangla updates. After the word is committed it deletes normally.
5. Swipe left or right on the space bar: language switches and no space is typed.
6. Mid-word, tap somewhere else in the text and keep typing: a fresh word must start (the old word must not reappear).
7. Password / URL / e-mail fields: letters come out as typed (no Bangla), even in Bangla mode.
8. English mode must feel exactly as before. Report lag, wrong conversions (compare with Ridmik/Avro), anything ugly or crashing.

## Tasks (the only place tasks live)  [ ] todo  [~] in progress  [x] done  [?] blocked on user
- [x] T0-T3 Handoff docs, Avro reference, skeleton, CI + rolling release + ci-status, signing (D9), Canvas soft keyboard (D11-D14)
- [x] T3b Gap under keys (`MonsoonIme.applySystemBars`): window + nav bar painted with the keyboard color, pill contrast follows theme
- [~] T4 Hardware keyboard pass-through: IME has no onKeyDown/onKeyUp overrides and no fullscreen UI, so physical keys reach
      apps untouched; soft view follows Android's "show on-screen keyboard" policy like Gboard. Awaiting owner's physical test.
- [x] T5 Globe: tap = next keyboard, hold = picker (owner to confirm)
- [ ] T6 Hardware-keyboard Bangla: only while a Bangla mode is active, translate printable keys in onKeyDown and pass everything
      else through; a shortcut (e.g. Ctrl+Space) toggles language; suggestions even when the soft view is hidden (candidates view)
- [x] T7 Avro Phonetic engine in `lang/avro/*` + JUnit tests + CI test step + `assets/avro/autodict.dct` (CI #5 green)
- [~] T8 Language model in the IME. DONE (T8a): `Language` enum (EN, AVRO), `PhoneticComposer`, live underlined Bangla via composing text,
      language pill in the strip + space-bar swipe (D15/D19), no auto-caps in Bangla, raw letters in password/URL/e-mail fields.
      TODO: T8b suggestion strip (see Next step), languages Provat/Bijoy/National come with T9
- [ ] T9 Fixed layouts as data: Provat and National from `third_party/avro-reference/layouts` (key maps, images stripped); Bijoy Classic
      from the standard chart (owner must verify the map against their habits); Bornona, Munir Optima, Avro Easy later (D17)
- [ ] T10 Settings activity + SharedPreferences: switches for the language key and for space-bar swipe (both default ON, D15), later more
- [ ] T11 Bangla digit hints on digit keys (English typed, Bangla long-press, D18)

## Roadmap (details in docs/REQUIREMENTS.md)
v0.2 Bangla (T6-T11). v0.3 Emoji (full set, search, recents) and clipboard one-tap. v0.4 Suggestions/autocorrect/next-word.
v0.5 Themes, resizing, one-handed, split, number row. v0.6 Edit/cursor panel, number pad, space-bar cursor drag. v0.7 Polish, perf, settings.

## Needs user (ask with the question tool; record answers in DECISIONS.md)
1. Placeholders I picked without asking: teal accent (Enter key), system haptic on every key, no key sound. Confirm or change.
2. When Bijoy Classic is built (T9): ask the owner to verify the key map.
3. License for the non-Avro parts of the app (MIT / Apache-2.0 / GPL-3.0 / none).
4. When the first network feature appears: also ship an offline-only APK flavor with no INTERNET permission? (see D10)

## Known bugs / notes
Avro choices I made without asking (confirm during testing): '.' right after a Bangla word gives the dari (।); ':' stays a literal colon
(Avro Windows would make it visarga ঃ); '^' after a word gives chandrabindu; capitals are never auto-applied in Bangla.
Language choice is not persisted yet (T10). Long-press on the language pill does nothing until T9 adds a second Bangla layout.
Emoji, clipboard and mic strip icons are drawn but do nothing yet. Launcher icon and setup screen are placeholders.
