package com.monsoonscript.keyboard.clipboard

/** Image types of clipboard images (E3.8, D70): file extensions, the names of stored files and what a text field accepts. Pure Kotlin, unit tested. */
object ImageMime {

    /** The file extension for an image type; unknown image types get "img" (the real type is kept next to the clip). */
    fun extFor(mime: String): String =
        when (mime.lowercase()) {
            "image/png" -> "png"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "image/bmp" -> "bmp"
            "image/heic" -> "heic"
            "image/heif" -> "heif"
            else -> "img"
        }

    /** The image type for a stored file's extension (the provider answers with this). */
    fun mimeFor(ext: String): String =
        when (ext.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            else -> "image/*"
        }

    /** Short name for the card: PNG, JPEG, GIF, WEBP ... */
    fun label(mime: String?): String {
        if (mime == null) return "IMAGE"
        return when (val sub = mime.substringAfter('/', "").lowercase()) {
            "", "*" -> "IMAGE"
            "jpg" -> "JPEG"
            else -> sub.uppercase()
        }
    }

    /** Does one entry of an editor's accepted-type list (a wildcard like "image" + slash + star, a full type like "image/png", or a lone star) cover [mime]? */
    fun matches(pattern: String, mime: String): Boolean {
        val p = pattern.trim().lowercase()
        val m = mime.trim().lowercase()
        if (p.isEmpty() || m.isEmpty()) return false
        if (p == "*" || p == "*/*") return true
        if (p == m) return true
        if (p.endsWith("/*")) return m.startsWith(p.substring(0, p.length - 1))
        return false
    }

    /** True if the editor lists a type that covers [mime]. A null or empty list means the field takes no keyboard images. */
    fun accepted(patterns: Array<String>?, mime: String): Boolean {
        if (patterns == null) return false
        for (p in patterns) if (matches(p, mime)) return true
        return false
    }

    /**
     * Names of stored files: 64 lower-case hex characters (the SHA-256 of the picture), a dot and the extension. The provider serves nothing else, so no
     * other file of the app can be reached through it and a path trick ("../") cannot get out of the image folder.
     */
    fun isStoredName(name: String): Boolean {
        val dot = name.indexOf('.')
        if (dot != 64) return false
        for (i in 0 until 64) {
            val ch = name[i]
            if (!((ch in '0'..'9') || (ch in 'a'..'f'))) return false
        }
        val ext = name.length - dot - 1
        if (ext !in 1..5) return false
        for (i in dot + 1 until name.length) {
            val ch = name[i]
            if (!((ch in 'a'..'z') || (ch in '0'..'9'))) return false
        }
        return true
    }
}
