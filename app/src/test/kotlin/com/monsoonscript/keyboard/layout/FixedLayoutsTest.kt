package com.monsoonscript.keyboard.layout

import com.monsoonscript.keyboard.lang.Language
import com.monsoonscript.keyboard.lang.avro.AvroLayoutTables
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Probhat and National (L4): the generated tables equal the Avro XML, the phone arrangements are the ones the owner chose
 * (D58, D59), and every Bangla character of the PC layouts is either typable on the phone or on an explicit "left out" list.
 * Run from the `app/` directory (the XML is read from ../third_party/avro-reference/layouts).
 */
class FixedLayoutsTest {
    private val layers = listOf("Normal", "Shift", "AltGr", "ShiftAltGr")

    private fun unescape(s: String) = s.replace("&apos;", "'").replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    /** pcKey -> four layers, straight from the original file (numeric keypad elements skipped). */
    private fun readXml(file: String): Map<String, List<String>> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("../third_party/avro-reference/layouts/$file"))
        val out = HashMap<String, Array<String>>()
        val nodes = doc.getElementsByTagName("KeyData").item(0).childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i)
            if (n.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue
            val m = Regex("Key_(.+)_(Normal|Shift|AltGr|ShiftAltGr)").matchEntire(n.nodeName) ?: continue
            out.getOrPut(m.groupValues[1]) { Array(4) { "" } }[layers.indexOf(m.groupValues[2])] = unescape(n.textContent.trim())
        }
        return out.mapValues { it.value.toList() }
    }

    private fun assertTableEqualsXml(layout: FixedLayout, file: String) {
        val xml = readXml(file)
        assertEquals("key names of $file", xml.keys, layout.keyNames)
        for ((k, v) in xml) for (l in 0..3) assertEquals("$file $k ${layers[l]}", v[l], layout.text(k, l))
    }

    @Test fun probhatTableEqualsTheAvroFile() = assertTableEqualsXml(AvroLayoutTables.PROBHAT, "Probhat.avrolayout")

    @Test fun nationalTableEqualsTheAvroFile() = assertTableEqualsXml(AvroLayoutTables.NATIONAL, "National (Jatiya).avrolayout")

    @Test fun htmlEntitiesInTheXmlAreDecoded() {
        assertEquals("'", AvroLayoutTables.PROBHAT.text("OEM7", FixedLayout.NORMAL))
        assertEquals("\"", AvroLayoutTables.PROBHAT.text("OEM7", FixedLayout.SHIFT))
        assertEquals("&", AvroLayoutTables.NATIONAL.text("7", FixedLayout.SHIFT))
        assertEquals("$", AvroLayoutTables.NATIONAL.text("4", FixedLayout.SHIFT))
    }

    // ---- the arrangement on the phone

    private fun rowText(language: Language, row: Int, shift: Boolean = false): String =
        Layouts.pages(language)[Layouts.PAGE_LETTERS][row].filter { it.kind == Kind.CHAR }.joinToString(" ") { if (shift) it.shifted else it.text }

    @Test fun probhatRowsAreD58() {
        assertEquals("দ ূ ী র ট এ ু ি ও প", rowText(Language.PROBHAT, 0))
        assertEquals("া স ড ত গ হ জ ক ল ে ো", rowText(Language.PROBHAT, 1))
        assertEquals("য় শ চ আ ব ন ম ্", rowText(Language.PROBHAT, 2))
        assertEquals(", ।", rowText(Language.PROBHAT, 3))
        // Shift
        assertEquals("ধ ঊ ঈ ড় ঠ ঐ উ ই ঔ ফ", rowText(Language.PROBHAT, 0, shift = true))
        assertEquals("অ ষ ঢ থ ঘ ঃ ঝ খ ং ৈ ৌ", rowText(Language.PROBHAT, 1, shift = true))
        assertEquals("য ঢ় ছ ঋ ভ ণ ঙ ?", rowText(Language.PROBHAT, 2, shift = true))
        assertEquals("ৃ ঁ", rowText(Language.PROBHAT, 3, shift = true))
    }

    @Test fun probhatRowsHaveElevenAndTenKeys() {
        val p = Layouts.pages(Language.PROBHAT)[Layouts.PAGE_LETTERS]
        assertEquals(10, p[0].size)
        assertEquals(11, p[1].size)
        assertEquals(11, p[2].count { it.kind == Kind.CHAR } + 3)
        assertEquals(5, p[3].size)
        for (row in p) assertTrue(row.sumOf { it.weight.toDouble() } in 10.0..11.0)
    }

    @Test fun nationalRowsAreTheBccGrid() {
        assertEquals("ঙ য ড প ট চ জ হ গ ড়", rowText(Language.NATIONAL, 0))
        assertEquals("ৃ ু ি ব ্ া ক ত দ", rowText(Language.NATIONAL, 1))
        assertEquals("ঁ ো ে র ন স ম", rowText(Language.NATIONAL, 2))
        assertEquals("ং য় ঢ ফ ঠ ছ ঝ ঞ ঘ ঢ়", rowText(Language.NATIONAL, 0, shift = true))
        assertEquals("ৗ ূ ী ভ । অ খ থ ধ", rowText(Language.NATIONAL, 1, shift = true))
        assertEquals("ঃ ৌ ৈ ল ণ ষ শ", rowText(Language.NATIONAL, 2, shift = true))
    }

    private fun key(language: Language, text: String): Key =
        Layouts.pages(language)[Layouts.PAGE_LETTERS].flatMap { it.asList() }.first { it.text == text }

    @Test fun longPressHintsFollowD58AndD59() {
        // National: independent vowels on AltGr, the Shift layer of those on Shift+AltGr
        val d = key(Language.NATIONAL, "ি")
        assertEquals("ই", d.hint); assertEquals("ঈ", d.hintShifted)
        val h = key(Language.NATIONAL, "া")
        assertEquals("আ", h.hint); assertEquals("আ", h.hintShifted) // no Shift+AltGr character: Shift keeps the plain hint
        assertEquals("ও", key(Language.NATIONAL, "ো").hint)
        assertEquals("ঔ", key(Language.NATIONAL, "ো").hintShifted)
        assertEquals("এ", key(Language.NATIONAL, "ে").hint)
        assertEquals("ঋ", key(Language.NATIONAL, "ৃ").hint)
        // Probhat: ৎ on ত, ঞ on ন, ZWNJ / ZWJ on the hasanta, nukta on the dari key
        assertEquals("ৎ", key(Language.PROBHAT, "ত").hint)
        assertEquals("ঞ", key(Language.PROBHAT, "ন").hint)
        val hasanta = key(Language.PROBHAT, "্")
        assertEquals(Key.ZWNJ, hasanta.hint); assertEquals(Key.ZWJ, hasanta.hintShifted)
        assertEquals("ZWNJ", hasanta.hintDrawn); assertEquals("ZWJ", hasanta.hintDrawnShifted)
        assertEquals("়", key(Language.PROBHAT, "।").hint)
        assertEquals("◌়", key(Language.PROBHAT, "।").hintDrawn)
    }

    @Test fun topRowKeepsTheDigitHintsInBothLayouts() {
        for (l in listOf(Language.PROBHAT, Language.NATIONAL)) {
            val top = Layouts.pages(l)[Layouts.PAGE_LETTERS][0]
            assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), top.map { it.hint })
            assertEquals(top.map { it.hint }, top.map { it.hintShifted })
        }
    }

    @Test fun englishAndAvroKeepTheirOwnPages() {
        assertSame(Layouts.pages, Layouts.pages(Language.ENGLISH))
        assertSame(Layouts.pages, Layouts.pages(Language.AVRO))
        assertSame(Layouts.pages(Language.PROBHAT), Layouts.pages(Language.PROBHAT))
        val q = key(Language.ENGLISH, "q")
        assertEquals("Q", q.shifted); assertEquals("1", q.hint); assertEquals("1", q.hintShifted)
        assertEquals("Q", q.faceShifted); assertEquals("q", q.face)
        val comma = key(Language.ENGLISH, ",")
        assertEquals(",", comma.shifted); assertNull(comma.hint)
    }

    @Test fun combiningSignsGetADottedCircleOnTheKeyFace() {
        assertEquals("◌ি", key(Language.PROBHAT, "ি").face)
        assertEquals("◌ৈ", key(Language.PROBHAT, "ে").faceShifted)
        assertEquals("ক", key(Language.PROBHAT, "ক").face) // a letter is drawn as it is
        assertEquals("ি", key(Language.PROBHAT, "ি").text) // ...but the typed text is never changed
    }

    // ---- nothing silently missing

    /** Every string a key of [language]'s three pages can type. */
    private fun typable(language: Language): Set<String> {
        val out = HashSet<String>()
        for (page in Layouts.pages(language)) for (row in page) for (k in row) {
            if (k.kind != Kind.CHAR) continue
            out += k.text; out += k.shifted
            k.hint?.let { out += it }; k.hintShifted?.let { out += it }
        }
        return out
    }

    /** The Bangla characters of a PC layout that matter: letters, signs, danda and the joiners, but not the Bangla digits (D18). */
    private fun banglaOf(layout: FixedLayout): Set<String> {
        val out = HashSet<String>()
        for (k in layout.keyNames) for (l in 0..3) {
            val s = layout.text(k, l)
            if (s.length != 1) continue
            val c = s[0]
            val bangla = c in '।'..'॥' || c in 'ঀ'..'৿' || c == '‌' || c == '‍'
            if (bangla && c !in '০'..'৯') out += s
        }
        return out
    }

    private fun setOf(vararg codePoints: Int): Set<String> = codePoints.map { String(Character.toChars(it)) }.toSet()

    @Test fun everyProbhatCharacterIsTypableExceptTheListedOnes() {
        val missing = banglaOf(AvroLayoutTables.PROBHAT) - typable(Language.PROBHAT)
        // ॥ (double danda) and the rare fraction / currency numerator signs of the PC number row: not on the phone in v1 (D58).
        assertEquals(setOf(0x0965, 0x09F2, 0x09F4, 0x09F5, 0x09F6, 0x09F7, 0x09F8, 0x09F9, 0x09FA), missing)
    }

    @Test fun everyNationalCharacterIsTypableExceptTheListedOnes() {
        val missing = banglaOf(AvroLayoutTables.NATIONAL) - typable(Language.NATIONAL)
        // ৢ ৣ ৄ ঽ clash with the top-row digit hints (D59); ॥ makes room for ZWNJ / ZWJ on the hasanta; the fraction / currency
        // numerator signs of the PC number row are not on the phone in v1. ৎ ৺ ৰ ৱ ZWNJ ZWJ and ৳ are all reachable.
        assertEquals(setOf(0x09E2, 0x09E3, 0x09C4, 0x09BD, 0x0965, 0x09F2, 0x09F4, 0x09F5, 0x09F6, 0x09F7, 0x09F8), missing)
    }

    @Test fun theTakaSignIsOnTheSymbolsPage() {
        assertTrue(Layouts.pages(Language.PROBHAT)[Layouts.PAGE_SYMBOLS].flatMap { it.asList() }.any { it.text == "৳" })
    }
}
