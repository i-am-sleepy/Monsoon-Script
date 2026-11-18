# Credits and licenses of the prediction data

The word suggestions of Monsoon Script (English, Bangla and Banglish) come from three packed files in `app/src/main/assets/predict/`
(`en.mslm`, `bn.mslm`, `rom.mslm`). They are built by `tools/pack_wm_data.py` from **WM Keyboard Data**
(https://github.com/wasi-master/wmkeyboard-data, commit and file hashes in [SOURCES.md](SOURCES.md)). The packing keeps only words
and counts (counts rounded to a 3.5 % log scale), drops offensive words, filters non-words and re-encodes everything; nothing is copied as text.
A plain-text version of the credits below is shown in the app: `app/src/main/assets/licenses/prediction-data.txt`.

The files are adaptations of the sources below. Treat the three packed files as **CC BY-SA 4.0** (the strictest license among the
lists they combine); the app itself stays GPL-3.0 (D25), which Creative Commons lists as a compatible license for CC BY-SA 4.0 adaptations.
Whoever reuses the packed files must keep the credits below. This summary is the maintainers' reading of the upstream notices, not legal advice.

## What went into which file

| Packed file | Words and counts | Word pairs and triples |
|---|---|---|
| `en.mslm` (English) | `en/en_full`: word counts from film subtitles (hermitdave/FrequencyWords, OpenSubtitles 2018), declared CC BY-SA 4.0 upstream; entries also found in the AOSP English list were kept by upstream (AOSP word lists: Apache-2.0) | `en/en_bigrams`, `en/en_trigrams`: Leipzig Corpora Collection news 2024 and web 2018 sentences (CC BY 4.0) blended with the Tatoeba sentence corpus (CC BY 2.0 FR / CC0) |
| `bn.mslm` (Bangla) | `bn/bn_full_v2`: words from tahmid02016/bangla-wordlist (public domain), counts from the Leipzig Corpora Collection (CC BY 4.0) | `bn/bn_bigrams`, `bn/bn_trigrams`: Leipzig Corpora Collection, 4.8 million Bengali sentences (CC BY 4.0), blended with Tatoeba |
| `rom.mslm` (Banglish) | `bn/bn_rom_v2`: Banglish spellings from istiaqfuad/bangla-english-banglish-pairs (CC BY 4.0) plus the Dakshina dataset (CC BY-SA 4.0) | none (the Banglish pair lists are not used, see below) |

Offensive-word lists (`en_offensive`, `bn_offensive`) are used only to remove words from the packs.

`app/src/main/assets/predict/rom2bn.bin` (D54) is a table of links made by this project from `rom.mslm` and `bn.mslm` (each Banglish word -> the Bangla word that the plain
Avro phonetic conversion gives, if the Bangla list has it); it adds no new data, and Banglish next-word suggestions drawn through it come from the Bangla pair counts above (Leipzig, CC BY 4.0).

## Credits

- **Leipzig Corpora Collection**, Universität Leipzig, https://wortschatz.uni-leipzig.de , CC BY 4.0. D. Goldhahn, T. Eckart and U. Quasthoff:
  "Building Large Monolingual Dictionaries at the Leipzig Corpora Collection: From 100 to 200 Languages", LREC 2012.
- **Tatoeba** sentence corpus, https://tatoeba.org , CC BY 2.0 FR / CC0 (contributors of the Tatoeba project).
- **hermitdave/FrequencyWords**, https://github.com/hermitdave/FrequencyWords , frequency lists counted from OpenSubtitles 2018
  (opensubtitles.org; P. Lison and J. Tiedemann, "OpenSubtitles2016: Extracting Large Parallel Corpora from Movie and TV Subtitles", LREC 2016);
  redistributed by WM Keyboard Data under CC BY-SA 4.0.
- **Dakshina dataset**, Google Research, https://github.com/google-research-datasets/dakshina , CC BY-SA 4.0. Roark, Wolf-Sonkin, Kirov, Mielke,
  Johny, Demirsahin and Hall, "Processing South Asian Languages Written in the Latin Script: the Dakshina Dataset", LREC 2020.
- **istiaqfuad/bangla-english-banglish-pairs**, https://huggingface.co/datasets/istiaqfuad/bangla-english-banglish-pairs , CC BY 4.0.
- **tahmid02016/bangla-wordlist**, https://github.com/tahmid02016/bangla-wordlist , public domain.
- **AOSP LatinIME** word lists, Copyright (C) The Android Open Source Project, Apache License 2.0.
- **WM Keyboard Data** (packaging, normalization, merging), Copyright (c) 2026 Wasi Master, MIT License:

  > Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the
  > "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish,
  > distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
  > the following conditions: The above copyright notice and this permission notice shall be included in all copies or substantial portions
  > of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
  > WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
  > LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  > CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

CC BY 4.0: https://creativecommons.org/licenses/by/4.0/ . CC BY-SA 4.0: https://creativecommons.org/licenses/by-sa/4.0/ .
Changes made by Monsoon Script: word lists cut to the most frequent 150,000 (English, Bangla) or kept whole (Banglish); non-words, offensive words and
a few name-only pairs removed; Bengali nukta letters recomposed (য় ড় ঢ়); counts re-encoded on a log scale; everything repacked into one binary format.

## What was deliberately left out (D41)

- **`*_v2` / `*_v3` pair lists** (`en_bigrams_v2`, `bn_bigrams_v3`, ...): they contain OpenSubtitles-derived text under non-commercial research terms.
- **Banglish pair lists** (`bn_rom_bigrams`, `bn_rom_trigrams`): built from private chat data of the dataset's owner, no license stated.
  Banglish next-word suggestions are learned only from the user's own typing.

## Open points for the maintainers

- `bn_rom_v2` word counts were merged by upstream with word counts from the dataset owner's private chats (upstream `scripts/import_facebook_chats.py`).
  Only words and rounded frequencies reach `rom.mslm`, no sentences, but those counts may include the data owner's chat counts. The license of that part is unstated.
- The English counts come from OpenSubtitles text; OPUS distributes the text itself for research use. Only aggregated word frequencies are used here and the
  declared upstream license is followed, but this is the part most worth a second look before a Play Store release.
- If an upstream notice changes, re-run `tools/pack_wm_data.py` against the new commit and update this file and SOURCES.md.
