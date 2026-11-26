package com.monsoonscript.keyboard.vault

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.format.Formatter
import android.view.View
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.vault.BackupInfo
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The vault's Backups screen (E1.16, D94): the earlier versions the app keeps before a save replaces the file (D36, the last 5) with a restore on a tap, "Save a
 * copy now" (the encrypted vault file, written where the owner chooses) and "Restore from a file". Every restore asks first, keeps the current version as a saved
 * version, and then locks the vault (the restored file has the master password of its own time). Needs the vault unlocked, like every screen with vault content.
 */
internal class VaultBackupsActivity : VaultBaseActivity() {
    private var backups: List<BackupInfo>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    override fun onStart() {
        super.onStart()
        call({ it.backups() }) { list ->
            backups = list
            render()
        }
    }

    private fun render() {
        val page = ui.page(getString(R.string.vault_backups_title), onBack = { finish() })
        with(ui) {
            val c = page.content
            c.add(header(getString(R.string.vault_backups_versions)))
            val list = backups
            when {
                list == null -> Unit // still asking the vault
                list.isEmpty() -> c.add(note(getString(R.string.vault_backups_none)))
                else -> {
                    list.forEachIndexed { i, b -> c.add(versionRow(b, newest = i == 0)) }
                    c.add(note(getString(R.string.vault_backups_hint)))
                }
            }
            c.add(header(getString(R.string.vault_backups_copies)))
            c.add(buttonRow(getString(R.string.vault_backups_save_copy), getString(R.string.vault_backups_save_copy_text)) { pickCopyPlace() })
            if (prefs.writable) {
                c.add(buttonRow(getString(R.string.vault_backups_restore_file), getString(R.string.vault_backups_restore_file_text)) { pickFileToRestore() })
            } else {
                c.add(note(getString(R.string.vault_backups_read_only)))
            }
        }
        setContentView(page.root)
    }

    private fun versionRow(b: BackupInfo, newest: Boolean): View = ui.row().apply {
        val whenText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(b.time))
        val size = Formatter.formatShortFileSize(this@VaultBackupsActivity, b.size)
        addView(ui.texts(whenText, if (newest) getString(R.string.vault_backups_newest) + " · " + size else size), ui.weighted())
        if (prefs.writable) setOnClickListener { confirm(getString(R.string.vault_backups_restore_text_version, whenText)) { restore { it.restoreBackup(b.id) } } }
    }

    // ------------------------------------------------------------------------------------------------------------------------ restore

    private fun confirm(text: String, go: () -> Unit) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.vault_backups_restore_title)
            .setMessage(text)
            .setPositiveButton(R.string.vault_backups_restore_yes) { _, _ -> go() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        ui.styleDialog(dialog)
    }

    /** Runs a restore call in the vault process; when it worked the vault is locked, so the unlock page follows. */
    private fun restore(block: (com.monsoonscript.vault.IVault) -> Unit) {
        busy.show(getString(R.string.vault_backups_restoring))
        call(block, fail = { report(it) }) {
            busy.hide()
            Toast.makeText(this, R.string.vault_backups_restored, Toast.LENGTH_LONG).show()
            onLocked()
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------ the two pickers

    private fun pickCopyPlace() {
        val base = prefs.fileName.removeSuffix(".kdbx").ifEmpty { getString(R.string.vault_title) }
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        @Suppress("DEPRECATION")
        startActivityForResult(VaultHub.createIntent("$base-$day.kdbx"), REQ_COPY)
    }

    private fun pickFileToRestore() {
        @Suppress("DEPRECATION")
        startActivityForResult(VaultHub.readFileIntent(), REQ_FILE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            REQ_COPY -> saveCopy(uri)
            REQ_FILE -> confirm(getString(R.string.vault_backups_restore_text_file, VaultHub.displayName(this, uri).ifEmpty { "?" })) {
                restore { it.restoreFromFile(uri) }
            }
        }
    }

    /** The encrypted vault file, as it is stored, copied to the place the owner chose (it stays encrypted: nothing secret is written in the clear). */
    private fun saveCopy(dest: Uri) {
        val source = prefs.fileUri?.let { Uri.parse(it) } ?: return
        busy.show(getString(R.string.vault_backups_saving))
        Thread {
            val ok = try {
                val input = contentResolver.openInputStream(source) ?: throw java.io.IOException("no input")
                input.use { i ->
                    val output = contentResolver.openOutputStream(dest, "wt") ?: throw java.io.IOException("no output")
                    output.use { o -> i.copyTo(o) }
                }
                true
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                if (!alive) return@runOnUiThread
                busy.hide()
                Toast.makeText(this, if (ok) R.string.vault_backups_saved else R.string.vault_backups_save_failed, Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private companion object {
        const val REQ_COPY = 21
        const val REQ_FILE = 22
    }
}
