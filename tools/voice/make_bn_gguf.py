#!/usr/bin/env python3
"""G5.2 spike: turn Sanjidh090/moonshine-base-bn (HF Transformers, BanglaBERT WordPiece tokenizer) into a transcribe.cpp GGUF.

transcribe.cpp's Moonshine converter only knows the stock SentencePiece-BPE tokenizer. Decoding needs no merges, only "piece -> text", so the WordPiece
vocabulary is re-labelled in SentencePiece style: a word-initial piece `x` becomes `▁x`, a continuation piece `##x` becomes `x`. The special tokens get
the names the converter looks for (<unk>, <s>, </s>, <pad>) and the real ids of this model (CLS = start, SEP = end). The weights are not touched.

usage: make_bn_gguf.py TRANSCRIBE_CPP_DIR OUT_DIR
"""
import json
import shutil
import subprocess
import sys
from pathlib import Path

from huggingface_hub import snapshot_download

REPO = "Sanjidh090/moonshine-base-bn"
SPECIAL = {"[PAD]": "<pad>", "[UNK]": "<unk>", "[CLS]": "<s>", "[SEP]": "</s>", "[MASK]": "<mask>"}


def load_vocab(src: Path) -> dict:
    tj = src / "tokenizer.json"
    if tj.exists():
        j = json.loads(tj.read_text(encoding="utf-8"))
        model = j.get("model", {})
        print("tokenizer.json model type:", model.get("type"))
        if model.get("type") == "WordPiece":
            return {k: int(v) for k, v in model["vocab"].items()}
    vt = src / "vocab.txt"
    if vt.exists():
        lines = vt.read_text(encoding="utf-8").split("\n")
        if lines and lines[-1] == "":
            lines.pop()
        return {tok: i for i, tok in enumerate(lines)}
    raise SystemExit("no WordPiece vocabulary found (tokenizer.json / vocab.txt)")


def main() -> None:
    tc = Path(sys.argv[1]).resolve()
    out = Path(sys.argv[2]).resolve()
    out.mkdir(parents=True, exist_ok=True)
    src = Path(snapshot_download(REPO))
    print("source files:", sorted(p.name for p in src.iterdir()))
    cfg = json.loads((src / "config.json").read_text(encoding="utf-8"))
    print("config:", {k: cfg.get(k) for k in ("vocab_size", "tie_word_embeddings", "hidden_size", "model_type", "bos_token_id", "eos_token_id", "decoder_start_token_id", "pad_token_id")})
    vocab = load_vocab(src)
    vocab_size = int(cfg["vocab_size"])
    by_id = {i: t for t, i in vocab.items()}
    print("wordpiece pieces:", len(vocab), "config vocab_size:", vocab_size, "max id:", max(by_id))
    ids = {name: vocab.get(name) for name in SPECIAL}
    print("special ids:", ids)
    if ids["[CLS]"] is None or ids["[SEP]"] is None or ids["[PAD]"] is None:
        raise SystemExit("[CLS]/[SEP]/[PAD] missing in the vocabulary")

    pieces: dict = {}
    added = []
    for i in range(vocab_size):
        tok = by_id.get(i)
        if tok is None:
            name = f"<extra_{i}>"
            added.append({"id": i, "content": name, "special": True})
            pieces[name] = i
            continue
        if tok in SPECIAL:
            name = SPECIAL[tok]
            added.append({"id": i, "content": name, "special": True})
        elif tok.startswith("##"):
            name = tok[2:]
        else:
            name = "\u2581" + tok
        if name in pieces:
            raise SystemExit(f"piece collision after relabelling: {tok!r} -> {name!r} (ids {pieces[name]} and {i})")
        pieces[name] = i
    print("relabelled pieces:", len(pieces), "examples:", [by_id[i] + " -> " + [k for k, v in pieces.items() if v == i][0] for i in range(1000, 1006) if i in by_id])

    work = out / "patched"
    if work.exists():
        shutil.rmtree(work)
    work.mkdir(parents=True)
    for name in ("config.json", "preprocessor_config.json"):
        if (src / name).exists():
            shutil.copy(src / name, work / name)
    for f in src.glob("*.safetensors"):
        (work / f.name).symlink_to(f.resolve())
    (work / "tokenizer.json").write_text(
        json.dumps({"model": {"type": "BPE", "byte_fallback": True, "vocab": pieces, "merges": []}, "added_tokens": added}, ensure_ascii=False),
        encoding="utf-8",
    )
    gen = {
        "bos_token_id": ids["[CLS]"],
        "eos_token_id": ids["[SEP]"],
        "pad_token_id": ids["[PAD]"],
        "decoder_start_token_id": ids["[CLS]"],
    }
    (work / "generation_config.json").write_text(json.dumps(gen), encoding="utf-8")
    print("generation_config:", gen)

    gguf = out / "moonshine-base-bn-F32.gguf"
    cmd = [sys.executable, str(tc / "scripts" / "convert-moonshine.py"), str(work), str(gguf),
           "--repo-id", REPO, "--variant", "moonshine-base", "--language", "bn"]
    print("run:", " ".join(cmd), flush=True)
    subprocess.run(cmd, check=True)
    print("wrote", gguf, gguf.stat().st_size)


if __name__ == "__main__":
    main()
