package com.monsoonscript.keyboard.vault

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.EditText
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.vault.ImportResult
import com.monsoonscript.vault.VaultError
import com.monsoonscript.vault.VaultException

/**
 * The vault's Import screen (E1.23, D104): bring the logins of another password manager (or Google Password Manager / Chrome) into the vault from the CSV file it
 * exported. Three steps on one page: choose the file, name the folder the logins go to, read the summary. The file is read INSIDE the vault process (only its
 * address travels), so no password passes through this process; the vault writes once at the end. A CSV file is plain text, so afterwards the screen offers to
 * delete it. Needs the vault unlocked and writable, like every screen that changes it.
 */
internal class VaultImportActivity : VaultBaseActivity() {
    private var file: Uri? = null
    private var fileName = ""
    private var folder = ""
    private var result: ImportResult? = null
    /** null = not tried, true = the file is gone, false = it could not be deleted from here. */
    private var deleted: Boolean? = null
    private var box: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            file = savedInstanceState.getString(KEY_FILE)?.let { Uri.parse(it) }
            fileName = savedInstanceState.getString(KEY_NAME).orEmpty()
            folder = savedInstanceState.getString(KEY_FOLDER).orEmpty()
            @Suppress("DEPRECATION")
            result = savedInstanceState.getParcelable(KEY_RESULT)
            deleted = if (savedInstanceState.containsKey(KEY_DELETED)) savedInstanceState.getBoolean(KEY_DELETED) else null
        }
        render()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_FILE, file?.toString())
        outState.putString(KEY_NAME, fileName)
        outState.putString(KEY_FOLDER, box?.text?.toString() ?: folder)
        outState.putParcelable(KEY_RESULT, result)
        deleted?.let { outState.putBoolean(KEY_DELETED, it) }
    }

    private fun render() {
        val page = ui.page(getString(R.string.import_title), onBack = { finish() })
        with(ui) {
            val c = page.content
            val done = result
            box = null
            when {
                done != null -> showSummary(c, done)
                file == null -> {
                    c.add(header(getString(R.string.import_how_header)))
                    c.add(note(getString(R.string.import_how)))
                    c.add(note(getString(R.string.import_plain_warning)))
                    c.add(gap(8))
                    c.add(padded(filledButton(getString(R.string.import_choose)) { pick() }, 20, 6))
                }
                else -> {
                    c.add(header(getString(R.string.import_file)))
                    c.add(infoRow(fileName.ifEmpty { "?" }, null))
                    c.add(header(getString(R.string.import_folder_header)))
                    val field = textBox(getString(R.string.import_folder_hint)).also {
                        it.setText(folder)
                        it.setSelection(it.text.length)
                        box = it
                    }
                    c.add(padded(field, 20, 6))
                    c.add(note(getString(R.string.import_folder_note)))
                    c.add(gap(8))
                    c.add(padded(filledButton(getString(R.string.import_button)) { startImport() }, 20, 6))
                    c.add(padded(outlineButton(getString(R.string.import_other_file)) { pick() }, 20, 6))
                }
            }
        }
        setContentView(page.root)
    }

    private fun showSummary(c: android.widget.LinearLayout, r: ImportResult) {
        with(ui) {
            c.add(header(getString(R.string.import_done_header, r.format.ifEmpty { "CSV" })))
            c.add(infoRow(getString(R.string.import_added), r.added.toString()))
            if (r.keptBoth > 0) c.add(infoRow(getString(R.string.import_kept_both), r.keptBoth.toString()))
            c.add(infoRow(getString(R.string.import_skipped), r.skipped.toString()))
            if (r.notLogins > 0) c.add(infoRow(getString(R.string.import_not_logins), r.notLogins.toString()))
            if (r.noPassword > 0) c.add(infoRow(getString(R.string.import_no_password), r.noPassword.toString()))
            if (r.codesDropped > 0) c.add(infoRow(getString(R.string.import_codes_dropped), r.codesDropped.toString()))
            if (r.failed > 0) c.add(infoRow(getString(R.string.import_failed), r.failed.toString()))
            if (r.added + r.keptBoth > 0) c.add(infoRow(getString(R.string.import_folder_header), folder))
            if (r.keptBoth > 0) c.add(note(getString(R.string.import_kept_both_note)))
            if (r.added + r.keptBoth == 0) c.add(note(getString(R.string.import_nothing_new)))
            c.add(gap(8))
            when (deleted) {
                true -> c.add(note(getString(R.string.import_deleted)))
                false -> c.add(note(getString(R.string.import_not_deleted)))
                null -> if (file != null) {
                    c.add(note(getString(R.string.import_delete_note)))
                    c.add(padded(filledButton(getString(R.string.import_delete)) { deleteFile() }, 20, 6))
                }
            }
            c.add(padded(outlineButton(getString(R.string.import_another)) { again() }, 20, 6))
            c.add(padded(outlineButton(getString(R.string.import_close)) { finish() }, 20, 6))
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------ steps

    private fun pick() {
        // The write flag is only a request, so the file can be deleted afterwards; the vault process reads the file with the same grant (same app).
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_FILE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != REQ_FILE || resultCode != RESULT_OK || uri == null) return
        file = uri
        fileName = VaultHub.displayName(this, uri)
        if (box?.text.isNullOrBlank() && folder.isBlank()) folder = suggestFolder(fileName)
        result = null
        deleted = null
        render()
    }

    /** "Google Passwords.csv" -> "Imported from Google Passwords" (the owner can change it). */
    private fun suggestFolder(name: String): String {
        val base = name.substringBeforeLast('.').trim().take(60)
        return if (base.isEmpty()) getString(R.string.import_folder_default) else getString(R.string.import_folder_suggestion, base)
    }

    private fun startImport() {
        val uri = file ?: return
        val name = box?.text?.toString().orEmpty().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.import_folder_needed, Toast.LENGTH_LONG).show()
            return
        }
        folder = name
        busy.show(getString(R.string.import_working))
        call({ it.importLogins(uri, name) }, fail = { failed(it) }) { r ->
            busy.hide()
            if (r.saved) show(r) else askConflict { show(r) } // D50: the vault file changed elsewhere, the owner chooses first
        }
    }

    private fun failed(e: Throwable) {
        if ((e as? VaultException)?.code == VaultError.TOO_BIG) {
            busy.hide()
            Toast.makeText(this, R.string.import_too_big, Toast.LENGTH_LONG).show()
        } else {
            report(e)
        }
    }

    private fun show(r: ImportResult) {
        result = r
        deleted = null
        render()
    }

    private fun again() {
        file = null
        fileName = ""
        folder = ""
        result = null
        deleted = null
        render()
    }

    /** Tries to delete the picked CSV (it holds every password in plain text); not every place allows it, then the owner is told to do it himself. */
    private fun deleteFile() {
        val uri = file ?: return
        Thread {
            val ok = try {
                DocumentsContract.deleteDocument(contentResolver, uri)
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                if (!alive) return@runOnUiThread
                deleted = ok
                render()
            }
        }.start()
    }

    private companion object {
        const val REQ_FILE = 1
        const val KEY_FILE = "file"
        const val KEY_NAME = "name"
        const val KEY_FOLDER = "folder"
        const val KEY_RESULT = "result"
        const val KEY_DELETED = "deleted"
    }
}
