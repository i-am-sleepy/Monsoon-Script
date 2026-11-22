package com.monsoonscript.keyboard.clipboard

import android.content.ClipData
import android.content.Intent

/** The share sheet for a saved picture (E3.8): the keyboard panel and the app's screens build it the same way. */
object ImageShare {
    /** The chooser for [clip] (an image clip), or null when it has no file. The caller adds NEW_TASK when it starts it from a service. */
    fun chooser(images: ImageFiles, clip: ClipItem): Intent? {
        val name = clip.image ?: return null
        val uri = images.uri(name)
        val send = Intent(Intent.ACTION_SEND)
            .setType(clip.mime ?: "image/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri("", uri) // lets the chooser hand the read permission on
        val chooser = Intent.createChooser(send, null)
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        chooser.clipData = send.clipData
        return chooser
    }
}
