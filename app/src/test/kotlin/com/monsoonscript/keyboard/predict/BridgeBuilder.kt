package com.monsoonscript.keyboard.predict

import com.monsoonscript.keyboard.lang.avro.AvroPhoneticConverter
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Builds `assets/predict/rom2bn.bin` (format: see [Bridge]): every Banglish word is converted with the plain Avro rules (no autocorrect) and looked
 * up in the Bangla pack. Run from `app/` after `tools/pack_wm_data.py` has rebuilt the packs, with the test classpath:
 *   java -cp <main+test classes>:<kotlin-stdlib> com.monsoonscript.keyboard.predict.BridgeBuilder
 * `BridgeTest` fails when the committed file is not what this code produces from the current packs.
 */
object BridgeBuilder {
    fun build(rom: LangPack, bn: LangPack): ByteArray {
        val conv = AvroPhoneticConverter().also { it.autoCorrectEnabled = false }
        val out = ByteBuffer.allocate(Bridge.HEADER + 3 * rom.wordCount).order(ByteOrder.LITTLE_ENDIAN)
        out.put('M'.code.toByte()).put('S'.code.toByte()).put('B'.code.toByte()).put('R'.code.toByte())
        out.putShort(1).putShort(0).putInt(rom.wordCount).putInt(bn.wordCount)
        for (r in 0 until rom.wordCount) {
            val v = bn.indexOf(TextNorm.key(conv.convert(rom.word(r)))) + 1 // 0 = no match
            out.put(v.toByte()).put((v shr 8).toByte()).put((v shr 16).toByte())
        }
        return out.array()
    }

    @JvmStatic fun main(args: Array<String>) {
        val file = File(if (args.isNotEmpty()) args[0] else "src/main/assets/predict/rom2bn.bin")
        val bytes = build(Packs.rom, Packs.bn)
        file.writeBytes(bytes)
        println("wrote ${file.path}: ${bytes.size} bytes")
    }
}
