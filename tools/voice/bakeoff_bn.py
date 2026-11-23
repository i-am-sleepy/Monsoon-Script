#!/usr/bin/env python3
"""Bangla model bake-off (branch voice-bn-bakeoff, NOT part of the app): the same FLEURS Bengali clips through several candidate models with the
reference implementations (transformers), WER/CER with the same light normalisation, parameters, and time per clip (CI x86, 4 threads; only
meaningful as a RELATIVE speed comparison, a phone is several times slower). A candidate only matters for the keyboard if it is small and fast.

usage: bakeoff_bn.py WORKDIR
"""
import gc
import os
import sys
import time
from pathlib import Path

import jiwer
import numpy as np
import torch
from transformers import AutoModelForSpeechSeq2Seq, AutoTokenizer, WhisperForConditionalGeneration, WhisperProcessor

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import eval_bn as E  # noqa: E402  (load_clips, norm, hf_decode)

# (label, kind, repo, number of clips)
CANDIDATES = [
    ("moonshine-base-bn (current)", "moonshine", "Sanjidh090/moonshine-base-bn", 60),
    ("whisper-small zero-shot", "whisper", "openai/whisper-small", 60),
    ("whisper-small-bn (anuragshas)", "whisper", "anuragshas/whisper-small-bn", 60),
    ("BanglaASR (bangla-speech-processing)", "whisper", "bangla-speech-processing/BanglaASR", 60),
    ("BuzzASR/bengali (large-v3 fine-tune, reference only)", "whisper-baked", "BuzzASR/bengali", 30),
]


def params_m(model) -> float:
    return sum(p.numel() for p in model.parameters()) / 1e6


def run_whisper(repo, kind, clips):
    proc = WhisperProcessor.from_pretrained(repo)
    model = WhisperForConditionalGeneration.from_pretrained(repo, torch_dtype=torch.float32).eval()
    outs, times = [], []
    for _, data, _ in clips:
        feats = proc(data, sampling_rate=16000, return_tensors="pt").input_features
        t = time.time()
        with torch.no_grad():
            try:
                kw = {} if kind == "whisper-baked" else dict(language="bn", task="transcribe")
                ids = model.generate(feats, num_beams=1, max_new_tokens=220, **kw)
            except Exception as e:  # a fine-tune with its own forced ids
                print("   generate with language failed, retrying plain:", repr(e)[:120], flush=True)
                ids = model.generate(feats, num_beams=1, max_new_tokens=220)
        times.append(time.time() - t)
        outs.append(proc.batch_decode(ids, skip_special_tokens=True)[0])
    return outs, times, params_m(model)


def run_moonshine(repo, clips):
    tok = AutoTokenizer.from_pretrained(repo)
    model = AutoModelForSpeechSeq2Seq.from_pretrained(repo, torch_dtype=torch.float32).eval()
    outs, times = [], []
    for _, data, _ in clips:
        t = time.time()
        outs.append(E.hf_decode(model, tok, data, False))
        times.append(time.time() - t)
    return outs, times, params_m(model)


def main():
    work = Path(sys.argv[1])
    work.mkdir(parents=True, exist_ok=True)
    torch.set_num_threads(4)
    E.N = 60
    clips = E.load_clips(work)
    print("clips:", len(clips), "seconds:", round(sum(len(c[1]) for c in clips) / 16000, 1), flush=True)
    rows = []
    samples = {}
    for label, kind, repo, n in CANDIDATES:
        sub = clips[:n]
        print(f"\n=== {label}  ({repo}, {len(sub)} clips)", flush=True)
        try:
            outs, times, pm = run_moonshine(repo, sub) if kind == "moonshine" else run_whisper(repo, kind, sub)
        except Exception as e:
            print("FAILED:", repr(e)[:300], flush=True)
            rows.append((label, None, None, None, None, repr(e)[:80]))
            continue
        refs = [E.norm(c[2]) for c in sub]
        hyps = [E.norm(h) for h in outs]
        wer, cer = 100 * jiwer.wer(refs, hyps), 100 * jiwer.cer(refs, hyps)
        secs = sum(len(c[1]) for c in sub) / 16000
        rows.append((label, wer, cer, pm, sum(times) / secs, ""))
        samples[label] = outs[:4]
        print(f"WER {wer:.2f}  CER {cer:.2f}  params {pm:.0f}M  decode time {sum(times) / secs:.2f} x audio length (CI x86)", flush=True)
        gc.collect()
    print("\n=== summary (same FLEURS Bengali clips; the clip count differs for the large reference)")
    print(f"{'model':56s} {'WER':>7s} {'CER':>7s} {'params':>8s} {'time/audio':>11s}")
    for label, wer, cer, pm, rt, err in rows:
        if wer is None:
            print(f"{label:56s} failed: {err}")
        else:
            print(f"{label:56s} {wer:7.2f} {cer:7.2f} {pm:7.0f}M {rt:10.2f}x")
    print("\n=== first 4 clips")
    for i in range(4):
        print(f"--- clip {i}\nREF : {clips[i][2]}")
        for label, outs in samples.items():
            print(f"{label[:22]:22s}: {outs[i]}")


if __name__ == "__main__":
    main()
