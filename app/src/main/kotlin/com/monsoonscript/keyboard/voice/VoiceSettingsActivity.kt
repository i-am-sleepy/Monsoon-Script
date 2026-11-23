package com.monsoonscript.keyboard.voice

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.prefs.Prefs
import com.monsoonscript.keyboard.settings.SettingsUi

/**
 * Settings > Voice typing (G5, D66): which engine speaks for each language, the local models (download, import from a file, delete) and Offline mode (D10).
 * The microphone key itself is in the keyboard's strip.
 */
class VoiceSettingsActivity : Activity() {

    private lateinit var ui: SettingsUi
    private lateinit var modelBox: LinearLayout
    private var download: VoiceModels.Download? = null
    private var progress = 0L
    private val model = VoiceModels.ENGLISH

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = SettingsUi(this)
        val page = ui.page(getString(R.string.voice_settings_title)) { finish() }
        with(ui) {
            page.content.add(header(getString(R.string.voice_settings_engine_header)))
            val engines = listOf(R.string.voice_engine_google, R.string.voice_engine_local).map { getString(it) }
            page.content.add(choiceRow(getString(R.string.voice_engine_english), Prefs.VOICE_ENGINE_EN, engines, Prefs.ENGINE_GOOGLE))
            page.content.add(choiceRow(getString(R.string.voice_engine_bangla), Prefs.VOICE_ENGINE_BN, engines, Prefs.ENGINE_GOOGLE))
            page.content.add(note(getString(R.string.voice_engine_note)))
            page.content.add(header(getString(R.string.voice_models_header)))
            modelBox = LinearLayout(this@VoiceSettingsActivity).apply { orientation = LinearLayout.VERTICAL }
            page.content.add(modelBox)
            page.content.add(infoRow(getString(R.string.voice_model_bangla), getString(R.string.voice_model_bangla_summary)))
            page.content.add(note(getString(R.string.voice_models_note)))
            page.content.add(header(getString(R.string.voice_privacy_header)))
            page.content.add(switchRow(getString(R.string.voice_offline), getString(R.string.voice_offline_summary), Prefs.VOICE_OFFLINE, false))
            page.content.add(note(getString(R.string.voice_credits, model.credit)))
        }
        setContentView(page.root)
        render()
    }

    override fun onDestroy() {
        download?.cancel()
        super.onDestroy()
    }

    private fun mb(bytes: Long) = "%d MB".format((bytes + 500_000L) / 1_000_000L)

    private fun render() {
        modelBox.removeAllViews()
        with(ui) {
            val installed = VoiceModels.installed(this@VoiceSettingsActivity, model)
            when {
                download != null -> {
                    val pct = (progress * 100 / model.bytes).toInt().coerceIn(0, 100)
                    modelBox.add(infoRow(model.title, getString(R.string.voice_model_downloading, pct, mb(model.bytes))))
                    modelBox.add(buttonRow(getString(R.string.voice_model_cancel), null) { download?.cancel() })
                }
                installed -> {
                    modelBox.add(infoRow(model.title, getString(R.string.voice_model_installed, mb(model.bytes))))
                    modelBox.add(buttonRow(getString(R.string.voice_model_delete), null) {
                        VoiceModels.delete(this@VoiceSettingsActivity, model)
                        render()
                    })
                }
                else -> {
                    val part = VoiceModels.partBytes(this@VoiceSettingsActivity, model)
                    val summary = if (part > 0) getString(R.string.voice_model_partial, mb(part), mb(model.bytes)) else getString(R.string.voice_model_missing, mb(model.bytes))
                    modelBox.add(infoRow(model.title, summary))
                    modelBox.add(buttonRow(getString(R.string.voice_model_download), null) { askDownload() })
                    modelBox.add(buttonRow(getString(R.string.voice_model_import), getString(R.string.voice_model_import_summary)) { pickFile() })
                }
            }
        }
    }

    private fun askDownload() {
        if (Prefs.of(this).getBoolean(Prefs.VOICE_OFFLINE, false)) {
            Toast.makeText(this, R.string.voice_err_offline, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle(R.string.voice_model_download)
            .setMessage(getString(R.string.voice_model_download_ask, mb(model.bytes)))
            .setPositiveButton(R.string.voice_model_download) { _, _ -> startDownload() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startDownload() {
        progress = VoiceModels.partBytes(this, model)
        download = VoiceModels.Download(this, model, object : VoiceModels.Download.Listener {
            override fun onProgress(done: Long, total: Long) {
                if (isDestroyed) return
                progress = done
                render()
            }

            override fun onDone(ok: Boolean, message: String?) {
                download = null
                if (isDestroyed) return
                if (!ok && message != "cancelled") Toast.makeText(this@VoiceSettingsActivity, getString(R.string.voice_model_failed, message ?: ""), Toast.LENGTH_LONG).show()
                render()
            }
        }).also { it.start() }
        render()
    }

    private fun pickFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        @Suppress("DEPRECATION")
        startActivityForResult(i, PICK)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != PICK || resultCode != RESULT_OK || uri == null) return
        Toast.makeText(this, R.string.voice_model_importing, Toast.LENGTH_SHORT).show()
        Thread({
            val ok = VoiceModels.importFrom(this, model, uri)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                Toast.makeText(this, if (ok) R.string.voice_model_import_ok else R.string.voice_model_import_bad, Toast.LENGTH_LONG).show()
                render()
            }
        }, "voice-import").start()
    }

    private companion object {
        const val PICK = 72
    }
}
