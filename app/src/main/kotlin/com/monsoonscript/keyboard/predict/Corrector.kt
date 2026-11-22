package com.monsoonscript.keyboard.predict

/**
 * Typo fixing for English typing (L8, D69): finds the real word a misspelled word was probably meant to be.
 *
 * Pure Kotlin, no Android. Everything runs on the prediction worker thread (see `ime/PredictionWorker`).
 *
 * The word lists contain many common misspellings at a moderate frequency next to genuinely rare real words, so "the word is in the
 * list" does not mean "it is right": the word is compared with how common the intended word is.
 *  - Only 3+ plain letters (or an apostrophe), all lowercase (a capital first letter only at a sentence start). A word that the
 *    Banglish list knows, that the owner typed before, or whose fix the owner undid is never touched.
 *  - A word the English list knows well (level [STRONG_KNOWN] and up) is a real word. A weakly listed word may only be fixed by a
 *    slip-like edit; an unlisted word by any one-letter edit. The fix must be far more common than the typed word ([MIN_GAP]).
 *  - Three kinds of fix are applied by themselves (on space): a well-known misspelling from [Misspellings]; a missing apostrophe in
 *    `dont`, `didnt` ... ([contractionFix], the lists split `don't` into fragments); and a clear slip (two letters swapped, a
 *    neighbouring key, a doubled letter) in a word that no list knows. Everything else only becomes a strip candidate that the owner
 *    may tap ([Fix.auto] = false).
 *  - Candidates are the real words one edit away: two letters swapped, a neighbouring key hit, a vowel swapped for a vowel, a letter
 *    doubled or missing. A pure completion (a letter missing at the END) is left to the strip's normal suggestions, and so is an extra
 *    last letter (valid plurals and verb forms the list lacks). Far-fetched edits (`thx` -> `the`) are never offered.
 *  - Score = how common the word is, minus a price per kind of edit, plus a bonus when it follows the previous word. The best must lead
 *    the second best clearly, and no Banglish-only word may be about as close (an ambiguous case becomes a plain strip candidate,
 *    [Fix.auto] = false). When the typed letters start a common word the owner is probably still typing: no fix at all.
 */
object Corrector {
    /** [word] is the fix in lookup form (lowercase). [auto]: sure enough to be applied on space and shown first on the strip. */
    class Fix(val word: String, val auto: Boolean)

    internal class Scored(val word: String, val id: Int, val cost: Double, val level: Int, val score: Double)

    // ---- tuning (calibrated on the real packs, see CorrectorTest)

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 20

    /** An English entry from this count level up is a real word (misspellings in the list sit around 45-85; about 3.5% per level). */
    const val STRONG_KNOWN = 90

    /** From this level up an English entry counts as listed (a weakly listed word is fixed only by a slip-like edit). */
    const val KNOWN_LEVEL = 40

    /** A Banglish entry from this level up protects the word (the misspelled "teh" sits at 50 there, real Banglish words from 80). */
    const val ROM_KNOWN = 60

    /** A word is Banglish when its Banglish level minus its English level is at least this (see [isBanglish]). */
    const val BANGLISH_LEAD = -10

    /** The fix must be this many levels (20 = twice as frequent) more common than the typed word. */
    const val MIN_GAP = 45

    /** The fix itself must be at least this common. */
    const val MIN_FIX_LEVEL = 90

    /** A fix applied by itself must be at least this common, and a slip-like edit (up to [C_DOUBLED]) of a word no list knows. */
    const val AUTO_FIX_LEVEL = 120

    /** One unit of edit cost is worth this many count levels. */
    const val COST_WEIGHT = 80.0

    const val CONTEXT_BONUS = 30.0

    /** Best must lead the second best by this many score points. */
    const val MARGIN = 40.0

    /** The owner is probably still typing when a completion of the typed letters is not much rarer than the fix. */
    const val COMPLETION_GAP = 60

    /** A Banglish-only word one edit away at about this price or less makes the case ambiguous. */
    const val ROM_RIVAL_LEVEL = 50
    const val ROM_COST_SLACK = 0.3

    /** `dont` and friends: the typed word may sit this high in the English list and still be a missing apostrophe. */
    const val CONTRACTION_MAX_TYPED = 150
    const val CONTRACTION_MIN_LEVEL = 50

    const val C_TRANSPOSE = 0.5
    const val C_APOSTROPHE = 0.5
    const val C_NEIGHBOUR = 0.7
    const val C_DOUBLED = 0.7
    const val C_VOWEL = 0.9
    const val C_LETTER = 1.0
    const val C_FAR = 1.3

    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz'"
    private const val MAX_COMPLETION_SCAN = 40_000

    // ---- QWERTY neighbours

    private val NEIGHBOURS: Map<Char, String> by lazy {
        val rows = arrayOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        val offset = doubleArrayOf(0.0, 0.25, 0.75)
        val m = HashMap<Char, StringBuilder>()
        for (r1 in rows.indices) for (c1 in rows[r1].indices) {
            val sb = StringBuilder()
            for (r2 in rows.indices) for (c2 in rows[r2].indices) {
                if (r1 == r2 && c1 == c2) continue
                val dy = Math.abs(r1 - r2)
                val dx = Math.abs((c1 + offset[r1]) - (c2 + offset[r2]))
                if ((dy == 0 && dx <= 1.0 + 1e-9) || (dy == 1 && dx < 1.0 - 1e-9)) sb.append(rows[r2][c2])
            }
            m[rows[r1][c1]] = sb
        }
        m.mapValues { it.value.toString() }
    }

    private fun adjacent(a: Char, b: Char): Boolean = NEIGHBOURS[a]?.indexOf(b)?.let { it >= 0 } ?: false

    // ---- public

    /**
     * The fix for [typed] (the word as typed, with its capitals) or null when there is none to offer. [w1] is the word before it in
     * lookup form (null when unknown), [capitalizeStart] true when the word begins a sentence, [rejected] the words whose fix the
     * owner undid. [rom] and [stats] are optional guards (the Banglish list, the owner's own words).
     */
    fun correct(
        en: LangPack, rom: LangPack?, stats: UserStats?, typed: String, w1: String?, capitalizeStart: Boolean,
        rejected: Set<String> = emptySet(),
    ): Fix? {
        val key = eligibleKey(typed, capitalizeStart) ?: return null
        if (key in rejected) return null
        if (stats != null && stats.wordWeight(key) > 0.0) return null
        if (rom != null && levelOf(rom, key) >= ROM_KNOWN) return null
        val typedLevel = levelOf(en, key) // -1 when the list does not have it
        Misspellings.fix(key)?.let { w -> if (levelOf(en, w) >= MIN_FIX_LEVEL) return Fix(w, true) }
        contractionFix(en, key, typedLevel)?.let { return it }
        if (typedLevel >= STRONG_KNOWN) return null
        if (w1 != null && rom != null && isBanglish(en, rom, w1)) return null // after a Banglish word: hands off (D69)

        // a weakly listed word may only be fixed by a slip-like edit, an unlisted one by any one-letter edit
        val maxCost = if (typedLevel >= KNOWN_LEVEL) C_VOWEL else C_LETTER
        val edits = edits(key)
        if (edits.isEmpty()) return null
        val w1Id = if (w1 != null) en.indexOf(w1) else -1
        val found = ArrayList<Scored>()
        for ((word, cost) in edits) {
            if (cost > maxCost + 1e-9) continue
            val id = en.indexOf(word)
            if (id < 0) continue
            val level = en.countLevel(id)
            if (level < MIN_FIX_LEVEL || level - maxOf(typedLevel, 0) < MIN_GAP) continue
            var score = level - COST_WEIGHT * cost
            if (w1Id >= 0 && en.bigramCount(w1Id, id) > 0f) score += CONTEXT_BONUS
            found.add(Scored(word, id, cost, level, score))
        }
        if (found.isEmpty()) return null
        found.sortByDescending { it.score }
        val best = found[0]

        // Still typing: the letters so far start a common word (a completion about as common as the fix).
        if (topCompletionLevel(en, key) >= best.level - COMPLETION_GAP) return null
        if (rom != null && topCompletionLevel(rom, key) >= ROM_KNOWN + COMPLETION_GAP) return null

        // by itself only: a slip-like edit, of a word no list knows, to a common word that clearly leads, with no Banglish word as close
        var auto = typedLevel < KNOWN_LEVEL && best.cost <= C_DOUBLED + 1e-9 && best.level >= AUTO_FIX_LEVEL
        if (auto && found.size > 1 && best.score - found[1].score < MARGIN) auto = false
        if (auto && rom != null && banglishRival(en, rom, edits, best.cost)) auto = false
        return Fix(best.word, auto)
    }

    /** `dont` -> `don't`, `didnt` -> `didn't`: the apostrophe goes back before the final t. The lists have `don` and `didn` as fragments, so no scoring here. */
    private fun contractionFix(en: LangPack, key: String, typedLevel: Int): Fix? {
        if (key.length < 4 || !key.endsWith("nt") || typedLevel > CONTRACTION_MAX_TYPED) return null
        val c = key.substring(0, key.length - 1) + "'t"
        val id = en.indexOf(c)
        return if (id >= 0 && en.countLevel(id) >= CONTRACTION_MIN_LEVEL) Fix(c, true) else null
    }

    /** Is [w] a Banglish word? The Banglish list knows it well and the English list does not rank it clearly higher (`ami`, `valo`, `bhai`; not `the`). */
    internal fun isBanglish(en: LangPack, rom: LangPack, w: String): Boolean {
        val r = levelOf(rom, w)
        if (r < ROM_KNOWN) return false
        val e = levelOf(en, w)
        return e < 0 || r - e >= BANGLISH_LEAD
    }

    private fun levelOf(p: LangPack, key: String): Int {
        val i = p.indexOf(key)
        return if (i < 0) -1 else p.countLevel(i)
    }

    /** The scored candidates, best first (for the tests and the tuning). Not used on the keyboard. */
    internal fun debugCandidates(en: LangPack, typed: String, w1: String?): List<Scored> {
        val key = TextNorm.key(typed)
        val w1Id = if (w1 != null) en.indexOf(w1) else -1
        val out = ArrayList<Scored>()
        for ((word, cost) in edits(key)) {
            val id = en.indexOf(word)
            if (id < 0) continue
            val level = en.countLevel(id)
            var score = level - COST_WEIGHT * cost
            if (w1Id >= 0 && en.bigramCount(w1Id, id) > 0f) score += CONTEXT_BONUS
            out.add(Scored(word, id, cost, level, score))
        }
        out.sortByDescending { it.score }
        return out
    }

    // ---- pieces

    /** The lookup form of [typed], or null when the word may not be touched at all. */
    internal fun eligibleKey(typed: String, capitalizeStart: Boolean): String? {
        if (typed.length < MIN_LENGTH || typed.length > MAX_LENGTH) return null
        var upper = 0
        for (i in typed.indices) {
            val c = typed[i]
            val ok = (c in 'a'..'z') || (c in 'A'..'Z') || TextNorm.isApostrophe(c)
            if (!ok) return null
            if (c in 'A'..'Z') {
                upper++
                if (i != 0) return null // ALL CAPS, camelCase and the like are names and codes: leave them
            }
        }
        if (upper > 0 && !capitalizeStart) return null // a capital in the middle of a sentence is a name
        if (typed.all { TextNorm.isApostrophe(it) }) return null
        return TextNorm.key(typed)
    }

    /** The highest count level among the words that start with [key] and are longer than it (-1 when there is none). */
    internal fun topCompletionLevel(p: LangPack, key: String): Int {
        val from = p.prefixStart(key)
        val to = minOf(p.prefixEnd(key), from + MAX_COMPLETION_SCAN)
        var best = -1
        for (i in from until to) {
            if (p.word(i).length == key.length) continue
            val l = p.countLevel(i)
            if (l > best) best = l
        }
        return best
    }

    /** Is a Banglish-only word (not an English one) about as close to the typed word as the best fix? */
    private fun banglishRival(en: LangPack, rom: LangPack, edits: Map<String, Double>, bestCost: Double): Boolean {
        for ((word, cost) in edits) {
            if (cost > bestCost + ROM_COST_SLACK) continue
            val r = rom.indexOf(word)
            if (r < 0 || rom.countLevel(r) < ROM_RIVAL_LEVEL) continue
            if (en.indexOf(word) >= 0) continue
            return true
        }
        return false
    }

    private fun isVowel(c: Char): Boolean = c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u'

    /**
     * Every string one edit away from [key] with the price of the cheapest way to get there. The word itself, pure completions (a letter
     * missing at the end) and a deleted last letter (unless it doubles the one before) are left out, and so are far-fetched
     * substitutions (a consonant for a far-away letter), which would turn slang like `thx` into a word.
     */
    internal fun edits(key: String): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        fun add(w: String, cost: Double) {
            if (w == key || w.length < 2) return
            val old = out[w]
            if (old == null || cost < old) out[w] = cost
        }
        val n = key.length
        // an extra letter in the typed word
        for (i in 0 until n) {
            val doubled = (i > 0 && key[i] == key[i - 1]) || (i + 1 < n && key[i] == key[i + 1])
            if (i == n - 1 && !doubled) continue // `emojis` is not a typo of `emoji`
            add(key.removeRange(i, i + 1), if (doubled) C_DOUBLED else C_LETTER)
        }
        // two neighbouring letters swapped
        for (i in 0 until n - 1) {
            if (key[i] == key[i + 1]) continue
            val sb = StringBuilder(key)
            sb[i] = key[i + 1]
            sb[i + 1] = key[i]
            add(sb.toString(), C_TRANSPOSE)
        }
        // a wrong letter: a neighbouring key (a slip) or a vowel for a vowel (a spelling mistake)
        for (i in 0 until n) for (ch in ALPHABET) {
            if (ch == key[i] || ch == '\'' || key[i] == '\'') continue
            val cost = when {
                adjacent(key[i], ch) -> C_NEIGHBOUR
                isVowel(key[i]) && isVowel(ch) -> C_VOWEL
                else -> C_FAR
            }
            if (cost >= C_FAR) continue
            val sb = StringBuilder(key)
            sb[i] = ch
            add(sb.toString(), cost)
        }
        // a missing letter (not at the very end: that is a completion)
        for (i in 0 until n) for (ch in ALPHABET) {
            val cost = when {
                ch == '\'' -> C_APOSTROPHE
                (i > 0 && key[i - 1] == ch) || key[i] == ch -> C_DOUBLED
                else -> C_LETTER
            }
            add(key.substring(0, i) + ch + key.substring(i), cost)
        }
        return out
    }
}
