package com.monsoonscript.keyboard.clipboard

/**
 * A small JSON reader and writer (E3.9, D71) for the backup file: pure Kotlin, so the backup format is unit tested without Android (the platform's own JSON
 * classes are not available in plain unit tests). Values are `Map<String, Any?>`, `List<Any?>`, `String`, `Long` (a number without fraction or exponent),
 * `Double`, `Boolean` and null.
 */
object MiniJson {
    private const val MAX_DEPTH = 32

    /** Text for a value built from maps, lists, strings, numbers, booleans and null. */
    fun write(value: Any?): String {
        val sb = StringBuilder()
        put(sb, value)
        return sb.toString()
    }

    private fun put(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> putString(sb, v)
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int -> sb.append(v)
            is Long -> sb.append(v)
            is Double -> sb.append(v)
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, x) in v) {
                    if (!first) sb.append(',')
                    first = false
                    putString(sb, k.toString())
                    sb.append(':')
                    put(sb, x)
                }
                sb.append('}')
            }
            is List<*> -> {
                sb.append('[')
                for (i in v.indices) {
                    if (i > 0) sb.append(',')
                    put(sb, v[i])
                }
                sb.append(']')
            }
            else -> throw IllegalArgumentException("unsupported value")
        }
    }

    private fun putString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when {
                ch == '"' -> sb.append("\\\"")
                ch == '\\' -> sb.append("\\\\")
                ch == '\n' -> sb.append("\\n")
                ch == '\r' -> sb.append("\\r")
                ch == '\t' -> sb.append("\\t")
                ch < ' ' -> {
                    sb.append("\\u00")
                    sb.append(HEX[ch.code ushr 4]).append(HEX[ch.code and 15])
                }
                else -> sb.append(ch)
            }
        }
        sb.append('"')
    }

    private val HEX = "0123456789abcdef".toCharArray()

    /** The value in [text]; throws [IllegalArgumentException] when it is not valid JSON (or nested deeper than 32 levels). */
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skip()
        val v = p.value(0)
        p.skip()
        if (p.pos != text.length) p.fail("extra text")
        return v
    }

    private class Parser(private val s: String) {
        var pos = 0

        fun fail(why: String): Nothing = throw IllegalArgumentException("$why at $pos")

        fun skip() {
            while (pos < s.length && (s[pos] == ' ' || s[pos] == '\n' || s[pos] == '\r' || s[pos] == '\t')) pos++
        }

        fun value(depth: Int): Any? {
            if (depth > MAX_DEPTH) fail("too deep")
            if (pos >= s.length) fail("unexpected end")
            return when (val ch = s[pos]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> str()
                't' -> word("true", true)
                'f' -> word("false", false)
                'n' -> word("null", null)
                else -> if (ch == '-' || ch in '0'..'9') num() else fail("unexpected character")
            }
        }

        private fun word(w: String, v: Any?): Any? {
            if (!s.startsWith(w, pos)) fail("bad word")
            pos += w.length
            return v
        }

        private fun obj(depth: Int): Map<String, Any?> {
            val out = LinkedHashMap<String, Any?>()
            pos++ // {
            skip()
            if (pos < s.length && s[pos] == '}') {
                pos++
                return out
            }
            while (true) {
                skip()
                if (pos >= s.length || s[pos] != '"') fail("name expected")
                val k = str()
                skip()
                if (pos >= s.length || s[pos] != ':') fail("colon expected")
                pos++
                skip()
                out[k] = value(depth + 1)
                skip()
                if (pos >= s.length) fail("unexpected end")
                if (s[pos] == ',') {
                    pos++
                    continue
                }
                if (s[pos] == '}') {
                    pos++
                    return out
                }
                fail("comma or end expected")
            }
        }

        private fun arr(depth: Int): List<Any?> {
            val out = ArrayList<Any?>()
            pos++ // [
            skip()
            if (pos < s.length && s[pos] == ']') {
                pos++
                return out
            }
            while (true) {
                skip()
                out.add(value(depth + 1))
                skip()
                if (pos >= s.length) fail("unexpected end")
                if (s[pos] == ',') {
                    pos++
                    continue
                }
                if (s[pos] == ']') {
                    pos++
                    return out
                }
                fail("comma or end expected")
            }
        }

        private fun str(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (pos >= s.length) fail("unterminated string")
                val ch = s[pos++]
                when {
                    ch == '"' -> return sb.toString()
                    ch == '\\' -> {
                        if (pos >= s.length) fail("unterminated string")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) fail("bad escape")
                                var code = 0
                                for (i in 0 until 4) {
                                    val d = Character.digit(s[pos + i], 16)
                                    if (d < 0) fail("bad escape")
                                    code = code * 16 + d
                                }
                                pos += 4
                                sb.append(code.toChar())
                            }
                            else -> fail("bad escape $e")
                        }
                    }
                    ch < ' ' -> fail("control character in string")
                    else -> sb.append(ch)
                }
            }
        }

        private fun num(): Any {
            val start = pos
            if (s[pos] == '-') pos++
            val digitsStart = pos
            while (pos < s.length && s[pos] in '0'..'9') pos++
            if (pos == digitsStart) fail("bad number")
            var whole = true
            if (pos < s.length && s[pos] == '.') {
                whole = false
                pos++
                val f = pos
                while (pos < s.length && s[pos] in '0'..'9') pos++
                if (pos == f) fail("bad number")
            }
            if (pos < s.length && (s[pos] == 'e' || s[pos] == 'E')) {
                whole = false
                pos++
                if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) pos++
                val e = pos
                while (pos < s.length && s[pos] in '0'..'9') pos++
                if (pos == e) fail("bad number")
            }
            val text = s.substring(start, pos)
            if (whole) text.toLongOrNull()?.let { return it }
            return text.toDoubleOrNull() ?: fail("bad number")
        }
    }
}
