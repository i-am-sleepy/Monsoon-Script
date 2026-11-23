package org.junit

// A tiny stand-in for JUnit 4 (annotation + the few asserts our tests use) so tools/local_check.sh can run the pure-Kotlin tests on a machine without Gradle.
// CI uses the real JUnit. If a test needs another assert, add it here.
@Retention(AnnotationRetention.RUNTIME) annotation class Test

object Assert {
    @JvmStatic fun assertTrue(c: Boolean) { if (!c) throw AssertionError("expected true") }
    @JvmStatic fun assertTrue(m: String, c: Boolean) { if (!c) throw AssertionError(m) }
    @JvmStatic fun assertFalse(c: Boolean) { if (c) throw AssertionError("expected false") }
    @JvmStatic fun assertFalse(m: String, c: Boolean) { if (c) throw AssertionError(m) }
    @JvmStatic fun assertEquals(e: Any?, a: Any?) { if (e != a) throw AssertionError("expected <$e> but was <$a>") }
    @JvmStatic fun assertEquals(m: String, e: Any?, a: Any?) { if (e != a) throw AssertionError("$m: expected <$e> but was <$a>") }
    @JvmStatic fun assertEquals(e: Double, a: Double, d: Double) { if (Math.abs(e - a) > d) throw AssertionError("expected <$e> but was <$a>") }
    @JvmStatic fun assertEquals(e: Float, a: Float, d: Float) { if (Math.abs(e - a) > d) throw AssertionError("expected <$e> but was <$a>") }
    @JvmStatic fun assertNull(a: Any?) { if (a != null) throw AssertionError("expected null but was <$a>") }
    @JvmStatic fun assertNull(m: String, a: Any?) { if (a != null) throw AssertionError("$m: expected null but was <$a>") }
    @JvmStatic fun assertNotNull(a: Any?) { if (a == null) throw AssertionError("expected not null") }
    @JvmStatic fun assertNotNull(m: String, a: Any?) { if (a == null) throw AssertionError(m) }
}
