package com.monsoonscript.keyboard.clipboard

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File
import java.io.FileNotFoundException

/**
 * Hands the saved pictures to other apps (E3.8, D70): the app a picture is pasted or shared into reads `content://<package>.clipimages/<name>` from here.
 * Not exported: Android lets another app in only for the one address it was explicitly given (grantUriPermissions). Read-only, and only files named like a
 * stored picture ([ImageMime.isStoredName]) are served, so no other file of the app can be reached and the thumbnails are never handed out.
 */
class ImageProvider : ContentProvider() {
    private var images: ImageFiles? = null

    override fun onCreate(): Boolean = true

    private fun files(): ImageFiles = images ?: ImageFiles(context!!).also { images = it }

    private fun fileFor(uri: Uri): File? {
        val segments = uri.pathSegments
        if (segments.size != 1) return null
        val name = segments[0]
        if (!ImageMime.isStoredName(name)) return null
        val f = files().file(name)
        return if (f.isFile) f else null
    }

    override fun getType(uri: Uri): String? {
        val f = fileFor(uri) ?: return null
        return ImageMime.mimeFor(f.name.substringAfterLast('.'))
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("read only")
        val f = fileFor(uri) ?: throw FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    /** Apps ask for the name and size of what they were given. */
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        val f = fileFor(uri) ?: return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val row = arrayOfNulls<Any>(cols.size)
        for (i in cols.indices) {
            row[i] = when (cols[i]) {
                OpenableColumns.DISPLAY_NAME -> "image." + f.name.substringAfterLast('.')
                OpenableColumns.SIZE -> f.length()
                else -> null
            }
        }
        return MatrixCursor(cols, 1).apply { addRow(row) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
