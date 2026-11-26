package com.monsoonscript.keyboard.vault

import android.annotation.TargetApi
import android.app.Activity
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import javax.crypto.Cipher

/**
 * The phone's own fingerprint prompt (platform BiometricPrompt, like the clipboard lock's), tied to a Keystore cipher: the cipher only works when the prompt
 * succeeds. Fingerprint only (strong sensors, D91); the button under the prompt is the way back to the master password. [done] is called exactly once, on the
 * main thread, unless [cancel] was called first (the screen went away: nothing is delivered).
 */
@TargetApi(Build.VERSION_CODES.P)
internal class FingerprintPrompt(private val activity: Activity) {
    enum class Outcome { OK, USE_PASSWORD, CANCELLED, LOCKED_OUT, ERROR }

    private var cancelCurrent: (() -> Unit)? = null

    fun show(cipher: Cipher, title: String, subtitle: String?, negative: String, done: (Outcome, Cipher?) -> Unit) {
        var ended = false // per prompt: a late answer of an earlier prompt can never end this one
        fun end(o: Outcome, c: Cipher?) {
            if (ended) return
            ended = true
            cancelCurrent = null
            done(o, c)
        }
        val builder = BiometricPrompt.Builder(activity).setTitle(title)
        if (subtitle != null) builder.setSubtitle(subtitle)
        builder.setNegativeButton(negative, activity.mainExecutor) { _, _ -> end(Outcome.USE_PASSWORD, null) }
        if (Build.VERSION.SDK_INT >= 30) builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        val sig = CancellationSignal()
        cancelCurrent = {
            if (!ended) {
                ended = true
                sig.cancel()
            }
        }
        try {
            builder.build().authenticate(BiometricPrompt.CryptoObject(cipher), sig, activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    val c = result?.cryptoObject?.cipher
                    end(if (c != null) Outcome.OK else Outcome.ERROR, c)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    end(
                        when (errorCode) {
                            ERROR_CANCELED, ERROR_USER_CANCELED -> Outcome.CANCELLED
                            ERROR_NEGATIVE_BUTTON -> Outcome.USE_PASSWORD
                            ERROR_LOCKOUT, ERROR_LOCKOUT_PERMANENT -> Outcome.LOCKED_OUT
                            else -> Outcome.ERROR
                        },
                        null,
                    )
                }
            })
        } catch (e: RuntimeException) {
            end(Outcome.ERROR, null)
        }
    }

    /** The screen is going away: take the prompt down and deliver nothing. */
    fun cancel() {
        val c = cancelCurrent
        cancelCurrent = null
        c?.invoke()
    }

    private companion object {
        const val ERROR_CANCELED = 5
        const val ERROR_LOCKOUT = 7
        const val ERROR_LOCKOUT_PERMANENT = 9
        const val ERROR_USER_CANCELED = 10
        const val ERROR_NEGATIVE_BUTTON = 13
    }
}
