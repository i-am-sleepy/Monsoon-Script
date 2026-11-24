package com.monsoonscript.keyboard.voice

/**
 * Cleans what a speech model returns before it is typed (G5, D66). Pure functions, unit tested.
 */
object VoiceText {
    // The keyboard types these three letters in their single-code-point form (D42, same as Avro); a model may use letter + nukta.
    private val NUKTA = mapOf(
        "\u09AF\u09BC" to "\u09DF", // য় 
        "\u09A1\u09BC" to "\u09DC", // ড় 
        "\u09A2\u09BC" to "\u09DD", // ঢ় 
    )

    // Punctuation that must stand right behind the word: the models separate every token with a space.
    private val BEFORE_PUNCT = Regex("\\s+([,.?!;:\\u0964\\u0965)\\]])")
    private val AFTER_OPEN = Regex("([(\\[])\\s+")
    private val SPACES = Regex("\\s+")

    // Spoken punctuation (Bangla): the Bangla models almost never write punctuation, so the words for it are turned into the marks.
    // A word only counts when it stands alone (no letter or vowel sign right before or after it).
    private const val L = "[\\p{L}\\p{M}]"
    private fun word(vararg w: String) = Regex("(?<!$L)(?:" + w.joinToString("|") + ")(?!$L)")
    private val SAY_DANDA = word("\u09A6\u09BE\u0981\u09A1\u09BC\u09BF", "\u09A6\u09BE\u0981\u09DC\u09BF", "\u09A6\u09BE\u09A1\u09BC\u09BF", "\u09A6\u09BE\u09DC\u09BF", "\u09AA\u09C2\u09B0\u09CD\u09A3\u099A\u09CD\u099B\u09C7\u09A6") // দাঁড়ি, দাড়ি, পূর্ণচ্ছেদ
    private val SAY_COMMA = word("\u0995\u09AE\u09BE") // কমা
    private val SAY_QUESTION = word("\u09AA\u09CD\u09B0\u09B6\u09CD\u09A8\u09AC\u09CB\u09A7\u0995(?:\\s+\u099A\u09BF\u09B9\u09CD\u09A8)?", "\u09AA\u09CD\u09B0\u09B6\u09CD\u09A8\\s+\u099A\u09BF\u09B9\u09CD\u09A8") // প্রশ্নবোধক (চিহ্ন), প্রশ্ন চিহ্ন
    private val SAY_EXCLAIM = word("\u09AC\u09BF\u09B8\u09CD\u09AE\u09DF\u09B8\u09C2\u099A\u0995(?:\\s+\u099A\u09BF\u09B9\u09CD\u09A8)?", "\u09AC\u09BF\u09B8\u09CD\u09AE\u09AF\u09BC\u09B8\u09C2\u099A\u0995(?:\\s+\u099A\u09BF\u09B9\u09CD\u09A8)?") // বিস্ময়সূচক (চিহ্ন)
    private val ENDS_WITH_MARK = Regex("[.,?!;:\\u0964\\u0965\u2026]$")

    // What the Bangla model writes for music, noise or silence instead of speech. A result that is only this is dropped.
    private val NOISE_WORDS = setOf(
        "\u09AE\u09BF\u0989\u099C\u09BF\u0995", // মিউজিক
        "\u09B8\u0982\u0997\u09C0\u09A4", // সংগীত
        "\u09B8\u0999\u09CD\u0997\u09C0\u09A4", // সঙ্গীত
    )
    // The model often starts a sentence with this word instead of the real first word (heard as "music"): dropped when at least two more words follow.
    private val LEADING_NOISE = Regex("^(?:" + NOISE_WORDS.joinToString("|") + ")\\s+(?=\\S+\\s+\\S+)")
    private val JUST_MARKS = Regex("[\\s.,?!;:\\u0964\\u0965\u2026]+")

    /** [punct] (Bangla only): understand the spoken marks above and end a sentence without a mark with a danda. */
    fun clean(text: String, lang: VoiceLang, punct: Boolean = false): String {
        var t = text.trim()
        if (lang == VoiceLang.BANGLA) {
            for ((from, to) in NUKTA) t = t.replace(from, to)
            if (punct) {
                t = SAY_DANDA.replace(t, "\u0964")
                t = SAY_COMMA.replace(t, ",")
                t = SAY_QUESTION.replace(t, "?")
                t = SAY_EXCLAIM.replace(t, "!")
            }
            t = BEFORE_PUNCT.replace(t, "$1")
            t = AFTER_OPEN.replace(t, "$1")
        }
        t = SPACES.replace(t, " ").trim()
        if (lang == VoiceLang.BANGLA && JUST_MARKS.replace(t, " ").trim() in NOISE_WORDS) return ""
        if (lang == VoiceLang.BANGLA) t = LEADING_NOISE.replace(t, "")
        if (lang == VoiceLang.BANGLA && punct && t.isNotEmpty() && !ENDS_WITH_MARK.containsMatchIn(t)) t += "\u0964"
        return t
    }
}
