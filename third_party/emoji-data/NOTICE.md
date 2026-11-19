# Credits and licenses of the emoji system

- **Emoji list, names and skin-tone sequences**: Unicode Emoji 17.0 (`emoji-test.txt`, UTS #51), Copyright © Unicode, Inc. Used under the Unicode License v3
  (the data-file license, full text in [CLDR-LICENSE](CLDR-LICENSE); SPDX `Unicode-3.0`).
- **Emoji names and search keywords (English, Bangla)**: Unicode CLDR `annotations` and `annotationsDerived`, Copyright © 1991-2026 Unicode, Inc., Unicode License v3 (same text).
  `tools/gen_emoji_data.py` lower-cases and splits them into search words and packs them into `assets/emoji/emoji.tsv`; nothing is translated or invented.
- **Emoji font**: Noto Color Emoji, Copyright 2022 Google Inc., SIL Open Font License 1.1 (text in [NOTO-OFL-1.1.txt](NOTO-OFL-1.1.txt)). The font file is bundled unmodified as
  `assets/emoji/NotoColorEmoji.ttf` and only used to draw the picker. The OFL allows bundling with software that is not itself under the OFL; the font must not be sold by itself.
- The in-app text (Settings > About and licenses) is `app/src/main/assets/licenses/emoji.txt`; keep it in step with this file.
- Where each file came from and its hash: [SOURCES.md](SOURCES.md).

This summary is the maintainers' reading of the upstream notices, not legal advice.
