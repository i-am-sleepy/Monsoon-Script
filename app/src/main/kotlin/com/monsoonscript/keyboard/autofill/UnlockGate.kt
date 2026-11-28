package com.monsoonscript.keyboard.autofill

import android.app.Activity
import android.content.Intent
import com.monsoonscript.keyboard.vault.VaultHub
import com.monsoonscript.keyboard.vault.VaultUnlockActivity

/**
 * Makes sure the vault is open before an Autofill screen goes on (E1.19, D100): if it is locked, the see-through master-password card opens over the app and
 * the owner unlocks there (fingerprint or master password, like for the keyboard's picker). The card is its own task, so there is no result to wait for: when
 * it closes, this screen is back in front and [resume] looks again. Call [resume] from the screen's onResume.
 */
internal class UnlockGate(private val activity: Activity, private val onUnlocked: () -> Unit, private val onCancelled: () -> Unit) {
    private var asked = false
    private var checking = false
    private var ready = false

    fun resume() {
        if (ready || checking) return
        checking = true
        VaultHub.client(activity).async({ it.status().unlocked }) { r ->
            checking = false
            if (activity.isFinishing || activity.isDestroyed) return@async
            when {
                r.getOrNull() == true -> {
                    ready = true
                    onUnlocked()
                }
                !asked -> {
                    asked = true
                    // quiet: the card must not tell the keyboard to open its picker (that is for the keyboard's own use of the card)
                    activity.startActivity(Intent(activity, VaultUnlockActivity::class.java).putExtra(VaultUnlockActivity.EXTRA_QUIET, true))
                }
                else -> onCancelled() // back from the card and still locked: the owner gave up
            }
        }
    }
}
