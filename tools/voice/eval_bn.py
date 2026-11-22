#!/usr/bin/env python3
"""G5.2 spike: does the converted Bangla model decode like the authors' reference?  Same clips through
  (a) transformers with the authors' recipe (beam 5, no-repeat 3, repetition penalty 1.2),
  (b) transformers greedy (what transcribe.cpp does),
  (c) each GGUF through transcribe-cli.
WER/CER use a light normalisation (punctuation removed, spaces collapsed).  Times are from a CI x86 machine, NOT a phone.

usage: eval_bn.py TRANSCRIBE_CLI WORKDIR GGUF [GGUF ...]
"""
import io
import os
import re
import subprocess
import sys
import time
from pathlib import Path

import jiwer
import numpy as np
import soundfile as sf
import torch
from datasets import Audio, get_dataset_split_names, load_dataset
from scipy.signal import resample_poly
from transformers import AutoModelForSpeechSeq2Seq, AutoTokenizer

REPO = "Sanjidh090/moonshine-base-bn"
DATA = "Sanjidh090/Lipi-Ghor-bn-882-SSTT"
N = int(os.environ.get("N", "60"))
MAX_SEC = 25.0


def norm(s: str) -> str:
    s = re.sub(r"[\u0964,.?!;:\"'()\-\u2013\u2014\u2026]", " ", s or "")
    return " ".join(s.split())


def load_clips(work: Path):
    print("dataset splits:", get_dataset_split_names(DATA), flush=True)
    ds = load_dataset(DATA, split="test", streaming=True)
    print("features:", ds.features, flush=True)
    audio_col = next(k for k, v in ds.features.items() if isinstance(v, Audio))
    text_col = next((k for k in ("sentence", "text", "transcription", "transcript", "normalized_text") if k in ds.features), None)
    if text_col is None:
        text_col = next(k for k, v in ds.features.items() if getattr(v, "dtype", None) == "string")
    print("columns used: audio =", audio_col, "text =", text_col, flush=True)
    ds = ds.cast_column(audio_col, Audio(decode=False))
    clips = []
    for ex in ds:
        a = ex[audio_col]
        data, sr = sf.read(io.BytesIO(a["bytes"]), dtype="float32")
        if data.ndim > 1:
            data = data.mean(axis=1)
        if sr != 16000:
            g = np.gcd(sr, 16000)
            data = resample_poly(data, 16000 // g, sr // g).astype("float32")
        if len(data) / 16000 > MAX_SEC or len(data) < 16000 * 0.8:
            continue
        i = len(clips)
        p = work / f"clip{i:03d}.wav"
        sf.write(p, data, 16000)
        clips.append((p, data, ex[text_col]))
        if len(clips) >= N:
            break
    return clips


def hf_decode(model, tok, data, beams):
    remainder = len(data) % 320
    if remainder:
        data = np.concatenate([data, np.zeros(320 - remainder, dtype=np.float32)])
    x = torch.tensor(data).unsqueeze(0)
    kw = dict(num_beams=5, no_repeat_ngram_size=3, repetition_penalty=1.2) if beams else dict(num_beams=1)
    with torch.no_grad():
        ids = model.generate(x, max_new_tokens=300, decoder_start_token_id=tok.cls_token_id or 2,
                             pad_token_id=tok.pad_token_id or 0, eos_token_id=tok.sep_token_id or 3, **kw)
    return tok.decode(ids[0].tolist(), skip_special_tokens=True)


def cli_decode(cli, gguf, wav):
    t = time.time()
    r = subprocess.run([cli, "-m", gguf, str(wav)], capture_output=True, text=True)
    dt = time.time() - t
    return r.stdout.strip(), r.stderr.strip(), r.returncode, dt


def score(name, refs, hyps):
    r = [norm(x) for x in refs]
    h = [norm(x) for x in hyps]
    print(f"{name:34s} WER {100 * jiwer.wer(r, h):6.2f}   CER {100 * jiwer.cer(r, h):6.2f}", flush=True)


def main():
    cli, work, ggufs = sys.argv[1], Path(sys.argv[2]), sys.argv[3:]
    work.mkdir(parents=True, exist_ok=True)
    clips = load_clips(work)
    print("clips:", len(clips), "total seconds:", round(sum(len(c[1]) for c in clips) / 16000, 1), flush=True)
    refs = [c[2] for c in clips]
    tok = AutoTokenizer.from_pretrained(REPO)
    model = AutoModelForSpeechSeq2Seq.from_pretrained(REPO, torch_dtype=torch.float32).eval()
    torch.set_num_threads(4)
    hyp = {"hf-beam5 (authors' recipe)": [], "hf-greedy": []}
    for _, d, _ in clips:
        hyp["hf-beam5 (authors' recipe)"].append(hf_decode(model, tok, d, True))
        hyp["hf-greedy"].append(hf_decode(model, tok, d, False))
    times = {}
    for g in ggufs:
        key = "gguf " + Path(g).name.replace("moonshine-base-bn-", "")
        hyp[key], times[key] = [], []
        for n, (p, _, _) in enumerate(clips):
            out, err, rc, dt = cli_decode(cli, g, p)
            if n < 2:
                print(f"[{key}] clip {n}: rc={rc}\n  stdout: {out[:300]!r}\n  stderr tail: {err[-400:]!r}", flush=True)
            hyp[key].append(out)
            times[key].append(dt)
    print("\n=== results (light normalisation) ===")
    for k, v in hyp.items():
        score(k, refs, v)
    for k, v in times.items():
        print(f"{k}: mean {np.mean(v):.2f} s per clip (whole process incl. model load; CI x86, not a phone)")
    print("\n=== first 8 clips: reference / authors' recipe / hf greedy / each gguf ===")
    for i in range(min(8, len(clips))):
        print(f"--- clip {i}\nREF : {refs[i]}")
        for k, v in hyp.items():
            print(f"{k[:12]:12s}: {v[i]}")


if __name__ == "__main__":
    main()
