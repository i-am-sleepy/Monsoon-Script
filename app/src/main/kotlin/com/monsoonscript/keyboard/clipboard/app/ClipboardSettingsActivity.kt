package com.monsoonscript.keyboard.clipboard.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.clipboard.ClipFormat
import com.monsoonscript.keyboard.clipboard.ClipHub
import com.monsoonscript.keyboard.clipboard.ClipPrefs
import com.monsoonscript.keyboard.clipboard.ClipStore
import com.monsoonscript.keyboard.clipboard.ClipUnlockActivity
import com.monsoonscript.keyboard.settings.SettingsUi

/**
 * The clipboard settings, one page with sections (D67): Management, Behavior, Appearance, Privacy. The rows write straight to [ClipPrefs]; [ClipHub] hands the
 * history rules to the store at once and the keyboard panel reads the rest when it opens. Opened by the gear in the app and in the keyboard panel.
 */
class ClipboardSettingsActivity : Activity() {
    private lateinit var hub: ClipHub
    private lateinit var gate: LockGate
    private var storedText: TextView? = null
    private var lockText: TextView? = null

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
        refreshStored()
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
        if (requestCode == REQUEST_VERIFY && resultCode == RESULT_OK) {
            hub.prefs.lockEnabled = true
            hub.lock.enabled = true
            hub.lock.grant() // the prompt just succeeded: no second one right away
            refreshLock()
        }
    }

    private fun refreshLock() {
        lockText?.text = getString(if (hub.prefs.lockEnabled) R.string.clip_set_lock_on else R.string.clip_set_lock_off)
    }

    override fun onPause() {
        hub.store.removeListener(storeListener)
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
    private fun pickRow(ui: SettingsUi, title: String, labels: List<String>, current: () -> Int, summary: (Int) -> String, onPick: (Int) -> Unit): View {
        val texts = ui.texts(title, summary(current()))
        val sub = texts.getChildAt(1) as TextView
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
