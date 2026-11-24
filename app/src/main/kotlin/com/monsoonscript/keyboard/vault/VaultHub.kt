package com.monsoonscript.keyboard.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.monsoonscript.keyboard.R
import com.monsoonscript.vault.VaultClient
import com.monsoonscript.vault.VaultError
import com.monsoonscript.vault.VaultException
import java.io.ByteArrayOutputStream

/**
 * The app's single connection to the vault process (D44) and the few helpers every vault screen shares: error texts, the file picker intents (D35) with the
 * permission that keeps the chosen file reachable, and reading a key file.
 */
internal object VaultHub {
    @Volatile
    private var client: VaultClient? = null

    fun client(context: Context): VaultClient =
        client ?: synchronized(this) { client ?: VaultClient(context.applicationContext).also { client = it } }

    /** The words for an error, in plain language (no engine jargon). */
    fun message(context: Context, e: Throwable): String {
        val ve = e as? VaultException ?: return context.getString(R.string.vault_err_generic, e.message ?: e.javaClass.simpleName)
        return when (ve.code) {
            VaultError.WRONG_CREDENTIALS -> context.getString(R.string.vault_err_wrong_credentials)
            VaultError.FILE_NOT_FOUND -> context.getString(R.string.vault_err_file_not_found)
            VaultError.NOT_A_VAULT -> context.getString(R.string.vault_err_not_a_vault)
            VaultError.UNSUPPORTED -> context.getString(R.string.vault_err_unsupported)
            VaultError.TOO_HEAVY -> context.getString(R.string.vault_err_too_heavy)
            VaultError.IO -> context.getString(R.string.vault_err_io)
            VaultError.LOCKED -> context.getString(R.string.vault_err_locked)
            VaultError.NOT_FOUND -> context.getString(R.string.vault_err_not_found)
            VaultError.CHANGED_ELSEWHERE -> context.getString(R.string.vault_err_changed)
            VaultError.READ_ONLY -> context.getString(R.string.vault_err_read_only)
            VaultError.INVALID -> ve.message ?: context.getString(R.string.vault_err_generic, "")
            else -> context.getString(R.string.vault_err_generic, ve.message ?: "")
        }
    }

    fun isLocked(e: Throwable): Boolean = (e as? VaultException)?.code == VaultError.LOCKED

    /** The picker for an existing vault: the owner chooses any file, KeePassDX / KeePassXC files open in place. */
    fun openIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("*/*")
        .addFlags(PICK_FLAGS)

    /** The picker for the place of a new vault. */
    fun createIntent(suggestedName: String): Intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("application/octet-stream")
        .putExtra(Intent.EXTRA_TITLE, suggestedName)
        .addFlags(PICK_FLAGS)

    /** The picker for a key file (read only). */
    fun keyFileIntent(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)

    private const val PICK_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION

    /**
     * Keeps the picked [uri] reachable after the app restarts. Returns true when the file can also be WRITTEN (the vault opens read-only otherwise).
     * [result] is the picker's result intent: Android says there which kinds of access it granted.
     */
    fun keepAccess(context: Context, uri: Uri, result: Intent): Boolean {
        val granted = result.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val resolver = context.contentResolver
        try {
            resolver.takePersistableUriPermission(uri, granted or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                return false
            } catch (_: SecurityException) {
                return granted and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0 // nothing to persist (a "file:" address, for instance); the grant lasts for now
            }
        }
        return granted and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0
    }

    fun displayName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0) ?: ""
            }
        } catch (_: RuntimeException) {
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: ""
    }

    /** Reads a key file (they are tiny; anything over 1 MB is refused). Call off the main thread. */
    fun readKeyFile(context: Context, uri: Uri): ByteArray {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw VaultException(VaultError.FILE_NOT_FOUND, "The key file could not be opened")
            input.use {
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = it.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > MAX_KEY_FILE) throw VaultException(VaultError.INVALID, context.getString(R.string.vault_err_key_file_big))
                }
                return out.toByteArray()
            }
        } catch (e: java.io.IOException) {
            throw VaultException(VaultError.IO, "The key file could not be read", e)
        } catch (e: SecurityException) {
            throw VaultException(VaultError.FILE_NOT_FOUND, "The key file is no longer reachable", e)
        }
    }

    private const val MAX_KEY_FILE = 1 shl 20
}
