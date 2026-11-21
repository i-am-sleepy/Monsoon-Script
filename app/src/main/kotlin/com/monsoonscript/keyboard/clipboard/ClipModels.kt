package com.monsoonscript.keyboard.clipboard

/** What a clip holds. */
object ClipKind {
    const val TEXT = 0
    const val IMAGE = 1
}

/** The category chips of the panel and the History tab. */
enum class ClipFilter { ALL, PINNED, TEXT, IMAGES }

/**
 * One saved clip. A text clip holds the text; an image clip (E3.8) holds the name of a file in the app's private folder.
 * Plain mutable fields: ClipStore changes them on the main thread only. [folder] is 0 for "no folder".
 */
class ClipItem(
    val id: Long,
    val kind: Int,
    text: String,
    val image: String?,
    val mime: String?,
    var pinned: Boolean,
    var folder: Long,
    val created: Long,
    var used: Long,
) {
    var text: String = text
        set(value) {
            field = value
            lowerCache = null
        }
    private var lowerCache: String? = null

    /** Lower-case text for searching; computed once per text. */
    fun lower(): String = lowerCache ?: text.lowercase().also { lowerCache = it }

    val isText: Boolean get() = kind == ClipKind.TEXT
    val isImage: Boolean get() = kind == ClipKind.IMAGE
}

/** A user-made collection of clips (Folders tab). */
class ClipFolder(val id: Long, var name: String, val created: Long)

/** A user-made text shortcut: typing [trigger] offers [text] (D65). Triggers are matched without regard to case. */
class ClipShortcut(val id: Long, var trigger: String, var text: String)
