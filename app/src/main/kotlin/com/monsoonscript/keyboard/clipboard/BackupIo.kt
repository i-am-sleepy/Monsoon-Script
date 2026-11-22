package com.monsoonscript.keyboard.clipboard

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Reads and writes the backup file (E3.9, D71): a zip with `backup.json` FIRST ([ClipBackup] text, UTF-8) and, when pictures are included, `images/<name>`.
 * The json being first lets an import check what the file is before it touches a single picture. Worker thread only (it does file work).
 */
object BackupIo {
    const val JSON_ENTRY = "backup.json"
    const val IMAGE_DIR = "images/"

    /** More than this is not a clipboard backup (a zip bomb, a wrong file): the text part is cut at 64 MB, entries at 20000. */
    private const val MAX_JSON_BYTES = 64 * 1024 * 1024
    private const val MAX_ENTRIES = 20_000

    /** What [read] found: [decoded] (see [ClipBackup.Decoded]); [imageFiles] maps picture names of the file to the files saved here; [fresh] are files that were new. */
    class Read(val decoded: ClipBackup.Decoded, val imageFiles: Map<String, String>, val fresh: List<String>)

    fun write(out: OutputStream, data: BackupData, images: ImageFiles?) {
        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(JSON_ENTRY))
        zip.write(ClipBackup.encode(data).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        if (data.includesImages && images != null) {
            val done = HashSet<String>()
            for (c in data.clips) {
                val name = c.image ?: continue
                if (!ImageMime.isStoredName(name) || !done.add(name)) continue
                val f = images.file(name)
                if (!f.isFile) continue
                zip.putNextEntry(ZipEntry(IMAGE_DIR + name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        zip.finish()
        zip.flush()
    }

    fun read(input: InputStream, images: ImageFiles): Read {
        val zip = ZipInputStream(input)
        val first = try {
            zip.nextEntry
        } catch (e: Exception) {
            null
        }
        if (first == null || first.name != JSON_ENTRY) return Read(ClipBackup.Decoded(null, ClipBackup.NOT_A_BACKUP), emptyMap(), emptyList())
        val json = readLimited(zip) ?: return Read(ClipBackup.Decoded(null, ClipBackup.DAMAGED), emptyMap(), emptyList())
        val decoded = ClipBackup.decode(json)
        val data = decoded.data ?: return Read(decoded, emptyMap(), emptyList())
        val map = HashMap<String, String>()
        val fresh = ArrayList<String>()
        if (data.includesImages) {
            var entries = 0
            try {
                while (entries++ < MAX_ENTRIES) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory || !e.name.startsWith(IMAGE_DIR)) continue
                    val name = e.name.substring(IMAGE_DIR.length)
                    if (!ImageMime.isStoredName(name)) continue
                    val got = images.importStream(zip) ?: continue
                    map[name] = got.name
                    if (got.fresh) fresh.add(got.name)
                }
            } catch (e: Exception) {
                // a damaged tail: keep the pictures read so far
            }
        }
        return Read(decoded, map, fresh)
    }

    private fun readLimited(zip: ZipInputStream): String? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(32 * 1024)
        var total = 0
        while (true) {
            val n = zip.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_JSON_BYTES) return null
            out.write(buf, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
