package com.monsoonscript.keyboard.clipboard

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.widget.Toast
import com.monsoonscript.keyboard.R

/**
 * The unlock prompt of the clipboard lock (E3.9, D71): no screen of its own, it only shows the phone's prompt on top of whatever called it (the keyboard
 * panel starts it from the keyboard, the app's lock screen from the app). Android 10 and newer: BiometricPrompt with fingerprint / face and the screen lock
 * as fallback. Android 8 and 9: the phone's PIN / pattern / password screen. On success [LockState.grant] is called, the result is RESULT_OK and it closes.
 *
 * With [EXTRA_VERIFY] it always asks, even when the lock is not switched on yet: the settings use it to prove the prompt works before the lock is turned on.
 */
class ClipUnlockActivity : Activity() {
    private var cancel: CancellationSignal? = null
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hub = ClipHub.get(this)
        val verify = intent.getBooleanExtra(EXTRA_VERIFY, false)
        if (!verify && hub.lock.isOpen()) { // nothing to unlock any more
            finishWith(true)
            return
        }
        val km = getSystemService(KeyguardManager::class.java)
        if (km == null || !km.isDeviceSecure) {
            Toast.makeText(this, R.string.clip_unlock_no_screen_lock, Toast.LENGTH_LONG).show()
            finishWith(false)
            return
        }
        if (savedInstanceState != null) { // recreated while a prompt was up: start over from the caller
            finishWith(false)
            return
        }
        if (Build.VERSION.SDK_INT >= 29) biometric() else credential(km)
    }

    private fun biometric() {
        val builder = BiometricPrompt.Builder(this)
            .setTitle(getString(R.string.clip_unlock_title))
            .setSubtitle(getString(R.string.clip_unlock_subtitle))
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        val signal = CancellationSignal()
        cancel = signal
        try {
            builder.build().authenticate(signal, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    finishWith(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    finishWith(false) // cancelled, too many tries, no sensor ...
                }
            })
        } catch (e: RuntimeException) {
            finishWith(false)
        }
    }

    private fun credential(km: KeyguardManager) {
        @Suppress("DEPRECATION")
        val prompt = km.createConfirmDeviceCredentialIntent(getString(R.string.clip_unlock_title), getString(R.string.clip_unlock_subtitle))
        if (prompt == null) {
            finishWith(false)
            return
        }
        @Suppress("DEPRECATION")
        startActivityForResult(prompt, REQUEST_CREDENTIAL)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CREDENTIAL) finishWith(resultCode == RESULT_OK)
    }

    private fun finishWith(ok: Boolean) {
        if (done) return
        done = true
        if (ok) ClipHub.get(this).lock.grant()
        setResult(if (ok) RESULT_OK else RESULT_CANCELED)
        finish()
    }

    override fun onDestroy() {
        if (!done) cancel?.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VERIFY = "verify"
        private const val REQUEST_CREDENTIAL = 1

        fun intent(context: android.content.Context, verify: Boolean = false): Intent =
            Intent(context, ClipUnlockActivity::class.java).putExtra(EXTRA_VERIFY, verify)
    }
}
