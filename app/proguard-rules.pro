# Manifest-declared classes (IME service, activities) are kept automatically.
# Add keep rules here only when a real problem shows up.

# E1 (KeePassDX engine, D26). Real problem found by the feasibility build: Joda-Time refers to joda-convert annotations
# that are not shipped (they are only annotations). These two lines are R8's own suggestion (missing_rules.txt).
-dontwarn org.joda.convert.FromString
-dontwarn org.joda.convert.ToString

# E1: the engine's native bridge. JNI finds these classes and methods by name, so they must not be renamed or removed.
-keep class com.kunzisoft.encrypt.** { *; }
