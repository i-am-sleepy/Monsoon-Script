package com.monsoonscript.keyboard.clipboard.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.clipboard.ClipBackup
import com.monsoonscript.keyboard.clipboard.ClipFormat
import com.monsoonscript.keyboard.clipboard.ClipHub
import com.monsoonscript.keyboard.clipboard.ClipPrefs
import com.monsoonscript.keyboard.clipboard.ClipStore
import com.monsoonscript.keyboard.clipboard.ClipUnlockActivity
import com.monsoonscript.keyboard.settings.SettingsUi
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The clipboard settings, one page with sections (D67): Management, Behavior, Images, Appearance, Privacy, Backup. The rows write straight to [ClipPrefs]; [ClipHub] hands the
 * history rules to the store at once and the keyboard panel reads the rest when it opens. Opened by the gear in the app and in the keyboard panel.
 */
class ClipboardSettingsActivity : Activity() {
    private lateinit var hub: ClipHub
    private lateinit var gate: LockGate
    private var storedText: TextView? = null
    private var lockText: TextView? = null
    private var autoSub: TextView? = null
    private var folderSub: TextView? = null
    private var statusSub: TextView? = null
    private var exportWithImages = false

    /** The backup status lines change while this page is open (the automatic backup runs in the background). */
    private val backupListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refreshBackup() }

    /** The saved clips are read in the background, and they change: keep the "N images" line right. */
    private val storeListener = object : ClipStore.Listener {
        override fun onClipsChanged() = refreshStored()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hub = ClipHub.get(this)
        hub.start()
        val prefs = hub.prefs
        val ui = SettingsUi(this, prefs.sp)
        val page = ui.page(getString(R.string.clip_settings_title)) { finish() }
        with(ui) {
            page.content.add(header(getString(R.string.clip_set_management)))
            page.content.add(switchRow(getString(R.string.clip_set_enabled), getString(R.string.clip_set_enabled_summary), ClipPrefs.K_ENABLED, true))
            val limits = ClipPrefs.LIMITS.map { it.toString() }
            page.content.add(
                pickRow(ui, getString(R.string.clip_set_limit), limits, { ClipPrefs.LIMITS.indexOf(prefs.maxItems).coerceAtLeast(0) },
                    { getString(R.string.clip_set_limit_summary, ClipPrefs.LIMITS[it]) }) { prefs.maxItems = ClipPrefs.LIMITS[it] },
            )
            val autos = listOf(R.string.clip_auto_never, R.string.clip_auto_1h, R.string.clip_auto_1d, R.string.clip_auto_7d, R.string.clip_auto_30d).map { getString(it) }
            page.content.add(
                pickRow(ui, getString(R.string.clip_set_auto), autos, { ClipPrefs.AUTO_DELETE.indexOf(prefs.autoDeleteMs).coerceAtLeast(0) },
                    { autos[it] }) { prefs.autoDeleteMs = ClipPrefs.AUTO_DELETE[it] },
            )
            page.content.add(switchRow(getString(R.string.clip_set_keep_pinned), getString(R.string.clip_set_keep_pinned_summary), ClipPrefs.K_KEEP_PINNED, true))
            page.content.add(switchRow(getString(R.string.clip_set_dedupe), getString(R.string.clip_set_dedupe_summary), ClipPrefs.K_DEDUPE, true))
            page.content.add(buttonRow(getString(R.string.clip_set_clear_unpinned), getString(R.string.clip_set_clear_unpinned_summary)) {
                confirm(R.string.clip_clear_unpinned_ask) { hub.store.clearUnpinned() }
            })
            page.content.add(buttonRow(getString(R.string.clip_set_clear_all), getString(R.string.clip_set_clear_all_summary)) {
                confirm(R.string.clip_clear_all_ask) { hub.store.clearAll() }
            })

            page.content.add(header(getString(R.string.clip_set_behavior)))
            page.content.add(switchRow(getString(R.string.clip_set_pinned_top), getString(R.string.clip_set_pinned_top_summary), ClipPrefs.K_PINNED_TOP, true))
            page.content.add(switchRow(getString(R.string.clip_set_group), getString(R.string.clip_set_group_summary), ClipPrefs.K_GROUP, true))
            page.content.add(switchRow(getString(R.string.clip_set_hide_paste), getString(R.string.clip_set_hide_paste_summary), ClipPrefs.K_HIDE_PASTE, false))

            page.content.add(header(getString(R.string.clip_set_images)))
            page.content.add(switchRow(getString(R.string.clip_set_save_images), getString(R.string.clip_set_save_images_summary), ClipPrefs.K_IMAGES, true))
            val caps = ClipPrefs.IMAGE_CAPS.map { "$it MB" }
            page.content.add(
                pickRow(ui, getString(R.string.clip_set_image_cap), caps, { ClipPrefs.IMAGE_CAPS.indexOf(prefs.imageCapMb).coerceAtLeast(0) },
                    { getString(R.string.clip_set_image_cap_summary, ClipPrefs.IMAGE_CAPS[it]) }) { prefs.imageCapMb = ClipPrefs.IMAGE_CAPS[it] },
            )
            val stored = texts(getString(R.string.clip_set_stored_images), "")
            storedText = stored.getChildAt(1) as TextView
            page.content.add(
                row().apply {
                    addView(stored, weighted())
                    setOnClickListener {
                        if (hub.store.imageCount() > 0) confirm(R.string.clip_delete_images_ask) { hub.store.clearImages() }
                    }
                },
            )
            refreshStored()

            page.content.add(header(getString(R.string.clip_set_appearance)))
            page.content.add(switchRow(getString(R.string.clip_set_compact), getString(R.string.clip_set_compact_summary), ClipPrefs.K_COMPACT, false))
            page.content.add(sliderRow(getString(R.string.clip_set_lines), ClipPrefs.K_LINES, 1, 5, 3) { getString(R.string.clip_lines_format, it) })
            page.content.add(switchRow(getString(R.string.clip_set_time), getString(R.string.clip_set_time_summary), ClipPrefs.K_TIME, true))

            page.content.add(header(getString(R.string.clip_set_privacy)))
            val lockTexts = texts(getString(R.string.clip_set_lock), "")
            lockText = lockTexts.getChildAt(1) as TextView
            page.content.add(
                row().apply {
                    addView(lockTexts, weighted())
                    setOnClickListener { toggleLock() }
                },
            )
            refreshLock()
            val waits = listOf(R.string.clip_lock_now, R.string.clip_lock_1m, R.string.clip_lock_5m, R.string.clip_lock_15m, R.string.clip_lock_30m, R.string.clip_lock_1h, R.string.clip_lock_4h)
                .map { getString(it) }
            page.content.add(
                pickRow(ui, getString(R.string.clip_set_lock_after), waits, { ClipPrefs.LOCK_MINUTES.indexOf(prefs.lockMinutes).coerceAtLeast(0) },
                    { getString(R.string.clip_set_lock_after_summary, waits[it]) }) { prefs.lockMinutes = ClipPrefs.LOCK_MINUTES[it] },
            )
            page.content.add(note(getString(R.string.clip_privacy_stays)))
            page.content.add(note(getString(R.string.clip_privacy_never)))
            page.content.add(note(getString(R.string.clip_privacy_limit)))

            page.content.add(header(getString(R.string.clip_set_backup)))
            page.content.add(buttonRow(getString(R.string.clip_set_export), getString(R.string.clip_set_export_summary)) { startExport() })
            page.content.add(buttonRow(getString(R.string.clip_set_import), getString(R.string.clip_set_import_summary)) { startImport() })
            val modes = listOf(R.string.clip_auto_backup_off, R.string.clip_auto_backup_daily, R.string.clip_auto_backup_weekly).map { getString(it) }
            page.content.add(
                pickRow(ui, getString(R.string.clip_set_auto_backup), modes, { prefs.autoBackup }, { autoBackupSummary(it) }, onSub = { autoSub = it }) { mode ->
                    prefs.lastBackupTry = 0L // a new choice may write a backup right away
                    prefs.autoBackup = mode
                    if (mode != ClipPrefs.BACKUP_OFF) {
                        if (prefs.backupFolder.isEmpty()) pickFolder() else hub.maybeAutoBackup()
                    }
                },
            )
            val folderTexts = texts(getString(R.string.clip_set_backup_folder), "")
            folderSub = folderTexts.getChildAt(1) as TextView
            page.content.add(
                row().apply {
                    addView(folderTexts, weighted())
                    setOnClickListener { pickFolder() }
                },
            )
            page.content.add(switchRow(getString(R.string.clip_set_backup_images), getString(R.string.clip_set_backup_images_summary), ClipPrefs.K_BACKUP_IMAGES, false))
            val statusTexts = texts(getString(R.string.clip_set_backup_last), "")
            statusSub = statusTexts.getChildAt(1) as TextView
            page.content.add(row().apply { addView(statusTexts, weighted()) })
            page.content.add(note(getString(R.string.clip_backup_not_encrypted)))
            refreshBackup()
        }
        gate = LockGate(this, ui, hub, page.root) // the biometric lock (D71)
        setContentView(gate.root)
    }

    override fun onStart() {
        super.onStart()
        gate.start()
    }

    override fun onStop() {
        gate.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        gate.resume()
        hub.store.addListener(storeListener)
        hub.prefs.sp.registerOnSharedPreferenceChangeListener(backupListener)
        refreshStored()
        refreshBackup()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_EXPORT_IMAGES, exportWithImages)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        exportWithImages = savedInstanceState.getBoolean(STATE_EXPORT_IMAGES)
    }

    /** Off -> on only after one successful prompt (so the owner cannot lock himself out with a prompt that does not work); on -> off needs no prompt, this page is behind the lock already. */
    private fun toggleLock() {
        if (hub.prefs.lockEnabled) {
            hub.prefs.lockEnabled = false
            refreshLock()
        } else {
            @Suppress("DEPRECATION")
            startActivityForResult(ClipUnlockActivity.intent(this, verify = true), REQUEST_VERIFY)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = if (resultCode == RESULT_OK) data?.data else null
        when (requestCode) {
            REQUEST_VERIFY -> if (resultCode == RESULT_OK) {
                hub.prefs.lockEnabled = true
                hub.lock.enabled = true
                hub.lock.grant() // the prompt just succeeded: no second one right away
                refreshLock()
            }
            REQUEST_EXPORT -> if (uri != null) exportTo(uri)
            REQUEST_IMPORT -> if (uri != null) importFrom(uri)
            REQUEST_FOLDER -> folderPicked(uri)
        }
    }

    /** Export (D71): with pictures in the history ask whether they go in the file, then the system "save as" screen. */
    private fun startExport() {
        if (!hub.store.loaded) {
            Toast.makeText(this, R.string.clip_import_err_loading, Toast.LENGTH_SHORT).show()
            return
        }
        val (count, bytes) = hub.imageStats()
        if (count == 0) {
            chooseExportFile(false)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.clip_export_images_ask)
            .setMessage(getString(R.string.clip_export_images_msg, count, ClipFormat.bytes(bytes)))
            .setPositiveButton(R.string.clip_export_with) { _, _ -> chooseExportFile(true) }
            .setNegativeButton(R.string.clip_export_without) { _, _ -> chooseExportFile(false) }
            .setNeutralButton(android.R.string.cancel, null)
            .show()
    }

    private fun chooseExportFile(withImages: Boolean) {
        exportWithImages = withImages
        val name = "monsoon-clipboard-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".zip"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE, name)
        launch(intent, REQUEST_EXPORT)
    }

    private fun exportTo(uri: Uri) {
        val app = applicationContext
        Toast.makeText(app, R.string.clip_export_saving, Toast.LENGTH_SHORT).show()
        hub.exportBackup(uri, exportWithImages) { ok ->
            Toast.makeText(app, if (ok) R.string.clip_export_done else R.string.clip_export_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun startImport() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        launch(intent, REQUEST_IMPORT)
    }

    private fun importFrom(uri: Uri) {
        val app = applicationContext
        Toast.makeText(app, R.string.clip_import_reading, Toast.LENGTH_SHORT).show()
        hub.importBackup(uri) { outcome ->
            val result = outcome.result
            val body = if (result != null) importSummary(result) else getString(importError(outcome.error))
            if (isFinishing || isDestroyed) {
                Toast.makeText(app, body, Toast.LENGTH_LONG).show()
            } else {
                AlertDialog.Builder(this)
                    .setTitle(if (result != null) R.string.clip_import_done_title else R.string.clip_import_failed_title)
                    .setMessage(body)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    /** One line per count that is not zero (the clips added line always). */
    private fun importSummary(r: com.monsoonscript.keyboard.clipboard.ImportResult): String {
        val lines = ArrayList<String>()
        lines.add(getString(R.string.clip_import_added, r.added))
        if (r.skipped > 0) lines.add(getString(R.string.clip_import_skipped, r.skipped))
        if (r.trimmed > 0) lines.add(getString(R.string.clip_import_trimmed, r.trimmed))
        if (r.foldersAdded > 0) lines.add(getString(R.string.clip_import_folders, r.foldersAdded))
        if (r.shortcutsAdded > 0) lines.add(getString(R.string.clip_import_shortcuts, r.shortcutsAdded))
        if (r.shortcutsSkipped > 0) lines.add(getString(R.string.clip_import_shortcuts_skipped, r.shortcutsSkipped))
        return lines.joinToString("\n")
    }

    private fun importError(code: Int): Int = when (code) {
        ClipBackup.NOT_A_BACKUP -> R.string.clip_import_err_not_backup
        ClipBackup.TOO_NEW -> R.string.clip_import_err_new
        ClipBackup.ENCRYPTED -> R.string.clip_import_err_encrypted
        ClipHub.ERROR_LOADING -> R.string.clip_import_err_loading
        else -> R.string.clip_import_err_damaged
    }

    /** The system folder picker for the automatic backup; the permission is kept so no storage permission is needed. */
    private fun pickFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
        if (!launch(intent, REQUEST_FOLDER)) folderPicked(null)
    }

    private fun folderPicked(uri: Uri?) {
        val prefs = hub.prefs
        if (uri == null || !keepFolderAccess(uri)) {
            if (prefs.backupFolder.isEmpty() && prefs.autoBackup != ClipPrefs.BACKUP_OFF) {
                prefs.autoBackup = ClipPrefs.BACKUP_OFF // no folder, nothing to write to
                Toast.makeText(this, R.string.clip_backup_folder_needed, Toast.LENGTH_LONG).show()
            }
            refreshBackup()
            return
        }
        val old = prefs.backupFolder
        if (old != uri.toString()) {
            if (old.isNotEmpty()) {
                try {
                    contentResolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                } catch (e: Exception) {
                    // the old grant is gone already
                }
            }
            prefs.lastBackup = 0L // the new folder has no backup yet
            prefs.backupFolder = uri.toString()
        }
        prefs.lastBackupTry = 0L
        prefs.backupFailed = false
        hub.maybeAutoBackup()
        refreshBackup()
    }

    private fun keepFolderAccess(uri: Uri): Boolean = try {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        true
    } catch (e: Exception) {
        false
    }

    /** True when the system screen opened; some phones have no file picker. */
    private fun launch(intent: Intent, request: Int): Boolean = try {
        @Suppress("DEPRECATION")
        startActivityForResult(intent, request)
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.clip_backup_no_picker, Toast.LENGTH_LONG).show()
        false
    }

    private fun autoBackupSummary(mode: Int): String = getString(
        when (mode) {
            ClipPrefs.BACKUP_DAILY -> R.string.clip_auto_backup_summary_daily
            ClipPrefs.BACKUP_WEEKLY -> R.string.clip_auto_backup_summary_weekly
            else -> R.string.clip_auto_backup_summary_off
        },
    )

    private fun backupFolderSummary(folder: String): String {
        if (folder.isEmpty()) return getString(R.string.clip_backup_folder_none)
        val id = try {
            DocumentsContract.getTreeDocumentId(Uri.parse(folder))
        } catch (e: Exception) {
            folder
        }
        return getString(R.string.clip_backup_folder_set, id.substringAfter(':').ifEmpty { id })
    }

    private fun backupStatus(): String {
        val p = hub.prefs
        return when {
            p.backupFailed -> getString(R.string.clip_backup_status_failed)
            p.lastBackup == 0L -> getString(R.string.clip_backup_status_never)
            else -> DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(p.lastBackup))
        }
    }

    private fun refreshBackup() {
        val p = hub.prefs
        autoSub?.text = autoBackupSummary(p.autoBackup)
        folderSub?.text = backupFolderSummary(p.backupFolder)
        statusSub?.text = backupStatus()
    }

    private fun refreshLock() {
        lockText?.text = getString(if (hub.prefs.lockEnabled) R.string.clip_set_lock_on else R.string.clip_set_lock_off)
    }

    override fun onPause() {
        hub.store.removeListener(storeListener)
        hub.prefs.sp.unregisterOnSharedPreferenceChangeListener(backupListener)
        super.onPause()
    }

    private fun refreshStored() {
        val (count, bytes) = hub.imageStats()
        storedText?.text = when (count) {
            0 -> getString(R.string.clip_stored_images_none)
            1 -> getString(R.string.clip_stored_images_one, ClipFormat.bytes(bytes))
            else -> getString(R.string.clip_stored_images_many, count, ClipFormat.bytes(bytes))
        }
    }

    /** A row whose tap opens a list of [labels]; [current] is the chosen index, [summary] the text under the title, [onPick] stores the choice. */
    private fun pickRow(
        ui: SettingsUi,
        title: String,
        labels: List<String>,
        current: () -> Int,
        summary: (Int) -> String,
        onSub: (TextView) -> Unit = {},
        onPick: (Int) -> Unit,
    ): View {
        val texts = ui.texts(title, summary(current()))
        val sub = texts.getChildAt(1) as TextView
        onSub(sub) // for rows whose text must change from outside too
        return ui.row().apply {
            addView(texts, ui.weighted())
            setOnClickListener {
                AlertDialog.Builder(this@ClipboardSettingsActivity)
                    .setTitle(title)
                    .setSingleChoiceItems(labels.toTypedArray(), current()) { dialog, which ->
                        onPick(which)
                        sub.text = summary(which)
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private companion object {
        const val REQUEST_VERIFY = 7
        const val REQUEST_EXPORT = 8
        const val REQUEST_IMPORT = 9
        const val REQUEST_FOLDER = 10
        const val STATE_EXPORT_IMAGES = "export_images"
    }

    private fun confirm(question: Int, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(question)
            .setPositiveButton(R.string.clip_delete_confirm) { _, _ ->
                action()
                Toast.makeText(this, R.string.clip_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
