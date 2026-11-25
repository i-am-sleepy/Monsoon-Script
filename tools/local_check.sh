#!/bin/bash
# Local compile check and test runner for a machine WITHOUT Gradle or Maven (written by claude-l; CI stays the real verdict).
#   tools/local_check.sh compile                          compile every app Kotlin file against a real android.jar (about 1 minute)
#   tools/local_check.sh test <TestClass> [<TestClass>]   compile the app + the named tests with a tiny JUnit stand-in and run them,
#                                                         e.g.  tools/local_check.sh test CorrectorTest ThemeSpecTest
# The first run downloads the Kotlin compiler (85 MB) and android.jar (27 MB) from github.com into $TOOLS (default /tmp/ms-tools).
# Tests that need Android classes at run time (not just to compile) do not work here: only the pure-Kotlin tests do.
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
TOOLS=${TOOLS:-/tmp/ms-tools}
KOTLIN_VER=2.0.21
mkdir -p "$TOOLS"
if [ ! -x "$TOOLS/kotlinc/bin/kotlinc" ]; then
  curl -sL -o "$TOOLS/kotlin.zip" "https://github.com/JetBrains/kotlin/releases/download/v$KOTLIN_VER/kotlin-compiler-$KOTLIN_VER.zip"
  (cd "$TOOLS" && unzip -q -o kotlin.zip && rm -f kotlin.zip)
fi
if [ ! -f "$TOOLS/ap/android-35/android.jar" ]; then
  rm -rf "$TOOLS/ap"
  git clone -q --depth 1 --filter=blob:none --no-checkout https://github.com/Sable/android-platforms.git "$TOOLS/ap"
  (cd "$TOOLS/ap" && git sparse-checkout set android-35 && git checkout -q)
fi
KC="$TOOLS/kotlinc/bin/kotlinc -no-stdlib -no-reflect -jvm-target 17 -nowarn"
STD="$TOOLS/kotlinc/lib/kotlin-stdlib.jar"
JAR="$TOOLS/ap/android-35/android.jar"
MAIN="$ROOT/app/src/main"

# R.string stand-in: every string name of values/strings.xml and every R.string.x used in the code (the real R is made by Gradle)
python3 -I - "$MAIN" "$TOOLS/R.kt" <<'PY'
import glob, re, sys
main, out = sys.argv[1], sys.argv[2]
names = set()
for f in glob.glob(main + '/res/values/*.xml'):
    names |= set(re.findall(r'<string name="([^"]+)"', open(f, encoding='utf-8').read()))
for f in glob.glob(main + '/kotlin/**/*.kt', recursive=True):
    names |= set(re.findall(r'(?<!android\.)R\.string\.([A-Za-z_0-9]+)', open(f, encoding='utf-8').read()))
body = ''.join('    const val %s = 0\n' % n for n in sorted(names))
import os
drawables = set(re.findall(r'(?<!android\.)R\.drawable\.([A-Za-z_0-9]+)', ' '.join(open(f, encoding='utf-8').read() for f in glob.glob(main + '/kotlin/**/*.kt', recursive=True))))
for f in glob.glob(main + '/res/drawable*/*'):
    drawables.add(os.path.splitext(os.path.basename(f))[0])
dbody = ''.join('    const val %s = 0\n' % n for n in sorted(drawables))
plurals = set(re.findall(r'(?<!android\.)R\.plurals\.([A-Za-z_0-9]+)', ' '.join(open(f, encoding='utf-8').read() for f in glob.glob(main + '/kotlin/**/*.kt', recursive=True))))
pbody = ''.join('    const val %s = 0\n' % n for n in sorted(plurals))
open(out, 'w').write('package com.monsoonscript.keyboard\nobject R {\n  object string {\n' + body + '  }\n  object drawable {\n' + dbody + '  }\n  object plurals {\n' + pbody + '  }\n}\n')
PY

# The password-vault screens (app/.../vault/) talk to :vault: its client side is compiled along, with stand-ins for what Gradle generates (as tools/compile_check.sh does).
VAULT_SRC="$ROOT/vault/src/main/kotlin/com/monsoonscript/vault"
VAULT_EXTRA=()
if [ -d "$VAULT_SRC" ]; then
  mkdir -p "$TOOLS/vault-stubs"
  printf 'package kotlinx.parcelize\nannotation class Parcelize\n' > "$TOOLS/vault-stubs/parcelize.kt"
  printf 'package com.monsoonscript.vault\nclass VaultService : android.app.Service() { override fun onBind(i: android.content.Intent?): android.os.IBinder? = null }\n' > "$TOOLS/vault-stubs/VaultServiceStub.kt"
  python3 -I "$ROOT/tools/aidl_stub.py" "$ROOT/vault/src/main/aidl/com/monsoonscript/vault/IVault.aidl" "$TOOLS/vault-stubs/IVault.java"
  VAULT_EXTRA=("$VAULT_SRC/VaultModel.kt" "$VAULT_SRC/VaultClient.kt" "$TOOLS/vault-stubs/parcelize.kt" "$TOOLS/vault-stubs/VaultServiceStub.kt" "$TOOLS/vault-stubs/IVault.java")
fi

compile_main() { # $1 = output dir, the rest = extra source files
  local OUT=$1; shift
  local LOG rc=0
  LOG=$(mktemp)
  rm -rf "$OUT"
  local SRC EXTRA=("${VAULT_EXTRA[@]}")
  if [ -n "$NO_VAULT" ]; then # running tests: the vault screens (and what opens them) are left out, their Parcelize classes cannot be built here
    SRC=$(cd "$MAIN/kotlin" && find . -name '*.kt' -not -path './vault/*' | xargs grep -L 'keyboard\.vault\|monsoonscript\.vault\|Vault[A-Za-z]*Activity')
    EXTRA=()
  else
    SRC=$(cd "$MAIN/kotlin" && find . -name '*.kt')
  fi
  (cd "$MAIN/kotlin" && $KC -cp "$STD:$JAR" -d "$OUT" $SRC "$TOOLS/R.kt" "${EXTRA[@]}" "$@") >"$LOG" 2>&1 || rc=$?
  grep -v '^Picked up' "$LOG" | grep -v 'describeContents\|writeToParcel\|is not abstract and does not implement' || true
  if grep ': error:' "$LOG" | grep -qv 'describeContents\|writeToParcel\|is not abstract and does not implement'; then rc=1; else rc=0; fi
  rm -f "$LOG"
  return $rc
}

case "$1" in
  compile)
    rc=0; compile_main "$TOOLS/out-main" || rc=$?
    echo "compile exit: $rc"; exit $rc ;;
  test)
    shift
    [ $# -ge 1 ] || { echo "usage: $0 test <TestClass>..."; exit 2; }
    TESTSRC="$ROOT/app/src/test/kotlin"
    FILES=("$ROOT/tools/junit-stub/Stub.kt" "$ROOT/tools/junit-stub/Run.kt")
    for h in $(find "$TESTSRC" -name 'Packs.kt'); do FILES+=("$h"); done # the helper that maps the real word packs
    CLASSES=()
    for t in "$@"; do
      f=$(find "$TESTSRC" -name "${t##*.}.kt" | head -1)
      [ -n "$f" ] || { echo "no test file for $t"; exit 2; }
      FILES+=("$f")
      CLASSES+=("$(grep -m1 '^package ' "$f" | awk '{print $2}').${t##*.}")
    done
    NO_VAULT=1 compile_main "$TOOLS/out-test" "${FILES[@]}" || { echo "compile failed"; exit 1; }
    cd "$ROOT/app" && java -cp "$TOOLS/out-test:$STD" RunKt "${CLASSES[@]}" 2>&1 | grep -v '^Picked up' ;;
  *)
    sed -n '2,8p' "$0"; exit 2 ;;
esac
