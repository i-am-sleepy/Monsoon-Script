package com.monsoonscript.vault

import android.content.Context
import android.net.Uri
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

/**
 * How the session reaches a file the owner picked (E1.17): an attachment to read, or the place to save one. The service uses [ContentUriStreams]; the tests use
 * plain files. The session itself stays free of Android contexts.
 */
interface UriStreams {
    @Throws(java.io.IOException::class)
    fun input(uri: Uri): InputStream

    @Throws(java.io.IOException::class)
    fun output(uri: Uri): OutputStream

    companion object {
        /** For a session that never touches picked files (the older tests). */
        val NONE: UriStreams = object : UriStreams {
            override fun input(uri: Uri): InputStream = throw FileNotFoundException("No file access")
            override fun output(uri: Uri): OutputStream = throw FileNotFoundException("No file access")
        }
    }
}

/** Reads and writes picked documents through the content resolver (the grant of the file picker belongs to the app's uid, so it holds in the vault process too). */
class ContentUriStreams(private val context: Context) : UriStreams {
    override fun input(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException("The file could not be opened")

    override fun output(uri: Uri): OutputStream =
        context.contentResolver.openOutputStream(uri, "wt") ?: throw FileNotFoundException("The place could not be opened")
}
