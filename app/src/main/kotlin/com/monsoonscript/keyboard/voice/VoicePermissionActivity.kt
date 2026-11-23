package com.monsoonscript.keyboard.voice

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.prefs.Prefs

/**
 * A keyboard cannot show Android's permission dialog itself, so the first use of the mic key opens this see-through screen over the current app (same idea
 * as the vault unlock screen, D37): first the one-time notice that Google's speech service gets the audio (D66), then the microphone permission. It
 * closes by itself; the owner holds the mic key again afterwards.
 */
class VoicePermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The notice is about Google's speech service: it is only needed when the owner uses that engine (a local model sends nothing anywhere).
        if (!intent.getBooleanExtra(EXTRA_NOTICE, true) || Prefs.of(this).getBoolean(Prefs.VOICE_NOTICE_OK, false)) {
            askMicrophone()
            return
        }
        AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle(R.string.voice_notice_title)
            .setMessage(R.string.voice_notice_text)
            .setPositiveButton(R.string.voice_notice_ok) { _, _ ->
                Prefs.of(this).edit().putBoolean(Prefs.VOICE_NOTICE_OK, true).apply()
                askMicrophone()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun askMicrophone() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }
        requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        Toast.makeText(this, if (granted) R.string.voice_perm_granted else R.string.voice_perm_denied, Toast.LENGTH_LONG).show()
        finish()
    }

    companion object {
        private const val REQUEST = 71
        private const val EXTRA_NOTICE = "notice"

        fun open(context: Context, showNotice: Boolean = true) {
            context.startActivity(
                Intent(context, VoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EXTRA_NOTICE, showNotice),
            )
        }
    }
}
