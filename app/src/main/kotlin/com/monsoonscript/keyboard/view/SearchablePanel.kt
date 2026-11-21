package com.monsoonscript.keyboard.view

/**
 * A panel that replaces the keys (emoji, clipboard) and has a search bar. While the owner types a query the panel shrinks to a results row plus the
 * query bar, the real keys sit below it, and the keyboard service feeds what is typed into [setQuery] instead of the editor (D56, D65).
 */
interface SearchablePanel {
    /** The text to search for: finished text plus the word still being composed. */
    fun setQuery(q: String)

    /** true = typing into the search bar (compact panel above the keys); false = browsing. */
    fun setSearching(on: Boolean)

    /** Height of the panel while the keys sit below it. */
    fun compactHeight(): Int
}
