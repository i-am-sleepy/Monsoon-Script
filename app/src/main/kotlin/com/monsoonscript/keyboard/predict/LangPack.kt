package com.monsoonscript.keyboard.predict

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Read-only view of one packed language file ("MSLM", written by tools/pack_wm_data.py; the byte layout is documented there).
 * The buffer is normally a memory-mapped file, so only the pages that lookups touch are ever read. Pure Kotlin.
 * Not for concurrent use from several threads (one prediction worker owns it).
 */
class LangPack(buffer: ByteBuffer) {
    private val b: ByteBuffer = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)

    val wordCount: Int
    val bigramCount: Int
    val trigramCount: Int

    /** Sum of the unigram counts of all words: the denominator of a word's unigram probability. */
    val totalCount: Float

    private val offOffsets: Int
    private val offBlob: Int
    private val offCounts: Int
    private val offBi: Int
    private val offTri: Int
    private val chars = CharArray(64)
    private var top: IntArray? = null

    init {
        require(b.limit() >= HEADER && b.get(0) == 'M'.code.toByte() && b.get(1) == 'S'.code.toByte() &&
            b.get(2) == 'L'.code.toByte() && b.get(3) == 'M'.code.toByte()) { "not an MSLM pack" }
        require(b.getShort(4).toInt() == 1) { "unsupported MSLM version" }
        wordCount = b.getInt(8)
        bigramCount = b.getInt(12)
        trigramCount = b.getInt(16)
        offOffsets = b.getInt(20)
        offBlob = b.getInt(24)
        offCounts = b.getInt(28)
        offBi = b.getInt(32)
        offTri = b.getInt(36)
        totalCount = b.getLong(40).toFloat()
    }

    // ---- words (ids are indexes in the sorted word list)

    fun word(i: Int): String {
        val start = offBlob + b.getInt(offOffsets + 4 * i)
        val end = offBlob + b.getInt(offOffsets + 4 * (i + 1))
        var n = 0
        var p = start
        while (p < end) {
            val c = b.get(p).toInt() and 0xFF
            when {
                c < 0x80 -> { chars[n++] = c.toChar(); p += 1 }
                c < 0xE0 -> { chars[n++] = (((c and 0x1F) shl 6) or (b.get(p + 1).toInt() and 0x3F)).toChar(); p += 2 }
                else -> {
                    chars[n++] = (((c and 0x0F) shl 12) or ((b.get(p + 1).toInt() and 0x3F) shl 6) or (b.get(p + 2).toInt() and 0x3F)).toChar()
                    p += 3
                }
            }
        }
        return String(chars, 0, n)
    }

    /** Unigram count, decoded from its 1-byte log scale (about 3.5% steps). */
    fun count(i: Int): Float = DECODE[b.get(offCounts + i).toInt() and 0xFF]

    /** Raw 0..255 log-scale value of the count; ordering by this equals ordering by count. */
    fun countLevel(i: Int): Int = b.get(offCounts + i).toInt() and 0xFF

    /** First id whose word is >= [key] in code-point order. */
    fun lowerBound(key: String): Int {
        var lo = 0
        var hi = wordCount
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (word(mid) < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** Id of [key] (already in [TextNorm.key] form) or -1. */
    fun indexOf(key: String): Int {
        val i = lowerBound(key)
        return if (i < wordCount && word(i) == key) i else -1
    }

    /** Ids from this to [prefixEnd] (exclusive) are the words that start with [prefix]. */
    fun prefixStart(prefix: String): Int = lowerBound(prefix)

    fun prefixEnd(prefix: String): Int = lowerBound(prefix + '￿')

    /** The most frequent words overall (for suggestions when there is no context). Computed once, about 150k byte reads. */
    fun topWords(k: Int): IntArray {
        top?.let { if (it.size >= k) return it }
        val t = TopN(k)
        for (i in 0 until wordCount) t.offer(i, countLevel(i))
        val r = t.ids()
        top = r
        return r
    }

    // ---- bigrams: record = w1 u24, w2 u24, count u8 (sorted by w1, w2)

    fun bigramW1(i: Int): Int = u24(offBi + 7 * i)
    fun bigramW2(i: Int): Int = u24(offBi + 7 * i + 3)
    fun bigramCountAt(i: Int): Float = DECODE[b.get(offBi + 7 * i + 6).toInt() and 0xFF]
    fun bigramLevelAt(i: Int): Int = b.get(offBi + 7 * i + 6).toInt() and 0xFF

    /** First record with key >= (w1, w2). */
    fun bigramLowerBound(w1: Int, w2: Int): Int {
        val key = (w1.toLong() shl 24) or w2.toLong()
        var lo = 0
        var hi = bigramCount
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            val k = (bigramW1(mid).toLong() shl 24) or bigramW2(mid).toLong()
            if (k < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    fun bigramCount(w1: Int, w2: Int): Float {
        val i = bigramLowerBound(w1, w2)
        return if (i < bigramCount && bigramW1(i) == w1 && bigramW2(i) == w2) bigramCountAt(i) else 0f
    }

    // ---- trigrams: record = w1 u24, w2 u24, w3 u24, count u8 (sorted by w1, w2, w3)

    fun trigramW1(i: Int): Int = u24(offTri + 10 * i)
    fun trigramW2(i: Int): Int = u24(offTri + 10 * i + 3)
    fun trigramW3(i: Int): Int = u24(offTri + 10 * i + 6)
    fun trigramCountAt(i: Int): Float = DECODE[b.get(offTri + 10 * i + 9).toInt() and 0xFF]
    fun trigramLevelAt(i: Int): Int = b.get(offTri + 10 * i + 9).toInt() and 0xFF

    private fun triKey(w1: Int, w2: Int, w3: Int): Long = (w1.toLong() shl 48) or (w2.toLong() shl 24) or w3.toLong()

    /** First record with key >= (w1, w2, w3). */
    fun trigramLowerBound(w1: Int, w2: Int, w3: Int): Int {
        val key = triKey(w1, w2, w3)
        var lo = 0
        var hi = trigramCount
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (triKey(trigramW1(mid), trigramW2(mid), trigramW3(mid)) < key) lo = mid + 1 else hi = mid
        }
        return lo
    }

    fun trigramCount(w1: Int, w2: Int, w3: Int): Float {
        val i = trigramLowerBound(w1, w2, w3)
        return if (i < trigramCount && trigramW1(i) == w1 && trigramW2(i) == w2 && trigramW3(i) == w3) trigramCountAt(i) else 0f
    }

    private fun u24(p: Int): Int =
        (b.get(p).toInt() and 0xFF) or ((b.get(p + 1).toInt() and 0xFF) shl 8) or ((b.get(p + 2).toInt() and 0xFF) shl 16)

    companion object {
        private const val HEADER = 48

        /** Count of a level: 2^(level / 10), the inverse of the packer's round(10 * log2(count)). */
        private val DECODE = FloatArray(256) { Math.pow(2.0, it / 10.0).toFloat() }
    }
}

/** Keeps the [n] best (highest level) ids seen; small n, so a linear scan for the weakest entry is fine. */
internal class TopN(private val n: Int) {
    private val ids = IntArray(n)
    private val levels = IntArray(n)
    private var size = 0
    private var worst = -1 // index of the weakest kept entry, valid when size == n

    fun offer(id: Int, level: Int) {
        if (size < n) {
            ids[size] = id
            levels[size] = level
            size++
            if (size == n) findWorst()
        } else if (level > levels[worst]) {
            ids[worst] = id
            levels[worst] = level
            findWorst()
        }
    }

    private fun findWorst() {
        var w = 0
        for (i in 1 until n) if (levels[i] < levels[w]) w = i
        worst = w
    }

    /** Best first. */
    fun ids(): IntArray {
        val order = (0 until size).sortedByDescending { levels[it] }
        return IntArray(size) { ids[order[it]] }
    }
}
