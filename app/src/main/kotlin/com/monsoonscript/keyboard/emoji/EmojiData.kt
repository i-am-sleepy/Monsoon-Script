package com.monsoonscript.keyboard.emoji

import com.monsoonscript.keyboard.predict.TextNorm
import java.io.BufferedReader
import java.util.Arrays
import java.util.Locale

/** The eight emoji tabs (Gboard order, D56). Smileys and People are one tab. The panel adds a "Recent" tab in front. */
object EmojiTabs {
    const val COUNT = 8
    val NAMES = arrayOf("Smileys & people", "Animals & nature", "Food & drink", "Activities", "Travel & places", "Objects", "Symbols", "Flags")
}

/**
 * The emoji list (assets/emoji/emoji.tsv, built by tools/gen_emoji_data.py from Unicode emoji-test 17.0 and CLDR). Pure Kotlin.
 *
 * Every emoji is ONE complete Unicode string, exactly as Unicode lists it (ZWJ sequences, flags, skin-tone variants): nothing here ever
 * splits, rebuilds or draws an emoji out of parts, and the IME commits these strings with commitText. Entries are BASE emoji sorted by
 * tab; skin-tone variants hang off their base in [variants].
 */
class EmojiData internal constructor(
    /** The base emoji strings. */
    val emoji: Array<String>,
    /** Tab (0 until [EmojiTabs.COUNT]) of each entry; entries are sorted by tab. */
    val tab: IntArray,
    private val en: Array<String>,
    private val bn: Array<String>,
    /** Skin-tone variants per entry (whole strings, Unicode order); [NO_VARIANTS] when the emoji has none. */
    val variants: Array<Array<String>>,
) {
    val size: Int get() = emoji.size

    /** First entry of each tab, plus the total at the end: tab t owns [tabStart[t], tabStart[t + 1]). */
    val tabStart: IntArray = IntArray(EmojiTabs.COUNT + 1).also { ts ->
        var i = 0
        for (t in 0..EmojiTabs.COUNT) {
            while (i < emoji.size && tab[i] < t) i++
            ts[t] = i
        }
    }

    private val enBar = IntArray(en.size) { en[it].indexOf('|').let { p -> if (p < 0) en[it].length else p } }
    private val bnBar = IntArray(bn.size) { bn[it].indexOf('|').let { p -> if (p < 0) bn[it].length else p } }

    private var owner: HashMap<String, Int>? = null

    /** Index of the base emoji that [s] is (or one of its variants), -1 when this list does not know it. */
    fun baseOf(s: String): Int {
        var m = owner
        if (m == null) {
            m = HashMap(emoji.size * 3)
            for (i in emoji.indices) {
                m[emoji[i]] = i
                for (v in variants[i]) m[v] = i
            }
            owner = m
        }
        return m[s] ?: -1
    }

    /** Number of strings in [flatStrings]. */
    val flatCount: Int get() = emoji.size + variants.sumOf { it.size }

    /** Every string of the list in one fixed order: an entry's base, then its variants, entry after entry (the device-support cache uses these positions). */
    fun flatStrings(): Array<String> {
        val out = arrayOfNulls<String>(flatCount)
        var n = 0
        for (i in emoji.indices) {
            out[n++] = emoji[i]
            for (v in variants[i]) out[n++] = v
        }
        @Suppress("UNCHECKED_CAST")
        return out as Array<String>
    }

    /**
     * The same list without what the phone's own font cannot draw (D56). [unsupported] is indexed like [flatStrings]. An entry whose base is
     * unsupported disappears with its variants; an unsupported variant only disappears itself.
     */
    fun withoutUnsupported(unsupported: BooleanArray): EmojiData {
        val keep = ArrayList<Int>(emoji.size)
        val vars = ArrayList<Array<String>>(emoji.size)
        var n = 0
        for (i in emoji.indices) {
            val baseOk = !unsupported[n++]
            val ok = ArrayList<String>(variants[i].size)
            for (v in variants[i]) if (!unsupported[n++]) ok.add(v)
            if (baseOk) {
                keep.add(i)
                vars.add(if (ok.isEmpty()) NO_VARIANTS else ok.toTypedArray())
            }
        }
        return EmojiData(
            Array(keep.size) { emoji[keep[it]] }, IntArray(keep.size) { tab[keep[it]] },
            Array(keep.size) { en[keep[it]] }, Array(keep.size) { bn[keep[it]] }, vars.toTypedArray(),
        )
    }

    /**
     * Search by name or keyword in English and Bangla (D56). The query is split into words; EVERY word must match the START of a word of the
     * emoji's name or keywords (either language). Best first: a name word typed in full, a name word's start, a keyword in full, a keyword's
     * start; then Unicode order. Writes up to out.size entry indexes to [out] and returns how many.
     */
    fun search(query: String, out: IntArray): Int {
        val tokens = words(query)
        if (tokens.isEmpty() || out.isEmpty()) return 0
        val ranked = IntArray(emoji.size)
        var n = 0
        for (i in emoji.indices) {
            var total = 0
            var ok = true
            for (t in tokens) {
                val a = bestMatch(en[i], enBar[i], t)
                val b = bestMatch(bn[i], bnBar[i], t)
                val s = if (a < b) a else b
                if (s >= NO_MATCH) {
                    ok = false
                    break
                }
                total += s
            }
            if (ok) ranked[n++] = total * RANK_SHIFT + i
        }
        Arrays.sort(ranked, 0, n)
        val count = if (n < out.size) n else out.size
        for (k in 0 until count) out[k] = ranked[k] % RANK_SHIFT
        return count
    }

    companion object {
        val NO_VARIANTS: Array<String> = emptyArray()
        private const val NO_MATCH = 4
        private const val RANK_SHIFT = 1 shl 16

        /** 0 = a name word typed in full, 1 = start of a name word, 2 = keyword in full, 3 = start of a keyword, [NO_MATCH] = nothing. */
        private fun bestMatch(text: String, bar: Int, token: String): Int {
            var best = NO_MATCH
            var from = 0
            while (true) {
                val p = text.indexOf(token, from)
                if (p < 0) return best
                if (p == 0 || text[p - 1] == ' ' || text[p - 1] == '|') {
                    val end = p + token.length
                    val full = end == text.length || text[end] == ' ' || text[end] == '|'
                    val score = (if (p < bar) 0 else 2) + (if (full) 0 else 1)
                    if (score < best) best = score
                    if (best == 0) return 0
                }
                from = p + 1
            }
        }

        private fun isWordChar(c: Char): Boolean {
            if (Character.isLetterOrDigit(c) || c == TextNorm.ZWNJ || c == TextNorm.ZWJ) return true
            val t = Character.getType(c)
            return t == Character.NON_SPACING_MARK.toInt() || t == Character.COMBINING_SPACING_MARK.toInt() || t == Character.ENCLOSING_MARK.toInt()
        }

        /** Same normal form as tools/gen_emoji_data.py (norm_words): lower-case, nukta letters precomposed, apostrophes dropped, words of letters/marks/digits. */
        fun words(text: String): List<String> {
            val s = TextNorm.precomposeBangla(text.replace('’', '\'').lowercase(Locale.ROOT))
            val out = ArrayList<String>(4)
            var start = -1
            for (i in s.indices) {
                val c = s[i]
                if (c == '\'') continue // dropped inside a word: women's -> womens
                if (isWordChar(c)) {
                    if (start < 0) start = i
                } else if (start >= 0) {
                    out.add(strip(s, start, i))
                    start = -1
                }
            }
            if (start >= 0) out.add(strip(s, start, s.length))
            return out
        }

        private fun strip(s: String, from: Int, to: Int): String {
            val part = s.substring(from, to)
            return if (part.indexOf('\'') < 0) part else part.replace("'", "")
        }

        /** Reads assets/emoji/emoji.tsv. Throws when the format version is not the one this reader knows. */
        fun parse(reader: BufferedReader): EmojiData {
            val head = reader.readLine() ?: throw IllegalArgumentException("empty emoji file")
            val h = head.split('\t')
            require(h.size >= 2 && h[0] == "#emoji" && h[1] == "1") { "unknown emoji file format: $head" }
            val emoji = ArrayList<String>(2048)
            val tab = ArrayList<Int>(2048)
            val en = ArrayList<String>(2048)
            val bn = ArrayList<String>(2048)
            val vars = ArrayList<Array<String>>(2048)
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty() || line[0] == '#') continue
                val f = line.split('\t')
                if (f.size < 4) continue
                val t = f[0].toIntOrNull() ?: continue
                if (t < 0 || t >= EmojiTabs.COUNT) continue
                emoji.add(f[1])
                tab.add(t)
                en.add(f[2])
                bn.add(f[3])
                val v = if (f.size > 4) f[4] else ""
                vars.add(if (v.isEmpty()) NO_VARIANTS else v.split(' ').filter { it.isNotEmpty() }.toTypedArray())
            }
            return EmojiData(emoji.toTypedArray(), tab.toIntArray(), en.toTypedArray(), bn.toTypedArray(), vars.toTypedArray())
        }
    }
}
