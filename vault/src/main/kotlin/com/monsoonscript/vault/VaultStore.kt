package com.monsoonscript.vault

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Where the vault bytes live (D35: a file the owner picked with Android's file picker, so in the service it is a document Uri;
 * in tests a plain file). The session only needs these four things, so it never knows which one it has.
 */
interface VaultStore {
    /** A short name for messages and for naming backups. */
    val displayName: String

    /** Private folder where the last versions of this vault are kept before they are overwritten (D36). */
    val backupDir: File

    /** Opens the stored bytes for reading; throws IOException when the file is gone. */
    fun openInput(): InputStream

    /** Opens the location for writing and replaces its content. */
    fun openOutput(): OutputStream

    /** A short text that changes whenever the file changes (size + modified time), or null when it cannot be told (D50). */
    fun stamp(): String?
}

class FileVaultStore(private val file: File, override val backupDir: File) : VaultStore {
    override val displayName: String get() = file.name
    override fun openInput(): InputStream = file.inputStream()
    override fun openOutput(): OutputStream = file.outputStream()
    override fun stamp(): String? = if (file.exists()) "${file.length()}:${file.lastModified()}" else null
}

/**
 * D36: "the last few versions are kept as backups". Called right before the vault is overwritten.
 * The first save of a session always keeps the version that was just opened; later saves (every change saves) only keep one when the newest
 * backup is older than [MIN_GAP_MS], so ten quick edits do not push every useful older version out of the [KEEP] slots.
 */
internal object VaultBackups {
    const val KEEP = 5
    const val MIN_GAP_MS = 10 * 60 * 1000L

    fun backup(store: VaultStore, force: Boolean, now: Long = System.currentTimeMillis()) {
        val dir = store.backupDir
        if (!dir.isDirectory && !dir.mkdirs()) return
        val existing = list(dir)
        if (!force && existing.isNotEmpty() && now - existing.last().lastModified() < MIN_GAP_MS) return

        val incoming = File(dir, ".incoming")
        try {
            store.openInput().use { input -> incoming.outputStream().use { output -> input.copyTo(output) } }
        } catch (e: IOException) {
            incoming.delete() // nothing to keep: the file does not exist yet (a brand-new vault) or cannot be read
            return
        }
        if (incoming.length() == 0L || (existing.isNotEmpty() && sameContent(incoming, existing.last()))) {
            incoming.delete()
            return
        }
        val safeName = store.displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
        val target = File(dir, "$safeName-$now.bak")
        if (!incoming.renameTo(target)) {
            incoming.delete()
            return
        }
        target.setLastModified(now)
        list(dir).dropLast(KEEP).forEach { it.delete() }
    }

    /** Backups, oldest first. */
    fun list(dir: File): List<File> =
        (dir.listFiles { f -> f.isFile && f.name.endsWith(".bak") } ?: emptyArray()).sortedBy { it.lastModified() }

    private fun sameContent(a: File, b: File): Boolean {
        if (a.length() != b.length()) return false
        a.inputStream().buffered().use { ia ->
            b.inputStream().buffered().use { ib ->
                while (true) {
                    val x = ia.read()
                    val y = ib.read()
                    if (x != y) return false
                    if (x == -1) return true
                }
            }
        }
    }
}
