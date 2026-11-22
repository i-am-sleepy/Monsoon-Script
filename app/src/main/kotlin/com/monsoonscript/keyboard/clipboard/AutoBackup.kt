package com.monsoonscript.keyboard.clipboard

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes an automatic backup into the folder the owner picked once (E3.9, D71): one zip per backup named `monsoon-clipboard-auto-<date>-<time>.zip`, the newest
 * [KEEP] stay. Android's document-tree access, so no storage permission. Worker thread only.
 */
object AutoBackup {
    const val KEEP = 5
    private const val PREFIX = "monsoon-clipboard-auto-"

    /** True when the backup is in the folder. A half-written file is removed again. */
    fun write(context: Context, tree: Uri, data: BackupData, images: ImageFiles?, now: Long): Boolean {
        val resolver = context.contentResolver
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, treeId)
        val name = PREFIX + SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(now)) + ".zip"
        val doc = DocumentsContract.createDocument(resolver, parent, "application/zip", name) ?: return false
        var ok = false
        try {
            resolver.openOutputStream(doc)?.use {
                BackupIo.write(it, data, images)
                ok = true
            }
        } catch (e: Exception) {
            ok = false
        }
        if (!ok) {
            try {
                DocumentsContract.deleteDocument(resolver, doc)
            } catch (e: Exception) {
                // nothing more to do
            }
            return false
        }
        try {
            rotate(context, tree, treeId)
        } catch (e: Exception) {
            // the backup itself is written; old ones stay until the next time
        }
        return true
    }

    /** Delete the oldest of our backups so that [KEEP] remain. */
    private fun rotate(context: Context, tree: Uri, treeId: String) {
        val resolver = context.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
        val ours = ArrayList<Pair<String, String>>() // display name, document id
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val n = c.getString(1) ?: continue
                if (n.startsWith(PREFIX) && n.endsWith(".zip")) ours.add(n to id)
            }
        }
        ours.sortByDescending { it.first } // the date is in the name: newest first
        for (i in KEEP until ours.size) {
            DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, ours[i].second))
        }
    }
}
