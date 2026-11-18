package com.monsoonscript.keyboard.predict

/**
 * A pack and how much its words count in a merged list (English mode merges English and Banglish, D41). With a [bridge] the pack is the Banglish one and
 * its word-pair data comes from the Bangla pack through the bridge (D54): context words are looked up there, and successors are shown as Banglish spellings.
 */
class WeightedPack(val pack: LangPack, val prior: Float = 1f, val bridge: Bridge? = null)

/**
 * Autocomplete and next-word prediction from packed n-gram data plus what the owner typed. Pure Kotlin, no allocation
 * worth worrying about on a worker thread (a few hundred small objects per call).
 *
 * How a word is scored (context = the two words before it, either may be missing):
 *  1. Static score by "stupid backoff": its trigram share of the (w2, w1) context if that trigram exists, else
 *     0.4 x its bigram share of the w1 context, else 0.4 x 0.4 x its overall frequency. With no context: its overall frequency.
 *     Every pack scores the word and the packs' scores add up (a word in English and in Banglish gets both).
 *  2. Learned usage is mixed in as an interpolated model: score = (1 - m1 - m2) * static + m1 * P(word | owner's words)
 *     + m2 * P(word | owner's words after w1). The weights m1/m2 grow with how much the owner has typed, so a fresh
 *     install follows the dictionary and a used one follows its owner.
 * Candidates: words that start with the typed prefix (most frequent ones, plus continuations seen in the context and the owner's
 * own words), or, with no prefix, the words that followed the context plus the most frequent words as filler.
 *
 * All strings in and out are in [TextNorm.key] form; the caller restores capitals with [TextNorm.matchCase].
 */
class Predictor(private val user: UserStats? = null) {

    /** Context of one pack: [b1]/[b2] are the two previous words as ids in [pairs] (the pack itself, or the Bangla pack behind a bridge), -1 when unknown. */
    private class Ctx(val wp: WeightedPack, val pairs: LangPack, val b1: Int, val b2: Int, val tri3Total: Float, val bi2Total: Float) {
        val pack: LangPack get() = wp.pack
    }

    /** Best [limit] words, strongest first. [prefix] is what is typed of the current word ("" for next-word prediction). */
    fun predict(packs: List<WeightedPack>, prefix: String, w1: String?, w2: String?, limit: Int = 3): List<String> {
        val ctxs = packs.map { context(it, w1, w2) }
        val scores = HashMap<String, Double>()
        for (c in ctxs) gather(c, prefix, scores)

        val u = user
        var m1 = 0.0
        var m2 = 0.0
        var uTotal = 0.0
        var follow = 0.0
        if (u != null) {
            uTotal = u.total()
            if (uTotal > 0) m1 = 0.5 * uTotal / (uTotal + 800.0)
            if (w1 != null) {
                follow = u.followTotal(w1)
                if (follow > 0) m2 = 0.6 * follow / (follow + 6.0)
            }
            if (m1 + m2 > 0.9) {
                val k = 0.9 / (m1 + m2)
                m1 *= k
                m2 *= k
            }
            // The owner's own words are candidates too, even when the dictionaries do not know them.
            val own = when {
                prefix.isNotEmpty() -> u.wordsWithPrefix(prefix, 20)
                w1 != null -> u.followers(w1, 10)
                else -> u.strongestWords(5)
            }
            // A word no dictionary knows must have been typed at least twice, so one typo does not become a suggestion.
            for (w in own) {
                if (w == prefix || w in scores) continue
                val st = staticScore(ctxs, w)
                if (st > 0.0 || u.wordWeight(w) >= MIN_UNKNOWN_USES) scores[w] = st
            }
        }

        // Right after a word, that same word again is almost never what comes next (unless the owner does type it twice).
        if (prefix.isEmpty() && w1 != null && (u == null || u.pairWeight(w1, w1) <= 0.0)) scores.remove(w1)

        val combined = ArrayList<Pair<String, Double>>(scores.size)
        for ((w, s) in scores) {
            var score = s
            if (u != null && (m1 > 0 || m2 > 0)) {
                val u1 = if (uTotal > 0) u.wordWeight(w) / uTotal else 0.0
                val u2 = if (follow > 0 && w1 != null) u.pairWeight(w1, w) / follow else 0.0
                score = (1 - m1 - m2) * s + m1 * u1 + m2 * u2
            }
            combined.add(w to score)
        }
        combined.sortWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
        return combined.take(limit).map { it.first }
    }

    // ---- candidates

    private fun gather(c: Ctx, prefix: String, out: HashMap<String, Double>) {
        val p = c.pack
        val ids = LinkedHashSet<Int>()
        if (prefix.isEmpty()) {
            val t = TopN(MAX_NEXT)
            forEachTrigramSuccessor(c) { id, level -> t.offer(id, level) }
            for (id in t.ids()) ids.add(id)
            val b = TopN(MAX_NEXT)
            forEachBigramSuccessor(c) { id, level -> b.offer(id, level) }
            for (id in b.ids()) ids.add(id)
            for (id in p.topWords(FILLER)) ids.add(id)
        } else {
            val start = p.prefixStart(prefix)
            val end = p.prefixEnd(prefix)
            val t = TopN(MAX_PREFIX)
            for (i in start until end) t.offer(i, p.countLevel(i))
            for (id in t.ids()) ids.add(id)
            // continuations seen right after the context, even if they are not among the most frequent words
            forEachTrigramSuccessor(c) { id, _ -> if (p.word(id).startsWith(prefix)) ids.add(id) }
            forEachBigramSuccessor(c) { id, _ -> if (p.word(id).startsWith(prefix)) ids.add(id) }
        }
        for (id in ids) {
            val w = p.word(id)
            if (w == prefix) continue
            val s = scoreId(c, id)
            out[w] = (out[w] ?: 0.0) + s
        }
    }

    /** Words (ids of the context's own pack) that followed the two previous words, with their count level. Behind a bridge: Banglish spellings of the Bangla successors. */
    private fun forEachTrigramSuccessor(c: Ctx, f: (id: Int, level: Int) -> Unit) {
        if (c.b1 < 0 || c.b2 < 0) return
        val q = c.pairs
        var i = q.trigramLowerBound(c.b2, c.b1, 0)
        while (i < q.trigramCount && q.trigramW1(i) == c.b2 && q.trigramW2(i) == c.b1) {
            val id = ownId(c, q.trigramW3(i))
            if (id >= 0) f(id, q.trigramLevelAt(i))
            i++
        }
    }

    private fun forEachBigramSuccessor(c: Ctx, f: (id: Int, level: Int) -> Unit) {
        if (c.b1 < 0) return
        val q = c.pairs
        var i = q.bigramLowerBound(c.b1, 0)
        while (i < q.bigramCount && q.bigramW1(i) == c.b1) {
            val id = ownId(c, q.bigramW2(i))
            if (id >= 0) f(id, q.bigramLevelAt(i))
            i++
        }
    }

    /** A word id of the pair pack as an id of the context's own pack (the same one, or the best Banglish spelling behind a bridge); -1 when there is none. */
    private fun ownId(c: Ctx, pairId: Int): Int = c.wp.bridge?.romOf(pairId) ?: pairId

    // ---- scoring

    private fun context(wp: WeightedPack, w1: String?, w2: String?): Ctx {
        val p = wp.pack
        val bridge = wp.bridge
        val pairs = bridge?.bn ?: p
        val i1 = if (w1 != null) p.indexOf(w1) else -1
        val i2 = if (w2 != null && i1 >= 0) p.indexOf(w2) else -1
        val b1 = if (bridge == null) i1 else bridge.bnOf(i1)
        val b2 = if (bridge == null || i2 < 0) i2 else bridge.bnOf(i2)
        var tri = 0f // total count of everything seen after (w2, w1): the denominator of a trigram share
        var bi = 0f // total count of everything seen after w1
        if (b1 >= 0 && b2 >= 0) {
            var k = pairs.trigramLowerBound(b2, b1, 0)
            while (k < pairs.trigramCount && pairs.trigramW1(k) == b2 && pairs.trigramW2(k) == b1) { tri += pairs.trigramCountAt(k); k++ }
        }
        if (b1 >= 0) {
            var k = pairs.bigramLowerBound(b1, 0)
            while (k < pairs.bigramCount && pairs.bigramW1(k) == b1) { bi += pairs.bigramCountAt(k); k++ }
        }
        return Ctx(wp, pairs, b1, b2, tri, bi)
    }

    private fun scoreId(c: Ctx, id: Int): Double {
        val p = c.pack
        val unigram = p.count(id) / p.totalCount.toDouble()
        val prior = c.wp.prior.toDouble()
        // No context, or this pack has no pair data for it (the Banglish pack has none at all): the word's own frequency, unpenalized,
        // so a pack is not punished for lacking data that another pack does not have either.
        if (c.b1 < 0 || (c.bi2Total <= 0f && c.tri3Total <= 0f)) return unigram * prior
        val q = c.pairs
        val pid = c.wp.bridge?.bnOf(id) ?: id // the word as an id of the pair pack; -1 = not known there (backoff to its own frequency)
        if (pid >= 0) {
            if (c.b2 >= 0 && c.tri3Total > 0) {
                val c3 = q.trigramCount(c.b2, c.b1, pid)
                if (c3 > 0f) return c3 / c.tri3Total.toDouble() * prior
            }
            if (c.bi2Total > 0) {
                val c2 = q.bigramCount(c.b1, pid)
                if (c2 > 0f) return ALPHA * c2 / c.bi2Total.toDouble() * prior
            }
        }
        return ALPHA * ALPHA * unigram * prior
    }

    /** Static score of a word the candidate search did not produce (the owner's own words): 0 when no pack knows it. */
    private fun staticScore(ctxs: List<Ctx>, word: String): Double {
        var s = 0.0
        for (c in ctxs) {
            val id = c.pack.indexOf(word)
            if (id >= 0) s += scoreId(c, id)
        }
        return s
    }

    companion object {
        private const val ALPHA = 0.4
        private const val MAX_PREFIX = 48
        private const val MAX_NEXT = 40
        private const val FILLER = 24
        private const val MIN_UNKNOWN_USES = 1.9 // a little under 2: a use fades a bit before the second one

        /**
         * Pack weights for English mode, which merges English and Banglish words (D41). Banglish words are down-weighted
         * (they would otherwise win on their sharper frequencies) unless one of the two previous words is Banglish-only,
         * which means the owner is writing Banglish right now.
         */
        fun englishMix(
            english: LangPack, banglish: LangPack, w1: String?, w2: String?,
            prefixLength: Int = 0, bridge: (() -> Bridge?)? = null,
        ): List<WeightedPack> {
            var banglishContext = false
            for (w in arrayOf(w1, w2)) if (w != null && leansBanglish(english, banglish, w)) banglishContext = true
            return if (banglishContext) {
                // The Bangla pair data (through the bridge) answers "what follows this Banglish word"; the bridge is only loaded when needed.
                listOf(WeightedPack(english, 0.1f), WeightedPack(banglish, 1f, bridge?.invoke()))
            } else {
                // A very short prefix with no Banglish word before it: Banglish words get a better chance (D54), English still wins on common words.
                val rom = if (prefixLength in 1..SHORT_PREFIX) SHORT_PREFIX_PRIOR else 0.05f
                listOf(WeightedPack(english, 1f), WeightedPack(banglish, rom))
            }
        }

        /** True when one of the two previous words is a Banglish word (see [leansBanglish]). */
        fun banglishContext(english: LangPack, banglish: LangPack, w1: String?, w2: String?): Boolean =
            (w1 != null && leansBanglish(english, banglish, w1)) || (w2 != null && leansBanglish(english, banglish, w2))

        /**
         * A word the Banglish list knows and that is at least [BANGLISH_RATIO] times more frequent there than in English. The English
         * list comes from subtitles and also holds foreign words ("ami"), so "not in English" would be too strict.
         */
        private fun leansBanglish(english: LangPack, banglish: LangPack, word: String): Boolean {
            val r = banglish.indexOf(word)
            if (r < 0) return false
            val e = english.indexOf(word)
            if (e < 0) return true
            return banglish.count(r) / banglish.totalCount > BANGLISH_RATIO * english.count(e) / english.totalCount
        }

        private const val BANGLISH_RATIO = 20f
        private const val SHORT_PREFIX = 3
        private const val SHORT_PREFIX_PRIOR = 0.4f
    }
}
