/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Derived from Avro Keyboard (OmicronLab), uAutoCorrect.pas (LoadDict).
 * Kotlin port for Monsoon Script; see third_party/avro-reference/NOTICE.md.
 */

package com.monsoonscript.keyboard.lang.avro

import java.io.InputStream

/** Loads Avro's `autodict.dct`: lines of `key value`, blank lines and lines starting with `/` are ignored. */
object AvroAutoCorrect {

    fun parse(lines: Sequence<String>): HashMap<String, String> {
        val map = HashMap<String, String>(4096)
        for (raw in lines) {
            val line = raw.trim { it <= ' ' }
            if (line.isEmpty() || line[0] == '/') continue
            val sp = line.indexOf(' ')
            if (sp < 1) continue // malformed (no key or no value)
            map[line.substring(0, sp)] = line.substring(sp + 1)
        }
        return map
    }

    /** Reads the dictionary (UTF-8) from [input] and closes it. */
    fun load(input: InputStream): HashMap<String, String> =
        input.bufferedReader(Charsets.UTF_8).use { parse(it.lineSequence()) }
}
