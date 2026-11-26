package com.monsoonscript.keyboard.vault

import android.annotation.TargetApi
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import com.monsoonscript.keyboard.voice.CrashLog
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Where the fingerprint unlock keeps its secret (E1.15, D39, D91): the master password text, encrypted with AES-GCM under a key that lives in the Android
 * Keystore. The key can only be used right after a fingerprint (every single use), and it is destroyed by Android when a fingerprint is added to the phone.
 * What the key cannot enforce (the restart and the 72 hours) is checked by [FingerprintPolicy] before the prompt is shown.
 *
 * Only the app's main process touches this (the vault service never sees it). The stored blob is useless without the key; the key is useless without a
 * finger. Nothing here is ever logged except the word that says why it was switched off.
 */
@TargetApi(Build.VERSION_CODES.P)
internal class FingerprintStore(context: Context) {
    private val app = context.applicationContext
    private val raw = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Why the fingerprint was switched off by itself; shown once on the unlock screen. */
    enum class Reason { NONE, RESTART, TOO_OLD, CHANGED, PASSWORD, BROKEN }

    enum class Availability { UNSUPPORTED, NOT_ENROLLED, READY }

    /** Can this phone do it at all, and is a fingerprint enrolled. Below Android 9 the platform prompt with a crypto key does not exist (D91). */
    fun availability(): Availability {
        if (Build.VERSION.SDK_INT < 28) return Availability.UNSUPPORTED
        if (!app.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) return Availability.UNSUPPORTED
        if (bootCount() < 0) return Availability.UNSUPPORTED // the "after a restart" rule could not be kept
        return try {
            when {
                Build.VERSION.SDK_INT >= 30 -> map(app.getSystemService(BiometricManager::class.java)?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG))
                Build.VERSION.SDK_INT >= 29 -> @Suppress("DEPRECATION") map(app.getSystemService(BiometricManager::class.java)?.canAuthenticate())
                else -> {
                    @Suppress("DEPRECATION")
                    val fm = app.getSystemService(FingerprintManager::class.java)
                    @Suppress("DEPRECATION")
                    when {
                        fm == null || !fm.isHardwareDetected -> Availability.UNSUPPORTED
                        !fm.hasEnrolledFingerprints() -> Availability.NOT_ENROLLED
                        else -> Availability.READY
                    }
                }
            }
        } catch (e: RuntimeException) {
            Availability.UNSUPPORTED
        }
    }

    private fun map(code: Int?): Availability = when (code) {
        BiometricManager.BIOMETRIC_SUCCESS -> Availability.READY
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NOT_ENROLLED
        else -> Availability.UNSUPPORTED
    }

    /** The key and the stored password exist. */
    val armed: Boolean get() = raw.getBoolean("armed", false) && raw.contains("blob") && raw.contains("iv")

    fun verdict(): FingerprintPolicy.Verdict = FingerprintPolicy.verdict(
        armed, raw.getInt("boot", -1), bootCount(),
        raw.getLong("pw_wall", 0L), System.currentTimeMillis(), raw.getLong("pw_up", 0L), SystemClock.elapsedRealtime(),
    )

    /** A cipher for [store]: a brand new key (an old one is thrown away first). It still needs the fingerprint before it can encrypt. Null when the key cannot be made. */
    fun encryptCipher(): Cipher? = try {
        deleteKey()
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true) // a finger for every single use
            .setInvalidatedByBiometricEnrollment(true) // a new fingerprint on the phone destroys the key
        if (Build.VERSION.SDK_INT >= 30) {
            spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            spec.setUserAuthenticationValidityDurationSeconds(-1)
        }
        gen.init(spec.build())
        val key = gen.generateKey()
        Cipher.getInstance(TRANSFORM).also { it.init(Cipher.ENCRYPT_MODE, key) }
    } catch (e: Exception) {
        CrashLog.note(app, "fingerprint: no key (${e.javaClass.simpleName})")
        deleteKey()
        null
    }

    /** A cipher for [read]. Null when the key is gone or Android destroyed it (fingerprints changed): then everything is switched off with the reason. */
    fun decryptCipher(): Cipher? {
        val iv = raw.getString("iv", null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        if (iv == null) {
            disarm(Reason.BROKEN)
            return null
        }
        return try {
            val key = keyStore().getKey(ALIAS, null) as? SecretKey
            if (key == null) {
                disarm(Reason.BROKEN)
                null
            } else {
                Cipher.getInstance(TRANSFORM).also { it.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv)) }
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            disarm(Reason.CHANGED)
            null
        } catch (e: Exception) {
            CrashLog.note(app, "fingerprint: key unusable (${e.javaClass.simpleName})")
            disarm(Reason.BROKEN)
            null
        }
    }

    /** Encrypts [secret] with the cipher the fingerprint just released, and remembers "set up now, this boot, password typed now". The caller wipes [secret]. */
    fun store(cipher: Cipher, secret: CharArray): Boolean {
        var bytes: ByteArray? = null
        return try {
            bytes = utf8(secret)
            val blob = cipher.doFinal(bytes)
            raw.edit()
                .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .putString("blob", Base64.encodeToString(blob, Base64.NO_WRAP))
                .putInt("boot", bootCount())
                .putLong("pw_wall", System.currentTimeMillis())
                .putLong("pw_up", SystemClock.elapsedRealtime())
                .putBoolean("armed", true)
                .remove("reason").remove("skip_boot")
                .commit()
        } catch (e: Exception) {
            CrashLog.note(app, "fingerprint: could not store (${e.javaClass.simpleName})")
            disarm(Reason.NONE)
            false
        } finally {
            bytes?.fill(0)
        }
    }

    /** The master password text, decrypted with the cipher the fingerprint just released; the caller wipes it. Null when it cannot be read. */
    fun read(cipher: Cipher): CharArray? {
        val blob = raw.getString("blob", null)?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return null
        var bytes: ByteArray? = null
        return try {
            bytes = cipher.doFinal(blob)
            chars(bytes)
        } catch (e: Exception) {
            CrashLog.note(app, "fingerprint: could not read (${e.javaClass.simpleName})")
            null
        } finally {
            bytes?.fill(0)
        }
    }

    /** The master password was typed (and was right): the 72 hours start again. */
    fun noteMasterPasswordUnlock() {
        if (!armed) return
        raw.edit().putLong("pw_wall", System.currentTimeMillis()).putLong("pw_up", SystemClock.elapsedRealtime()).apply()
    }

    /** Throws the key and the stored password away. [why] is shown once on the unlock screen (NONE = silently). */
    fun disarm(why: Reason) {
        deleteKey()
        val e = raw.edit().remove("armed").remove("iv").remove("blob").remove("boot").remove("pw_wall").remove("pw_up")
        if (why == Reason.NONE) e.remove("reason") else e.putInt("reason", why.ordinal)
        e.commit()
        if (why != Reason.NONE) CrashLog.note(app, "fingerprint: switched off ($why)")
    }

    /** Back to nothing: the key, the stored password and every memory of it (the switch was turned off, or the vault was forgotten). */
    fun turnOff() {
        deleteKey()
        raw.edit().clear().commit()
    }

    /** The reason of the last automatic switch-off, once. */
    fun takeReason(): Reason {
        val r = Reason.values().getOrElse(raw.getInt("reason", 0)) { Reason.NONE }
        if (r != Reason.NONE) raw.edit().remove("reason").apply()
        return r
    }

    /** The owner dismissed the "set up fingerprint" prompt: it is not asked again until the phone restarts or the switch is turned off and on. */
    fun skipSetupThisBoot() {
        raw.edit().putInt("skip_boot", bootCount()).apply()
    }

    val setupSkipped: Boolean get() = raw.getInt("skip_boot", -2) == bootCount()

    fun clearSkip() {
        raw.edit().remove("skip_boot").apply()
    }

    // ------------------------------------------------------------------------------------------------------------------------ helpers

    private fun bootCount(): Int = try {
        Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)
    } catch (e: RuntimeException) {
        -1
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun deleteKey() {
        try {
            val ks = keyStore()
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
        } catch (e: Exception) {
            // nothing to delete, or the Keystore is not answering: the key is useless without the blob in any case
        }
    }

    private fun utf8(secret: CharArray): ByteArray {
        val bb = Charsets.UTF_8.encode(CharBuffer.wrap(secret))
        val out = ByteArray(bb.remaining())
        bb.get(out)
        if (bb.hasArray()) bb.array().fill(0)
        return out
    }

    private fun chars(bytes: ByteArray): CharArray {
        val cb = Charsets.UTF_8.decode(ByteBuffer.wrap(bytes))
        val out = CharArray(cb.remaining())
        cb.get(out)
        if (cb.hasArray()) cb.array().fill('\u0000')
        return out
    }

    companion object {
        private const val FILE = "vault_fp"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "monsoon_vault_fp"
        private const val TRANSFORM = "AES/GCM/NoPadding"
    }
}
