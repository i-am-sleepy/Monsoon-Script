package com.monsoonscript.keyboard.autofill

import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import com.monsoonscript.keyboard.vault.VaultHub
import com.monsoonscript.keyboard.vault.VaultPrefs
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The password vault as an Android Autofill service (E1.19, D100): when the owner taps a login field in an app or a browser page, Android asks here what to
 * offer. The answer is only titles and user names of the entries that belong to that app or site; the password is fetched after the tap (see
 * [AutofillResponses]). A locked vault offers "Unlock vault". Nothing here keeps a vault open or holds a secret.
 */
internal class MonsoonAutofillService : AutofillService() {
    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        val form = if (structure != null) AutofillParser.parse(structure, packageName) else null
        if (form == null || !VaultPrefs(this).hasVault) {
            callback.onSuccess(null)
            return
        }
        val cancelled = AtomicBoolean(false)
        cancellationSignal.setOnCancelListener { cancelled.set(true) }
        VaultHub.client(this).async({ v -> if (v.status().unlocked) v.matches(form.webDomain ?: "", form.packageName) else null }) { r ->
            if (cancelled.get()) return@async
            val response = r.fold(
                onSuccess = { matches -> if (matches == null) AutofillResponses.unlockResponse(this, form) else AutofillResponses.matchResponse(this, form, matches) },
                onFailure = { e -> if (VaultHub.isLocked(e)) AutofillResponses.unlockResponse(this, form) else null }, // not reachable: offer nothing rather than an error
            )
            callback.onSuccess(response)
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess() // the save prompt comes with E1.20
    }
}
