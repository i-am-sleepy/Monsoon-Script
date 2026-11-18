package com.monsoonscript.vault

import com.kunzisoft.keepass.database.crypto.kdf.KdfEngine
import com.kunzisoft.keepass.database.crypto.kdf.KdfFactory
import com.kunzisoft.keepass.database.crypto.kdf.Limits
import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.element.MasterCredential
import com.kunzisoft.keepass.hardware.HardwareKey
import java.io.File

/**
 * E1 Phase 0 spike (docs/PASSWORD_MANAGER.md). This is the ONLY file that touches the upstream engine API,
 * so that an upstream API change means fixing one adapter (D26, D47). Not product code: it only proves that
 * the engine builds, packages, and can create, save and reload a KDBX with both key-derivation families.
 *
 * Engine facts used (read from KeePassDX 4.5.8): Database.createData / saveData / loadData are stream based;
 * a new database defaults to AES-KDF, so Argon2id (native code, no Java fallback) is selected explicitly;
 * the KDF engines are shared singletons, so vault operations must be serialized.
 */
object VaultSpike {
    private val limits = Limits(isMemorySufficient = { _, _ -> true }, parallelism = 1L)
    private val noHardwareKey: (HardwareKey, ByteArray?) -> ByteArray = { _, _ -> ByteArray(0) }

    @Synchronized
    fun roundTrip(dir: File, kdf: KdfEngine, label: String): String {
        val credential = MasterCredential("spike-password".toCharArray())
        val file = File(dir, "spike-$label.kdbx")
        val cache = File(dir, "spike-$label.tmp")

        val created = Database()
        created.createData("Spike", "Root", null)
        created.kdfEngine = kdf
        created.saveData(cache, { file.outputStream() }, credential, noHardwareKey, limits)

        val reloaded = Database()
        file.inputStream().use { input ->
            reloaded.loadData(
                input,
                credential,
                noHardwareKey,
                readOnly = false,
                allowUserVerification = false,
                cacheDirectory = dir,
                limits = limits,
                fixDuplicateUUID = false,
                progressTaskUpdater = null
            )
        }
        check(reloaded.loaded) { "$label: database not marked as loaded" }
        check(reloaded.rootGroup != null) { "$label: no root group after loading" }
        return "$label ok: ${file.length()} bytes"
    }

    fun runAll(dir: File): List<String> = listOf(
        roundTrip(dir, KdfFactory.aesKdf, "aes-kdf"),
        roundTrip(dir, KdfFactory.argon2idKdf, "argon2id")
    )
}
