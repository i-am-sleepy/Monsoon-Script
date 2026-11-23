package com.monsoonscript.keyboard.clipboard

import android.content.ClipData
import android.content.Intent
import android.net.Uri

/** The share sheet for saved pictures (E3.8, E3.10): the keyboard panel and the app's screens build it the same way. */
object ImageShare {
    /** The chooser for [clip] (an image clip), or null when it has no file. The caller adds NEW_TASK when it starts it from a service. */
    fun chooser(images: ImageFiles, clip: ClipItem): Intent? = chooserMany(images, listOf(clip), "")

    /**
     * The chooser for one or more picture clips, with [text] (may be empty) along as the message; null when none of them has a file. One picture is a normal
     * send, several are one "send multiple" so the other app gets them all (D77).
     */
    fun chooserMany(images: ImageFiles, clips: List<ClipItem>, text: String): Intent? {
        val uris = ArrayList<Uri>(clips.size)
        var mime: String? = null
        var same = true
        for (c in clips) {
            val name = c.image ?: continue
            uris.add(images.uri(name))
            val m = c.mime ?: "image/*"
            if (mime == null) mime = m else if (mime != m) same = false
        }
        if (uris.isEmpty()) return null
        val send = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE)
            .setType(if (same) mime else "image/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (uris.size == 1) send.putExtra(Intent.EXTRA_STREAM, uris[0]) else send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        if (text.isNotEmpty()) send.putExtra(Intent.EXTRA_TEXT, text)
        val data = ClipData.newRawUri("", uris[0]) // lets the chooser hand the read permission on, for every picture
        for (i in 1 until uris.size) data.addItem(ClipData.Item(uris[i]))
        send.clipData = data
        val chooser = Intent.createChooser(send, null)
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        chooser.clipData = data
        return chooser
    }
}
