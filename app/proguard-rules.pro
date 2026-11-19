# Manifest-declared classes (IME service, activities) are kept automatically.
# Add keep rules here only when a real problem shows up.

# E1 (KeePassDX engine, D26). Real problem found by the feasibility build: Joda-Time refers to joda-convert annotations
# that are not shipped (they are only annotations). These two lines are R8's own suggestion (missing_rules.txt).
-dontwarn org.joda.convert.FromString
-dontwarn org.joda.convert.ToString

# E1: the engine's native bridge. JNI finds these classes and methods by name, so they must not be renamed or removed.
-keep class com.kunzisoft.encrypt.** { *; }

# E1: BouncyCastle registers its algorithms by CLASS-NAME STRINGS at run time (BouncyCastleProvider and the JCA look them
# up reflectively), which the shrinker cannot see. Real failure found by the shrunk-build smoke test on the emulator:
# "Blowfish KeyGenerator not available" (the engine encrypts its attachment cache with Blowfish, see LoadedKey.kt).
# Keep only the algorithm families the engine uses (Blowfish, Twofish, ChaCha) plus the provider machinery they need.
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
-keep class org.bouncycastle.jcajce.provider.config.** { *; }
-keep class org.bouncycastle.jcajce.provider.util.** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.util.** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.Blowfish** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.Twofish** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.ChaCha** { *; }

# E1 Phase 0 diagnosis: the shrunk build failed its smoke test with an exception whose name was scrambled ("l.c").
# Keep real names so the failure can be read. (Open-source app: name scrambling gives no secrecy; to be decided at merge.)
-dontobfuscate
