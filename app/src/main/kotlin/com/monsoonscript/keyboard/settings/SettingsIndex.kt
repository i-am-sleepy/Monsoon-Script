package com.monsoonscript.keyboard.settings

import com.monsoonscript.keyboard.R

/**
 * Every setting the search box on the main screen can find (L12, D83): its title and summary are the same strings as the row on its page, so a hit opens that
 * page ([page]; the pages with their own screen open it directly) and scrolls to the row. A new setting on a page needs a line here.
 */
internal object SettingsIndex {
    const val THEMES = "themes"
    const val CLIPBOARD = "clipboard"
    const val VOICE = "voice"
    const val VAULT = "vault"

    class Entry(val title: Int, val summary: Int, val page: String, val icon: Icons.Kind)

    private fun typing(title: Int, summary: Int = 0) = Entry(title, summary, SettingsPageActivity.TYPING, Icons.Kind.KEYBOARD)
    private fun suggestions(title: Int, summary: Int = 0) = Entry(title, summary, SettingsPageActivity.SUGGESTIONS, Icons.Kind.SPARKLE)
    private fun appearance(title: Int, summary: Int = 0) = Entry(title, summary, SettingsPageActivity.APPEARANCE, Icons.Kind.DROP)

    val ALL: List<Entry> = listOf(
        typing(R.string.settings_language_key, R.string.settings_language_key_summary),
        typing(R.string.settings_space_swipe, R.string.settings_space_swipe_summary),
        typing(R.string.settings_digits),
        typing(R.string.settings_auto_show, R.string.settings_auto_show_summary),
        typing(R.string.settings_volume_cursor),
        typing(R.string.settings_space_drag, R.string.settings_space_drag_summary),
        typing(R.string.settings_drag_delay),
        typing(R.string.settings_drag_speed),
        typing(R.string.settings_vibration),
        typing(R.string.settings_key_sound, R.string.settings_key_sound_summary),
        typing(R.string.settings_sound_source),
        typing(R.string.settings_sound_settings, R.string.settings_sound_settings_summary),
        typing(R.string.settings_long_press),
        suggestions(R.string.settings_show_suggestions, R.string.settings_show_suggestions_summary),
        suggestions(R.string.settings_next_word, R.string.settings_next_word_summary),
        suggestions(R.string.settings_typed_first, R.string.settings_typed_first_summary),
        suggestions(R.string.settings_autocorrect, R.string.settings_autocorrect_summary),
        suggestions(R.string.settings_learn, R.string.settings_learn_summary),
        suggestions(R.string.settings_clear, R.string.settings_clear_summary),
        Entry(R.string.themes_title, R.string.themes_row_summary, THEMES, Icons.Kind.DROP),
        appearance(R.string.settings_number_row, R.string.settings_number_row_summary),
        appearance(R.string.settings_emoji_key, R.string.settings_emoji_key_summary),
        appearance(R.string.settings_height_portrait),
        appearance(R.string.settings_height_landscape),
        appearance(R.string.settings_key_gap),
        appearance(R.string.settings_key_text),
        appearance(R.string.settings_strip_text),
        Entry(R.string.clip_title, R.string.clip_row_summary, CLIPBOARD, Icons.Kind.CLIPBOARD),
        Entry(R.string.vault_title, R.string.vault_row_summary, VAULT, Icons.Kind.LOCK),
        Entry(R.string.voice_settings_title, R.string.voice_settings_summary, VOICE, Icons.Kind.MIC),
        Entry(R.string.settings_licenses, R.string.settings_licenses_summary, SettingsPageActivity.ABOUT, Icons.Kind.INFO),
    )

    /** The name of the page (or screen) an entry lives on. */
    fun pageName(page: String): Int = when (page) {
        SettingsPageActivity.TYPING -> R.string.settings_typing
        SettingsPageActivity.SUGGESTIONS -> R.string.settings_suggestions
        SettingsPageActivity.APPEARANCE -> R.string.settings_appearance
        CLIPBOARD -> R.string.clip_title
        VOICE -> R.string.voice_settings_title
        VAULT -> R.string.vault_title
        THEMES -> R.string.themes_title
        else -> R.string.settings_about
    }
}
