#!/usr/bin/env python3
"""Why does the Bangla model sometimes write "music" (মিউজিক) as the first word? Same FLEURS clips, four ways of starting the audio:
A as recorded; B leading silence cropped tight (what a speech detector does); C = B + 0.4 s of silence in front; D = A + 0.4 s of silence in front.
Counts how often the text STARTS with that word (FLEURS sentences never do) and the WER. Branch voice-bn-bakeoff only.   usage: lead_bn.py WORKDIR
"""
import os
import sys
from pathlib import Path

import jiwer
import numpy as np
import torch
from transformers import AutoModelForSpeechSeq2Seq, AutoTokenizer

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import eval_bn as E  # noqa: E402

MUSIC = "\u09AE\u09BF\u0989\u099C\u09BF\u0995"  # মিউজিক


def tight(x: np.ndarray, keep: float = 0.05) -> np.ndarray:
    """Cut the silence before the first frame that is clearly louder than the quietest part."""
    fr = 320
    n = len(x) // fr
    if n < 4:
        return x
    e = np.sqrt((x[: n * fr].reshape(n, fr) ** 2).mean(axis=1) + 1e-12)
    thr = max(np.percentile(e, 10) * 4.0, 10 ** (-45 / 20))
    idx = np.nonzero(e > thr)[0]
    if len(idx) == 0:
        return x
    start = max(0, idx[0] * fr - int(keep * 16000))
    return x[start:]


def pad(x: np.ndarray, sec: float) -> np.ndarray:
    return np.concatenate([np.zeros(int(sec * 16000), dtype=np.float32), x])


def main():
    work = Path(sys.argv[1])
    work.mkdir(parents=True, exist_ok=True)
    torch.set_num_threads(4)
    E.N = 80
    clips = E.load_clips(work)
    tok = AutoTokenizer.from_pretrained(E.REPO)
    model = AutoModelForSpeechSeq2Seq.from_pretrained(E.REPO, torch_dtype=torch.float32).eval()
    variants = {
        "A as recorded": lambda x: x,
        "B tight crop": lambda x: tight(x),
        "C tight + 0.4 s silence": lambda x: pad(tight(x), 0.4),
        "D recorded + 0.4 s silence": lambda x: pad(x, 0.4),
    }
    refs = [E.norm(c[2]) for c in clips]
    print(f"clips: {len(clips)}")
    for name, f in variants.items():
        outs = [E.hf_decode(model, tok, f(c[1]).astype(np.float32), False) for c in clips]
        starts = sum(1 for o in outs if E.norm(o).startswith(MUSIC))
        anywhere = sum(1 for o in outs if MUSIC in o)
        wer = 100 * jiwer.wer(refs, [E.norm(o) for o in outs])
        print(f"{name:28s} starts with music: {starts:3d}   contains music: {anywhere:3d}   WER {wer:6.2f}", flush=True)


if __name__ == "__main__":
    main()
