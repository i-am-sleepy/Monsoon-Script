package com.monsoonscript.vault

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * The vault file as the owner picked it with Android's file picker (D35): a document Uri (a persisted permission is held by our app's
 * user id, so this works from the vault process too) or a plain file Uri. The backups live in the app's private storage, one folder per
 * vault, named after a hash of the Uri.
 */
class UriVaultStore(private val context: Context, val uri: Uri) : VaultStore {
    override val displayName: String = queryName() ?: "vault"

    override val backupDir: File = File(context.filesDir, "vault-backups/${hash(uri.toString())}")

    override fun openInput(): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())

    /** "wt" = write and truncate: with plain "w" some providers keep the old tail of a longer file. */
    override fun openOutput(): OutputStream =
        context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("The file cannot be opened for writing: $uri")

    override fun stamp(): String? {
        if (uri.scheme == "file") {
            val f = File(uri.path ?: return null)
            return if (f.exists()) "${f.length()}:${f.lastModified()}" else null
        }
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) "${c.getLong(0)}:${c.getLong(1)}" else null
            }
        } catch (_: Exception) {
            null // a provider that cannot tell: conflicts then go unnoticed, same as without the check
        }
    }

    private fun queryName(): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).take(6).joinToString("") { "%02x".format(it) }
}
