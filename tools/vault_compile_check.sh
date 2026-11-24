#!/bin/bash
# Compile-check the :vault module (and its emulator tests) against the REAL KeePassDX engine sources BEFORE you push: catches wrong engine
# API calls, type errors and missing imports in about 30 s instead of waiting for the engine CI. Sibling of tools/compile_check.sh (which checks the app).
# Usage (repo root): tools/vault_compile_check.sh      -> prints compiler errors in vault/ files, exit 0 = none.
# Needs: java, python3, git, a Gradle install whose lib/ has the embedded Kotlin compiler (override with GRADLE_LIB=...), and the submodule:
#   git submodule update --init --depth 1 third_party/keepassdx
# How it works: the engine's own Kotlin/Java sources are compiled together with vault/src/main and the vault emulator tests. The engine's
# third-party jars (joda-time, commons-io, BouncyCastle) are not available offline, so errors INSIDE third_party/ are expected and ignored.
# Also ignored: "describeContents" errors (the Parcelize compiler plugin is not run) and the AIDL-generated IVault class is replaced by a
# stand-in generated from IVault.aidl. JUnit / InstrumentationRegistry are replaced by tiny stubs, so only the calls the tests use are known.
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
WORK=${TMPDIR:-/tmp}/monsoon-vault-check
G=${GRADLE_LIB:-/opt/gradle/lib}
KDX=$ROOT/third_party/keepassdx
[ -d "$KDX/database/src/main" ] || { echo "Submodule missing: git submodule update --init --depth 1 third_party/keepassdx"; exit 2; }
mkdir -p "$WORK/stubs"
JAR=${TMPDIR:-/tmp}/monsoon-compile-check/android-platforms/android-35/android.jar
if [ ! -f "$JAR" ]; then
  JAR=$WORK/android-platforms/android-35/android.jar
  if [ ! -f "$JAR" ]; then
    rm -rf "$WORK/android-platforms"
    git clone -q --depth 1 --filter=blob:none --no-checkout https://github.com/Sable/android-platforms.git "$WORK/android-platforms"
    git -C "$WORK/android-platforms" sparse-checkout set android-35
    git -C "$WORK/android-platforms" checkout -q
  fi
fi

cat > "$WORK/stubs/parcelize.kt" <<'EOF'
package kotlinx.parcelize
annotation class Parcelize
EOF
cat > "$WORK/stubs/junit.kt" <<'EOF'
package org.junit
annotation class Test
annotation class Before
annotation class After
object Assert {
    fun assertEquals(a: Any?, b: Any?) {}
    fun assertEquals(msg: String?, a: Any?, b: Any?) {}
    fun assertTrue(b: Boolean) {}
    fun assertTrue(msg: String?, b: Boolean) {}
    fun assertFalse(b: Boolean) {}
    fun assertFalse(msg: String?, b: Boolean) {}
    fun assertNull(a: Any?) {}
    fun assertNull(msg: String?, a: Any?) {}
    fun fail(msg: String?) {}
}
EOF
cat > "$WORK/stubs/instr.kt" <<'EOF'
package androidx.test.platform.app
class Instr { val targetContext: android.content.Context get() = TODO() }
object InstrumentationRegistry { fun getInstrumentation(): Instr = Instr() }
EOF
# Stand-in for the class the AIDL compiler generates, built from the method list in IVault.aidl.
python3 -I - "$ROOT/vault/src/main/aidl/com/monsoonscript/vault/IVault.aidl" "$WORK/stubs/IVault.java" <<'PY'
import re, sys
src = re.sub(r'/\*.*?\*/', '', open(sys.argv[1], encoding='utf-8').read(), flags=re.S)
body = src[src.index('interface IVault'):]
out = ['package com.monsoonscript.vault;', 'public interface IVault extends android.os.IInterface {']
for m in re.finditer(r'^\s*([\w<>\[\]\.]+)\s+(\w+)\(([^)]*)\);', body, flags=re.M):
    ret, name, args = m.groups()
    conv = lambda t: t.replace('Uri', 'android.net.Uri').replace('List<', 'java.util.List<')
    params = ', '.join(conv(re.sub(r'^in\s+', '', a.strip())) for a in args.split(',') if a.strip())
    out.append('  %s %s(%s) throws android.os.RemoteException;' % (conv(ret), name, params))
out += ['  abstract class Stub extends android.os.Binder implements IVault {',
        '    public static IVault asInterface(android.os.IBinder obj) { return null; }',
        '    public android.os.IBinder asBinder() { return this; }', '  }', '}']
open(sys.argv[2], 'w').write('\n'.join(out) + '\n')
PY

j() { ls "$G"/$1 | head -1; }
CP=$(j 'kotlin-compiler-embeddable-*.jar'):$(j 'kotlin-stdlib-[0-9]*.jar'):$(j 'kotlin-reflect-*.jar'):$(j 'kotlin-script-runtime-*.jar'):$(j 'trove4j-*.jar'):$(j 'kotlinx-coroutines-core-jvm-*.jar'):$(j 'annotations-*.jar')
# Every vault test that only uses the stubbed JUnit calls; tests with other needs (fixtures, other asserts) are left out on purpose.
TESTS="VaultSessionTest VaultServiceTest"
SRC="$(find "$KDX/database/src/main" "$KDX/crypto/src/main" \( -name '*.kt' -o -name '*.java' \)) $(find "$ROOT/vault/src/main" -name '*.kt') $WORK/stubs/parcelize.kt $WORK/stubs/junit.kt $WORK/stubs/instr.kt $WORK/stubs/IVault.java"
for t in $TESTS; do SRC="$SRC $ROOT/vault/src/androidTest/kotlin/com/monsoonscript/vault/$t.kt"; done
rm -rf "$WORK/out"
cd "$WORK"
set +e
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 17 -Xskip-metadata-version-check \
  -cp "$(j 'kotlin-stdlib-[0-9]*.jar'):$JAR" -d "$WORK/out" $SRC > "$WORK/all.log" 2>&1
OWN=$(grep -E "$ROOT/vault/.*: error:" "$WORK/all.log" | grep -v describeContents | sed "s|$ROOT/||")
if [ -n "$OWN" ]; then echo "$OWN"; exit 1; fi
echo "vault/ compiles against the engine (engine-internal errors ignored: $(grep -c ': error:' "$WORK/all.log") of which none in vault/)"
