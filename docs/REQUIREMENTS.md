# REQUIREMENTS (from the owner; stable reference, not a task list. Tasks live in STATUS.md)

Goal: match everything Ridmik Keyboard offers, plus the owner's own needs, minus its clunkiness and regressions.
Reimplement from scratch. No Ridmik code or assets.

## Must have
1. **Languages**: English, Bangla Avro Phonetic, Bangla Provat, and/or Bijoy. All switchable directly on the keyboard
   (no trip to app settings). Avro/Bijoy must work with a physical keyboard too.
2. **Emoji**: a section with the full emoji database (search and recents).
3. **Clipboard**: history with visible one-tap access.
4. **Switch IME**: on-screen way to jump to Gboard or another keyboard.
5. **Physical keyboard support (important)**: Ridmik interferes with hardware input (Enter, Shift, etc. break in other apps).
   Gboard is the gold standard: no interference, and it adds shortcuts and word suggestions. Minimum is zero interference.
6. **Reliable, persistent, lightweight, super fast.**
7. **Look**: sleek and modern, close to Gboard. NOT Ridmik's clunky design. Ask the owner about UI decisions before building them.

## Ridmik features to cover (inventory from settings screenshots)
- Top toolbar: language/layout switcher, emoji, store/themes, clipboard, code/symbols, number pad, more, voice typing.
- Edit panel: arrow keys, Select, Select All, Copy, Paste, Home, End, Backspace.
- Number pad: digits, + - * , : % . space, backspace, Enter, back to ABC.
- Symbols pages with Bangla digit hints; second page with currency, math and legal symbols (° π √ ÷ × { } [ ] © ® ™ and similar),
  Tab, and a cursor-move key.
- Appearance: themes/palette, number row (normal and large), hide long-press hints, keyboard height resize (portrait and landscape).
- Modes: one-handed (width per orientation), split keyboard (and split for foldables).
- Behavior: forced Enter key (never show emoji on Enter), key popup dismiss delay, vibration duration, key sound volume,
  key long-press delay, space-bar cursor drag (long-press delay and speed), emoji key for physical Alt, show typed word first in
  phonetic suggestions, voice typing engine choice.
- Typing aids: block offensive words, auto-correction, correction suggestions, personalized suggestions, next-word suggestions.

## Avoid
Clunky UI and slow responses; anything that interferes with physical keyboards; settings buried where a switch belongs on the keyboard.
