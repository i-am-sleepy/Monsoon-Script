package com.monsoonscript.keyboard.predict

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Links the Banglish word list ("rom" pack) to the Bangla pack, so that Banglish next-word suggestions can use the Bangla word-pair data (D54).
 * Each Banglish word points to the Bangla word that the plain Avro conversion makes of it (if the Bangla list has it); the other direction picks,
 * for a Bangla word, the most frequent Banglish spelling that points to it. File `assets/predict/rom2bn.bin`, written by `BridgeBuilder` (test sources):
 * magic "MSBR", u16 version 1, u16 0, u32 Banglish word count, u32 Bangla word count, then one u24 per Banglish word (Bangla id + 1, 0 = no match), little-endian.
 * Pure Kotlin; like [LangPack], used from one thread only.
 */
class Bridge(private val rom: LangPack, val bn: LangPack, buffer: ByteBuffer) {
    private val b: ByteBuffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    private val best: IntArray // Bangla id -> its most frequent Banglish spelling, -1 for none

    init {
        require(b.limit() >= HEADER && b.get(0) == 'M'.code.toByte() && b.get(1) == 'S'.code.toByte() &&
            b.get(2) == 'B'.code.toByte() && b.get(3) == 'R'.code.toByte()) { "not an MSBR bridge" }
        require(b.getShort(4).toInt() == 1) { "unsupported MSBR version" }
        require(b.getInt(8) == rom.wordCount && b.getInt(12) == bn.wordCount) { "bridge was built for other packs" }
        require(b.limit() >= HEADER + 3 * rom.wordCount) { "bridge file is cut short" }
        best = IntArray(bn.wordCount) { -1 }
        for (r in 0 until rom.wordCount) {
            val x = bnOf(r)
            if (x >= 0 && (best[x] < 0 || rom.countLevel(r) > rom.countLevel(best[x]))) best[x] = r
        }
    }

    /** The Bangla word id for a Banglish word id, -1 when there is no match. */
    fun bnOf(romId: Int): Int {
        if (romId < 0 || romId >= rom.wordCount) return -1
        val p = HEADER + 3 * romId
        val v = (b.get(p).toInt() and 0xFF) or ((b.get(p + 1).toInt() and 0xFF) shl 8) or ((b.get(p + 2).toInt() and 0xFF) shl 16)
        return v - 1
    }

    /** The most frequent Banglish spelling (id in the Banglish pack) of a Bangla word id, -1 when none is known. */
    fun romOf(bnId: Int): Int = if (bnId < 0 || bnId >= best.size) -1 else best[bnId]

    companion object {
        const val HEADER = 16
    }
}
