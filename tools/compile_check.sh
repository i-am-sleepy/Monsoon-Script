#!/bin/bash
# Compile-check the app's Kotlin against a real android.jar BEFORE you push: catches type errors, wrong Android API signatures and missing imports,
# which otherwise only show up in CI (several minutes later). Not covered: resources, the manifest, R8, lint, tests (use the Gradle test build for those).
# Usage (repo root): tools/compile_check.sh      -> prints compiler errors, exit 0 = compiles.
# Needs: java, python3, git, and a Gradle install whose lib/ has the embedded Kotlin compiler (the sandbox has /opt/gradle/lib; override with GRADLE_LIB=...).
# The android.jar comes from github.com/Sable/android-platforms (API 35 stubs), fetched once into $TMPDIR/monsoon-compile-check.
# The vault screens (app/.../vault/) talk to :vault, so its data classes and VaultClient are compiled along (the AIDL-generated IVault is a stand-in, the Parcelize
# compiler plugin is not run, so its "describeContents" complaints are ignored). Libraries from Gradle that the app uses (androidx.autofill, E1.21) are stood in
# by signature-only Java files in tools/stubs/ (compile check only, never part of the build). To check :vault itself use tools/vault_compile_check.sh.
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
# The generated R class is replaced by a stub with every resource name the code uses (R.string.*, R.plurals.*, R.drawable.* ...).
python3 -I - "$ROOT/app/src/main" "$WORK/R.kt" <<'PY'
import glob, re, sys
main, out = sys.argv[1], sys.argv[2]
used = {}  # resource type -> names
for f in glob.glob(main + '/res/values/*.xml'):
    text = open(f, encoding='utf-8').read()
    for kind in ('string', 'plurals'):
        used.setdefault(kind, set()).update(re.findall(r'<%s name="([^"]+)"' % kind, text))
for f in glob.glob(main + '/kotlin/**/*.kt', recursive=True):
    for kind, name in re.findall(r'\bR\.([a-z]+)\.([A-Za-z_0-9]+)', open(f, encoding='utf-8').read()):
        used.setdefault(kind, set()).add(name)
body = ''
for kind in sorted(used):
    body += '  object %s {\n' % kind + ''.join('    const val %s = 0\n' % n for n in sorted(used[kind])) + '  }\n'
open(out, 'w').write('package com.monsoonscript.keyboard\nobject R {\n' + body + '}\n')
PY
j() { ls "$G"/$1 | head -1; }
CP=$(j 'kotlin-compiler-embeddable-*.jar'):$(j 'kotlin-stdlib-[0-9]*.jar'):$(j 'kotlin-reflect-*.jar'):$(j 'kotlin-script-runtime-*.jar'):$(j 'trove4j-*.jar'):$(j 'kotlinx-coroutines-core-jvm-*.jar'):$(j 'annotations-*.jar')
# The :vault client side (data classes + VaultClient), with stand-ins for what Gradle generates.
mkdir -p "$WORK/stubs"
printf 'package kotlinx.parcelize\nannotation class Parcelize\n' > "$WORK/stubs/parcelize.kt"
printf 'package com.monsoonscript.vault\nclass VaultService : android.app.Service() { override fun onBind(i: android.content.Intent?): android.os.IBinder? = null }\n' > "$WORK/stubs/VaultServiceStub.kt"
python3 -I "$ROOT/tools/aidl_stub.py" "$ROOT/vault/src/main/aidl/com/monsoonscript/vault/IVault.aidl" "$WORK/stubs/IVault.java"
VAULT=$ROOT/vault/src/main/kotlin/com/monsoonscript/vault
rm -rf "$WORK/out"
cd "$ROOT/app/src/main/kotlin"
set +e
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 \
  -cp "$(j 'kotlin-stdlib-[0-9]*.jar'):$JAR" -d "$WORK/out" $(find . -name '*.kt') "$WORK/R.kt" \
  "$VAULT/VaultModel.kt" "$VAULT/VaultClient.kt" "$WORK/stubs/parcelize.kt" "$WORK/stubs/VaultServiceStub.kt" "$WORK/stubs/IVault.java" $(find "$ROOT/tools/stubs" -name '*.java') > "$WORK/all.log" 2>&1
python3 -I - "$WORK/all.log" <<'PY'
import re, sys
noise = ('describeContents', 'writeToParcel', 'is not abstract and does not implement')
blocks, cur = [], None
for line in open(sys.argv[1], encoding='utf-8', errors='replace').read().splitlines():
    if line.startswith('Picked up'):
        continue
    if re.match(r'^\S.*:\d+:\d+: (error|warning|info):', line) or line.startswith('error:'):
        cur = [line]
        blocks.append(cur)
    elif cur is not None:
        cur.append(line)
for b in blocks:
    if ': error:' in b[0] and not any(n in b[0] for n in noise):
        print('\n'.join(b))
PY
# Only real errors decide the exit code (the Parcelize noise above is not one).
if grep ': error:' "$WORK/all.log" | grep -qv 'describeContents\|writeToParcel\|is not abstract and does not implement'; then exit 1; fi
exit 0
