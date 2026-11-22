package com.monsoonscript.keyboard.clipboard

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest

/**
 * The private folder of copied pictures (E3.8, D70): `files/clip-images/` inside the app, invisible to the Gallery and to other apps. Per picture two files:
 * the original bytes `<sha256>.<ext>` (kept as they came, so a GIF stays animated when pasted) and a small thumbnail `<sha256>.<ext>.t` for the cards.
 * The name is the SHA-256 of the bytes, so the same picture is stored once.
 *
 * Everything here does file work: call it from a worker thread, never from the main thread (except the cheap [uri], [file] and [sizeOf]).
 */
class ImageFiles(context: Context) {
    private val app: Context = context.applicationContext
    val dir: File = File(app.filesDir, "clip-images")

    /** The authority of [ImageProvider] (declared in the manifest as `${applicationId}.clipimages`). */
    val authority: String = app.packageName + ".clipimages"

    /** A saved picture. [fresh] = its file was written now (false when the same picture was stored before). */
    class Imported(val name: String, val mime: String, val fresh: Boolean)

    fun file(name: String): File = File(dir, name)

    fun thumbFile(name: String): File = File(dir, "$name.t")

    /** The `content://` address other apps use to read a stored picture (through [ImageProvider]). */
    fun uri(name: String): Uri = Uri.Builder().scheme("content").authority(authority).appendPath(name).build()

    /** Disk space of one picture: original and thumbnail. */
    fun sizeOf(name: String): Long = file(name).length() + thumbFile(name).length()

    fun delete(names: List<String>) {
        for (n in names) {
            if (!ImageMime.isStoredName(n)) continue
            file(n).delete()
            thumbFile(n).delete()
        }
    }

    /**
     * Copy the picture behind [uri] into the folder. Returns null when it cannot be read, is larger than [MAX_BYTES], or is not a picture Android can decode.
     * The type is read from the bytes, not taken from what the clip announced.
     */
    fun import(resolver: ContentResolver, uri: Uri): Imported? =
        try {
            resolver.openInputStream(uri)?.use { importStream(it) }
        } catch (e: Exception) {
            null // unreadable, permission gone ...
        }

    /** Same as [import] for a picture that arrives as a stream (a backup file); the caller closes [input]. */
    fun importStream(input: InputStream): Imported? {
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val tmp = File(dir, TMP_PREFIX + System.nanoTime())
        try {
            val sha = copyLimited(input, tmp) ?: return null
            if (tmp.length() == 0L) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(tmp.path, bounds)
            val mime = bounds.outMimeType ?: return null
            if (!mime.startsWith("image/") || bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val name = sha + "." + ImageMime.extFor(mime)
            val dest = file(name)
            val thumb = thumbFile(name)
            if (!thumb.exists() && !makeThumb(tmp, bounds.outWidth, bounds.outHeight, thumb)) return null
            if (dest.exists()) return Imported(name, mime, false)
            if (!tmp.renameTo(dest)) {
                thumb.delete()
                return null
            }
            return Imported(name, mime, true)
        } catch (e: Exception) {
            return null // unreadable, permission gone, disk full ...
        } finally {
            tmp.delete() // nothing left behind unless it was renamed
        }
    }

    /** The thumbnail of a stored picture, or null when it is missing or broken. */
    fun loadThumb(name: String): Bitmap? =
        try {
            if (ImageMime.isStoredName(name)) BitmapFactory.decodeFile(thumbFile(name).path) else null
        } catch (e: Exception) {
            null
        }

    /** Delete files no clip refers to (left over after a crash). Only files older than an hour, so a picture that is being added right now is safe. */
    fun sweep(known: Set<String>) {
        val files = dir.listFiles() ?: return
        val limit = System.currentTimeMillis() - HOUR
        for (f in files) {
            if (f.lastModified() > limit) continue
            val base = if (f.name.endsWith(".t")) f.name.substring(0, f.name.length - 2) else f.name
            if (base !in known) f.delete()
        }
    }

    /** Copy [src] into [out]; the SHA-256 in hex, or null when it is bigger than [MAX_BYTES]. */
    private fun copyLimited(src: InputStream, out: File): String? {
        val md = MessageDigest.getInstance("SHA-256")
        var total = 0L
        FileOutputStream(out).use { dst ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = src.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) return null
                md.update(buf, 0, n)
                dst.write(buf, 0, n)
            }
        }
        val sb = StringBuilder(64)
        for (b in md.digest()) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 15])
        }
        return sb.toString()
    }

    /** Decode a reduced copy and save it as a small PNG (when it has transparency) or JPEG. */
    private fun makeThumb(src: File, w: Int, h: Int, out: File): Boolean {
        val longSide = maxOf(w, h)
        var sample = 1
        while (longSide / (sample * 2) >= THUMB_PX) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeFile(src.path, opts) ?: return false
        var bmp = decoded
        val scale = THUMB_PX.toFloat() / maxOf(decoded.width, decoded.height)
        if (scale < 1f) {
            val tw = maxOf(1, (decoded.width * scale + 0.5f).toInt())
            val th = maxOf(1, (decoded.height * scale + 0.5f).toInt())
            bmp = Bitmap.createScaledBitmap(decoded, tw, th, true)
        }
        try {
            FileOutputStream(out).use { os ->
                val format = if (bmp.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                return bmp.compress(format, 82, os)
            }
        } finally {
            if (bmp !== decoded) bmp.recycle()
            decoded.recycle()
        }
    }

    companion object {
        /** Larger pictures are not saved (D70). */
        const val MAX_BYTES = 10L * 1024 * 1024

        /** Longest side of a thumbnail in pixels. */
        const val THUMB_PX = 160

        private const val TMP_PREFIX = "tmp-"
        private const val HOUR = 3_600_000L
        private val HEX = "0123456789abcdef".toCharArray()
    }
}
