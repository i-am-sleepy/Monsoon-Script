#!/usr/bin/env python3
"""Pack WM Keyboard Data (https://github.com/wasi-master/wmkeyboard-data) into the compact "MSLM" files the keyboard memory-maps.

Usage (from the repo root; the argument is a checkout of wmkeyboard-data):
    python3 -I tools/pack_wm_data.py /path/to/wmkeyboard-data

Writes app/src/main/assets/predict/{en,bn,rom}.mslm and third_party/wmkeyboard-data/SOURCES.md (upstream commit + sha256 of every input).
Decisions behind the choices: DECISIONS.md D41/D42. Attribution and licenses: third_party/wmkeyboard-data/NOTICE.md.

Inputs used (and nothing else):
  en : en/en_full, en/en_bigrams, en/en_trigrams, en/en_offensive
  bn : bn/bn_full_v2, bn/bn_bigrams, bn/bn_trigrams, bn/bn_offensive
  rom: bn/bn_rom_v2 (words only; the Banglish pair lists are NOT used, D41)
NEVER use the *_v2/_v3 pair lists: they contain OpenSubtitles text with non-commercial terms.

MSLM file layout, little-endian (the Kotlin reader is app/src/main/kotlin/com/monsoonscript/keyboard/predict/LangPack.kt):
   0  "MSLM"            magic
   4  u16 version = 1
   6  u16 reserved
   8  u32 nWords   12 u32 nBigrams   16 u32 nTrigrams
  20  u32 offWordOffsets   (u32[nWords + 1], byte offsets into the word blob)
  24  u32 offWordBlob      (UTF-8 words, sorted by code point, no separators)
  28  u32 offCounts        (u8[nWords]: q = round(10 * log2(count)), decoded as 2^(q/10))
  32  u32 offBigrams       (nBigrams x 7 bytes:  w1 u24, w2 u24, q u8; sorted by (w1, w2))
  36  u32 offTrigrams      (nTrigrams x 10 bytes: w1 u24, w2 u24, w3 u24, q u8; sorted by (w1, w2, w3))
  40  u64 totalCount       (sum of the exact unigram counts of the kept words)
  48  data
A word id is its index in the sorted word list.
"""

import glob
import gzip
import hashlib
import math
import os
import re
import struct
import subprocess
import sys

WORDS_EN = 150_000
WORDS_BN = 150_000
BLOCK_TOM_MARY = {"tom", "mary"}  # Tatoeba's favourite names bias the English pairs (D41 analysis)

# The upstream block lists are crude: they contain ordinary words ("i", "us", "kid", "food", "church", Bangla "বাংলাদেশ" ...). These are the
# hand-reviewed exceptions among the most frequent words; real profanity and slurs stay blocked. Rarer false positives just are not suggested.
EN_ALLOW = set("""i us kill killed killing killer kills hell sit crazy kid cool stupid hate food ha ah beat mad kiss attack fish blow church sad
bloody slow fat bob ow danny bell bigger burn bomb beach c faith doc suicide dumb ugly babe insane style pal naked post ed fbi cos jerry pair
tongue bones crash b horn sexual trap bang babies desire laid illegal boom beast pot demon fed f slave jo g adult joint assault el pee aw em ya heh
hitler tom""".split())
BN_ALLOW = {"\u09AC\u09BE\u0982\u09B2\u09BE\u09A6\u09C7\u09B6", "\u09B9\u09AF\u09B0\u09A4", "\u09AC\u09BE\u099A\u09CD\u099A\u09BE",
            "\u09AE\u09BE\u09B2", "\u099A\u09BE\u09AA\u09BE", "\u099B\u09BE\u0997\u09B2", "\u09AA\u09BE\u0997\u09B2"}

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "predict")
SOURCES_MD = os.path.join(ROOT, "third_party", "wmkeyboard-data", "SOURCES.md")

EN_WORD = re.compile(r"^[a-z]+(?:'[a-z]+)*$")
ROM_WORD = re.compile(r"^[a-z]+(?:'[a-z]+)*$")
BN_WORD = re.compile("^[ঀ-৿‌‍]+$")
BN_DIGITS = re.compile("[০-৯]")
PRECOMPOSE = {"ড়": "ড়", "ঢ়": "ঢ়", "য়": "য়"}  # D42: same form Avro commits


def read_counts(path):
    with gzip.open(path, "rt", encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line:
                continue
            text, _, c = line.rpartition(" ")
            if c.isdigit():
                yield text, int(c)


def read_lines(path):
    with gzip.open(path, "rt", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                yield line


def norm_en(w):
    return w.lower().replace("’", "'")


def norm_bn(w):
    for a, b in PRECOMPOSE.items():
        w = w.replace(a, b)
    return w


def quant(count):
    return max(0, min(255, int(round(10 * math.log2(max(count, 1))))))


def u24(v):
    assert 0 <= v < (1 << 24)
    return struct.pack("<I", v)[:3]


class Blocklist:
    def __init__(self, words_and_phrases, norm):
        self.words = set()
        self.phrases = set()
        for line in words_and_phrases:
            toks = norm(line).split()
            if len(toks) == 1:
                self.words.add(toks[0])
            elif toks:
                self.phrases.add(" ".join(toks))

    def hits(self, tokens):
        if any(t in self.words for t in tokens):
            return True
        if self.phrases:
            text = " ".join(tokens)
            return any(p in text for p in self.phrases)
        return False


def build_vocab(rows, accept, limit, norm):
    """rows: (text, count) most frequent first. Returns {word: count} of the `limit` most frequent accepted words."""
    vocab = {}
    for text, count in rows:
        w = norm(text)
        if not accept(w):
            continue
        vocab[w] = vocab.get(w, 0) + count
    top = sorted(vocab.items(), key=lambda kv: (-kv[1], kv[0]))
    if limit:
        top = top[:limit]
    return dict(top)


def collect_pairs(path, order, ids, norm, extra_block):
    """Return {tuple(ids): count} for n-grams whose words are all in the vocabulary; plus how many were dropped."""
    out = {}
    dropped = 0
    for text, count in read_counts(path):
        toks = [norm(t) for t in text.split(" ")]
        if len(toks) != order or any(t not in ids for t in toks) or extra_block(toks):
            dropped += 1
            continue
        key = tuple(ids[t] for t in toks)
        out[key] = out.get(key, 0) + count
    return out, dropped


def write_pack(path, vocab, bigrams, trigrams):
    assert all(ord(c) < 0x10000 for w in vocab for c in w), "BMP only: the Kotlin reader compares UTF-16 units"
    words = sorted(vocab)
    blob_parts, offsets, pos = [], [], 0
    for w in words:
        b = w.encode("utf-8")
        offsets.append(pos)
        blob_parts.append(b)
        pos += len(b)
    offsets.append(pos)
    blob = b"".join(blob_parts)
    counts = bytes(quant(vocab[w]) for w in words)
    total = sum(vocab.values())
    bi = b"".join(u24(a) + u24(b) + bytes([quant(c)]) for (a, b), c in sorted(bigrams.items()))
    tri = b"".join(u24(a) + u24(b) + u24(c) + bytes([quant(n)]) for (a, b, c), n in sorted(trigrams.items()))
    off_offsets = 48
    off_blob = off_offsets + 4 * len(offsets)
    off_counts = off_blob + len(blob)
    off_bi = off_counts + len(counts)
    off_tri = off_bi + len(bi)
    header = b"MSLM" + struct.pack("<HHIII", 1, 0, len(words), len(bigrams), len(trigrams))
    header += struct.pack("<IIIII", off_offsets, off_blob, off_counts, off_bi, off_tri) + struct.pack("<Q", total)
    assert len(header) == 48, len(header)
    with open(path, "wb") as f:
        f.write(header)
        f.write(struct.pack("<%dI" % len(offsets), *offsets))
        f.write(blob)
        f.write(counts)
        f.write(bi)
        f.write(tri)
    return words


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    if len(sys.argv) != 2 or not os.path.isdir(os.path.join(sys.argv[1], "data")):
        sys.exit(__doc__)
    src = sys.argv[1]
    data = os.path.join(src, "data")
    os.makedirs(OUT_DIR, exist_ok=True)
    used = []

    def inp(rel):
        p = os.path.join(data, rel + ".txt.gz")
        used.append(p)
        return p

    # ---- English
    en_block = Blocklist(read_lines(inp("en/en_offensive")), norm_en)
    en_block.words -= EN_ALLOW
    en_ok = lambda w: bool(EN_WORD.match(w)) and len(w) <= 24 and (len(w) > 1 or w in ("a", "i")) and w not in en_block.words
    en = build_vocab(read_counts(inp("en/en_full")), en_ok, WORDS_EN, norm_en)
    en_ids = {w: i for i, w in enumerate(sorted(en))}
    blocked = lambda toks: en_block.hits(toks) or any(t in BLOCK_TOM_MARY for t in toks)
    en_bi, d2 = collect_pairs(inp("en/en_bigrams"), 2, en_ids, norm_en, blocked)
    en_tri, d3 = collect_pairs(inp("en/en_trigrams"), 3, en_ids, norm_en, blocked)
    write_pack(os.path.join(OUT_DIR, "en.mslm"), en, en_bi, en_tri)
    print(f"en : {len(en)} words, {len(en_bi)} bigrams (dropped {d2}), {len(en_tri)} trigrams (dropped {d3})")

    # ---- Bangla (precomposed nukta letters, D42)
    bn_block = Blocklist(read_lines(inp("bn/bn_offensive")), norm_bn)
    bn_block.words -= BN_ALLOW
    bn_ok = lambda w: bool(BN_WORD.match(w)) and not BN_DIGITS.search(w) and w not in bn_block.words and len(w) <= 24
    bn = build_vocab(read_counts(inp("bn/bn_full_v2")), bn_ok, WORDS_BN, norm_bn)
    bn_ids = {w: i for i, w in enumerate(sorted(bn))}
    bn_blocked = lambda toks: bn_block.hits(toks)
    bn_bi, d2 = collect_pairs(inp("bn/bn_bigrams"), 2, bn_ids, norm_bn, bn_blocked)
    bn_tri, d3 = collect_pairs(inp("bn/bn_trigrams"), 3, bn_ids, norm_bn, bn_blocked)
    write_pack(os.path.join(OUT_DIR, "bn.mslm"), bn, bn_bi, bn_tri)
    print(f"bn : {len(bn)} words, {len(bn_bi)} bigrams (dropped {d2}), {len(bn_tri)} trigrams (dropped {d3})")

    # ---- Banglish words only (D41: the pair lists come from private chats and are not used)
    rom_ok = lambda w: bool(ROM_WORD.match(w)) and len(w) <= 24 and w not in en_block.words
    rom = build_vocab(read_counts(inp("bn/bn_rom_v2")), rom_ok, 0, norm_en)
    write_pack(os.path.join(OUT_DIR, "rom.mslm"), rom, {}, {})
    print(f"rom: {len(rom)} words")

    # ---- provenance
    commit = subprocess.run(["git", "-C", src, "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip()
    os.makedirs(os.path.dirname(SOURCES_MD), exist_ok=True)
    with open(SOURCES_MD, "w", encoding="utf-8") as f:
        f.write("# Sources of the packed prediction data\n\n")
        f.write("Generated by `tools/pack_wm_data.py`; do not edit by hand. Credits and licenses: [NOTICE.md](NOTICE.md).\n\n")
        f.write(f"Upstream: https://github.com/wasi-master/wmkeyboard-data at commit `{commit}`.\n\n")
        f.write("| Input file (under `data/`) | sha256 |\n|---|---|\n")
        for p in used:
            f.write(f"| `{os.path.relpath(p, data)}` | `{sha256(p)}` |\n")
        f.write("\n| Packed output (under `app/src/main/assets/predict/`) | bytes | sha256 |\n|---|---|---|\n")
        for p in sorted(glob.glob(os.path.join(OUT_DIR, "*.mslm"))):
            f.write(f"| `{os.path.basename(p)}` | {os.path.getsize(p)} | `{sha256(p)}` |\n")
    for p in sorted(glob.glob(os.path.join(OUT_DIR, "*.mslm"))):
        print(f"{os.path.basename(p):10} {os.path.getsize(p):>9} bytes")


if __name__ == "__main__":
    main()
