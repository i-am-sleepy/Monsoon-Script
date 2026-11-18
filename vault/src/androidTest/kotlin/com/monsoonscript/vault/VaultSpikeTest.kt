package com.monsoonscript.vault

import androidx.test.platform.app.InstrumentationRegistry
import com.kunzisoft.encrypt.NativeLib
import com.kunzisoft.keepass.database.crypto.kdf.KdfFactory
import org.junit.Assert.assertTrue
import org.junit.Test

/** Runs on an emulator in CI (e1-feasibility workflow): proves the native crypto loads and both KDF families work. */
class VaultSpikeTest {
    private val dir get() = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir

    @Test
    fun nativeCryptoLibrariesLoad() {
        assertTrue("native crypto library did not load", NativeLib.loaded())
    }

    @Test
    fun createSaveLoadWithAesKdf() {
        assertTrue(VaultSpike.roundTrip(dir, KdfFactory.aesKdf, "aes-kdf").contains("ok"))
    }

    @Test
    fun createSaveLoadWithArgon2id() {
        assertTrue(VaultSpike.roundTrip(dir, KdfFactory.argon2idKdf, "argon2id").contains("ok"))
    }
}
