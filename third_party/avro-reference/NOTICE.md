# Avro reference material (read-only)

Source: Avro Keyboard (OmicronLab), Windows/Delphi, licensed under the Mozilla Public License 2.0 (see `LICENSE.txt`).
Included here only as reference for the Kotlin port of the Avro Phonetic engine and the fixed layouts.

- `engine/`   Pascal sources: phonetic conversion, regex builder, constants, Bangla chars, autocorrect, Unicode-to-Bijoy converter, `autodict.dct`
- `layouts/`  `.avrolayout` XML key maps with the embedded bitmap images removed (only key mappings kept)
- `docs/`     Avro's own typing guides (phonetic and fixed layouts)

- `data/`     the suggestion dictionary exported from Avro's `Database.db3` as TSV (`avro-dictionary.tsv`: 159,425 words with
              the first-phoneme bucket they were stored under; `avro-suffixes.tsv`: 749 English->Bangla suffix rows). Same MPL-2.0 license.
              Any compact index shipped in the app is derived data: keep the attribution.

Not included: the original `Database.db3` binary.

## Ported files (keep this list current)
Every Kotlin file derived from the sources above must stay MPL-2.0, keep the MPL header, live under `.../lang/avro/`
(or `.../lang/bijoy/` for Bijoy conversion), and be listed here.

- `app/src/main/kotlin/com/monsoonscript/keyboard/lang/avro/AvroPhoneticConverter.kt` (from clsEnglishToBangla.pas)
- `.../lang/avro/AvroAutoCorrect.kt` (from uAutoCorrect.pas)
- `.../lang/avro/AvroRules.kt` and `.../lang/avro/AvroBanglaChars.kt` (generated from clsEnglishToBangla.pas and BanglaChars.pas)
- `app/src/main/assets/avro/autodict.dct` (unmodified copy of `engine/autodict.dct`)
- `app/src/test/resources/avro/golden.tsv` (test data produced with a translation of the same Pascal rules)
