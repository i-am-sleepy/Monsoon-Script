package com.monsoonscript.keyboard.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
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

    /**
     * Android asked "Save password?" and the owner said yes (E1.20, D100). What was typed goes to the save screen through [SaveHandoff] (no password in an Intent);
     * the screen opens the unlock card first when the vault is locked. A login that is already stored asks nothing there.
     */
    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure // the last context holds what the fields contain now
        val form = if (structure != null) AutofillParser.parse(structure, packageName, forSave = true) else null
        val password = form?.passwordText
        if (form == null || password.isNullOrEmpty() || !VaultPrefs(this).hasVault) {
            callback.onSuccess()
            return
        }
        val token = SaveHandoff.put(PendingSave(form.packageName, form.webDomain, form.usernameText.orEmpty(), password))
        val intent = Intent(this, AutofillSaveActivity::class.java).putExtra(X_TOKEN, token).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 28) {
            // Android opens the screen itself after we answer: a background service may not start an activity from Android 10 on
            callback.onSuccess(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE).intentSender)
        } else {
            startActivity(intent)
            callback.onSuccess()
        }
    }
}
