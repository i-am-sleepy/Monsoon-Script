# Usage (from the repo root): python3 -I tools/gen_avro_dict_assets.py
# Splits third_party/avro-reference/data/avro-dictionary.tsv into one word list per bucket under
# app/src/main/assets/avro/dict/<bucket>.txt (bucket name lower-cased, one word per line, UTF-8) and copies the suffix table.
import io, os, collections

DATA = "third_party/avro-reference/data"
OUT = "app/src/main/assets/avro"
os.makedirs(OUT + "/dict", exist_ok=True)
buckets = collections.OrderedDict()
with io.open(DATA + "/avro-dictionary.tsv", encoding="utf-8") as f:
    for line in f.read().split("\n"):
        if not line:
            continue
        b, w = line.split("\t", 1)
        buckets.setdefault(b.lower(), []).append(w)
assert len(buckets) == 47, len(buckets)
n = 0
for b, words in buckets.items():
    io.open("%s/dict/%s.txt" % (OUT, b), "w", encoding="utf-8", newline="\n").write("\n".join(words) + "\n")
    n += len(words)
sfx = io.open(DATA + "/avro-suffixes.tsv", encoding="utf-8").read()
io.open(OUT + "/suffixes.tsv", "w", encoding="utf-8", newline="\n").write(sfx)
print("buckets:", len(buckets), "words:", n)
