package com.monsoonscript.keyboard.layout

/**
 * A fixed Bangla layout (Probhat, National, later Bijoy) as it is on a PC keyboard: for every PC key the text of four layers.
 * The soft keyboard arranges these keys for a phone (`Layouts`), a hardware keyboard can map its key codes to the same names
 * (Avro's names: "A".."Z", "0".."9", "MINUS", "PLUS", "COMMA", "PERIOD", "OEM1".."OEM7").
 *
 * [table] rows are `[pcKey, normal, shift, altGr, shiftAltGr]`; "" means the layer has nothing on that key.
 * The tables of Probhat and National are generated (`lang/avro/AvroLayoutTables.kt`, tools/gen_fixed_layouts.py).
 */
class FixedLayout(val id: String, val name: String, table: Array<Array<String>>) {
    private val keys = HashMap<String, Array<String>>(table.size * 2)

    init {
        for (row in table) {
            require(row.size == 5) { "row of $id needs a key name and four layers: ${row.toList()}" }
            keys[row[0]] = arrayOf(row[1], row[2], row[3], row[4])
        }
    }

    /** The PC key names this layout defines. */
    val keyNames: Set<String> get() = keys.keys

    /** The text of [pcKey] on [layer] ([NORMAL], [SHIFT], [ALTGR] or [SHIFT_ALTGR]); "" when the layer is empty or the key is unknown. */
    fun text(pcKey: String, layer: Int): String = keys[pcKey]?.get(layer) ?: ""

    companion object {
        const val NORMAL = 0
        const val SHIFT = 1
        const val ALTGR = 2
        const val SHIFT_ALTGR = 3
    }
}
