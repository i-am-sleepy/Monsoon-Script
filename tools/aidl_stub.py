# Writes a stand-in for the class the AIDL compiler generates from IVault.aidl (only the method list), so Kotlin code can be compile-checked offline.
# Usage: python3 -I tools/aidl_stub.py <IVault.aidl> <out IVault.java>   (used by tools/compile_check.sh and tools/vault_compile_check.sh)
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
