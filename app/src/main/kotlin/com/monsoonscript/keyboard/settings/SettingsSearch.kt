package com.monsoonscript.keyboard.settings

/** The matching of the settings search box (L12, D83): every word the person typed must appear somewhere in the title or the summary, in any order, ignoring case. */
object SettingsSearch {
    fun words(query: String): List<String> = query.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }

    fun matches(query: String, vararg texts: String): Boolean {
        val wanted = words(query)
        if (wanted.isEmpty()) return false
        val haystack = texts.joinToString(" ").lowercase()
        return wanted.all { haystack.contains(it) }
    }
}
