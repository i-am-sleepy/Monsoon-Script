package com.monsoonscript.keyboard.lang

/**
 * Typing languages / layouts. [shortLabel] is drawn on the strip's language key, [spaceLabel] on the space bar.
 * Provat, Bijoy and National join this list with T9.
 */
enum class Language(val shortLabel: String, val spaceLabel: String, val isBangla: Boolean) {
    ENGLISH("EN", "English", false),
    AVRO("বাং", "বাংলা (Avro)", true),
}
