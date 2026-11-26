package com.monsoonscript.keyboard.theme

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import com.monsoonscript.keyboard.R

/** The Share button of a theme (L10 step 3, D93): sends the shareable line, with a short explanation of how to use it, to any app the person picks. */
internal object ThemeSharing {
    fun send(activity: Activity, spec: ThemeSpec) {
        val text = activity.getString(R.string.theme_share_message, spec.name, ThemeShare.encode(spec))
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            activity.startActivity(Intent.createChooser(send, activity.getString(R.string.theme_share_chooser)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(activity, R.string.theme_share_failed, Toast.LENGTH_LONG).show()
        }
    }
}
