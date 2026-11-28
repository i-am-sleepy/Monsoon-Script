package com.monsoonscript.keyboard.autofill

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.autofill.AutofillManager
import android.widget.Toast
import com.monsoonscript.keyboard.R

/** Turning the vault on as the phone's Autofill service (E1.19.3, D100): the status for the Settings row and the way to Android's own confirmation screen. */
internal object AutofillSetup {
    private fun manager(context: Context): AutofillManager? = context.getSystemService(AutofillManager::class.java)

    /** This phone has autofill services at all (a few stripped-down phones do not). */
    fun supported(context: Context): Boolean = manager(context)?.isAutofillSupported == true

    /** Our service is the one the owner picked in Android's settings. */
    fun isOn(context: Context): Boolean = manager(context)?.hasEnabledAutofillServices() == true

    /**
     * Opens Android's "Make Monsoon Script vault your autofill service?" question (the owner confirms there, an app cannot do it alone). The answer comes back
     * as the activity result with [requestCode] (returns true). Where that screen does not exist, the phone's settings open instead, with a hint on what to
     * look for (returns false: no result will come).
     */
    fun request(activity: Activity, requestCode: Int): Boolean {
        val ask = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE, Uri.parse("package:${activity.packageName}"))
        try {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(ask, requestCode)
            return true
        } catch (_: ActivityNotFoundException) {
        }
        try {
            activity.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: ActivityNotFoundException) {
        }
        Toast.makeText(activity, R.string.autofill_open_failed, Toast.LENGTH_LONG).show()
        return false
    }
}
