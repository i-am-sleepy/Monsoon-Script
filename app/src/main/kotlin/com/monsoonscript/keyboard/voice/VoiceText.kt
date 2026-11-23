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

    fun clean(text: String, lang: VoiceLang): String {
        var t = text.trim()
        if (lang == VoiceLang.BANGLA) {
            for ((from, to) in NUKTA) t = t.replace(from, to)
            t = BEFORE_PUNCT.replace(t, "$1")
            t = AFTER_OPEN.replace(t, "$1")
        }
        return SPACES.replace(t, " ").trim()
    }
}
