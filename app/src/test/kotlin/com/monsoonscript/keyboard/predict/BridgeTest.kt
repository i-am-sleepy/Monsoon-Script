package com.monsoonscript.keyboard.predict

import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeTest {
    private val file = File("src/main/assets/predict/rom2bn.bin")
    private val bridge by lazy { Bridge(Packs.rom, Packs.bn, ByteBuffer.wrap(file.readBytes())) }

    @Test fun committedFileIsWhatTheBuilderMakesFromTheCurrentPacks() {
        // If this fails, a pack changed: run BridgeBuilder again (see its comment) and commit the new rom2bn.bin.
        assertArrayEquals(BridgeBuilder.build(Packs.rom, Packs.bn), file.readBytes())
    }

    @Test fun commonBanglishWordsPointToTheirBanglaWords() {
        val rom = Packs.rom
        val bn = Packs.bn
        assertEquals(bn.indexOf("আমি"), bridge.bnOf(rom.indexOf("ami"))) // আমি
        assertEquals(bn.indexOf("তুমি"), bridge.bnOf(rom.indexOf("tumi"))) // তুমি
        assertEquals(-1, bridge.bnOf(-1))
        assertEquals(-1, bridge.bnOf(rom.wordCount))
    }

    @Test fun backwardLinkIsTheMostFrequentSpelling() {
        val rom = Packs.rom
        val bn = Packs.bn
        val id = bridge.romOf(bn.indexOf("আমি")) // আমি
        assertTrue(id >= 0)
        assertEquals("ami", rom.word(id))
        assertEquals(-1, bridge.romOf(-1))
        // every back link points to a Banglish word that really maps to that Bangla word
        for (b in 0 until bn.wordCount step 53) {
            val r = bridge.romOf(b)
            if (r >= 0) assertEquals(b, bridge.bnOf(r))
        }
    }

    @Test fun aBridgeBuiltForOtherPacksIsRefused() {
        val bytes = file.readBytes()
        bytes[8] = (bytes[8] + 1).toByte() // wrong Banglish word count
        try {
            Bridge(Packs.rom, Packs.bn, ByteBuffer.wrap(bytes))
            assertTrue("should have been refused", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("other packs"))
        }
    }

    @Test fun banglishNextWordUsesTheBanglaPairs() {
        val predictor = Predictor()
        fun next(vararg ctx: String): List<String> {
            val w1 = ctx.last()
            val w2 = if (ctx.size >= 2) ctx[ctx.size - 2] else null
            return predictor.predict(Predictor.englishMix(Packs.en, Packs.rom, w1, w2, 0) { bridge }, "", w1, w2, 3)
        }
        val afterAmi = next("ami")
        assertTrue("got $afterAmi", afterAmi.any { it in listOf("mone", "amar", "jani") }) // the generic na/to/e are gone
        assertFalse(afterAmi.contains("ami")) // never the word just typed
        assertTrue(next("kemon").any { it in listOf("achen", "hobe", "kore") })
        // a Banglish word without a match still falls back to the generic list, and English context never uses the bridge
        assertEquals(3, next("tomake").size)
        assertEquals("to", next("how").first())
    }

    @Test fun bridgeIsOnlyLoadedForBanglishContext() {
        var loads = 0
        Predictor.englishMix(Packs.en, Packs.rom, "how", null, 0) { loads++; bridge }
        assertEquals(0, loads)
        Predictor.englishMix(Packs.en, Packs.rom, "ami", null, 0) { loads++; bridge }
        assertEquals(1, loads)
    }
}
