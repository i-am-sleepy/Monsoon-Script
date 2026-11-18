package com.monsoonscript.keyboard.predict

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

/** The real packs from the assets folder; unit tests run with the module directory (app/) as working directory. */
object Packs {
    private fun load(name: String): LangPack {
        val ch = RandomAccessFile(File("src/main/assets/predict/$name.mslm"), "r").channel
        return LangPack(ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size()))
    }

    val en: LangPack by lazy { load("en") }
    val bn: LangPack by lazy { load("bn") }
    val rom: LangPack by lazy { load("rom") }

    fun english(w1: String? = null, w2: String? = null): List<WeightedPack> = Predictor.englishMix(en, rom, w1, w2)
}
