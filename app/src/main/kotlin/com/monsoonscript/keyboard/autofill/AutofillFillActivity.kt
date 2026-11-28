package com.monsoonscript.keyboard.autofill

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.view.autofill.AutofillManager
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.vault.VaultHub

/**
 * The invisible screen behind a tap in the Autofill dropdown (E1.19, D100). It makes sure the vault is open (the unlock card opens over it if not), then hands
 * the framework what the tap asked for: the filled-in entry (MODE_FILL) or, after "Unlock vault", the real list (MODE_UNLOCK). It shows nothing of its own, so
 * a locked vault costs the owner one card and one tap more, not a trip to the vault app.
 */
internal class AutofillFillActivity : Activity() {
    private var gate: UnlockGate? = null
    private var form: ParsedForm? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        val f = intent.form()
        if (f == null || savedInstanceState != null) { // recreated by the system: start over from the dropdown
            cancel()
            return
        }
        form = f
        gate = UnlockGate(this, ::proceed, ::cancel)
    }

    override fun onResume() {
        super.onResume()
        gate?.resume()
    }

    private fun proceed() {
        val f = form ?: return cancel()
        val client = VaultHub.client(this)
        when (intent.getStringExtra(X_MODE)) {
            AutofillResponses.MODE_UNLOCK -> {
                val chips = InlineChips.fromIntent(this, intent) // the owner tapped the "Unlock vault" chip: the list comes back as chips again
                client.async({ it.matches(f.webDomain ?: "", f.packageName) }) { r ->
                    r.fold({ matches -> finishWith(AutofillResponses.matchResponse(this, f, matches, chips)) }, ::fail)
                }
            }
            AutofillResponses.MODE_FILL -> {
                val id = intent.getStringExtra(X_ENTRY) ?: return cancel()
                client.async({ AutofillResponses.filled(it, id, f) }) { r ->
                    r.fold({ dataset ->
                        if (dataset == null) {
                            Toast.makeText(this, R.string.autofill_nothing, Toast.LENGTH_LONG).show()
                            cancel()
                        } else {
                            finishWith(dataset)
                        }
                    }, ::fail)
                }
            }
            else -> cancel()
        }
    }

    private fun finishWith(result: Parcelable?) {
        if (isFinishing) return
        if (result == null) return cancel()
        setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, result))
        finish()
    }

    private fun fail(e: Throwable) {
        if (!VaultHub.isLocked(e)) Toast.makeText(this, VaultHub.message(this, e), Toast.LENGTH_LONG).show()
        cancel()
    }

    private fun cancel() {
        setResult(RESULT_CANCELED)
        finish()
    }
}
