package com.monsoonscript.keyboard.clipboard

/** A clip in a backup. [folder] is the id of a [BackupFolder] of the same backup, 0 = none. Image clips name their file inside the backup's `images/` folder. */
class BackupClip(
    val kind: Int,
    val text: String,
    val image: String?,
    val mime: String?,
    val pinned: Boolean,
    val folder: Long,
    val created: Long,
    val used: Long,
)

class BackupFolder(val id: Long, val name: String, val created: Long)

class BackupShortcut(val trigger: String, val text: String)

/** Everything a backup file carries (E3.9, D71). [includesImages] = the file has the image clips and their files. */
class BackupData(
    val created: Long,
    val includesImages: Boolean,
    val folders: List<BackupFolder>,
    val clips: List<BackupClip>,
    val shortcuts: List<BackupShortcut>,
)

/** What an import did. [added] counts clips that are in the history now; [trimmed] imported clips the history limit or auto-delete removed again. */
class ImportResult(val added: Int, val skipped: Int, val trimmed: Int, val foldersAdded: Int, val shortcutsAdded: Int, val shortcutsSkipped: Int)

/**
 * The text of a backup file (`backup.json` inside the zip): version 1, with an `encrypted` field (always false for now) so a password-protected variant can be added
 * later without older apps misreading it (D71). Pure Kotlin, unit tested.
 */
object ClipBackup {
    const val APP = "monsoon-clipboard"
    const val FORMAT = 1

    const val OK = 0
    const val NOT_A_BACKUP = 1
    const val TOO_NEW = 2
    const val ENCRYPTED = 3
    const val DAMAGED = 4

    /** [data] or the reason there is none ([error], one of the constants above). */
    class Decoded(val data: BackupData?, val error: Int)

    fun encode(b: BackupData): String {
        val root = LinkedHashMap<String, Any?>()
        root["app"] = APP
        root["format"] = FORMAT
        root["encrypted"] = false
        root["created"] = b.created
        root["images"] = b.includesImages
        root["folders"] = b.folders.map { linkedMapOf<String, Any?>("id" to it.id, "name" to it.name, "created" to it.created) }
        root["clips"] = b.clips.map { c ->
            val m = LinkedHashMap<String, Any?>()
            m["kind"] = if (c.kind == ClipKind.IMAGE) "image" else "text"
            if (c.kind == ClipKind.IMAGE) {
                m["image"] = c.image
                m["mime"] = c.mime
            } else {
                m["text"] = c.text
            }
            m["pinned"] = c.pinned
            if (c.folder != 0L) m["folder"] = c.folder
            m["created"] = c.created
            m["used"] = c.used
            m
        }
        root["shortcuts"] = b.shortcuts.map { linkedMapOf<String, Any?>("trigger" to it.trigger, "text" to it.text) }
        return MiniJson.write(root)
    }

    fun decode(json: String): Decoded {
        val root = try {
            MiniJson.parse(json) as? Map<*, *>
        } catch (e: IllegalArgumentException) {
            return Decoded(null, DAMAGED)
        } ?: return Decoded(null, NOT_A_BACKUP)
        if (root["app"] != APP) return Decoded(null, NOT_A_BACKUP)
        val format = (root["format"] as? Long) ?: return Decoded(null, NOT_A_BACKUP)
        if (format > FORMAT) return Decoded(null, TOO_NEW)
        if (root["encrypted"] == true) return Decoded(null, ENCRYPTED)
        try {
            val folders = ArrayList<BackupFolder>()
            for (f in list(root["folders"])) {
                val m = f as? Map<*, *> ?: continue
                val id = m["id"] as? Long ?: continue
                val name = m["name"] as? String ?: continue
                folders.add(BackupFolder(id, name, m["created"] as? Long ?: 0L))
            }
            val clips = ArrayList<BackupClip>()
            for (c in list(root["clips"])) {
                val m = c as? Map<*, *> ?: continue
                val created = m["created"] as? Long ?: 0L
                val used = m["used"] as? Long ?: created
                val pinned = m["pinned"] == true
                val folder = m["folder"] as? Long ?: 0L
                if (m["kind"] == "image") {
                    val file = m["image"] as? String ?: continue
                    clips.add(BackupClip(ClipKind.IMAGE, "", file, m["mime"] as? String ?: "image/*", pinned, folder, created, used))
                } else {
                    val text = m["text"] as? String ?: continue
                    clips.add(BackupClip(ClipKind.TEXT, text, null, null, pinned, folder, created, used))
                }
            }
            val shortcuts = ArrayList<BackupShortcut>()
            for (s in list(root["shortcuts"])) {
                val m = s as? Map<*, *> ?: continue
                val trigger = m["trigger"] as? String ?: continue
                val text = m["text"] as? String ?: continue
                shortcuts.add(BackupShortcut(trigger, text))
            }
            return Decoded(BackupData(root["created"] as? Long ?: 0L, root["images"] == true, folders, clips, shortcuts), OK)
        } catch (e: RuntimeException) {
            return Decoded(null, DAMAGED)
        }
    }

    private fun list(v: Any?): List<Any?> = v as? List<*> ?: emptyList<Any?>()
}
