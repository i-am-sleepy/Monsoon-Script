#!/bin/bash
# G5.2 spike driver (runs in CI, see .github/workflows/voice-spike.yml). Everything printed goes to $RES/log.txt.
set -uo pipefail
RES=${RES:-/tmp/voice-res}; mkdir -p "$RES"; exec > >(tee -a "$RES/log.txt") 2>&1
TC=/tmp/transcribe.cpp; W=/tmp/voice-work; mkdir -p $W
git clone -q https://github.com/handy-computer/transcribe.cpp.git $TC || exit 1
echo "transcribe.cpp commit: $(git -C $TC rev-parse HEAD)"
python -m pip install -q --upgrade pip
python -m pip install -q torch --index-url https://download.pytorch.org/whl/cpu || exit 1
python -m pip install -q numpy gguf safetensors transformers huggingface_hub datasets soundfile scipy jiwer || exit 1
echo "== build transcribe.cpp (CPU only, no BLAS, like a phone) =="
cmake -S $TC -B $TC/build -DCMAKE_BUILD_TYPE=Release -DTRANSCRIBE_BUILD_TOOLS=ON -DTRANSCRIBE_BUILD_TESTS=OFF -DTRANSCRIBE_USE_SYSTEM_BLAS=OFF > $W/cmake.log 2>&1 || { tail -30 $W/cmake.log; exit 1; }
cmake --build $TC/build -j4 --target transcribe-cli transcribe-quantize > $W/build.log 2>&1 || { tail -40 $W/build.log; exit 1; }
CLI=$(find $TC/build -name transcribe-cli -type f | head -1); QZ=$(find $TC/build -name transcribe-quantize -type f | head -1)
echo "cli: $CLI  quantize: $QZ"
echo "== convert =="
python tools/voice/make_bn_gguf.py $TC $W || exit 1
F32=$W/moonshine-base-bn-F32.gguf
echo "== quantize =="
for q in Q8_0 Q4_K_M; do $QZ $F32 $W/moonshine-base-bn-$q.gguf --quant $q 2>&1 | tail -5; done
ls -l $W/*.gguf
echo "== evaluate =="
GG=$(ls $W/moonshine-base-bn-*.gguf)
python tools/voice/eval_bn.py "$CLI" $W/clips $GG
mkdir -p "$RES/gguf"; cp $W/moonshine-base-bn-Q8_0.gguf "$RES/gguf/" 2>/dev/null || true
echo "== done =="
