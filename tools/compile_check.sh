#!/bin/bash
# Compile-check the app's Kotlin against a real android.jar BEFORE you push: catches type errors, wrong Android API signatures and missing imports,
# which otherwise only show up in CI (several minutes later). Not covered: resources, the manifest, R8, lint, tests (use the Gradle test build for those).
# Usage (repo root): tools/compile_check.sh      -> prints compiler errors, exit 0 = compiles.
# Needs: java, python3, git, and a Gradle install whose lib/ has the embedded Kotlin compiler (the sandbox has /opt/gradle/lib; override with GRADLE_LIB=...).
# The android.jar comes from github.com/Sable/android-platforms (API 35 stubs), fetched once into $TMPDIR/monsoon-compile-check.
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK=${TMPDIR:-/tmp}/monsoon-compile-check
G=${GRADLE_LIB:-/opt/gradle/lib}
mkdir -p "$WORK"
JAR=$WORK/android-platforms/android-35/android.jar
if [ ! -f "$JAR" ]; then
  rm -rf "$WORK/android-platforms"
  git clone -q --depth 1 --filter=blob:none --no-checkout https://github.com/Sable/android-platforms.git "$WORK/android-platforms"
  git -C "$WORK/android-platforms" sparse-checkout set android-35
  git -C "$WORK/android-platforms" checkout -q
fi
# The generated R class is replaced by a stub with every string name (the code only uses R.string.*).
python3 -I - "$ROOT/app/src/main" "$WORK/R.kt" <<'PY'
import glob, re, sys
main, out = sys.argv[1], sys.argv[2]
names = set()
for f in glob.glob(main + '/res/values/*.xml'):
    names |= set(re.findall(r'<string name="([^"]+)"', open(f, encoding='utf-8').read()))
for f in glob.glob(main + '/kotlin/**/*.kt', recursive=True):
    names |= set(re.findall(r'R\.string\.([A-Za-z_0-9]+)', open(f, encoding='utf-8').read()))
body = ''.join('    const val %s = 0\n' % n for n in sorted(names))
open(out, 'w').write('package com.monsoonscript.keyboard\nobject R {\n  object string {\n' + body + '  }\n}\n')
PY
j() { ls "$G"/$1 | head -1; }
CP=$(j 'kotlin-compiler-embeddable-*.jar'):$(j 'kotlin-stdlib-[0-9]*.jar'):$(j 'kotlin-reflect-*.jar'):$(j 'kotlin-script-runtime-*.jar'):$(j 'trove4j-*.jar'):$(j 'kotlinx-coroutines-core-jvm-*.jar'):$(j 'annotations-*.jar')
rm -rf "$WORK/out"
cd "$ROOT/app/src/main/kotlin"
set +e
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 \
  -cp "$(j 'kotlin-stdlib-[0-9]*.jar'):$JAR" -d "$WORK/out" $(find . -name '*.kt') "$WORK/R.kt" 2>&1 | { grep -v '^Picked up' || true; }
exit ${PIPESTATUS[0]}
