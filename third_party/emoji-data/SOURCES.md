# Emoji data sources (E2, D56)

Everything here was downloaded on 2026-11-19 and is read by `tools/gen_emoji_data.py` (run from the repo root) to build `app/src/main/assets/emoji/emoji.tsv`.
unicode.org is not reachable from the agent sandbox, so the same official files come from Unicode's own GitHub repositories.

| File | Upstream | sha256 |
|---|---|---|
| `emoji-test.txt` (Unicode Emoji 17.0, UTS #51, dated 2025-08-04) | https://raw.githubusercontent.com/unicode-org/unicodetools/main/unicodetools/data/emoji/17.0/emoji-test.txt | `1d8a944f88d7952f7ef7c5167fef3c67995bcae24543949710231b03a201acda` |
| `annotations-en.xml` | https://raw.githubusercontent.com/unicode-org/cldr/main/common/annotations/en.xml | `14d0e9bb5003c085250df4ac7bd7ba1e26d92e74174d7a3a2f42dc16e07fb155` |
| `annotations-bn.xml` | https://raw.githubusercontent.com/unicode-org/cldr/main/common/annotations/bn.xml | `9b7bf4bd7b5deafd4370c5085d9a6a5e04029e21c005ace782fbd54c937fca1d` |
| `annotationsDerived-en.xml` | https://raw.githubusercontent.com/unicode-org/cldr/main/common/annotationsDerived/en.xml | `a4a6c1a4c341d94179cb116623c5f44c47400a5bc55da483423ef7430ecf622e` |
| `annotationsDerived-bn.xml` | https://raw.githubusercontent.com/unicode-org/cldr/main/common/annotationsDerived/bn.xml | `46aafaaf8b74f17773cce1bac76dbd09d23611a5cfa34915fd017fd9ef26295e` |
| `CLDR-LICENSE` | https://raw.githubusercontent.com/unicode-org/cldr/main/LICENSE | |
| `NOTO-OFL-1.1.txt` | https://raw.githubusercontent.com/googlefonts/noto-emoji/main/2D/fonts/LICENSE | |

The font is NOT kept in this folder: `app/src/main/assets/emoji/NotoColorEmoji.ttf` is Noto Color Emoji v2.057 (`noto-emoji:20260911`), CBDT/CBLC bitmap format (works on every
Android version we support), 10,730,124 bytes, sha256 `15671215ab769fdc7162a045d56fd7d7e477c51b04e6b3c761d914d8fdd6cc44`, from
https://raw.githubusercontent.com/googlefonts/noto-emoji/main/2D/fonts/NotoColorEmoji.ttf (the `github.com/.../raw/...` form answers 403 in the sandbox; use raw.githubusercontent.com).

Generator check with `--font` (HarfBuzz via `pip install uharfbuzz`): all 3,944 fully-qualified sequences of Unicode 17 (1,914 base emoji + 2,030 skin-tone variants) shape to ONE glyph
in this font; nothing had to be dropped. Rerun the generator with `--font` after updating any input; it prints what the font cannot draw.

To update to a newer Unicode version: replace the five data files (same URLs, new version folder for emoji-test), rerun `python3 -I tools/gen_emoji_data.py --font <new font>`, check the printed
summary, update the hashes above and the version in `assets/licenses/emoji.txt`.
