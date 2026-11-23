# Monsoon Script

A fast, lightweight Android keyboard for English and Bangla (Avro Phonetic, Provat, Bijoy) with a clean Gboard-style look
and proper physical-keyboard support. Work in progress.

## License
Monsoon Script is free software under the GNU General Public License version 3 (GPL-3.0): see `LICENSE`. Copyright (C) 2026 the Monsoon Script contributors.
Parts keep their own licenses, each stated where it lives: the Avro Phonetic files (`app/src/main/kotlin/**/lang/avro`, `app/src/main/assets/avro`, `third_party/avro-reference`) are MPL-2.0
(a notice in every file; the MPL lets them be combined with GPL-3.0); the packed word-suggestion data is CC BY-SA 4.0 with CC BY / CC0 / Apache-2.0 / public-domain sources
(`third_party/wmkeyboard-data/NOTICE.md`); the emoji data is under the Unicode License v3 and the bundled Noto Color Emoji font under the SIL OFL 1.1. The same texts are in the app under Settings > About > Licenses.

## Continuing the project from any Claude chat
The repo is the shared memory: `CLAUDE.md` (rules), `STATUS.md` (shared task board) and `status/claude-X.md` (each Claude's own notes).
Four Claudes work in parallel: claude-l, claude-e, claude-g, claude-s. Open a Claude chat, tap + then Connectors and switch GitHub on, then paste this, with
the right name in place of X (add your request at the end, or leave it empty to continue that Claude's own next step):

> You are claude-X. Continue my Android keyboard project in github.com/i-am-sleepy/Monsoon-Script. Read CLAUDE.md, then STATUS.md, then
> status/claude-X.md, and continue your own tasks. Commit straight to main and keep your status file updated. My request: ...

Names: claude-l (Avro typing, suggestions), claude-e (layouts), claude-g (settings), claude-s (physical keyboard).
Tip for the owner: say "checkpoint" at any time and the agent pushes its work and updates its notes file immediately.

## Layout
`CLAUDE.md` agent rules · `STATUS.md` shared board and the only task list · `status/` one notes file per Claude · `DECISIONS.md` settled choices ·
`ARCHITECTURE.md` code map · `docs/REQUIREMENTS.md` feature spec · `third_party/avro-reference/` MPL-2.0 reference sources
