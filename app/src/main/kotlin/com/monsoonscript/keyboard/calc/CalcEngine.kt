package com.monsoonscript.keyboard.calc

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * What the suggestion strip offers for a calculation at the end of the typed text (L13, D97). [typed] is the exact text of the calculation (what "replace" deletes),
 * [value] the answer as shown (Bangla digits when the calculation was typed with Bangla digits).
 */
class CalcOffer(
    val start: Int,
    val typed: String,
    val value: String,
    val endsWithEquals: Boolean,
    val spaced: Boolean,
) {
    /** The chip that adds the answer after the calculation. */
    val appendLabel: String get() = if (endsWithEquals) value else "= $value"

    /** The chip that replaces the calculation by the answer. */
    val replaceLabel: String get() = "\u2192 $value"

    val appendText: String get() = if (endsWithEquals) (if (spaced) " $value" else value) else if (spaced) " = $value" else "=$value"

    val replaceText: String get() = value
}

/**
 * The calculator behind the strip (L13, D97). Pure Kotlin, offline, one pass over at most [WINDOW] characters per key. [offer] looks at the end of the text before the
 * cursor, finds the longest calculation that ends right there and answers it; most of the time (normal typing) it says null after one look at the last character.
 * What it understands: + - * / x × ÷ ^ **, brackets, % (also "20% of 150", "150 + 10%"), mod, ! , ² ³, √, constants (pi e tau phi), the usual functions (sqrt, trig in
 * degrees or radians, logs, exp, rounding, min / max / sum / avg, gcd / lcm, ncr / npr, ...), 0x / 0b / 0o numbers, "255 to hex", and unit conversions
 * ("5 km in miles", "98.6 f to c"). Things that look like dates, ranges or phone numbers (5-10, 12/5, 01711-222333) are left alone unless an "=" or spaces say it is
 * a sum.
 */
object CalcEngine {
    const val WINDOW = 80
    private const val MAX_DEPTH = 28

    // ---- token kinds
    private const val NUM = 1
    private const val ID = 2
    private const val PLUS = 3
    private const val MINUS = 4
    private const val MUL = 5
    private const val DIV = 6
    private const val POW = 7
    private const val LP = 8
    private const val RP = 9
    private const val COMMA = 10
    private const val BANG = 11
    private const val PCT = 12
    private const val EQ = 13
    private const val ARROW = 14
    private const val DEG = 15

    private class Tok(val kind: Int, val num: Double = 0.0, val text: String = "")

    private class CalcError : RuntimeException() {
        override fun fillInStackTrace(): Throwable = this
    }

    private val FAIL = CalcError()

    private fun fail(): Nothing = throw FAIL

    private class Res(val value: String, val spaced: Boolean)

    private val CONSTANTS = hashSetOf("pi", "e", "tau", "phi")
    private val CONVERT_WORDS = hashSetOf("to", "in", "as", "into")
    private val BASE_WORDS = mapOf(
        "hex" to 16, "hexadecimal" to 16, "bin" to 2, "binary" to 2, "oct" to 8, "octal" to 8, "dec" to 10, "decimal" to 10,
    )
    private val FUNCTIONS = hashSetOf(
        "sqrt", "cbrt", "abs", "sign", "floor", "ceil", "round", "trunc", "fract", "exp", "ln", "log", "log2", "log10", "logb",
        "sin", "cos", "tan", "asin", "acos", "atan", "atan2", "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh", "asinh", "acosh", "atanh",
        "sec", "csc", "cot", "deg", "rad", "sq", "cube", "pow", "root", "min", "max", "sum", "avg", "mean",
        "gcd", "lcm", "ncr", "npr", "fact", "factorial", "mod", "hypot",
    )

    // ---------------------------------------------------------------- the entry point

    /**
     * The offer for the calculation that ends [before] (the text before the cursor, its last line), or null. [radians] picks the angle unit of the trig functions.
     */
    fun offer(before: CharSequence, radians: Boolean = false): CalcOffer? {
        val total = before.length
        if (total == 0 || !canEnd(before)) return null
        var from = max(0, total - WINDOW)
        for (i in total - 1 downTo from) if (before[i] == '\n') {
            from = i + 1
            break
        }
        if (from >= total) return null
        val n = total - from
        val orig = before.subSequence(from, total).toString()
        // digits of both scripts count as digits: the engine works on an ASCII copy of the same length
        val bangla = CalcFormat.hasBanglaDigit(orig)
        val norm = if (bangla) String(CharArray(n) { CalcFormat.asciiDigit(orig[it]) }) else orig
        var any = norm[n - 1] == ')' || norm[n - 1] == '=' || norm[n - 1] == '\u03C0' // sqrt(pi), pi=: no digit needed
        if (!any) for (c in norm) if (c in '0'..'9') {
            any = true
            break
        }
        if (!any) return null
        for (i in 0 until n) {
            if (!isStart(norm, i)) continue
            val r = evaluate(norm, i, radians) ?: continue
            val sub = norm.substring(i)
            val eq = sub.trimEnd().endsWith("=")
            if (!eq && looksLikeNonMath(sub)) return null // a date, a range, a phone number: not even its shorter tails (5/2026 in 12/5/2026) are sums
            val value = if (bangla) CalcFormat.toBangla(r.value) else r.value
            return CalcOffer(from + i, orig.substring(i), value, eq, r.spaced || orig.indexOf(' ', i) >= 0)
        }
        return null
    }

    /** Can a calculation end with the last character? Digits, closing brackets, % ! = and a few signs always; a letter only as the end of a unit, base or constant word. */
    private fun canEnd(s: CharSequence): Boolean {
        val c = s[s.length - 1]
        if (c in '0'..'9' || CalcFormat.isBanglaDigit(c)) return true
        when (c) {
            ')', ']', '}', '%', '!', '=', '\u00B2', '\u00B3', '\u00B0', '\u03C0' -> return true
        }
        if (!c.isLetter()) return false
        var i = s.length - 1
        while (i > 0 && s[i - 1].isLetter()) i--
        val word = s.subSequence(i, s.length).toString().lowercase()
        return word in CONSTANTS || word in BASE_WORDS || CalcUnits.find(word) != null
    }

    /** A calculation may start here: not in the middle of a word or number, and with a character a calculation can start with. */
    private fun isStart(s: String, i: Int): Boolean {
        val c = s[i]
        if (i > 0) {
            val p = s[i - 1]
            if (p.isLetterOrDigit() || p == '.' || p == '_') return false
        }
        if (c in '0'..'9' || c == '(' || c == '[' || c == '{' || c == '\u221A' || c == '\u03C0') return true
        if (c == '.') return i + 1 < s.length && s[i + 1] in '0'..'9'
        if (c == '-' || c == '+' || c == '\u2212') return i + 1 < s.length && (s[i + 1] in '0'..'9' || s[i + 1] == '.' || s[i + 1] == '(' || s[i + 1].isLetter())
        return c.isLetter()
    }

    /** Plain digit groups joined with - or / and no spaces: a range, a date or a phone number, not a sum (5-10, 12/5, 2020-2024, 01711-222333). */
    private fun looksLikeNonMath(e: String): Boolean {
        if (e.isEmpty() || e[0] == '-') return false
        var dashes = 0
        var slashes = 0
        for (c in e) {
            when {
                c in '0'..'9' -> {}
                c == '-' -> dashes++
                c == '/' -> slashes++
                else -> return false
            }
        }
        if (dashes > 0 && slashes == 0) return true
        if (slashes == 0 || dashes > 0) return false
        val parts = e.split('/')
        if (parts.any { it.isEmpty() }) return false
        if (parts.size >= 3) return true
        if (parts[0].length > 1 && parts[0][0] == '0' || parts[1].length > 1 && parts[1][0] == '0') return true
        val a = parts[0].toLongOrNull() ?: return false
        val b = parts[1].toLongOrNull() ?: return false
        return (a in 1..31 && b in 1..12) || (a in 1..12 && b in 1..31)
    }

    // ---------------------------------------------------------------- evaluating one candidate

    private fun evaluate(s: String, start: Int, radians: Boolean): Res? {
        val toks = lex(s, start) ?: return null
        var size = toks.size
        if (size == 0) return null
        var eq = false
        if (toks[size - 1].kind == EQ) {
            eq = true
            size--
        }
        if (size == 0) return null
        val list = toks.subList(0, size)
        try {
            val conv = convertTail(list, radians)
            if (conv != null) return conv
            val p = Parser(list, radians)
            val v = p.parseAll()
            if (!(p.strong > 0 || (eq && p.weak > 0))) return null
            val text = CalcFormat.number(v) ?: return null
            return Res(text, false)
        } catch (e: CalcError) {
            return null
        }
    }

    /** "5 km in miles", "98.6 f to c", "255 to hex": the tail says "convert"; null when the tail does not look like that at all. */
    private fun convertTail(t: List<Tok>, radians: Boolean): Res? {
        val n = t.size
        if (n < 3) return null
        val target = t[n - 1]
        val kw = t[n - 2]
        if (target.kind != ID) return null
        if (!(kw.kind == ARROW || (kw.kind == ID && kw.text in CONVERT_WORDS))) return null
        val targetName = target.text
        // a unit conversion needs a unit before the word as well
        if (n >= 4 && t[n - 3].kind == ID) {
            val from = CalcUnits.find(t[n - 3].text)
            val to = CalcUnits.find(targetName)
            if (from != null && to != null) {
                val value = Parser(t.subList(0, n - 3), radians).parseAll()
                val out = CalcUnits.convert(value, from, to) ?: fail()
                val text = CalcFormat.number(out) ?: fail()
                return Res("$text ${to.symbol}", true)
            }
        }
        val base = BASE_WORDS[targetName]
        if (base != null) {
            val value = Parser(t.subList(0, n - 2), radians).parseAll()
            if (value != Math.rint(value) || abs(value) >= 9.0e15) fail()
            if (base == 10) return Res(CalcFormat.number(value) ?: fail(), true)
            val l = value.toLong()
            val digits = java.lang.Long.toString(abs(l), base).uppercase()
            return Res(if (l < 0) "-$digits" else digits, true)
        }
        return null
    }

    // ---------------------------------------------------------------- the lexer

    private fun lex(s: String, start: Int): ArrayList<Tok>? {
        val out = ArrayList<Tok>()
        val n = s.length
        var i = start
        var depth = 0
        while (i < n) {
            val c = s[i]
            if (c == ' ' || c == '\t' || c == '\u00A0') {
                i++
                continue
            }
            if (c in '0'..'9' || (c == '.' && i + 1 < n && s[i + 1] in '0'..'9')) {
                var j = i
                if (c == '0' && i + 2 < n + 0 && (s[i + 1] == 'x' || s[i + 1] == 'X' || s[i + 1] == 'b' || s[i + 1] == 'B' || s[i + 1] == 'o' || s[i + 1] == 'O')) {
                    val radix = when (s[i + 1].lowercaseChar()) { 'x' -> 16; 'b' -> 2; else -> 8 }
                    var k = i + 2
                    while (k < n && Character.digit(s[k], radix) >= 0) k++
                    if (k > i + 2) {
                        val v = s.substring(i + 2, k).toLongOrNull(radix) ?: return null
                        out.add(Tok(NUM, v.toDouble()))
                        i = k
                        continue
                    }
                }
                val sb = StringBuilder()
                while (j < n && s[j] in '0'..'9') sb.append(s[j++])
                val firstGroup = sb.length
                if (depth == 0 && firstGroup in 1..3) { // 1,234,567
                    while (j + 3 < n && s[j] == ',' && s[j + 1] in '0'..'9' && s[j + 2] in '0'..'9' && s[j + 3] in '0'..'9' && (j + 4 >= n || s[j + 4] !in '0'..'9')) {
                        sb.append(s, j + 1, j + 4)
                        j += 4
                    }
                }
                if (j + 1 < n && s[j] == '.' && s[j + 1] in '0'..'9') {
                    sb.append('.')
                    j++
                    while (j < n && s[j] in '0'..'9') sb.append(s[j++])
                } else if (sb.isEmpty() && j < n && s[j] == '.') {
                    return null
                }
                if (j < n && (s[j] == 'e' || s[j] == 'E')) {
                    var k = j + 1
                    if (k < n && (s[k] == '+' || s[k] == '-')) k++
                    if (k < n && s[k] in '0'..'9') {
                        while (k < n && s[k] in '0'..'9') k++
                        sb.append(s, j, k)
                        j = k
                    }
                }
                val v = sb.toString().toDoubleOrNull() ?: return null
                out.add(Tok(NUM, v))
                i = j
                continue
            }
            if (c.isLetter() || c == '_' || (c == '\u00B0' && i + 1 < n && s[i + 1].isLetter())) {
                var j = i + 1
                while (j < n && (s[j].isLetterOrDigit() || s[j] == '_')) j++
                var word = s.substring(i, j).lowercase()
                if (j + 1 < n && s[j] == '/' && s[j + 1].isLetter()) { // km/h, m/s
                    var k = j + 1
                    while (k < n && s[k].isLetter()) k++
                    val merged = word + "/" + s.substring(j + 1, k).lowercase()
                    if (CalcUnits.isSlashUnit(merged)) {
                        word = merged
                        j = k
                    }
                }
                out.add(Tok(ID, text = canonical(word)))
                i = j
                continue
            }
            when (c) {
                '+' -> out.add(Tok(PLUS))
                '-' -> {
                    if (i + 1 < n && s[i + 1] == '>') {
                        out.add(Tok(ARROW))
                        i++
                    } else {
                        out.add(Tok(MINUS))
                    }
                }
                '\u2212', '\u2013' -> out.add(Tok(MINUS))
                '*' -> {
                    if (i + 1 < n && s[i + 1] == '*') {
                        out.add(Tok(POW))
                        i++
                    } else {
                        out.add(Tok(MUL))
                    }
                }
                '\u00D7', '\u00B7', '\u2217', '\u22C5' -> out.add(Tok(MUL))
                '/', '\u00F7' -> out.add(Tok(DIV))
                '^' -> out.add(Tok(POW))
                '\u00B2' -> {
                    out.add(Tok(POW))
                    out.add(Tok(NUM, 2.0))
                }
                '\u00B3' -> {
                    out.add(Tok(POW))
                    out.add(Tok(NUM, 3.0))
                }
                '\u221A' -> out.add(Tok(ID, text = "sqrt"))
                '\u221B' -> out.add(Tok(ID, text = "cbrt"))
                '(', '[', '{' -> {
                    depth++
                    out.add(Tok(LP))
                }
                ')', ']', '}' -> {
                    depth--
                    if (depth < 0) return null
                    out.add(Tok(RP))
                }
                ',' -> out.add(Tok(COMMA))
                '!' -> out.add(Tok(BANG))
                '%' -> out.add(Tok(PCT))
                '\u00B0' -> out.add(Tok(DEG))
                '=' -> {
                    if (i + 1 < n && s[i + 1] == '>') {
                        out.add(Tok(ARROW))
                        i++
                    } else {
                        out.add(Tok(EQ))
                    }
                }
                else -> return null
            }
            i++
        }
        for (k in 0 until out.size - 1) if (out[k].kind == EQ) return null // "=" only ends a calculation
        return out
    }

    private fun canonical(word: String): String = when (word) {
        "\u03C0" -> "pi"
        "arcsin" -> "asin"
        "arccos" -> "acos"
        "arctan" -> "atan"
        "factorial" -> "fact"
        "mean" -> "avg"
        "\u00D7" -> "x"
        else -> word
    }

    // ---------------------------------------------------------------- the parser and evaluator

    private class V(val v: Double, val pct: Boolean = false)

    private class Parser(val t: List<Tok>, val radians: Boolean) {
        var p = 0
        var strong = 0 // operators and functions: this is a calculation
        var weak = 0 // %, !, constants, degree signs: a calculation only with a trailing "="
        private var level = 0

        private fun peek(): Tok? = if (p < t.size) t[p] else null

        private fun enter() {
            if (++level > MAX_DEPTH) fail()
        }

        fun parseAll(): Double {
            val r = additive()
            if (p != t.size) fail()
            return check(r.v)
        }

        private fun check(v: Double): Double {
            if (v.isNaN() || v.isInfinite()) fail()
            return v
        }

        private fun snap(res: Double, a: Double, b: Double): Double = if (abs(res) < 1e-13 * max(abs(a), abs(b))) 0.0 else res

        fun additive(): V {
            enter()
            var left = term()
            while (true) {
                val k = peek()?.kind
                if (k != PLUS && k != MINUS) break
                p++
                val right = term()
                strong++
                val rv = if (right.pct && !left.pct) left.v * right.v else right.v // 150 + 10% is 150 + 15
                val res = if (k == PLUS) left.v + rv else left.v - rv
                left = V(check(snap(res, left.v, rv)))
            }
            level--
            return left
        }

        /** Does a value start at token [i]? ([signs]: a + or - counts too.) */
        private fun startsOperand(i: Int, signs: Boolean): Boolean {
            val tk = if (i < t.size) t[i] else return false
            return when (tk.kind) {
                NUM, LP -> true
                ID -> tk.text in CONSTANTS || tk.text in FUNCTIONS
                PLUS, MINUS -> signs
                else -> false
            }
        }

        private fun term(): V {
            var left = unary()
            while (true) {
                val tk = peek() ?: break
                when {
                    tk.kind == MUL -> {
                        p++
                        val r = unary()
                        strong++
                        left = V(check(left.v * r.v))
                    }
                    tk.kind == DIV -> {
                        p++
                        val r = unary()
                        strong++
                        if (r.v == 0.0) fail()
                        left = V(check(left.v / r.v))
                    }
                    tk.kind == ID && tk.text == "x" && startsOperand(p + 1, true) -> {
                        p++
                        val r = unary()
                        strong++
                        left = V(check(left.v * r.v))
                    }
                    tk.kind == ID && tk.text == "of" -> {
                        p++
                        val r = unary()
                        strong++
                        left = V(check(left.v * r.v))
                    }
                    (tk.kind == ID && tk.text == "mod") || (tk.kind == PCT && startsOperand(p + 1, false)) -> {
                        p++
                        val r = unary()
                        strong++
                        left = V(check(floorMod(left.v, r.v)))
                    }
                    implicitFollows(tk) -> {
                        val r = power()
                        strong++
                        left = V(check(left.v * r.v))
                    }
                    else -> break
                }
            }
            return left
        }

        /** 2(3+4), (1+2)(3+4), 2pi, 3 sin(30): a value follows a value without a sign. Two plain numbers side by side are not a product. */
        private fun implicitFollows(tk: Tok): Boolean {
            val prev = if (p > 0) t[p - 1] else return false
            return when (tk.kind) {
                LP -> true
                NUM -> prev.kind == RP || (prev.kind == ID && prev.text in CONSTANTS)
                ID -> (tk.text in FUNCTIONS || (tk.text in CONSTANTS && !(tk.text == "e" && prev.kind == NUM))) &&
                    !(tk.text == "e" && prev.kind == NUM)
                else -> false
            }
        }

        private fun unary(): V {
            val tk = peek() ?: fail()
            return when (tk.kind) {
                MINUS -> {
                    p++
                    V(-unary().v)
                }
                PLUS -> {
                    p++
                    unary()
                }
                else -> power()
            }
        }

        private fun power(): V {
            val base = postfix()
            val tk = peek()
            if (tk != null && tk.kind == POW) {
                p++
                enter()
                val exp = unary() // right to left: 2^3^2 is 2^9
                level--
                strong++
                return V(check(Math.pow(base.v, exp.v)))
            }
            return base
        }

        private fun postfix(): V {
            var v = primary()
            while (true) {
                val tk = peek() ?: break
                if (tk.kind == BANG) {
                    p++
                    weak++
                    v = V(factorial(v.v))
                } else if (tk.kind == PCT && !startsOperand(p + 1, false)) {
                    p++
                    weak++
                    v = V(v.v / 100.0, pct = true)
                } else if (tk.kind == DEG) {
                    p++
                    weak++
                    v = V(if (radians) v.v * PI / 180.0 else v.v)
                } else break
            }
            return v
        }

        private fun primary(): V {
            val tk = peek() ?: fail()
            when (tk.kind) {
                NUM -> {
                    p++
                    return V(tk.num)
                }
                LP -> {
                    p++
                    val inner = additive()
                    if (peek()?.kind != RP) fail()
                    p++
                    return V(inner.v)
                }
                ID -> return identifier(tk)
                else -> fail()
            }
        }

        private fun identifier(tk: Tok): V {
            val name = tk.text
            when (name) {
                "pi" -> return constant(PI)
                "e" -> return constant(Math.E)
                "tau" -> return constant(2.0 * PI)
                "phi" -> return constant((1.0 + sqrt(5.0)) / 2.0)
            }
            if (name !in FUNCTIONS) fail()
            p++
            val args = ArrayList<Double>(2)
            if (peek()?.kind == LP) {
                p++
                if (peek()?.kind == RP) fail()
                while (true) {
                    args.add(additive().v)
                    val k = peek()?.kind
                    if (k == COMMA) {
                        p++
                        continue
                    }
                    if (k == RP) {
                        p++
                        break
                    }
                    fail()
                }
            } else {
                args.add(unary().v) // sin 30, sqrt 16
            }
            strong++
            return V(check(call(name, args, radians)))
        }

        private fun constant(v: Double): V {
            p++
            weak++
            return V(v)
        }
    }

    // ---------------------------------------------------------------- the functions

    private fun floorMod(a: Double, b: Double): Double {
        if (b == 0.0) fail()
        return a - b * Math.floor(a / b)
    }

    private fun factorial(x: Double): Double {
        if (x < 0 || x > 170 || x != Math.rint(x)) fail()
        var r = 1.0
        for (i in 2..x.toInt()) r *= i
        return r
    }

    private fun isInt(x: Double): Boolean = x == Math.rint(x) && abs(x) < 9.0e15

    private fun angleIn(x: Double, radians: Boolean): Double = if (radians) x else x * PI / 180.0

    private fun angleOut(x: Double, radians: Boolean): Double = if (radians) x else x * 180.0 / PI

    /** A trig result next to zero is zero (cos 90 is 0, not 6e-17). */
    private fun clean(r: Double): Double = if (abs(r) < 1e-15) 0.0 else r

    /** In degrees the right angles are exact: a multiple of 90, 0..3, or -1 when [x] is not one. */
    private fun rightAngle(x: Double): Int {
        var a = x % 360.0
        if (a < 0) a += 360.0
        for (k in 0..3) if (abs(a - 90.0 * k) < 1e-12) return k
        return -1
    }

    private fun sin(x: Double, rad: Boolean): Double {
        if (!rad) when (rightAngle(x)) { 0, 2 -> return 0.0; 1 -> return 1.0; 3 -> return -1.0 }
        return clean(Math.sin(angleIn(x, rad)))
    }

    private fun cos(x: Double, rad: Boolean): Double {
        if (!rad) when (rightAngle(x)) { 0 -> return 1.0; 2 -> return -1.0; 1, 3 -> return 0.0 }
        return clean(Math.cos(angleIn(x, rad)))
    }

    private fun tan(x: Double, rad: Boolean): Double {
        if (!rad) when (rightAngle(x)) { 0, 2 -> return 0.0; 1, 3 -> fail() }
        val c = cos(x, rad)
        if (c == 0.0) fail()
        return clean(sin(x, rad) / c)
    }

    private fun need(args: List<Double>, min: Int, max: Int = min) {
        if (args.size < min || args.size > max) fail()
    }

    private fun call(name: String, a: List<Double>, rad: Boolean): Double {
        when (name) {
            "sqrt" -> { need(a, 1); if (a[0] < 0) fail(); return sqrt(a[0]) }
            "cbrt" -> { need(a, 1); return Math.cbrt(a[0]) }
            "abs" -> { need(a, 1); return abs(a[0]) }
            "sign" -> { need(a, 1); return Math.signum(a[0]) }
            "floor" -> { need(a, 1); return Math.floor(a[0]) }
            "ceil" -> { need(a, 1); return Math.ceil(a[0]) }
            "trunc" -> { need(a, 1); return if (a[0] < 0) Math.ceil(a[0]) else Math.floor(a[0]) }
            "fract" -> { need(a, 1); return a[0] - (if (a[0] < 0) Math.ceil(a[0]) else Math.floor(a[0])) }
            "round" -> {
                need(a, 1, 2)
                val d = if (a.size == 2) a[1] else 0.0
                if (!isInt(d) || abs(d) > 12) fail()
                val scale = Math.pow(10.0, d)
                val r = Math.floor(abs(a[0]) * scale + 0.5) / scale
                return if (a[0] < 0) -r else r
            }
            "exp" -> { need(a, 1); return Math.exp(a[0]) }
            "ln" -> { need(a, 1); if (a[0] <= 0) fail(); return Math.log(a[0]) }
            "log", "log10" -> {
                need(a, 1, 2)
                if (a[0] <= 0) fail()
                if (a.size == 2) { // log(x, base)
                    if (a[1] <= 0 || a[1] == 1.0) fail()
                    return Math.log(a[0]) / Math.log(a[1])
                }
                return Math.log10(a[0])
            }
            "log2" -> { need(a, 1); if (a[0] <= 0) fail(); return Math.log(a[0]) / Math.log(2.0) }
            "logb" -> { need(a, 2); if (a[0] <= 0 || a[1] <= 0 || a[1] == 1.0) fail(); return Math.log(a[0]) / Math.log(a[1]) }
            "sin" -> { need(a, 1); return sin(a[0], rad) }
            "cos" -> { need(a, 1); return cos(a[0], rad) }
            "tan" -> { need(a, 1); return tan(a[0], rad) }
            "sec" -> { need(a, 1); val c = cos(a[0], rad); if (c == 0.0) fail(); return 1.0 / c }
            "csc" -> { need(a, 1); val s = sin(a[0], rad); if (s == 0.0) fail(); return 1.0 / s }
            "cot" -> { need(a, 1); val s = sin(a[0], rad); if (s == 0.0) fail(); return cos(a[0], rad) / s }
            "asin" -> { need(a, 1); if (abs(a[0]) > 1) fail(); return clean(angleOut(Math.asin(a[0]), rad)) }
            "acos" -> { need(a, 1); if (abs(a[0]) > 1) fail(); return clean(angleOut(Math.acos(a[0]), rad)) }
            "atan" -> { need(a, 1); return clean(angleOut(Math.atan(a[0]), rad)) }
            "atan2" -> { need(a, 2); return clean(angleOut(Math.atan2(a[0], a[1]), rad)) }
            "sinh" -> { need(a, 1); return Math.sinh(a[0]) }
            "cosh" -> { need(a, 1); return Math.cosh(a[0]) }
            "tanh" -> { need(a, 1); return Math.tanh(a[0]) }
            "asinh" -> { need(a, 1); return Math.log(a[0] + sqrt(a[0] * a[0] + 1.0)) }
            "acosh" -> { need(a, 1); if (a[0] < 1) fail(); return Math.log(a[0] + sqrt(a[0] * a[0] - 1.0)) }
            "atanh" -> { need(a, 1); if (abs(a[0]) >= 1) fail(); return 0.5 * Math.log((1 + a[0]) / (1 - a[0])) }
            "deg" -> { need(a, 1); return a[0] * 180.0 / PI }
            "rad" -> { need(a, 1); return a[0] * PI / 180.0 }
            "sq" -> { need(a, 1); return a[0] * a[0] }
            "cube" -> { need(a, 1); return a[0] * a[0] * a[0] }
            "pow" -> { need(a, 2); return Math.pow(a[0], a[1]) }
            "root" -> { need(a, 2); if (a[1] == 0.0) fail(); return if (a[0] < 0 && isInt(a[1]) && abs(a[1]) % 2 == 1.0) -Math.pow(-a[0], 1.0 / a[1]) else Math.pow(a[0], 1.0 / a[1]) }
            "min" -> { need(a, 1, 64); return a.min() }
            "max" -> { need(a, 1, 64); return a.max() }
            "sum" -> { need(a, 1, 64); return a.sum() }
            "avg" -> { need(a, 1, 64); return a.sum() / a.size }
            "mod" -> { need(a, 2); return floorMod(a[0], a[1]) }
            "hypot" -> { need(a, 2); return Math.hypot(a[0], a[1]) }
            "fact" -> { need(a, 1); return factorial(a[0]) }
            "gcd" -> { need(a, 2, 16); var g = 0L; for (x in a) { if (!isInt(x)) fail(); g = gcd(g, abs(x).toLong()) }; return g.toDouble() }
            "lcm" -> {
                need(a, 2, 16)
                var l = 1.0
                for (x in a) {
                    if (!isInt(x) || x == 0.0) fail()
                    val g = gcd(l.toLong(), abs(x).toLong())
                    l = l / g * abs(x)
                    if (l > 9.0e15) fail()
                }
                return l
            }
            "ncr" -> { need(a, 2); return choose(a[0], a[1]) }
            "npr" -> { need(a, 2); return arrange(a[0], a[1]) }
        }
        fail()
    }

    private fun gcd(a: Long, b: Long): Long {
        var x = a
        var y = b
        while (y != 0L) {
            val t = x % y
            x = y
            y = t
        }
        return x
    }

    private fun choose(n: Double, k: Double): Double {
        if (!isInt(n) || !isInt(k) || k < 0 || k > n || n > 1000) fail()
        val kk = minOf(k, n - k).toInt()
        var r = 1.0
        for (i in 1..kk) r = r * (n - kk + i) / i
        return Math.rint(r)
    }

    private fun arrange(n: Double, k: Double): Double {
        if (!isInt(n) || !isInt(k) || k < 0 || k > n || n > 1000) fail()
        var r = 1.0
        for (i in 0 until k.toInt()) r *= (n - i)
        return r
    }
}
