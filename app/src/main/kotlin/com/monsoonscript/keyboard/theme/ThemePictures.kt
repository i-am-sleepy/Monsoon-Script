package com.monsoonscript.keyboard.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import com.monsoonscript.keyboard.view.Backdrop
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * The background pictures of the themes (L10 step 2, D87): a picture the owner picks is shrunk (longest side [PictureMath.MAX_EDGE]), turned upright and saved as a
 * small JPEG in the app's own folder, so it does not depend on the photo app, and a theme only remembers the file name ([ThemeSpec.picture]). A decoded picture is
 * kept in memory for the keyboard (the app and the keyboard are one process); at most [KEEP] of them.
 */
object ThemePictures {
    private const val TAG = "ThemePictures"
    private const val DIR = "theme_pictures"
    private const val KEEP = 3
    private val bitmaps = LinkedHashMap<String, Bitmap>()

    private fun dir(context: Context): File = File(context.filesDir, DIR).also { it.mkdirs() }

    fun file(context: Context, name: String?): File? = if (PictureMath.isValidFileName(name)) File(dir(context), name!!) else null

    private fun newName(context: Context): String {
        while (true) {
            val name = "p" + java.lang.Long.toString(System.currentTimeMillis(), 36) + (1000 + (Math.random() * 9000).toInt()) + ".jpg"
            if (!File(dir(context), name).exists()) return name
        }
    }

    /**
     * Read the picture at [uri] (the photo the owner chose), store it and give back its file name, or null when it could not be read. It takes a moment for
     * a big photo, so call it off the main thread.
     */
    fun importFrom(context: Context, uri: Uri): String? {
        return try {
            val resolver = context.contentResolver
            // first only the size: in this mode decodeStream ALWAYS returns null (the size is written into [bounds]), so its result must not be checked
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val first = resolver.openInputStream(uri) ?: return fail("cannot open $uri")
            first.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return fail("no size read from $uri (${bounds.outMimeType})")
            val opts = BitmapFactory.Options().apply { inSampleSize = PictureMath.sampleSize(bounds.outWidth, bounds.outHeight) }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return fail("cannot decode $uri")
            val rotation = try {
                resolver.openInputStream(uri)?.use { orientationDegrees(ExifInterface(it)) } ?: 0
            } catch (e: IOException) {
                0
            }
            val (tw, th) = PictureMath.scaledSize(decoded.width, decoded.height)
            val m = Matrix()
            m.postScale(tw.toFloat() / decoded.width, th.toFloat() / decoded.height)
            if (rotation != 0) m.postRotate(rotation.toFloat())
            var out = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true)
            if (out.hasAlpha()) { // a JPEG has no see-through parts: lay the picture on black
                val flat = Bitmap.createBitmap(out.width, out.height, Bitmap.Config.ARGB_8888)
                Canvas(flat).apply {
                    drawColor(Color.BLACK)
                    drawBitmap(out, 0f, 0f, null)
                }
                out = flat
            }
            val name = newName(context)
            val target = File(dir(context), name)
            FileOutputStream(target).use { out.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            name
        } catch (e: IOException) {
            fail("import failed", e)
        } catch (e: RuntimeException) {
            fail("import failed", e)
        } catch (e: OutOfMemoryError) {
            fail("import ran out of memory", e)
        }
    }

    /** Why an import gave nothing goes to the log (adb logcat -s ThemePictures), so a picture that "could not be read" can be traced. */
    private fun fail(why: String, e: Throwable? = null): String? {
        if (e != null) Log.w(TAG, why, e) else Log.w(TAG, why)
        return null
    }

    private fun orientationDegrees(exif: ExifInterface): Int = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    /** Delete the picture file [name] (nothing happens when there is none). */
    fun delete(context: Context, name: String?) {
        file(context, name)?.delete()
        synchronized(bitmaps) { bitmaps.keys.removeAll { it.startsWith("$name:") } }
    }

    /** A copy of the picture file [name] under a new name (for a copy of a theme), or null when there is nothing to copy. */
    fun copy(context: Context, name: String?): String? {
        val from = file(context, name)?.takeIf { it.isFile } ?: return null
        val copyName = newName(context)
        return try {
            from.copyTo(File(dir(context), copyName), overwrite = false)
            copyName
        } catch (e: IOException) {
            null
        }
    }

    /** The decoded picture of [spec] and its cache key, or null when it has no picture (or the file is gone or damaged). */
    private fun load(context: Context, spec: ThemeSpec): Pair<String, Bitmap>? {
        val name = spec.picture ?: return null
        val f = file(context, name)?.takeIf { it.isFile } ?: return null
        val key = "$name:${f.lastModified()}:${f.length()}"
        val bitmap = synchronized(bitmaps) {
            bitmaps[key] ?: BitmapFactory.decodeFile(f.path)?.also {
                bitmaps[key] = it
                while (bitmaps.size > KEEP) bitmaps.remove(bitmaps.keys.first())
            }
        } ?: return null
        return key to bitmap
    }

    /** The picture of [spec] itself (for the crop screen), or null. */
    fun bitmap(context: Context, spec: ThemeSpec): Bitmap? = load(context, spec)?.second

    /** The veil color of [spec]: its fade as a see-through color of the theme's own side (dark themes fade towards black, light ones towards white). */
    fun veil(spec: ThemeSpec): Int {
        val veilColor = if (spec.isLight) Color.WHITE else Color.BLACK
        return (PictureMath.veilAlpha(spec.pictureFade) shl 24) or (veilColor and 0x00FFFFFF)
    }

    /** The backdrop to draw for [spec], or null when it has no picture (or the file is gone or damaged: then the plain background color is used). */
    fun backdrop(context: Context, spec: ThemeSpec): Backdrop? {
        val (key, bitmap) = load(context, spec) ?: return null
        return Backdrop(key, bitmap, veil(spec), spec.cropPortrait, spec.cropLandscape)
    }
}
