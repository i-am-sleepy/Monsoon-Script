package com.monsoonscript.keyboard.lang

/**
 * Typing languages / layouts. [shortLabel] is drawn on the strip's language key, [spaceLabel] on the space bar and in the
 * language popup (D60: the pill says বাং for every Bangla layout, the space bar names it). The saved choice is the enum NAME,
 * and the order here is the order of the popup (D19). Bijoy joins this list with T9.3.
 */
enum class Language(val shortLabel: String, val spaceLabel: String, val isBangla: Boolean) {
    ENGLISH("EN", "English", false),
    AVRO("বাং", "বাংলা (Avro)", true),
    PROBHAT("বাং", "বাংলা (প্রভাত)", true),
    NATIONAL("বাং", "বাংলা (জাতীয়)", true),
}
