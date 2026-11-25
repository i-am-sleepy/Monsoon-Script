package com.monsoonscript.keyboard.voice

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.prefs.Prefs
import com.monsoonscript.keyboard.settings.SettingsUi
import com.monsoonscript.keyboard.settings.ThemedActivity

/**
 * Settings > Voice typing (G5, D66): which engine speaks for each language, the local models (download, import from a file, delete) and Offline mode (D10).
 * The microphone key itself is in the keyboard's strip.
 */
class VoiceSettingsActivity : ThemedActivity() {

    private lateinit var ui: SettingsUi
    private lateinit var modelBox: LinearLayout
    private lateinit var googleBox: LinearLayout
    private val downloads = mutableMapOf<String, VoiceModels.Download>()
    private val progress = mutableMapOf<String, Long>()
    private var importFor: VoiceModel? = null

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
            googleBox = LinearLayout(this@VoiceSettingsActivity).apply { orientation = LinearLayout.VERTICAL }
            page.content.add(googleBox)
            page.content.add(switchRow(getString(R.string.voice_bn_punct), getString(R.string.voice_bn_punct_summary), Prefs.VOICE_BN_PUNCT, true))
            page.content.add(header(getString(R.string.voice_models_header)))
            modelBox = LinearLayout(this@VoiceSettingsActivity).apply { orientation = LinearLayout.VERTICAL }
            page.content.add(modelBox)
            page.content.add(navRow(getString(R.string.voice_diag_title), getString(R.string.voice_diag_summary)) { startActivity(Intent(this@VoiceSettingsActivity, VoiceDiagActivity::class.java)) })
            page.content.add(navRow(getString(R.string.voice_problems_title), getString(R.string.voice_problems_summary)) { startActivity(Intent(this@VoiceSettingsActivity, ProblemsActivity::class.java)) })
            page.content.add(note(getString(R.string.voice_models_note)))
            page.content.add(header(getString(R.string.voice_privacy_header)))
            page.content.add(switchRow(getString(R.string.voice_offline), getString(R.string.voice_offline_summary), Prefs.VOICE_OFFLINE, false))
            page.content.add(note(getString(R.string.voice_credits, VoiceModels.ALL.joinToString("; ") { it.credit })))
        }
        setContentView(page.root)
        render()
        renderGoogle()
    }

    override fun onResume() {
        super.onResume()
        if (::googleBox.isInitialized) renderGoogle()
    }

    /** Google voice only works when the phone's speech service app may use the microphone (found on the owner's vivo: error 9 until it was allowed). */
    private fun renderGoogle() {
        googleBox.removeAllViews()
        val pkg = "com.google.android.tts"
        val known = try {
            packageManager.getPackageInfo(pkg, 0)
            true
        } catch (e: Exception) {
            false
        }
        if (!known || packageManager.checkPermission(android.Manifest.permission.RECORD_AUDIO, pkg) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        with(ui) {
            googleBox.add(infoRow(getString(R.string.voice_google_perm_title), getString(R.string.voice_google_perm_summary)))
            googleBox.add(buttonRow(getString(R.string.voice_google_perm_open), null) {
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + pkg)))
            })
        }
    }

    override fun onDestroy() {
        downloads.values.forEach { it.cancel() }
        super.onDestroy()
    }

    private fun mb(bytes: Long) = "%d MB".format((bytes + 500_000L) / 1_000_000L)

    private fun render() {
        modelBox.removeAllViews()
        with(ui) {
            for (m in VoiceModels.ALL) {
                val installed = VoiceModels.installed(this@VoiceSettingsActivity, m)
                when {
                    downloads[m.id] != null -> {
                        val pct = ((progress[m.id] ?: 0L) * 100 / m.bytes).toInt().coerceIn(0, 100)
                        modelBox.add(infoRow(m.title, getString(R.string.voice_model_downloading, pct, mb(m.bytes))))
                        modelBox.add(buttonRow(getString(R.string.voice_model_cancel), null) { downloads[m.id]?.cancel() })
                    }
                    installed -> {
                        modelBox.add(infoRow(m.title, getString(R.string.voice_model_installed, mb(m.bytes))))
                        modelBox.add(buttonRow(getString(R.string.voice_model_delete), null) {
                            VoiceModels.delete(this@VoiceSettingsActivity, m)
                            render()
                        })
                    }
                    else -> {
                        val part = VoiceModels.partBytes(this@VoiceSettingsActivity, m)
                        val summary = if (part > 0) getString(R.string.voice_model_partial, mb(part), mb(m.bytes)) else getString(R.string.voice_model_missing, mb(m.bytes))
                        modelBox.add(infoRow(m.title, summary))
                        modelBox.add(buttonRow(getString(R.string.voice_model_download), null) { askDownload(m) })
                        modelBox.add(buttonRow(getString(R.string.voice_model_import), getString(R.string.voice_model_import_summary)) { pickFile(m) })
                    }
                }
            }
            val vadOk = VoiceModels.installed(this@VoiceSettingsActivity, VoiceModels.VAD)
            modelBox.add(infoRow(VoiceModels.VAD.title, getString(if (vadOk) R.string.voice_vad_installed else R.string.voice_vad_missing)))
            if (!vadOk) modelBox.add(buttonRow(getString(R.string.voice_model_download), null) {
                VoiceModels.ensureVad(this@VoiceSettingsActivity)
                modelBox.postDelayed({ if (!isDestroyed) render() }, 4000L)
            })
        }
    }

    private fun askDownload(m: VoiceModel) {
        if (Prefs.of(this).getBoolean(Prefs.VOICE_OFFLINE, false)) {
            Toast.makeText(this, R.string.voice_err_offline, Toast.LENGTH_LONG).show()
            return
        }
        val host = android.net.Uri.parse(m.url).host ?: ""
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle(R.string.voice_model_download)
            .setMessage(getString(R.string.voice_model_download_ask, mb(m.bytes), host))
            .setPositiveButton(R.string.voice_model_download) { _, _ -> startDownload(m) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startDownload(m: VoiceModel) {
        VoiceModels.ensureVad(this) // the speech detector (1 MB) comes along
        progress[m.id] = VoiceModels.partBytes(this, m)
        downloads[m.id] = VoiceModels.Download(this, m, object : VoiceModels.Download.Listener {
            override fun onProgress(done: Long, total: Long) {
                if (isDestroyed) return
                progress[m.id] = done
                render()
            }

            override fun onDone(ok: Boolean, message: String?) {
                downloads.remove(m.id)
                if (isDestroyed) return
                if (!ok && message != "cancelled") Toast.makeText(this@VoiceSettingsActivity, getString(R.string.voice_model_failed, message ?: ""), Toast.LENGTH_LONG).show()
                render()
            }
        }).also { it.start() }
        render()
    }

    private fun pickFile(m: VoiceModel) {
        importFor = m
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
        @Suppress("DEPRECATION")
        startActivityForResult(i, PICK)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        val m = importFor
        if (requestCode != PICK || resultCode != RESULT_OK || uri == null || m == null) return
        Toast.makeText(this, R.string.voice_model_importing, Toast.LENGTH_SHORT).show()
        Thread({
            val ok = VoiceModels.importFrom(this, m, uri)
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
