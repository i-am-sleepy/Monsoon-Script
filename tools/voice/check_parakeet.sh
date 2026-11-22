#!/bin/bash
# G5.6 helper (runs in CI): find the Parakeet Q4_K_M GGUF on Hugging Face, print its size + SHA-256 (to pin in the app),
# and run it through transcribe-cli on an English sample to see it works and how fast it is (CI x86, not a phone).
set -uo pipefail
RES=${RES:-/tmp/parakeet-res}; mkdir -p "$RES"; exec > >(tee -a "$RES/log.txt") 2>&1
W=/tmp/pk; mkdir -p $W
python - <<'PY'
import json, urllib.request
for repo in ("handy-computer/parakeet-tdt_ctc-110m-gguf", "epapanita/parakeet-tdt_ctc-110m-gguf"):
    url = f"https://huggingface.co/api/models/{repo}/tree/main"
    try:
        items = json.load(urllib.request.urlopen(url, timeout=30))
        print("==", repo)
        for it in items:
            lfs = it.get("lfs") or {}
            print("  ", it.get("path"), it.get("size"), lfs.get("oid", ""))
    except Exception as e:
        print("==", repo, "ERROR", e)
PY
git clone -q --depth 1 https://github.com/handy-computer/transcribe.cpp.git $W/tc
echo "transcribe.cpp commit: $(git -C $W/tc rev-parse HEAD)"
cmake -S $W/tc -B $W/tc/build -DCMAKE_BUILD_TYPE=Release -DTRANSCRIBE_BUILD_TESTS=OFF -DTRANSCRIBE_USE_SYSTEM_BLAS=OFF > $W/cmake.log 2>&1 || { tail -20 $W/cmake.log; exit 1; }
cmake --build $W/tc/build -j4 --target transcribe-cli > $W/build.log 2>&1 || { tail -30 $W/build.log; exit 1; }
CLI=$(find $W/tc/build -name transcribe-cli -type f | head -1)
for repo in epapanita handy-computer; do
  f=$(python - "$repo" <<'PY'
import json, sys, urllib.request
repo = sys.argv[1] + "/parakeet-tdt_ctc-110m-gguf"
try:
    items = json.load(urllib.request.urlopen(f"https://huggingface.co/api/models/{repo}/tree/main", timeout=30))
    print(next(i["path"] for i in items if "Q4_K_M" in i["path"]))
except Exception:
    pass
PY
)
  [ -z "$f" ] && { echo "no Q4_K_M file in $repo"; continue; }
  echo "== download $repo / $f"
  curl -sL -o $W/$repo.gguf "https://huggingface.co/$repo/parakeet-tdt_ctc-110m-gguf/resolve/main/$f" || continue
  ls -l $W/$repo.gguf; sha256sum $W/$repo.gguf
done
M=$(ls $W/*.gguf | head -1)
echo "== run $M on samples"
for s in $W/tc/samples/fleurs-en.wav $W/tc/samples/death.wav; do
  [ -f "$s" ] && { echo "-- $s"; $CLI -m "$M" "$s" 2>&1 | grep -E "duration|text:|timings|realtime|name:|license|max audio" ; }
done
echo "== done =="
