# E1: rules the KeePassDX engine needs when an app shrinks its code with R8. Every one of them fixed a REAL failure found by
# the Phase 0 smoke test on an emulator (docs/PASSWORD_MANAGER_PHASE0.md). They apply automatically to any app that depends on :vault.

# Joda-Time refers to joda-convert annotations that are not shipped (only annotations). R8's own suggestion.
-dontwarn org.joda.convert.FromString
-dontwarn org.joda.convert.ToString

# The engine's native bridge: JNI finds these classes and methods by name, so they must not be renamed or removed.
-keep class com.kunzisoft.encrypt.** { *; }

# BouncyCastle registers its algorithms by CLASS-NAME STRINGS at run time (the provider and the JCA look them up reflectively),
# which the shrinker cannot see. Without these rules the shrunk build fails with "Blowfish KeyGenerator not available" (the engine
# encrypts its attachment cache with Blowfish, see LoadedKey.kt). Only the families the engine uses are kept (Blowfish, Twofish, ChaCha).
-keep class org.bouncycastle.jce.provider.BouncyCastleProvider { *; }
-keep class org.bouncycastle.jcajce.provider.config.** { *; }
-keep class org.bouncycastle.jcajce.provider.util.** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.util.** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.Blowfish** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.Twofish** { *; }
-keep class org.bouncycastle.jcajce.provider.symmetric.ChaCha** { *; }
