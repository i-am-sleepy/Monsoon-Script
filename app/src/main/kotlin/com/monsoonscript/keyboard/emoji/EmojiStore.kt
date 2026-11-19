package com.monsoonscript.keyboard.emoji

/** The two string slots the store needs; the IME backs them with a SharedPreferences file (tests use a map). */
interface TextStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

/**
 * Recently used emoji and the skin tone remembered for each emoji (D56). Pure Kotlin; saved on the phone only, as plain strings:
 * `recent` = emoji strings newest first, one per line; `tones` = `base TAB variant` per line. Emoji strings never contain a line break or a tab.
 */
class EmojiStore(private val store: TextStore, private val maxRecent: Int = MAX_RECENT) {
    private val recentList = ArrayList<String>()
    private val toneMap = HashMap<String, String>()

    init {
        store.get(KEY_RECENT)?.split('\n')?.let { for (s in it) if (s.isNotEmpty() && recentList.size < maxRecent && s !in recentList) recentList.add(s) }
        store.get(KEY_TONES)?.split('\n')?.let {
            for (line in it) {
                val tab = line.indexOf('\t')
                if (tab > 0 && tab < line.length - 1 && toneMap.size < MAX_TONES) toneMap[line.substring(0, tab)] = line.substring(tab + 1)
            }
        }
    }

    /** Newest first. */
    val recent: List<String> get() = recentList

    /** The emoji was committed: it moves to the front (or enters at the front); the oldest falls off. */
    fun recordUse(s: String) {
        if (s.isEmpty()) return
        if (recentList.isNotEmpty() && recentList[0] == s) return
        recentList.remove(s)
        recentList.add(0, s)
        while (recentList.size > maxRecent) recentList.removeAt(recentList.size - 1)
        store.put(KEY_RECENT, recentList.joinToString("\n"))
    }

    fun clearRecent() {
        if (recentList.isEmpty()) return
        recentList.clear()
        store.put(KEY_RECENT, "")
    }

    /** The variant chosen for this base emoji, or null for the plain (yellow) one. */
    fun toneOf(base: String): String? = toneMap[base]

    /** Remember [variant] for [base]; null (or the base itself) goes back to the plain emoji. */
    fun rememberTone(base: String, variant: String?) {
        val changed = if (variant == null || variant == base) toneMap.remove(base) != null else toneMap.put(base, variant) != variant
        if (!changed) return
        val sb = StringBuilder()
        for ((b, v) in toneMap) {
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(b).append('\t').append(v)
        }
        store.put(KEY_TONES, sb.toString())
    }

    companion object {
        const val KEY_RECENT = "emoji_recent"
        const val KEY_TONES = "emoji_tones"
        const val MAX_RECENT = 32
        private const val MAX_TONES = 800
    }
}
