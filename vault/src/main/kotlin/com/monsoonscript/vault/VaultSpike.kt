package com.monsoonscript.vault

import com.kunzisoft.keepass.database.crypto.EncryptionAlgorithm
import com.kunzisoft.keepass.database.crypto.kdf.KdfEngine
import com.kunzisoft.keepass.database.crypto.kdf.KdfFactory
import com.kunzisoft.keepass.database.crypto.kdf.Limits
import com.kunzisoft.keepass.database.element.Database
import com.kunzisoft.keepass.database.element.MasterCredential
import com.kunzisoft.keepass.hardware.HardwareKey
import java.io.File

/**
 * E1 Phase 0 spike (docs/PASSWORD_MANAGER.md). This is the ONLY file that touches the upstream engine API for
 * opening, saving and merging, so that an upstream API change means fixing one adapter (D26, D47). Not product code:
 * it proves that the engine builds, packages, and can create, save, reload and merge a KDBX.
 *
 * Engine facts used (read from KeePassDX 4.5.8): Database.createData / saveData / loadData / mergeData are stream based;
 * a new database defaults to AES-KDF, so Argon2id (native code, no Java fallback) is selected explicitly;
 * the KDF engines are shared singletons, so every operation here is @Synchronized.
 */
object VaultSpike {
    private val limits = Limits(isMemorySufficient = { _, _ -> true }, parallelism = 1L)
    private val noHardwareKey: (HardwareKey, ByteArray?) -> ByteArray = { _, _ -> ByteArray(0) }

    fun newDatabase(
        kdf: KdfEngine,
        name: String = "Spike",
        cipher: EncryptionAlgorithm = EncryptionAlgorithm.AESRijndael
    ): Database {
        val db = Database()
        db.createData(name, "Root", null)
        db.kdfEngine = kdf
        db.encryptionAlgorithm = cipher
        return db
    }

    @Synchronized
    fun save(db: Database, file: File, password: String, dir: File) {
        val cache = File(dir, file.name + ".tmp")
        db.saveData(cache, { file.outputStream() }, MasterCredential(password.toCharArray()), noHardwareKey, limits)
    }

    @Synchronized
    fun open(file: File, password: String, dir: File): Database {
        val db = Database()
        file.inputStream().use { input ->
            db.loadData(
                input,
                MasterCredential(password.toCharArray()),
                noHardwareKey,
                readOnly = false,
                allowUserVerification = false,
                cacheDirectory = dir,
                limits = limits,
                fixDuplicateUUID = false,
                progressTaskUpdater = null
            )
        }
        return db
    }

    @Synchronized
    fun mergeFrom(db: Database, other: File, password: String) {
        other.inputStream().use { input ->
            db.mergeData(input, MasterCredential(password.toCharArray()), noHardwareKey, limits, null)
        }
    }

    /** Creates a KDBX in [dir], saves it, loads it again. Returns a short report; throws on failure. */
    fun roundTrip(
        dir: File,
        kdf: KdfEngine,
        label: String,
        cipher: EncryptionAlgorithm = EncryptionAlgorithm.AESRijndael
    ): String {
        val file = File(dir, "spike-$label-${cipher.name}.kdbx")
        save(newDatabase(kdf, cipher = cipher), file, "spike-password", dir)
        val reloaded = open(file, "spike-password", dir)
        check(reloaded.loaded) { "$label/${cipher.name}: database not marked as loaded" }
        check(reloaded.rootGroup != null) { "$label/${cipher.name}: no root group after loading" }
        return "$label/${cipher.name} ok: ${file.length()} bytes"
    }

    /** AES-KDF once, then Argon2id with each of the three vault ciphers (each goes through a different crypto path). */
    fun runAll(dir: File): List<String> {
        val report = mutableListOf(roundTrip(dir, KdfFactory.aesKdf, "aes-kdf"))
        for (cipher in EncryptionAlgorithm.values()) {
            report += roundTrip(dir, KdfFactory.argon2idKdf, "argon2id", cipher)
        }
        return report
    }
}
