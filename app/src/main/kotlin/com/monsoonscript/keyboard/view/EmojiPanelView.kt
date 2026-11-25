package com.monsoonscript.keyboard.view

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import com.monsoonscript.keyboard.emoji.EmojiData
import com.monsoonscript.keyboard.emoji.EmojiGrid
import com.monsoonscript.keyboard.emoji.EmojiStore
import com.monsoonscript.keyboard.emoji.EmojiTabs
import kotlin.math.abs

/**
 * The emoji panel (E2, D56), drawn on one Canvas like the keyboard: no RecyclerView, nothing allocated while scrolling or drawing.
 *
 * Browse mode (the panel replaces the keys, same height): search pill on top, one scrolling grid of all emoji (Recent first, then the eight
 * tabs, each with a small header), and a bottom bar with ABC, a tab icon per section and backspace. Long-press an emoji that has skin tones
 * for a popup; couples and handshakes ask twice (first person, then second). The choice is remembered per emoji and typed at once.
 *
 * Search mode ([setSearching]): the panel shrinks to a row of results and the query bar; the real keys sit below it and the IME feeds what
 * is typed into [setQuery] instead of the editor.
 *
 * Every emoji is drawn and sent as ONE complete Unicode string (ZWJ sequences, flags, tones); the panel never splits or rebuilds one.
 */
class EmojiPanelView(context: Context, private var pal: KeyboardView.Palette) : View(context), SearchablePanel {

    interface Listener {
        /** Type this complete emoji string into the editor. */
        fun onEmoji(text: String)

        fun onBackspace()

        /** The ABC key: back to the letter keys. */
        fun onClose()

        /** The search pill was tapped: the IME starts routing typed text into the query. */
        fun onSearchTap()

        /** The arrow in the search bar: leave search and clear the query. */
        fun onSearchBack()

        /** The x in the search pill/bar: clear the query. */
        fun onSearchClear()
    }

    var listener: Listener? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    private val conf = ViewConfiguration.get(context)
    private val slop = conf.scaledTouchSlop.toFloat()
    private val minFling = conf.scaledMinimumFlingVelocity.toFloat()
    private val maxFling = conf.scaledMaximumFlingVelocity.toFloat()
    private val scroller = OverScroller(context)
    private var vt: VelocityTracker? = null

    // ---- paints (built once)
    private val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(15f) }
    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(12f) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = dp(13f)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 0.085f // unit-square coordinates; the canvas is scaled to the icon size
    }
    private val rect = RectF()

    // ---- icons, unit square
    private val tabIcons: Array<Path> = Array(9) { Path() }
    private val iconSearch = Path().apply {
        addCircle(.42f, .42f, .27f, Path.Direction.CW)
        moveTo(.62f, .62f); lineTo(.9f, .9f)
    }
    private val iconKeyboard = Path().apply {
        addRoundRect(RectF(.06f, .2f, .94f, .8f), .1f, .1f, Path.Direction.CW)
        addCircle(.24f, .4f, .018f, Path.Direction.CW)
        addCircle(.41f, .4f, .018f, Path.Direction.CW)
        addCircle(.59f, .4f, .018f, Path.Direction.CW)
        addCircle(.76f, .4f, .018f, Path.Direction.CW)
        moveTo(.28f, .62f); lineTo(.72f, .62f)
    }
    private val iconBack = Path().apply {
        moveTo(.86f, .5f); lineTo(.16f, .5f)
        moveTo(.46f, .2f); lineTo(.16f, .5f); lineTo(.46f, .8f)
    }
    private val iconClear = Path().apply {
        moveTo(.2f, .2f); lineTo(.8f, .8f)
        moveTo(.8f, .2f); lineTo(.2f, .8f)
    }
    private val iconBackspace = Path().apply {
        moveTo(.34f, .18f); lineTo(.94f, .18f); lineTo(.94f, .82f); lineTo(.34f, .82f); lineTo(.04f, .5f); close()
        moveTo(.52f, .38f); lineTo(.76f, .62f); moveTo(.76f, .38f); lineTo(.52f, .62f)
    }

    init {
        // 0 recent: clock
        tabIcons[0].apply {
            addCircle(.5f, .5f, .4f, Path.Direction.CW)
            moveTo(.5f, .26f); lineTo(.5f, .5f); lineTo(.68f, .6f)
        }
        // 1 smileys and people: face
        tabIcons[1].apply {
            addCircle(.5f, .5f, .42f, Path.Direction.CW)
            moveTo(.36f, .4f); lineTo(.36f, .45f)
            moveTo(.64f, .4f); lineTo(.64f, .45f)
            moveTo(.3f, .6f); quadTo(.5f, .82f, .7f, .6f)
        }
        // 2 animals and nature: paw
        tabIcons[2].apply {
            addOval(RectF(.27f, .48f, .73f, .9f), Path.Direction.CW)
            addCircle(.18f, .42f, .08f, Path.Direction.CW)
            addCircle(.37f, .22f, .08f, Path.Direction.CW)
            addCircle(.63f, .22f, .08f, Path.Direction.CW)
            addCircle(.82f, .42f, .08f, Path.Direction.CW)
        }
        // 3 food and drink: fork
        tabIcons[3].apply {
            moveTo(.28f, .08f); lineTo(.28f, .38f)
            moveTo(.5f, .08f); lineTo(.5f, .38f)
            moveTo(.72f, .08f); lineTo(.72f, .38f)
            moveTo(.28f, .38f); quadTo(.5f, .64f, .72f, .38f)
            moveTo(.5f, .52f); lineTo(.5f, .92f)
        }
        // 4 activities: ball
        tabIcons[4].apply {
            addCircle(.5f, .5f, .4f, Path.Direction.CW)
            moveTo(.5f, .1f); lineTo(.5f, .9f)
            moveTo(.1f, .5f); lineTo(.9f, .5f)
        }
        // 5 travel and places: car
        tabIcons[5].apply {
            addRoundRect(RectF(.06f, .42f, .94f, .74f), .08f, .08f, Path.Direction.CW)
            moveTo(.2f, .42f); lineTo(.32f, .22f); lineTo(.68f, .22f); lineTo(.8f, .42f)
            addCircle(.3f, .76f, .09f, Path.Direction.CW)
            addCircle(.7f, .76f, .09f, Path.Direction.CW)
        }
        // 6 objects: light bulb
        tabIcons[6].apply {
            addCircle(.5f, .38f, .27f, Path.Direction.CW)
            moveTo(.38f, .6f); lineTo(.38f, .74f)
            moveTo(.62f, .6f); lineTo(.62f, .74f)
            moveTo(.36f, .74f); lineTo(.64f, .74f)
            moveTo(.41f, .88f); lineTo(.59f, .88f)
        }
        // 7 symbols: heart
        tabIcons[7].apply {
            moveTo(.5f, .86f)
            cubicTo(.04f, .54f, .14f, .12f, .5f, .34f)
            cubicTo(.86f, .12f, .96f, .54f, .5f, .86f)
            close()
        }
        // 8 flags: flag on a pole
        tabIcons[8].apply {
            moveTo(.24f, .1f); lineTo(.24f, .92f)
            moveTo(.24f, .14f); lineTo(.84f, .14f); lineTo(.68f, .34f); lineTo(.84f, .54f); lineTo(.24f, .54f)
        }
    }

    // ---- content
    private var data: EmojiData? = null
    private var store: EmojiStore? = null
    private var shown: Array<String> = emptyArray() // what each base emoji draws and types: the remembered skin tone or the plain emoji
    private var recentStr: Array<String> = emptyArray()
    private var recentBase = IntArray(0)
    private var query = ""
    private var resultsMode = false // a non-blank query: the grid shows search results instead of the tabs
    private val resIdx = IntArray(MAX_RESULTS)
    private var resCount = 0
    private var searching = false // typing into the query: the panel is only the results row and the query bar

    // ---- geometry (px)
    private val grid = EmojiGrid()
    private var barH = dp(44f)
    private var resH = dp(52f)
    private var cols = 8
    private var cell = 0f
    private var gridTop = 0f
    private var gridBottom = 0f
    private var gridSize = 0f
    private var emojiBase = 0f
    private var resSize = 0f
    private var resBase = 0f
    private var textBase = 0f
    private var headerBase = 0f
    private var labelBase = 0f
    private val sideW = dp(48f)
    private val kbBtnW = dp(52f) // the zone at the right end of the search bar row taken by the keyboard button (G1.8)
    private var tabW = 0f
    private val headerH = dp(30f)

    private var scrollPos = 0f // vertical scroll of the grid, px
    private var hscroll = 0f // horizontal scroll of the results row in search mode, px

    // ---- touch state
    private var region = R_NONE
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var ignore = false // the gesture started outside an open popup: it only closes it
    private var longDone = false // the long press fired (popup shown): releasing must not also type the emoji
    private var pressedHit = -1L
    private var bottomKey = KEY_NONE
    private var topPressed = false
    private var kbPressed = false // the keyboard button next to the search bar is held
    private var repeatCount = 0
    private val longPressRunnable = Runnable { onLongPress() }
    private val repeatRunnable = object : Runnable {
        override fun run() {
            listener?.onBackspace()
            repeatCount++
            postDelayed(this, if (repeatCount > 12) REPEAT_FAST_MS else REPEAT_MS)
        }
    }

    // ---- skin-tone popup
    private var popStage = 0 // 0 = none, 1 = choose a tone (or the first person's), 2 = the second person's tone
    private var popBaseIdx = -1
    private var popFirst = 0
    private var popTwoSlot = false
    private var popFromLong = false
    private var popHover = -1
    private var popCount = 0
    private val popText = arrayOfNulls<String>(6)
    private val popTone = IntArray(6)
    private val popRect = RectF()
    private val popPad = dp(6f)
    private var popOptW = 0f
    private var popOptH = 0f
    private var popCapH = 0f

    // ---------------------------------------------------------------- public API

    fun setPalette(p: KeyboardView.Palette) {
        if (p === pal) return
        pal = p
        invalidate()
    }

    /** The list and font are ready (or changed). */
    fun setContent(d: EmojiData, typeface: Typeface, s: EmojiStore) {
        data = d
        store = s
        emojiPaint.typeface = typeface
        rebuildShown()
        refreshRecents()
        recomputeResults()
        relayout()
        invalidate()
    }

    /** The panel is about to be shown: fresh recents and remembered tones, back to the top. */
    fun open() {
        cancelAll()
        rebuildShown()
        refreshRecents()
        relayout()
        scrollPos = 0f
        invalidate()
    }

    /** The panel is being hidden. */
    fun close() {
        cancelAll()
    }

    val currentQuery: String get() = query

    val hasContent: Boolean get() = data != null

    /** Height of the panel while the keys sit below it (search typing): the results row plus the query bar. */
    override fun compactHeight(): Int {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return (dp(if (land) 38f else 44f) + dp(if (land) 44f else 52f)).toInt()
    }

    /** The text to search for: committed text plus whatever is still being composed. A blank query shows the tabs again. */
    override fun setQuery(q: String) {
        if (q == query) return
        query = q
        resultsMode = q.trim().isNotEmpty()
        recomputeResults()
        hscroll = 0f
        scrollPos = 0f
        scroller.abortAnimation()
        dismissPopup()
        relayout()
        invalidate()
    }

    /** true = typing into the search bar (compact panel above the keys); false = browsing. */
    override fun setSearching(on: Boolean) {
        if (on == searching) return
        cancelAll()
        searching = on
        hscroll = 0f
        invalidate()
    }

    // ---------------------------------------------------------------- data helpers

    private fun rebuildShown() {
        val d = data ?: return
        val st = store
        shown = Array(d.size) { i -> st?.toneOf(d.emoji[i]) ?: d.emoji[i] }
    }

    private fun refreshRecents() {
        val d = data
        val st = store
        if (d == null || st == null) {
            recentStr = emptyArray()
            recentBase = IntArray(0)
            return
        }
        val strs = ArrayList<String>(st.recent.size)
        val bases = ArrayList<Int>(st.recent.size)
        for (s in st.recent) {
            val b = d.baseOf(s)
            if (b >= 0) {
                strs.add(s)
                bases.add(b)
            }
        }
        recentStr = strs.toTypedArray()
        recentBase = IntArray(bases.size) { bases[it] }
    }

    private fun recomputeResults() {
        val d = data
        resCount = if (resultsMode && d != null) d.search(query, resIdx) else 0
    }

    private fun sectionSize(d: EmojiData, s: Int): Int = when {
        resultsMode -> resCount
        s == 0 -> recentStr.size
        else -> d.tabStart[s] - d.tabStart[s - 1]
    }

    private fun baseAt(d: EmojiData, s: Int, i: Int): Int = when {
        resultsMode -> resIdx[i]
        s == 0 -> recentBase[i]
        else -> d.tabStart[s - 1] + i
    }

    private fun textAt(d: EmojiData, s: Int, i: Int): String =
        if (!resultsMode && s == 0) recentStr[i] else shown[baseAt(d, s, i)]

    // ---------------------------------------------------------------- layout

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        relayout()
    }

    private fun baselineOf(p: Paint): Float {
        val fm = p.fontMetrics
        return -(fm.ascent + fm.descent) / 2f
    }

    private fun relayout() {
        val w = width.toFloat()
        if (w <= 0f) return
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        barH = dp(if (land) 38f else 44f)
        resH = dp(if (land) 44f else 52f)
        cols = (w / dp(46f)).toInt().coerceIn(6, 18)
        cell = w / cols
        gridSize = cell * 0.58f
        resSize = resH * 0.6f
        emojiPaint.textSize = gridSize
        emojiBase = baselineOf(emojiPaint)
        emojiPaint.textSize = resSize
        resBase = baselineOf(emojiPaint)
        textBase = baselineOf(textPaint)
        headerBase = baselineOf(headerPaint)
        labelBase = baselineOf(labelPaint)
        gridTop = barH
        gridBottom = height - barH
        tabW = (w - 2 * sideW) / 9f
        val d = data ?: return
        if (resultsMode) {
            grid.layout(intArrayOf(resCount), booleanArrayOf(false), intArrayOf(0), cols, cell, 0f)
        } else {
            val counts = IntArray(9)
            counts[0] = recentStr.size
            for (t in 0 until EmojiTabs.COUNT) counts[t + 1] = d.tabStart[t + 1] - d.tabStart[t]
            grid.layout(counts, ALL_HEADERS, EMPTY_ROWS, cols, cell, headerH)
        }
        scrollPos = scrollPos.coerceIn(0f, grid.maxScroll(gridBottom - gridTop))
    }

    // ---------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        pal.paintBackground(canvas, width, height)
        if (searching) {
            drawResultsRow(canvas)
            drawPill(canvas, resH, true)
            return
        }
        drawGrid(canvas)
        drawPill(canvas, 0f, false)
        drawBottomBar(canvas)
        if (popStage != 0) drawPopup(canvas)
    }

    private fun drawGrid(c: Canvas) {
        val d = data
        if (d == null) {
            drawCentered(c, LOADING, width / 2f, (gridTop + gridBottom) / 2f)
            return
        }
        val gridH = gridBottom - gridTop
        val y0 = scrollPos
        val y1 = scrollPos + gridH
        emojiPaint.textSize = gridSize
        val pS = if (pressedHit >= 0 && !dragging) EmojiGrid.section(pressedHit) else -1
        val pI = if (pressedHit >= 0 && !dragging) EmojiGrid.index(pressedHit) else -1
        var s = grid.sectionAt(y0)
        while (s < grid.sectionCount && grid.sectionTop(s) < y1) {
            val secY = gridTop + grid.sectionTop(s) - scrollPos
            if (!resultsMode) {
                headerPaint.color = pal.textMuted
                c.drawText(SECTION_NAMES[s], dp(14f), secY + headerH * 0.5f + headerBase, headerPaint)
            }
            val n = sectionSize(d, s)
            val itemsTop = grid.itemsTop(s)
            val rows = grid.rows(s)
            var r0 = ((y0 - itemsTop) / cell).toInt()
            if (r0 < 0) r0 = 0
            var r1 = ((y1 - itemsTop) / cell).toInt()
            if (r1 > rows - 1) r1 = rows - 1
            for (r in r0..r1) {
                val cy = gridTop + itemsTop + r * cell - scrollPos + cell / 2f
                for (col in 0 until cols) {
                    val i = r * cols + col
                    if (i >= n) break
                    val cx = col * cell + cell / 2f
                    if (s == pS && i == pI) {
                        fillPaint.color = pal.keyPressed
                        c.drawCircle(cx, cy, cell * 0.44f, fillPaint)
                    }
                    c.drawText(textAt(d, s, i), cx, cy + emojiBase, emojiPaint)
                }
            }
            if (n == 0) {
                val msg = if (resultsMode) NO_RESULTS else EMPTY_RECENT
                val my = if (resultsMode) (gridTop + gridBottom) / 2f else gridTop + itemsTop - scrollPos + cell / 2f
                drawCentered(c, msg, width / 2f, my)
            }
            s++
        }
    }

    private fun drawCentered(c: Canvas, msg: String, cx: Float, cy: Float) {
        textPaint.color = pal.textMuted
        val tw = textPaint.measureText(msg)
        c.drawText(msg, cx - tw / 2f, cy + textBase, textPaint)
    }

    /** The search pill: browsing (top of the panel) or typing (below the results row). */
    private fun drawPill(c: Canvas, y0: Float, typing: Boolean) {
        val w = width.toFloat()
        pal.erase(c, width, height, 0f, y0, w, y0 + barH, fillPaint)
        val left = dp(10f)
        val right = if (typing) w - dp(10f) else w - kbBtnW // not typing: the keyboard button sits right of the bar
        rect.set(left, y0 + dp(5f), right, y0 + barH - dp(5f))
        fillPaint.color = if (topPressed) pal.keyPressed else pal.key
        c.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, fillPaint)
        val cy = rect.centerY()
        val icon = dp(18f)
        drawIcon(c, if (typing) iconBack else iconSearch, left + dp(24f), cy, icon, pal.textMuted)
        val hasQuery = query.isNotEmpty()
        val textLeft = left + dp(46f)
        val textRight = right - (if (hasQuery) dp(44f) else dp(16f))
        val avail = textRight - textLeft
        var caretX = textLeft
        if (hasQuery) {
            textPaint.color = pal.text
            val tw = textPaint.measureText(query)
            c.save()
            c.clipRect(textLeft, rect.top, textRight, rect.bottom)
            // a long query scrolls so that its end (where the typing happens) stays visible
            c.drawText(query, if (tw > avail) textRight - tw else textLeft, cy + textBase, textPaint)
            c.restore()
            caretX = if (tw > avail) textRight else textLeft + tw
            drawIcon(c, iconClear, right - dp(22f), cy, dp(14f), pal.textMuted)
        } else {
            textPaint.color = pal.textMuted
            c.drawText(HINT_SEARCH, textLeft, cy + textBase, textPaint)
        }
        if (typing) {
            fillPaint.color = pal.accent
            c.drawRect(caretX + dp(1f), cy - dp(10f), caretX + dp(2.5f), cy + dp(10f), fillPaint)
        } else {
            // the keyboard button: same job as ABC in the bottom bar, back to the typing keys
            val br = rect.height() / 2f
            val bx = w - dp(10f) - br
            fillPaint.color = if (kbPressed) pal.keyPressed else pal.key
            c.drawCircle(bx, cy, br, fillPaint)
            drawIcon(c, iconKeyboard, bx, cy, dp(22f), pal.text)
        }
    }

    private fun drawResultsRow(c: Canvas) {
        val d = data
        val w = width.toFloat()
        if (d == null) return
        if (resCount == 0) {
            drawCentered(c, if (resultsMode) NO_RESULTS else TYPE_HINT, w / 2f, resH / 2f)
            return
        }
        emojiPaint.textSize = resSize
        for (i in 0 until resCount) {
            val x = i * resH - hscroll
            if (x + resH < 0f) continue
            if (x > w) break
            c.drawText(shown[resIdx[i]], x + resH / 2f, resH / 2f + resBase, emojiPaint)
        }
    }

    private fun drawBottomBar(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val y0 = h - barH
        pal.erase(c, width, height, 0f, y0, w, h, fillPaint)
        val cy = y0 + barH / 2f
        if (bottomKey == KEY_ABC) {
            fillPaint.color = pal.specialPressed
            c.drawCircle(sideW / 2f, cy, barH * 0.42f, fillPaint)
        }
        labelPaint.color = pal.text
        c.drawText("ABC", sideW / 2f, cy + labelBase, labelPaint)
        val active = activeTab()
        for (t in 0 until 9) {
            val cx = sideW + tabW * (t + 0.5f)
            if (bottomKey == t) {
                fillPaint.color = pal.specialPressed
                c.drawCircle(cx, cy, barH * 0.42f, fillPaint)
            }
            drawIcon(c, tabIcons[t], cx, cy, dp(20f), if (t == active) pal.text else pal.textMuted)
            if (t == active) {
                fillPaint.color = pal.accent
                rect.set(cx - dp(9f), h - dp(4f), cx + dp(9f), h - dp(2f))
                c.drawRoundRect(rect, dp(1f), dp(1f), fillPaint)
            }
        }
        if (bottomKey == KEY_BKSP) {
            fillPaint.color = pal.specialPressed
            c.drawCircle(w - sideW / 2f, cy, barH * 0.42f, fillPaint)
        }
        drawIcon(c, iconBackspace, w - sideW / 2f, cy, dp(22f), pal.text)
    }

    /** The section the top of the grid is in (-1 while showing search results); at the very bottom the last one. */
    private fun activeTab(): Int {
        if (resultsMode || grid.sectionCount < 9) return -1
        if (scrollPos >= grid.maxScroll(gridBottom - gridTop) - 1f) return grid.sectionCount - 1
        return grid.sectionAt(scrollPos + 1f)
    }

    private fun drawIcon(c: Canvas, path: Path, cx: Float, cy: Float, size: Float, color: Int) {
        iconPaint.color = color
        c.save()
        c.translate(cx - size / 2f, cy - size / 2f)
        c.scale(size, size)
        c.drawPath(path, iconPaint)
        c.restore()
    }

    private fun drawPopup(c: Canvas) {
        fillPaint.color = pal.bubble
        c.drawRoundRect(popRect, dp(12f), dp(12f), fillPaint)
        if (popCapH > 0f) {
            headerPaint.color = pal.textMuted
            val cap = if (popStage == 2) SECOND_PERSON else FIRST_PERSON
            c.drawText(cap, popRect.left + popPad + dp(4f), popRect.top + popPad + popCapH / 2f + headerBase, headerPaint)
        }
        emojiPaint.textSize = gridSize
        val oy = popRect.top + popPad + popCapH
        for (i in 0 until popCount) {
            val ox = popRect.left + popPad + i * popOptW
            if (i == popHover) {
                fillPaint.color = pal.keyPressed
                rect.set(ox + dp(1f), oy, ox + popOptW - dp(1f), oy + popOptH)
                c.drawRoundRect(rect, dp(8f), dp(8f), fillPaint)
            }
            c.drawText(popText[i] ?: "", ox + popOptW / 2f, oy + popOptH / 2f + emojiBase, emojiPaint)
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollPos = scroller.currY.toFloat().coerceIn(0f, grid.maxScroll(gridBottom - gridTop))
            postInvalidateOnAnimation()
        }
    }

    // ---------------------------------------------------------------- touch

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> down(e)
            MotionEvent.ACTION_MOVE -> move(e)
            MotionEvent.ACTION_UP -> up(e)
            MotionEvent.ACTION_CANCEL -> cancelGesture()
        }
        return true
    }

    private fun down(e: MotionEvent) {
        cancelGesture()
        scroller.abortAnimation()
        vt = VelocityTracker.obtain().also { it.addMovement(e) }
        val px = e.x
        val py = e.y
        downX = px
        downY = py
        lastX = px
        lastY = py
        if (popStage != 0) {
            val o = optionAt(px, py)
            if (o < 0) {
                dismissPopup()
                ignore = true
            } else {
                popHover = o
                region = R_POPUP
            }
            invalidate()
            return
        }
        if (searching) {
            region = if (py < resH) R_RESULTS else R_QUERY
            topPressed = region == R_QUERY
            invalidate()
            return
        }
        when {
            py < barH -> {
                region = R_TOP
                kbPressed = px > width - kbBtnW
                topPressed = !kbPressed
            }
            py >= height - barH -> {
                region = R_BOTTOM
                bottomKey = bottomKeyAt(px)
                if (bottomKey == KEY_BKSP) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    listener?.onBackspace()
                    repeatCount = 0
                    postDelayed(repeatRunnable, REPEAT_START_MS)
                }
            }
            else -> {
                region = R_GRID
                val d = data
                if (d != null) {
                    pressedHit = grid.hit(px, py - gridTop + scrollPos)
                    if (pressedHit >= 0) {
                        val b = baseAt(d, EmojiGrid.section(pressedHit), EmojiGrid.index(pressedHit))
                        if (d.variants[b].isNotEmpty()) postDelayed(longPressRunnable, LONG_PRESS_MS)
                    }
                }
            }
        }
        invalidate()
    }

    private fun move(e: MotionEvent) {
        vt?.addMovement(e)
        if (ignore) return
        val px = e.x
        val py = e.y
        when (region) {
            R_POPUP -> {
                val o = optionAt(px, py)
                if (o != popHover) {
                    popHover = o
                    invalidate()
                }
            }
            R_GRID -> {
                if (!dragging && abs(py - downY) > slop) {
                    dragging = true
                    removeCallbacks(longPressRunnable)
                    pressedHit = -1L
                }
                if (dragging) {
                    scrollPos = (scrollPos - (py - lastY)).coerceIn(0f, grid.maxScroll(gridBottom - gridTop))
                    invalidate()
                }
            }
            R_RESULTS -> {
                if (!dragging && abs(px - downX) > slop) dragging = true
                if (dragging) {
                    val max = (resCount * resH - width).coerceAtLeast(0f)
                    hscroll = (hscroll - (px - lastX)).coerceIn(0f, max)
                    invalidate()
                }
            }
            R_BOTTOM -> {
                if (bottomKey != KEY_NONE && bottomKeyAt(px) != bottomKey) {
                    bottomKey = KEY_NONE
                    removeCallbacks(repeatRunnable)
                    invalidate()
                }
            }
            else -> Unit
        }
        lastX = px
        lastY = py
    }

    private fun up(e: MotionEvent) {
        vt?.addMovement(e)
        removeCallbacks(longPressRunnable)
        removeCallbacks(repeatRunnable)
        val px = e.x
        val py = e.y
        if (!ignore) {
            when (region) {
                R_POPUP -> popupSelect(optionAt(px, py))
                R_GRID -> {
                    if (dragging) fling() else if (pressedHit >= 0 && !longDone) tapCell(pressedHit)
                }
                R_RESULTS -> if (!dragging) tapResult(px)
                R_TOP -> if (py < barH) topTap(px)
                R_QUERY -> if (py >= resH) queryBarTap(px)
                R_BOTTOM -> {
                    val k = bottomKeyAt(px)
                    if (k == bottomKey && k != KEY_NONE) bottomTap(k)
                }
                else -> Unit
            }
        }
        endGesture()
    }

    private fun cancelGesture() {
        removeCallbacks(longPressRunnable)
        removeCallbacks(repeatRunnable)
        endGesture()
    }

    private fun endGesture() {
        vt?.recycle()
        vt = null
        region = R_NONE
        dragging = false
        ignore = false
        longDone = false
        pressedHit = -1L
        bottomKey = KEY_NONE
        topPressed = false
        kbPressed = false
        invalidate()
    }

    private fun cancelAll() {
        cancelGesture()
        scroller.abortAnimation()
        dismissPopup()
    }

    private fun fling() {
        val t = vt ?: return
        t.computeCurrentVelocity(1000, maxFling)
        val vy = t.yVelocity
        if (abs(vy) < minFling) return
        scroller.fling(0, scrollPos.toInt(), 0, (-vy).toInt(), 0, 0, 0, grid.maxScroll(gridBottom - gridTop).toInt())
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(longPressRunnable)
        removeCallbacks(repeatRunnable)
    }

    // ---------------------------------------------------------------- actions

    private fun tapCell(hit: Long) {
        val d = data ?: return
        val s = EmojiGrid.section(hit)
        val i = EmojiGrid.index(hit)
        if (i >= sectionSize(d, s)) return
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        listener?.onEmoji(textAt(d, s, i))
    }

    private fun tapResult(px: Float) {
        if (data == null) return
        val i = ((px + hscroll) / resH).toInt()
        if (i < 0 || i >= resCount) return
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        listener?.onEmoji(shown[resIdx[i]])
    }

    private fun topTap(px: Float) {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        val barEnd = width - kbBtnW // the search bar ends where the keyboard button begins
        when {
            px > barEnd -> listener?.onClose() // the keyboard button: the same as ABC
            query.isNotEmpty() && px > barEnd - dp(52f) -> listener?.onSearchClear()
            else -> listener?.onSearchTap()
        }
    }

    private fun queryBarTap(px: Float) {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (px < dp(56f)) listener?.onSearchBack()
        else if (query.isNotEmpty() && px > width - dp(52f)) listener?.onSearchClear()
    }

    private fun bottomKeyAt(px: Float): Int = when {
        px < sideW -> KEY_ABC
        px > width - sideW -> KEY_BKSP
        else -> ((px - sideW) / tabW).toInt().coerceIn(0, 8)
    }

    private fun bottomTap(k: Int) {
        when (k) {
            KEY_ABC -> {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                listener?.onClose()
            }
            KEY_BKSP -> Unit // already typed on touch-down, repeats while held
            else -> {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (resultsMode) listener?.onSearchClear() // the tabs are back; the IME has cleared the query
                scrollToSection(k)
            }
        }
    }

    private fun scrollToSection(s: Int) {
        if (s >= grid.sectionCount) return
        val target = grid.sectionTop(s).coerceAtMost(grid.maxScroll(gridBottom - gridTop))
        val dy = (target - scrollPos).toInt()
        if (dy == 0) return
        scroller.startScroll(0, scrollPos.toInt(), 0, dy, 240)
        postInvalidateOnAnimation()
    }

    // ---------------------------------------------------------------- skin-tone popup

    private fun onLongPress() {
        val d = data ?: return
        val hit = pressedHit
        if (hit < 0 || dragging || region != R_GRID) return
        val s = EmojiGrid.section(hit)
        val i = EmojiGrid.index(hit)
        val b = baseAt(d, s, i)
        if (d.variants[b].isEmpty()) return
        longDone = true
        pressedHit = -1L
        region = R_POPUP // the finger is still down: sliding onto a tone and lifting chooses it
        popFromLong = true
        popAnchorX = grid.itemLeft(i) + cell / 2f
        popAnchorTop = gridTop + grid.itemTop(s, i) - scrollPos
        popAnchorBottom = popAnchorTop + cell
        popBaseIdx = b
        popFirst = 0
        buildPopup(d, 1)
        if (popCount == 0) {
            dismissPopup()
            return
        }
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        popHover = optionAt(lastX, lastY)
        invalidate()
    }

    private var popAnchorX = 0f
    private var popAnchorTop = 0f
    private var popAnchorBottom = 0f

    /** Tone number 1..5 of a variant: the skin-tone modifiers U+1F3FB..1F3FF in order, as `first * 10 + second` (second = 0 when only one). */
    private fun toneTuple(v: String): Int {
        var first = 0
        var second = 0
        var i = 0
        while (i < v.length) {
            val cp = v.codePointAt(i)
            if (cp in 0x1F3FB..0x1F3FF) {
                if (first == 0) first = cp - 0x1F3FA else second = cp - 0x1F3FA
            }
            i += Character.charCount(cp)
        }
        return first * 10 + second
    }

    private fun findTone(vs: Array<String>, a: Int, b: Int): String? {
        val want = a * 10 + b
        for (v in vs) if (toneTuple(v) == want) return v
        if (a == b && a != 0) for (v in vs) if (toneTuple(v) == a * 10) return v
        return null
    }

    private fun buildPopup(d: EmojiData, stage: Int) {
        val vs = d.variants[popBaseIdx]
        popTwoSlot = false
        for (v in vs) if (toneTuple(v) % 10 != 0) popTwoSlot = true
        popStage = stage
        var n = 0
        if (stage == 1) {
            popText[n] = d.emoji[popBaseIdx]
            popTone[n] = 0
            n++
        }
        for (t in 1..5) {
            val v = when {
                stage == 2 -> findTone(vs, popFirst, t)
                popTwoSlot -> findTone(vs, t, t)
                else -> findTone(vs, t, 0)
            } ?: continue
            popText[n] = v
            popTone[n] = t
            n++
        }
        popCount = if (n > 1) n else 0 // a popup with a single choice is pointless
        popOptW = cell
        popOptH = cell
        popCapH = if (popTwoSlot) dp(20f) else 0f
        val w = popCount * popOptW + 2 * popPad
        val h = popOptH + popCapH + 2 * popPad
        val margin = dp(4f)
        val left = (popAnchorX - w / 2f).coerceIn(margin, (width - margin - w).coerceAtLeast(margin))
        var top = popAnchorTop - h - dp(2f)
        if (top < 0f) top = popAnchorBottom + dp(2f)
        popRect.set(left, top, left + w, top + h)
        popHover = -1
    }

    private fun optionAt(px: Float, py: Float): Int {
        if (popStage == 0 || !popRect.contains(px, py)) return -1
        val oy = popRect.top + popPad + popCapH
        if (py < oy || py > oy + popOptH) return -1
        val i = ((px - popRect.left - popPad) / popOptW).toInt()
        return if (i in 0 until popCount && px >= popRect.left + popPad) i else -1
    }

    private fun popupSelect(o: Int) {
        val d = data
        if (d == null) {
            dismissPopup()
            return
        }
        if (o < 0) {
            // The finger that opened the popup was lifted without sliding: keep it open so a tap can choose. Any later miss closes it.
            if (popFromLong) popFromLong = false else dismissPopup()
            return
        }
        popFromLong = false
        val tone = popTone[o]
        if (popStage == 1 && popTwoSlot && tone != 0) {
            popFirst = tone
            buildPopup(d, 2) // second person; the popup stays where it is
            if (popCount == 0) dismissPopup()
            invalidate()
            return
        }
        val b = popBaseIdx
        val variant = if (tone == 0) null else popText[o]
        dismissPopup()
        chooseVariant(d, b, variant)
    }

    private fun chooseVariant(d: EmojiData, base: Int, variant: String?) {
        val text = variant ?: d.emoji[base]
        store?.rememberTone(d.emoji[base], variant)
        shown[base] = text
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        listener?.onEmoji(text)
    }

    private fun dismissPopup() {
        if (popStage == 0) return
        popStage = 0
        popCount = 0
        popHover = -1
        popFromLong = false
        invalidate()
    }

    private companion object {
        const val MAX_RESULTS = 96
        const val R_NONE = 0
        const val R_GRID = 1
        const val R_TOP = 2
        const val R_BOTTOM = 3
        const val R_POPUP = 4
        const val R_RESULTS = 5
        const val R_QUERY = 6
        const val KEY_NONE = -3
        const val KEY_ABC = -1
        const val KEY_BKSP = -2
        const val LONG_PRESS_MS = 350L
        const val REPEAT_START_MS = 400L
        const val REPEAT_MS = 50L
        const val REPEAT_FAST_MS = 30L

        const val HINT_SEARCH = "Search emoji"
        const val TYPE_HINT = "Type a name, like smile or হাসি"
        const val NO_RESULTS = "No emoji found"
        const val EMPTY_RECENT = "Emoji you use will appear here"
        const val LOADING = "Loading emoji…"
        const val FIRST_PERSON = "First person"
        const val SECOND_PERSON = "Second person"

        val SECTION_NAMES: Array<String> = arrayOf("Recent") + EmojiTabs.NAMES
        val ALL_HEADERS = BooleanArray(9) { true }
        val EMPTY_ROWS = intArrayOf(1, 0, 0, 0, 0, 0, 0, 0, 0)
    }
}
