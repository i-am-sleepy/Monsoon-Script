package com.monsoonscript.keyboard.predict

/**
 * Typo fixing for English typing (L8, D69, strengthened in D73): finds the real word a misspelled word was probably meant to be.
 *
 * Pure Kotlin, no Android. Everything runs on the prediction worker thread (see `ime/PredictionWorker`).
 *
 * The word lists contain many common misspellings at a moderate frequency next to genuinely rare real words, so "the word is in the
 * list" does not mean "it is right": the word is compared with how common the intended word is.
 *  - Only 3+ plain letters (or an apostrophe), all lowercase (a capital first letter only at a sentence start). A word that the
 *    Banglish list knows, that the owner typed before, or whose fix the owner undid is never touched.
 *  - A word the English list knows well (level [STRONG_KNOWN] and up) is a real word. A rare listed word may be fixed when the right
 *    word is far more common ([WEAK_AUTO_GAP]); an unlisted word needs only a fix that is common enough.
 *  - Candidates are the real words up to TWO slips away ([MAX_COST]): two letters swapped, a neighbouring key hit, a vowel swapped for
 *    a vowel, a letter doubled, missing or extra. Every slip has a price, so `tgid` -> `this` (two neighbouring keys) is cheap and
 *    a far-away letter is expensive. A pure completion (a letter missing at the END) is left to the strip's normal suggestions, and so is
 *    an extra last letter (valid plurals and verb forms the list lacks).
 *  - Score = how common the word is, minus a price per slip, plus a bonus when the pair "previous word + candidate" is in the pairs
 *    data (`it works`, not `it woke`). The best must lead the second best clearly.
 *  - Applied by itself ([Fix.auto]): a well-known misspelling ([Misspellings]), a missing apostrophe (`dont` -> `don't`, [contractionFix]),
 *    and a clear fix of a word that no list knows or that is rare in the list. Longer and rarer fixes need a bigger lead, two slips
 *    need a word of 4+ letters. Anything else is only a strip candidate that the owner may tap.
 *  - A Banglish-only word about as close as the fix (an ambiguous case) turns the fix into a tap candidate; after a Banglish word
 *    nothing but the misspelling list and `dont` fires; when the typed letters start a common word the owner is still typing.
 */
object Corrector {
    /** [word] is the fix in lookup form (lowercase). [auto]: sure enough to be applied on space and shown first on the strip. */
    class Fix(val word: String, val auto: Boolean)

    internal class Scored(val word: String, val id: Int, val cost: Double, val level: Int, val score: Double, val context: Double = 0.0)

    // ---- tuning (calibrated on the real packs, see CorrectorTest)

    const val MIN_LENGTH = 3
    const val MAX_LENGTH = 20

    /** An English entry from this count level up is a real word (misspellings in the list sit around 45-85; about 3.5% per level, 20 levels = twice as frequent). */
    const val STRONG_KNOWN = 90

    /** From this level up an English entry counts as listed (a weakly listed word is fixed only by a slip-like edit). */
    const val KNOWN_LEVEL = 40

    /** A listed word up to this level may be fixed by itself when the right word is [WEAK_AUTO_GAP] more common (`loke`, `woks`). */
    const val WEAK_AUTO_MAX = 70
    const val WEAK_AUTO_GAP = 100

    /**
     * A Banglish entry from [ROM_KNOWN] up (single-occurrence noise sits at 20-26, a token seen several times in Banglish text, `baraber`,
     * `islame`, `loke`, from about 30) protects the word, unless the previous word is English and the pairs data supports the fix (`mistakes
     * like`). From [TABLE_ROM_MAX] up (`ami`, `valo`) the word is Banglish for sure and never touched. The misspelling list and `dont` are
     * checked before all this (a misspelling like `teh` is also repeated in chat), so the list's own entries may sit below [TABLE_ROM_MAX].
     */
    const val ROM_KNOWN = 30
    const val TABLE_ROM_MAX = 60

    /** ... and then the fix must also be one of the very commonest English words (`like` 215; `age` 162, `island` 156, `barber` 119 are not). */
    const val WEAK_BANGLISH_LEVEL = 195

    /** A word is Banglish when its Banglish level minus its English level is at least this (see [isBanglish]). */
    const val BANGLISH_LEAD = -10

    /** The fix must be this many levels more common than the typed word. */
    const val MIN_GAP = 45

    /** The fix itself must be at least this common. */
    const val MIN_FIX_LEVEL = 90

    /** A fix applied by itself must be at least this common: [AUTO_FIX_LEVEL] for short words, [AUTO_FIX_LEVEL_LONG] from [LONG_WORD] letters (a long word with one sensible fix is safe). */
    const val AUTO_FIX_LEVEL = 120
    const val AUTO_FIX_LEVEL_LONG = 100
    const val LONG_WORD = 7

    /** One unit of edit cost is worth this many count levels. */
    const val COST_WEIGHT = 80.0

    /** A pair "previous word + candidate" in the data adds [CONTEXT_BASE] plus a share of its level, at most [CONTEXT_MAX]. */
    const val CONTEXT_BASE = 30.0
    const val CONTEXT_MAX = 70.0

    /** Best must lead the second best by this many score points; two slips need more. */
    const val MARGIN = 40.0
    const val MARGIN_MULTI = 60.0

    /** The owner is probably still typing when a completion of the typed letters is not much rarer than the fix. */
    const val COMPLETION_GAP = 60

    /**
     * A Banglish-only word is a rival of the fix when it is about as likely: its Banglish level plus [ROM_BOOST] (the Banglish counts are on a
     * lower scale than the English ones) minus the price of its edit reaches the fix's score minus [RIVAL_SLACK]. Rare noise words
     * (below [ROM_RIVAL_LEVEL], or too many slips away: up to [ROM_COST_SLACK] more than the fix) never count.
     */
    const val ROM_RIVAL_LEVEL = 30
    const val ROM_COST_SLACK = 0.6
    const val ROM_BOOST = 70.0
    const val RIVAL_SLACK = 30.0

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

    /** The most a fix may cost: two slips (a far letter plus a neighbouring key still fits). */
    const val MAX_COST = 2.1

    /** A fix with two slips is offered only for words of at least [MULTI_MIN_LENGTH] letters when both slips are neighbouring keys (cost up to [MULTI_CHEAP_COST]), else [MULTI_LONG_LENGTH]. */
    const val MULTI_MIN_LENGTH = 4
    const val MULTI_CHEAP_COST = 1.5
    const val MULTI_LONG_LENGTH = 6

    private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz'"
    private const val MAX_COMPLETION_SCAN = 40_000
    private const val INF = 1e9

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
        val typedLevel = levelOf(en, key) // -1 when the list does not have it
        Misspellings.fix(key)?.let { w -> if (levelOf(en, w) >= MIN_FIX_LEVEL) return Fix(w, true) }
        contractionFix(en, key, typedLevel)?.let { return it }
        val romLevel = if (rom != null) levelOf(rom, key) else -1
        if (romLevel >= TABLE_ROM_MAX) return null
        val weakBanglish = romLevel >= ROM_KNOWN // seen a few times in Banglish text: only a fix that the previous word supports
        if (typedLevel >= STRONG_KNOWN) return null
        if (w1 != null && rom != null && isBanglish(en, rom, w1)) return null // after a Banglish word: hands off (D69)

        // how far a fix may be: unlisted words up to two slips, rare listed words one, the rest only a slip-like edit
        val costLimit = when {
            typedLevel < KNOWN_LEVEL -> MAX_COST
            typedLevel <= WEAK_AUTO_MAX -> C_LETTER
            else -> C_VOWEL
        }
        val typedBase = maxOf(typedLevel, 0)
        val w1Id = if (w1 != null) en.indexOf(w1) else -1
        val found = ArrayList<Scored>()
        for ((word, cost) in candidates(en, key, costLimit)) {
            val id = en.indexOf(word)
            if (id < 0) continue
            val level = en.countLevel(id)
            if (level < MIN_FIX_LEVEL || level - typedBase < MIN_GAP) continue
            val ctx = contextBonus(en, w1Id, id)
            found.add(Scored(word, id, cost, level, level - COST_WEIGHT * cost + ctx, ctx))
        }
        if (found.isEmpty()) return null
        found.sortByDescending { it.score }
        val best = found[0]
        if (weakBanglish && (best.context <= 0.0 || best.level < WEAK_BANGLISH_LEVEL)) return null

        // Still typing: the letters so far start a common word (a completion about as common as the fix).
        if (topCompletionLevel(en, key) >= best.level - COMPLETION_GAP) return null
        if (rom != null && topCompletionLevel(rom, key) >= ROM_KNOWN + COMPLETION_GAP) return null

        val multi = best.cost > C_LETTER + 1e-9 // two slips: only for words long enough to be sure (`tmrw` -> `tire` would be noise)
        val n = key.length
        if (multi && n < (if (best.cost <= MULTI_CHEAP_COST + 1e-9) MULTI_MIN_LENGTH else MULTI_LONG_LENGTH)) return null
        var auto = best.level >= (if (n >= LONG_WORD) AUTO_FIX_LEVEL_LONG else AUTO_FIX_LEVEL)
        if (typedLevel >= KNOWN_LEVEL && !(typedLevel <= WEAK_AUTO_MAX && best.level - typedLevel >= WEAK_AUTO_GAP)) auto = false
        if (auto && found.size > 1 && best.score - found[1].score < (if (multi) MARGIN_MULTI else MARGIN)) auto = false
        if (auto && rom != null && banglishRival(en, rom, key, best)) auto = false
        return Fix(best.word, auto)
    }

    /** The scored candidates, best first (for the tests and the tuning). Not used on the keyboard. */
    internal fun debugCandidates(en: LangPack, typed: String, w1: String?): List<Scored> {
        val key = TextNorm.key(typed)
        val w1Id = if (w1 != null) en.indexOf(w1) else -1
        val out = ArrayList<Scored>()
        for ((word, cost) in candidates(en, key, MAX_COST)) {
            val id = en.indexOf(word)
            if (id < 0) continue
            val level = en.countLevel(id)
            out.add(Scored(word, id, cost, level, level - COST_WEIGHT * cost + contextBonus(en, w1Id, id)))
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

    /** The bonus for a candidate that follows the previous word in the pairs data (`it works`): bigger for a more common pair, 0 when there is no pair. */
    private fun contextBonus(en: LangPack, w1Id: Int, id: Int): Double {
        if (w1Id < 0) return 0.0
        val i = en.bigramLowerBound(w1Id, id)
        if (i >= en.bigramCount || en.bigramW1(i) != w1Id || en.bigramW2(i) != id) return 0.0
        return minOf(CONTEXT_MAX, CONTEXT_BASE + en.bigramLevelAt(i) / 3.0)
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

    /** Is a Banglish-only word (not an English one) about as likely as the best fix? */
    private fun banglishRival(en: LangPack, rom: LangPack, key: String, best: Scored): Boolean {
        for ((word, cost) in candidates(rom, key, best.cost + ROM_COST_SLACK)) {
            val r = rom.indexOf(word)
            if (r < 0 || rom.countLevel(r) < ROM_RIVAL_LEVEL || en.indexOf(word) >= 0) continue
            if (rom.countLevel(r) + ROM_BOOST - COST_WEIGHT * cost >= best.score - RIVAL_SLACK) return true
        }
        return false
    }

    /**
     * The real words (of pack [p]) within [limit] slips of [key], with the price of the cheapest way: every one-slip edit (any first
     * letter) and, for the words that start with the same letter, a weighted edit distance (two slips). Pure completions are left out.
     */
    internal fun candidates(p: LangPack, key: String, limit: Double): Map<String, Double> {
        val out = LinkedHashMap<String, Double>()
        for ((w, c) in edits(key)) if (c <= limit + 1e-9 && p.indexOf(w) >= 0) out[w] = c
        if (limit > C_LETTER + 1e-9) {
            val first = key.substring(0, 1)
            val from = p.prefixStart(first)
            val to = p.prefixEnd(first)
            for (i in from until to) {
                val w = p.word(i)
                val d = w.length - key.length
                if (d > 2 || d < -2 || w == key || w.startsWith(key)) continue
                if (d == -1 && key.startsWith(w) && key[key.length - 1] != key[key.length - 2]) continue // `emojis` is not a typo of `emoji`
                val c = editCost(key, w, limit)
                if (c <= limit + 1e-9) {
                    val old = out[w]
                    if (old == null || c < old) out[w] = c
                }
            }
        }
        return out
    }

    private fun isVowel(c: Char): Boolean = c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u'

    private fun subCost(a: Char, b: Char): Double = when {
        a == b -> 0.0
        a == '\'' || b == '\'' -> C_FAR
        adjacent(a, b) -> C_NEIGHBOUR
        isVowel(a) && isVowel(b) -> C_VOWEL
        else -> C_FAR
    }

    /** What an extra letter in the typed word costs (cheap when it doubles a neighbour). */
    private fun extraCost(a: String, i: Int): Double =
        if ((i > 0 && a[i] == a[i - 1]) || (i + 1 < a.length && a[i] == a[i + 1])) C_DOUBLED else C_LETTER

    /** What a missing letter costs: an apostrophe is cheap, so is a doubled letter. [b] is the real word, [j] the missing letter's place in it. */
    private fun missingCost(b: String, j: Int): Double = when {
        b[j] == '\'' -> C_APOSTROPHE
        (j > 0 && b[j] == b[j - 1]) || (j + 1 < b.length && b[j] == b[j + 1]) -> C_DOUBLED
        else -> C_LETTER
    }

    /** The weighted edit distance from the typed word [a] to the real word [b] (swap of two neighbours included); more than [limit] when it is not worth knowing. */
    internal fun editCost(a: String, b: String, limit: Double): Double {
        val n = a.length
        val m = b.length
        var prev2 = DoubleArray(m + 1)
        var prev = DoubleArray(m + 1)
        var cur = DoubleArray(m + 1)
        for (j in 1..m) prev[j] = prev[j - 1] + missingCost(b, j - 1)
        for (i in 1..n) {
            val ex = extraCost(a, i - 1)
            cur[0] = prev[0] + ex
            var rowMin = cur[0]
            for (j in 1..m) {
                var v = prev[j - 1] + subCost(a[i - 1], b[j - 1])
                val del = prev[j] + ex
                if (del < v) v = del
                val ins = cur[j - 1] + missingCost(b, j - 1)
                if (ins < v) v = ins
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1] && a[i - 1] != a[i - 2]) {
                    val t = prev2[j - 2] + C_TRANSPOSE
                    if (t < v) v = t
                }
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > limit + 1e-9) return INF
            val tmp = prev2
            prev2 = prev
            prev = cur
            cur = tmp
        }
        return prev[m]
    }

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
