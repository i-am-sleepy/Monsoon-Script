package com.monsoonscript.keyboard.view

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.LongSparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import com.monsoonscript.keyboard.clipboard.CardGrid
import com.monsoonscript.keyboard.clipboard.ClipFormat
import com.monsoonscript.keyboard.clipboard.ClipPanelModel
import com.monsoonscript.keyboard.clipboard.ClipPrefs
import com.monsoonscript.keyboard.clipboard.ImageMime
import com.monsoonscript.keyboard.clipboard.ThumbLoader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The clipboard panel (E3, D65), drawn on one Canvas like the emoji panel: no RecyclerView. It only DRAWS [model] and turns touches into model calls; what
 * is shown and done lives in the unit-tested [ClipPanelModel].
 *
 * Browse mode (replaces the keys, same height): toolbar (title, search, 2/1 column toggle, settings; or the selection actions), scrolling category chips,
 * equal-size cards (tap = paste, long press = select, three dots = menu), and a bottom bar (ABC, recent, pinned, clipboard, emoji, backspace).
 * Search mode ([setSearching]): a row of result cards above the query bar, with the real keys below; the keyboard service feeds [setQuery].
 *
 * Nothing is allocated while scrolling or drawing except the text layout of a card the first time it comes into view (cached).
 */
class ClipboardPanelView(
    context: Context,
    private var pal: KeyboardView.Palette,
    private val model: ClipPanelModel,
) : View(context), SearchablePanel {

    interface Listener {
        fun onBackspace()

        /** The ABC key: back to the letter keys. */
        fun onClose()

        /** The emoji button in the bottom bar. */
        fun onEmoji()

        /** The search button or the query pill: the keyboard starts routing typed text into the query. */
        fun onSearchTap()

        /** The arrow in the search bar: leave search and clear the query. */
        fun onSearchBack()

        /** The x in the search bar / pill: clear the query. */
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
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(14f) }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textSize = dp(14f)
    }
    private val barTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textSize = dp(15f)
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(11f) }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(13f) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = dp(13f)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 0.085f // unit-square coordinates; the canvas is scaled to the icon size
    }
    private val rect = RectF()

    // ---- picture thumbnails on image cards (E3.8, D70): small square on the left, "Image" and the type beside it
    /** Where the thumbnails come from; set by the keyboard service. A thumbnail still being read shows a plain square. */
    var thumbs: ThumbLoader? = null
    private val thumbReady = Runnable { invalidate() }
    private val thumbRect = RectF()
    private val thumbSrc = Rect()
    private val thumbPath = Path()
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    // ---- icons, unit square
    private val iconSearch = Path().apply {
        addCircle(.42f, .42f, .27f, Path.Direction.CW)
        moveTo(.62f, .62f); lineTo(.9f, .9f)
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
    private val iconClipboard = Path().apply {
        addRoundRect(RectF(.22f, .18f, .78f, .9f), .08f, .08f, Path.Direction.CW)
        addRoundRect(RectF(.38f, .08f, .62f, .26f), .05f, .05f, Path.Direction.CW)
        moveTo(.34f, .5f); lineTo(.66f, .5f)
        moveTo(.34f, .68f); lineTo(.58f, .68f)
    }
    private val iconClock = Path().apply {
        addCircle(.5f, .5f, .4f, Path.Direction.CW)
        moveTo(.5f, .26f); lineTo(.5f, .5f); lineTo(.68f, .6f)
    }
    private val iconPin = Path().apply {
        addCircle(.5f, .3f, .2f, Path.Direction.CW)
        moveTo(.5f, .5f); lineTo(.5f, .92f)
        moveTo(.3f, .5f); lineTo(.7f, .5f)
    }
    private val iconSmiley = Path().apply {
        addCircle(.5f, .5f, .42f, Path.Direction.CW)
        moveTo(.36f, .4f); lineTo(.36f, .45f)
        moveTo(.64f, .4f); lineTo(.64f, .45f)
        moveTo(.3f, .6f); quadTo(.5f, .82f, .7f, .6f)
    }
    private val iconList = Path().apply { // shown while the cards are in two columns: tap = one column
        moveTo(.14f, .28f); lineTo(.86f, .28f)
        moveTo(.14f, .5f); lineTo(.86f, .5f)
        moveTo(.14f, .72f); lineTo(.86f, .72f)
    }
    private val iconGrid = Path().apply { // shown while the cards are in one column: tap = two columns
        addRoundRect(RectF(.14f, .14f, .46f, .46f), .06f, .06f, Path.Direction.CW)
        addRoundRect(RectF(.54f, .14f, .86f, .46f), .06f, .06f, Path.Direction.CW)
        addRoundRect(RectF(.14f, .54f, .46f, .86f), .06f, .06f, Path.Direction.CW)
        addRoundRect(RectF(.54f, .54f, .86f, .86f), .06f, .06f, Path.Direction.CW)
    }
    private val iconGear = Path().apply {
        addCircle(.5f, .5f, .15f, Path.Direction.CW)
        addCircle(.5f, .5f, .32f, Path.Direction.CW)
        for (k in 0 until 8) {
            val a = k * Math.PI / 4.0
            moveTo(.5f + .32f * cos(a).toFloat(), .5f + .32f * sin(a).toFloat())
            lineTo(.5f + .44f * cos(a).toFloat(), .5f + .44f * sin(a).toFloat())
        }
    }
    private val iconSelectAll = Path().apply {
        addRoundRect(RectF(.14f, .14f, .86f, .86f), .12f, .12f, Path.Direction.CW)
        moveTo(.3f, .52f); lineTo(.45f, .66f); lineTo(.72f, .34f)
    }
    private val iconFolder = Path().apply {
        moveTo(.08f, .26f); lineTo(.38f, .26f); lineTo(.47f, .38f); lineTo(.92f, .38f)
        lineTo(.92f, .8f); lineTo(.08f, .8f); close()
    }
    private val iconShare = Path().apply {
        moveTo(.5f, .62f); lineTo(.5f, .12f)
        moveTo(.3f, .3f); lineTo(.5f, .12f); lineTo(.7f, .3f)
        moveTo(.18f, .5f); lineTo(.18f, .88f); lineTo(.82f, .88f); lineTo(.82f, .5f)
    }
    private val iconTrash = Path().apply {
        moveTo(.18f, .24f); lineTo(.82f, .24f)
        moveTo(.4f, .24f); lineTo(.4f, .12f); lineTo(.6f, .12f); lineTo(.6f, .24f)
        moveTo(.26f, .24f); lineTo(.3f, .88f); lineTo(.7f, .88f); lineTo(.74f, .24f)
    }
    private val iconTick = Path().apply {
        moveTo(.25f, .52f); lineTo(.43f, .68f); lineTo(.76f, .32f)
    }

    // ---- state
    private var searching = false
    private var lines = ClipPrefs.DEFAULT_LINES // lines of text per card (with [autoFit]: the most a card may show)
    private var autoFit = true // E3.13: one column, every card as tall as its text up to [lines] lines
    private var compact = false
    private var showTime = true
    private var scrollPos = 0f // vertical scroll of the cards, px
    private var chipScroll = 0f
    private var hscroll = 0f // horizontal scroll of the result row in search mode
    private var lastChip = -1
    private var lastQuery = ""

    // ---- geometry (px)
    private val grid = CardGrid()
    private var barH = dp(44f)
    private var chipH = dp(40f)
    private var botH = dp(44f)
    private var resRowH = dp(80f)
    private var listTop = 0f
    private var listBottom = 0f
    private var cardPadTop = dp(8f)
    private var padH = dp(10f)
    private var lineH = 0f
    private var bodyH = 0f // height of the text area of a card: [lines] lines, but room for a thumbnail when the list has pictures (Auto fit: the height of a picture card)
    private var heights = FloatArray(0) // Auto fit: the measured height of every card
    private var resCardW = dp(150f)
    private var resLines = 3
    private val footerH = dp(20f)
    private val gap = dp(8f)
    private val edge = dp(8f)
    private val radius = dp(12f)
    private val btnW = dp(44f)
    private val sideW = dp(48f)
    private var botW = 0f
    private var textBase = 0f
    private var titleBase = 0f
    private var barTitleBase = 0f
    private var smallBase = 0f
    private var chipBase = 0f
    private var labelBase = 0f
    private var chipX = FloatArray(16)
    private var chipsEnd = 0f
    private var chipMax = 0f
    private var emptyLines: List<String> = emptyList()

    // ---- card text cache (a layout is built when a card first comes into view)
    private class Entry(
        val layout: StaticLayout?, val time: String, val title: String?,
        // what the layout was built for: a different text (edit), width (1 / 2 columns) or time (copied again) rebuilds it
        val raw: String, val width: Int, val nLines: Int, val used: Long, val fit: Boolean,
    )
    private val cache = LongSparseArray<Entry>()

    /** Auto fit: the measured text height of a card, kept for the whole list so a re-layout is a lookup per card (a layout is only rebuilt for the cards in view). */
    private class Fit(val raw: String, val width: Int, val cap: Int, val body: Float)
    private val fitCache = LongSparseArray<Fit>()

    // ---- popup (the 3-dot menu or the folder list)
    private var popKind = P_NONE
    private var popCount = 0
    private val popLabels = arrayOfNulls<String>(MAX_POP)
    private val popIds = LongArray(MAX_POP)
    private val popRect = RectF()
    private val popItemH = dp(40f)
    private val popPad = dp(6f)
    private var popHover = -1
    private var anchorL = 0f
    private var anchorT = 0f
    private var anchorR = 0f
    private var anchorB = 0f

    // ---- touch state
    private var region = R_NONE
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var ignore = false // the gesture only closed a popup
    private var longDone = false
    private var pressedCard = -1
    private var pressKind = PK_CARD
    private var topKey = T_NONE
    private var bottomKey = B_NONE
    private var queryPressed = false
    private var repeatCount = 0
    private val longPressRunnable = Runnable { onLongPress() }
    private val repeatRunnable = object : Runnable {
        override fun run() {
            listener?.onBackspace()
            repeatCount++
            postDelayed(this, if (repeatCount > 12) REPEAT_FAST_MS else REPEAT_MS)
        }
    }

    // ---------------------------------------------------------------- public API

    fun setPalette(p: KeyboardView.Palette) {
        if (p === pal) return
        pal = p
        invalidate()
    }

    /** Card look from the settings: lines of text (1-10), Auto fit (one column, cards as tall as their text, [lines] is the cap), compact padding, whether the time is shown. */
    fun setStyle(lines: Int, autoFit: Boolean, compact: Boolean, showTime: Boolean) {
        val l = lines.coerceIn(1, ClipPrefs.MAX_LINES)
        if (l == this.lines && autoFit == this.autoFit && compact == this.compact && showTime == this.showTime) return
        this.lines = l
        this.autoFit = autoFit
        this.compact = compact
        this.showTime = showTime
        cache.clear()
        fitCache.clear()
        relayout()
        invalidate()
    }

    /** The panel is about to be shown: fresh list, back to the top. */
    fun open() {
        cancelAll()
        model.open()
        scrollPos = 0f
        chipScroll = 0f
        hscroll = 0f
        lastChip = -1
        lastQuery = ""
        cache.clear()
        relayout()
        invalidate()
    }

    /** The panel is being hidden. */
    fun close() {
        cancelAll()
        model.close()
        cache.clear() // fitCache stays: it is tiny and makes the next opening a lookup per card, not a measuring of the whole history
    }

    /** The model changed (list, selection, popup, columns): lay out again and redraw. Called by the keyboard service's host. */
    fun onModelChanged() {
        relayout()
        invalidate()
    }

    override fun compactHeight(): Int {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return (dp(if (land) 64f else 80f) + dp(if (land) 38f else 44f)).toInt()
    }

    override fun setQuery(q: String) {
        model.setQuery(q) // the model calls back through onModelChanged
    }

    override fun setSearching(on: Boolean) {
        if (on == searching) return
        cancelAll()
        searching = on
        hscroll = 0f
        cache.clear() // the cards are laid out for another width
        relayout()
        invalidate()
    }

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
        val h = height.toFloat()
        if (w <= 0f) return
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        barH = dp(if (land) 38f else 44f)
        chipH = dp(if (land) 36f else 40f)
        botH = barH
        resRowH = dp(if (land) 64f else 80f)
        listTop = barH + chipH
        listBottom = h - botH
        cardPadTop = dp(if (compact) 5f else 8f)
        padH = dp(if (compact) 8f else 10f)
        lineH = textPaint.fontSpacing
        textBase = baselineOf(textPaint)
        titleBase = baselineOf(titlePaint)
        barTitleBase = baselineOf(barTitlePaint)
        smallBase = baselineOf(smallPaint)
        chipBase = baselineOf(chipPaint)
        labelBase = baselineOf(labelPaint)
        botW = (w - 2f * sideW) / 4f
        if (autoFit && !searching) {
            layoutFit(w)
        } else {
            var anyImage = false
            for (card in model.cards) if (card.clip?.isImage == true) {
                anyImage = true
                break
            }
            bodyH = if (anyImage) maxOf(lines * lineH, dp(40f)) else lines * lineH
            val cardH = cardPadTop + bodyH + footerH + dp(2f)
            grid.layout(model.cards.size, model.columns, w, cardH, gap, edge)
        }
        resCardW = dp(150f)
        val resCardH = resRowH - dp(12f)
        resLines = ((resCardH - 2f * dp(6f)) / lineH).toInt().coerceIn(1, 3)
        if (lastChip != model.chipIndex || lastQuery != model.query) {
            lastChip = model.chipIndex
            lastQuery = model.query
            scrollPos = 0f
            hscroll = 0f
            scroller.abortAnimation()
            cache.clear()
        }
        scrollPos = scrollPos.coerceIn(0f, grid.maxScroll(listBottom - listTop))
        layoutChips()
        emptyLines = model.emptyMessage.split('\n')
        layoutPopup()
    }

    /** Auto fit: one column; every card gets the height of its own text (a picture card keeps the height of its thumbnail). */
    private fun layoutFit(w: Float) {
        val n = model.cards.size
        if (heights.size < n) heights = FloatArray(n + 64)
        if (fitCache.size() > n + 100) fitCache.clear() // clips that were deleted leave entries behind
        bodyH = maxOf(2f * lineH, dp(40f))
        val chrome = cardPadTop + footerH + dp(2f)
        val cw = w - 2f * edge
        for (i in 0 until n) heights[i] = chrome + fitBody(model.cards[i], cw)
        grid.layoutFit(n, w, heights, gap, edge)
    }

    /** The height of the text area of a card of width [cw] when Auto fit is on: its whole text up to [lines] lines (a shortcut also has its trigger line). */
    private fun fitBody(card: ClipPanelModel.Card, cw: Float): Float {
        val clip = card.clip
        if (clip != null && clip.isImage) return bodyH
        val sc = card.shortcut
        val raw = clip?.text ?: sc?.text ?: ""
        val width = (cw - 2f * padH).toInt().coerceAtLeast(1)
        val hit = fitCache.get(card.key)
        if (hit != null && hit.width == width && hit.cap == lines && hit.raw == raw) return hit.body
        val src = ClipFormat.fitSource(raw)
        val layout = buildLayout(src, width, textLinesFor(false, sc != null, lines))
        val body = maxOf((if (sc != null) lineH else 0f) + (layout?.height ?: 0), lineH)
        fitCache.put(card.key, Fit(raw, width, lines, body))
        return body
    }

    private fun layoutChips() {
        val n = model.chips.size
        if (chipX.size < n + 1) chipX = FloatArray(n + 1)
        var x = dp(10f)
        for (i in 0 until n) {
            chipX[i] = x
            x += chipPaint.measureText(model.chips[i].label) + dp(28f) + dp(8f)
        }
        chipX[n] = x
        chipsEnd = x
        chipMax = (x - dp(2f) - width).coerceAtLeast(0f)
        chipScroll = chipScroll.coerceIn(0f, chipMax)
    }

    private fun layoutPopup() {
        popKind = P_NONE
        popCount = 0
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || searching) return
        if (model.menuCard != null) {
            for (m in model.menuItems()) {
                popLabels[popCount] = MENU_LABELS[m.ordinal]
                popIds[popCount] = m.ordinal.toLong()
                popCount++
            }
            popKind = P_MENU
        } else if (model.pickerOpen) {
            val choices = model.folderChoices()
            val room = (((h - dp(16f)) / popItemH).toInt() - 2).coerceIn(2, MAX_POP - 2)
            for (ch in choices) {
                if (popCount >= room) break
                popLabels[popCount] = ch.label
                popIds[popCount] = ch.folder
                popCount++
            }
            if (choices.size == 1) { // only "No folder": say where folders are made
                popLabels[popCount] = "No folders yet"
                popIds[popCount++] = -1L
                popLabels[popCount] = "Create them in the Monsoon app"
                popIds[popCount++] = -1L
            }
            popKind = P_FOLDER
        } else {
            return
        }
        val pw = if (popKind == P_FOLDER) dp(230f) else dp(176f)
        val ph = popCount * popItemH + 2f * popPad
        var left = anchorR - pw
        if (left + pw > w - dp(6f)) left = w - dp(6f) - pw
        if (left < dp(6f)) left = dp(6f)
        var top = anchorT - ph - dp(4f)
        if (top < dp(4f)) top = anchorB + dp(4f)
        if (top + ph > h - dp(4f)) top = h - dp(4f) - ph
        if (top < dp(2f)) top = dp(2f)
        popRect.set(left, top, left + pw, top + ph)
        popHover = -1
    }

    // ---------------------------------------------------------------- card text

    /** Lines the text of a card may use: a picture card shows 2 (its name and type), a shortcut card gives one of its [nLines] to the trigger. */
    private fun textLinesFor(image: Boolean, shortcut: Boolean, nLines: Int): Int =
        if (image) 2 else if (shortcut) (nLines - 1).coerceAtLeast(1) else nLines

    private fun buildLayout(src: String, width: Int, maxLines: Int): StaticLayout? {
        if (src.isEmpty()) return null
        return StaticLayout.Builder.obtain(src, 0, src.length, textPaint, width)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
    }

    /** [side] is the width of the thumbnail beside the text of an image card, 0 for other cards. [fit] = laid out for Auto fit (a longer text source). */
    private fun entryFor(card: ClipPanelModel.Card, w: Float, nLines: Int, side: Float, fit: Boolean): Entry {
        val key = card.key
        val clip = card.clip
        val sc = card.shortcut
        val image = clip != null && clip.isImage
        val raw = if (image) clip?.mime ?: "" else clip?.text ?: sc?.text ?: ""
        val width = (w - 2f * padH - (if (image) side + dp(8f) else 0f)).toInt().coerceAtLeast(1)
        val used = clip?.used ?: 0L
        val hit = cache.get(key)
        if (hit != null && hit.width == width && hit.nLines == nLines && hit.used == used && hit.fit == fit && hit.raw == raw) return hit
        if (cache.size() > 80) cache.clear()
        val src = if (image) "Image\n" + ImageMime.label(raw) else if (fit) ClipFormat.fitSource(raw) else ClipFormat.previewSource(raw)
        val layout = buildLayout(src, width, textLinesFor(image, sc != null, nLines))
        val time = if (clip != null && showTime) ClipFormat.ago(clip.used, System.currentTimeMillis()) else ""
        val e = Entry(layout, time, sc?.trigger, raw, width, nLines, used, fit)
        cache.put(key, e)
        return e
    }

    // ---------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(pal.bg)
        if (searching) {
            drawResultsRow(canvas)
            drawQueryBar(canvas, resRowH)
            return
        }
        drawList(canvas)
        drawToolbar(canvas)
        drawChips(canvas)
        drawBottomBar(canvas)
        if (popKind != P_NONE) drawPopup(canvas)
    }

    private fun drawList(c: Canvas) {
        val w = width.toFloat()
        val n = model.cards.size
        if (n == 0) {
            drawEmpty(c, (listTop + listBottom) / 2f)
            return
        }
        val viewH = listBottom - listTop
        c.save()
        c.clipRect(0f, listTop, w, listBottom)
        val r0 = grid.firstRow(scrollPos)
        val r1 = grid.lastRow(scrollPos + viewH)
        for (r in r0..r1) {
            for (col in 0 until grid.cols) {
                val i = r * grid.cols + col
                if (i >= n) break
                val x = grid.left(i)
                val y = listTop + grid.top(i) - scrollPos
                val pressed = i == pressedCard && region == R_LIST && !dragging && pressKind == PK_CARD
                drawCard(c, model.cards[i], x, y, grid.cardW, grid.heightOf(i), lines, true, pressed)
            }
        }
        c.restore()
    }

    private fun drawEmpty(c: Canvas, cy: Float) {
        textPaint.color = pal.textMuted
        val total = emptyLines.size * lineH
        var y = cy - total / 2f + lineH / 2f
        for (line in emptyLines) {
            val tw = textPaint.measureText(line)
            c.drawText(line, (width - tw) / 2f, y + textBase, textPaint)
            y += lineH
        }
    }

    /** One card: text (or a shortcut's trigger and text), and in browse mode a footer with the pin mark, the time and the three dots / the selection circle. */
    private fun drawCard(
        c: Canvas, card: ClipPanelModel.Card, x: Float, y: Float, w: Float, h: Float, nLines: Int, footer: Boolean, pressed: Boolean,
    ) {
        val clip = card.clip
        val selected = model.isSelected(card)
        rect.set(x, y, x + w, y + h)
        fillPaint.color = if (pressed || selected) pal.keyPressed else pal.key
        c.drawRoundRect(rect, radius, radius, fillPaint)
        if (selected) {
            strokePaint.color = pal.accent
            c.drawRoundRect(rect, radius, radius, strokePaint)
        }
        val image = clip != null && clip.isImage
        val side = if (!image) 0f else if (footer) bodyH else h - 2f * cardPadTop
        val e = entryFor(card, w, nLines, side, footer && autoFit)
        var ty = y + cardPadTop
        val title = e.title
        val layout = e.layout
        if (image) {
            drawThumb(c, clip?.image, x + padH, ty, side)
            if (layout != null) {
                c.save()
                c.translate(x + padH + side + dp(8f), ty + (side - layout.height) / 2f)
                textPaint.color = pal.textMuted
                layout.draw(c)
                c.restore()
            }
        } else {
            if (title != null) {
                titlePaint.color = pal.accent
                c.drawText(title, x + padH, ty + lineH / 2f + titleBase, titlePaint)
                ty += lineH
            }
            if (layout != null) {
                c.save()
                c.translate(x + padH, ty)
                textPaint.color = pal.text
                layout.draw(c)
                c.restore()
            }
        }
        if (!footer || clip == null) return
        val fy = y + h - footerH / 2f - dp(1f)
        var fx = x + padH
        if (clip.pinned) {
            drawIcon(c, iconPin, fx + dp(6f), fy, dp(13f), pal.accent)
            fx += dp(18f)
        }
        if (e.time.isNotEmpty()) {
            smallPaint.color = pal.textMuted
            c.drawText(e.time, fx, fy + smallBase, smallPaint)
        }
        val rx = x + w - dp(18f)
        if (model.selecting) {
            if (selected) {
                fillPaint.color = pal.accent
                c.drawCircle(rx, fy, dp(8f), fillPaint)
                drawIcon(c, iconTick, rx, fy, dp(12f), pal.onAccent)
            } else {
                strokePaint.color = pal.textMuted
                c.drawCircle(rx, fy, dp(8f), strokePaint)
            }
        } else {
            fillPaint.color = pal.textMuted
            c.drawCircle(rx, fy - dp(5f), dp(1.6f), fillPaint)
            c.drawCircle(rx, fy, dp(1.6f), fillPaint)
            c.drawCircle(rx, fy + dp(5f), dp(1.6f), fillPaint)
        }
    }

    /** The thumbnail of a picture, cropped to a rounded square; until it is loaded only the square shows. Draws without allocating. */
    private fun drawThumb(c: Canvas, name: String?, left: Float, top: Float, side: Float) {
        val r = dp(6f)
        thumbRect.set(left, top, left + side, top + side)
        fillPaint.color = pal.keyPressed
        c.drawRoundRect(thumbRect, r, r, fillPaint)
        val bmp = if (name != null) thumbs?.thumb(name, thumbReady) else null
        if (bmp == null) return
        val s = minOf(bmp.width, bmp.height)
        val sx = (bmp.width - s) / 2
        val sy = (bmp.height - s) / 2
        thumbSrc.set(sx, sy, sx + s, sy + s)
        thumbPath.rewind()
        thumbPath.addRoundRect(thumbRect, r, r, Path.Direction.CW)
        c.save()
        c.clipPath(thumbPath)
        c.drawBitmap(bmp, thumbSrc, thumbRect, bitmapPaint)
        c.restore()
    }

    private fun drawToolbar(c: Canvas) {
        val w = width.toFloat()
        fillPaint.color = pal.bg
        c.drawRect(0f, 0f, w, barH, fillPaint)
        val cy = barH / 2f
        val icon = dp(22f)
        if (model.selecting) {
            drawKeyBg(c, S_CLEAR, btnW / 2f, cy)
            drawIcon(c, iconClear, btnW / 2f, cy, dp(20f), pal.text)
            val n = model.selectionCount
            val label = if (w >= dp(400f)) "$n selected" else "$n"
            barTitlePaint.color = pal.text
            c.drawText(label, btnW + dp(2f), cy + barTitleBase, barTitlePaint)
            val keys = intArrayOf(S_DELETE, S_EXPORT, S_FOLDER, S_PIN, S_ALL)
            val paths = arrayOf(iconTrash, iconShare, iconFolder, iconPin, iconSelectAll)
            for (k in keys.indices) {
                val bx = w - btnW * (k + 0.5f)
                drawKeyBg(c, keys[k], bx, cy)
                drawIcon(c, paths[k], bx, cy, icon, pal.text)
            }
            return
        }
        val gx = w - btnW * 0.5f
        val tx = w - btnW * 1.5f
        drawKeyBg(c, T_SETTINGS, gx, cy)
        drawIcon(c, iconGear, gx, cy, icon, pal.text)
        drawKeyBg(c, T_COLUMNS, tx, cy)
        drawIcon(c, if (model.columns == 2) iconList else iconGrid, tx, cy, icon, pal.text)
        if (model.query.isEmpty()) {
            val sx = w - btnW * 2.5f
            drawKeyBg(c, T_SEARCH, sx, cy)
            drawIcon(c, iconSearch, sx, cy, icon, pal.text)
            drawIcon(c, iconClipboard, dp(24f), cy, dp(20f), pal.text)
            barTitlePaint.color = pal.text
            c.drawText(TITLE, dp(42f), cy + barTitleBase, barTitlePaint)
        } else {
            // a query is active: the pill replaces the title, tap it to keep typing, the x clears
            val left = dp(10f)
            val right = w - btnW * 2f - dp(4f)
            rect.set(left, dp(5f), right, barH - dp(5f))
            fillPaint.color = if (topKey == T_PILL) pal.keyPressed else pal.key
            c.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, fillPaint)
            drawIcon(c, iconSearch, left + dp(20f), cy, dp(16f), pal.textMuted)
            val textLeft = left + dp(38f)
            val textRight = right - dp(38f)
            titlePaint.color = pal.text
            c.save()
            c.clipRect(textLeft, rect.top, textRight, rect.bottom)
            val tw = titlePaint.measureText(model.query)
            c.drawText(model.query, if (tw > textRight - textLeft) textRight - tw else textLeft, cy + titleBase, titlePaint)
            c.restore()
            drawIcon(c, iconClear, right - dp(20f), cy, dp(13f), pal.textMuted)
        }
    }

    private fun drawKeyBg(c: Canvas, key: Int, cx: Float, cy: Float) {
        if (topKey != key) return
        fillPaint.color = pal.specialPressed
        c.drawCircle(cx, cy, barH * 0.42f, fillPaint)
    }

    private fun drawChips(c: Canvas) {
        val w = width.toFloat()
        val n = model.chips.size
        fillPaint.color = pal.bg
        c.drawRect(0f, barH, w, listTop, fillPaint)
        val cy = barH + chipH / 2f
        val h = chipH - dp(10f)
        c.save()
        c.clipRect(0f, barH, w, listTop)
        for (i in 0 until n) {
            val x0 = chipX[i] - chipScroll
            val x1 = chipX[i + 1] - dp(8f) - chipScroll
            if (x1 < 0f) continue
            if (x0 > w) break
            rect.set(x0, cy - h / 2f, x1, cy + h / 2f)
            val active = i == model.chipIndex
            fillPaint.color = if (active) pal.accent else pal.key
            c.drawRoundRect(rect, h / 2f, h / 2f, fillPaint)
            chipPaint.color = if (active) pal.onAccent else pal.text
            c.drawText(model.chips[i].label, x0 + dp(14f), cy + chipBase, chipPaint)
        }
        c.restore()
    }

    private fun drawBottomBar(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val y0 = h - botH
        fillPaint.color = pal.bg
        c.drawRect(0f, y0, w, h, fillPaint)
        val cy = y0 + botH / 2f
        if (bottomKey == B_ABC) {
            fillPaint.color = pal.specialPressed
            c.drawCircle(sideW / 2f, cy, botH * 0.42f, fillPaint)
        }
        labelPaint.color = pal.text
        c.drawText("ABC", sideW / 2f, cy + labelBase, labelPaint)
        val paths = arrayOf(iconClock, iconPin, iconClipboard, iconSmiley)
        for (k in 0 until 4) {
            val cx = sideW + botW * (k + 0.5f)
            val key = B_RECENT + k
            if (bottomKey == key) {
                fillPaint.color = pal.specialPressed
                c.drawCircle(cx, cy, botH * 0.42f, fillPaint)
            }
            val active = (k == 0 && model.chipIndex == 0) || (k == 1 && model.chipIndex == 1) || k == 2
            drawIcon(c, paths[k], cx, cy, dp(20f), if (active) pal.text else pal.textMuted)
            if (k == 2) { // the clipboard is the panel we are in
                fillPaint.color = pal.accent
                rect.set(cx - dp(9f), h - dp(4f), cx + dp(9f), h - dp(2f))
                c.drawRoundRect(rect, dp(1f), dp(1f), fillPaint)
            }
        }
        if (bottomKey == B_BKSP) {
            fillPaint.color = pal.specialPressed
            c.drawCircle(w - sideW / 2f, cy, botH * 0.42f, fillPaint)
        }
        drawIcon(c, iconBackspace, w - sideW / 2f, cy, dp(22f), pal.text)
    }

    private fun drawResultsRow(c: Canvas) {
        val w = width.toFloat()
        val n = model.cards.size
        if (n == 0) {
            drawEmpty(c, resRowH / 2f)
            return
        }
        val cardH = resRowH - dp(12f)
        c.save()
        c.clipRect(0f, 0f, w, resRowH)
        for (i in 0 until n) {
            val x = edge + i * (resCardW + gap) - hscroll
            if (x + resCardW < 0f) continue
            if (x > w) break
            val pressed = i == pressedCard && region == R_RESULTS && !dragging
            drawCard(c, model.cards[i], x, dp(6f), resCardW, cardH, resLines, false, pressed)
        }
        c.restore()
    }

    /** The query bar: browsing is the toolbar's pill; this is the typing one below the results row. */
    private fun drawQueryBar(c: Canvas, y0: Float) {
        val w = width.toFloat()
        fillPaint.color = pal.bg
        c.drawRect(0f, y0, w, y0 + barH, fillPaint)
        val left = dp(10f)
        val right = w - dp(10f)
        rect.set(left, y0 + dp(5f), right, y0 + barH - dp(5f))
        fillPaint.color = if (queryPressed) pal.keyPressed else pal.key
        c.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, fillPaint)
        val cy = rect.centerY()
        drawIcon(c, iconBack, left + dp(24f), cy, dp(18f), pal.textMuted)
        val query = model.query
        val hasQuery = query.isNotEmpty()
        val textLeft = left + dp(46f)
        val textRight = right - (if (hasQuery) dp(44f) else dp(16f))
        val avail = textRight - textLeft
        var caretX = textLeft
        titlePaint.color = pal.text
        if (hasQuery) {
            val tw = titlePaint.measureText(query)
            c.save()
            c.clipRect(textLeft, rect.top, textRight, rect.bottom)
            c.drawText(query, if (tw > avail) textRight - tw else textLeft, cy + titleBase, titlePaint)
            c.restore()
            caretX = if (tw > avail) textRight else textLeft + tw
            drawIcon(c, iconClear, right - dp(22f), cy, dp(14f), pal.textMuted)
        } else {
            titlePaint.color = pal.textMuted
            c.drawText(HINT_SEARCH, textLeft, cy + titleBase, titlePaint)
        }
        fillPaint.color = pal.accent
        c.drawRect(caretX + dp(1f), cy - dp(10f), caretX + dp(2.5f), cy + dp(10f), fillPaint)
    }

    private fun drawPopup(c: Canvas) {
        fillPaint.color = pal.bubble
        c.drawRoundRect(popRect, dp(12f), dp(12f), fillPaint)
        for (i in 0 until popCount) {
            val top = popRect.top + popPad + i * popItemH
            val label = popLabels[i] ?: continue
            val enabled = popIds[i] != -1L
            if (i == popHover && enabled) {
                fillPaint.color = pal.keyPressed
                rect.set(popRect.left + dp(4f), top, popRect.right - dp(4f), top + popItemH)
                c.drawRoundRect(rect, dp(8f), dp(8f), fillPaint)
            }
            textPaint.color = when {
                !enabled -> pal.textMuted
                popKind == P_MENU && label == "Delete" -> DELETE_RED
                else -> pal.text
            }
            c.save()
            c.clipRect(popRect.left + dp(4f), top, popRect.right - dp(4f), top + popItemH)
            c.drawText(label, popRect.left + dp(16f), top + popItemH / 2f + textBase, textPaint)
            c.restore()
        }
    }

    private fun drawIcon(c: Canvas, path: Path, cx: Float, cy: Float, size: Float, color: Int) {
        iconPaint.color = color
        c.save()
        c.translate(cx - size / 2f, cy - size / 2f)
        c.scale(size, size)
        c.drawPath(path, iconPaint)
        c.restore()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollPos = scroller.currY.toFloat().coerceIn(0f, grid.maxScroll(listBottom - listTop))
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
        if (!searching && popKind != P_NONE) {
            val o = popItemAt(px, py)
            if (o < 0) {
                model.closePopups()
                ignore = true
            } else {
                popHover = o
                region = R_POPUP
            }
            invalidate()
            return
        }
        if (searching) {
            if (py < resRowH) {
                region = R_RESULTS
                pressedCard = resultAt(px)
            } else {
                region = R_QUERY
                queryPressed = true
            }
            invalidate()
            return
        }
        when {
            py < barH -> {
                region = R_TOP
                topKey = toolbarKeyAt(px)
            }
            py < listTop -> region = R_CHIPS
            py >= height - botH -> {
                region = R_BOTTOM
                bottomKey = bottomKeyAt(px)
                if (bottomKey == B_BKSP) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    listener?.onBackspace()
                    repeatCount = 0
                    postDelayed(repeatRunnable, REPEAT_START_MS)
                }
            }
            else -> {
                region = R_LIST
                val cy = py - listTop + scrollPos
                pressedCard = grid.hit(px, cy)
                if (pressedCard >= 0) {
                    val left = grid.left(pressedCard)
                    val top = grid.top(pressedCard)
                    val inDots = !model.selecting && model.cards[pressedCard].clip != null &&
                        px > left + grid.cardW - dp(40f) && cy > top + grid.heightOf(pressedCard) - dp(34f)
                    pressKind = if (inDots) PK_DOTS else PK_CARD
                    if (!inDots && model.cards[pressedCard].clip != null) postDelayed(longPressRunnable, LONG_PRESS_MS)
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
                val o = popItemAt(px, py)
                if (o != popHover) {
                    popHover = o
                    invalidate()
                }
            }
            R_LIST -> {
                if (!dragging && abs(py - downY) > slop) {
                    dragging = true
                    removeCallbacks(longPressRunnable)
                }
                if (dragging) {
                    scrollPos = (scrollPos - (py - lastY)).coerceIn(0f, grid.maxScroll(listBottom - listTop))
                    invalidate()
                }
            }
            R_CHIPS -> {
                if (!dragging && abs(px - downX) > slop) dragging = true
                if (dragging) {
                    chipScroll = (chipScroll - (px - lastX)).coerceIn(0f, chipMax)
                    invalidate()
                }
            }
            R_RESULTS -> {
                if (!dragging && abs(px - downX) > slop) dragging = true
                if (dragging) {
                    val max = (model.cards.size * (resCardW + gap) + edge * 2f - gap - width).coerceAtLeast(0f)
                    hscroll = (hscroll - (px - lastX)).coerceIn(0f, max)
                    invalidate()
                }
            }
            R_TOP -> if (topKey != T_NONE && toolbarKeyAt(px) != topKey) {
                topKey = T_NONE
                invalidate()
            }
            R_BOTTOM -> if (bottomKey != B_NONE && bottomKeyAt(px) != bottomKey) {
                bottomKey = B_NONE
                removeCallbacks(repeatRunnable)
                invalidate()
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
                R_POPUP -> popupSelect(popItemAt(px, py))
                R_LIST -> {
                    if (dragging) {
                        fling()
                    } else if (pressedCard >= 0 && !longDone) {
                        haptic()
                        if (pressKind == PK_DOTS) openMenuFor(pressedCard) else model.tapCard(pressedCard)
                    }
                }
                R_CHIPS -> if (!dragging) chipTap(px)
                R_TOP -> if (py < barH && toolbarKeyAt(px) == topKey && topKey != T_NONE) toolbarTap(topKey)
                R_RESULTS -> if (!dragging && pressedCard >= 0 && pressedCard == resultAt(px)) {
                    haptic()
                    model.tapCard(pressedCard)
                }
                R_QUERY -> if (py >= resRowH) queryBarTap(px)
                R_BOTTOM -> {
                    val k = bottomKeyAt(px)
                    if (k == bottomKey && k != B_NONE) bottomTap(k)
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
        pressedCard = -1
        pressKind = PK_CARD
        topKey = T_NONE
        bottomKey = B_NONE
        queryPressed = false
        invalidate()
    }

    private fun cancelAll() {
        cancelGesture()
        scroller.abortAnimation()
    }

    private fun fling() {
        val t = vt ?: return
        t.computeCurrentVelocity(1000, maxFling)
        val vy = t.yVelocity
        if (abs(vy) < minFling) return
        scroller.fling(0, scrollPos.toInt(), 0, (-vy).toInt(), 0, 0, 0, grid.maxScroll(listBottom - listTop).toInt())
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(longPressRunnable)
        removeCallbacks(repeatRunnable)
    }

    private fun haptic() = performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

    private fun onLongPress() {
        if (region != R_LIST || pressedCard < 0 || dragging) return
        longDone = true
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        model.longPressCard(pressedCard)
    }

    // ---------------------------------------------------------------- hit testing and actions

    private fun resultAt(px: Float): Int {
        val i = ((px + hscroll - edge) / (resCardW + gap)).toInt()
        if (px + hscroll < edge || i < 0 || i >= model.cards.size) return -1
        val inCard = (px + hscroll - edge) - i * (resCardW + gap)
        return if (inCard <= resCardW) i else -1
    }

    /** The toolbar button under [px]: the selection actions while selecting, else search / columns / settings (or the query pill). */
    private fun toolbarKeyAt(px: Float): Int {
        val w = width.toFloat()
        val fromRight = ((w - px) / btnW).toInt()
        if (model.selecting) {
            if (px < btnW) return S_CLEAR
            return when (fromRight) {
                0 -> S_DELETE
                1 -> S_EXPORT
                2 -> S_FOLDER
                3 -> S_PIN
                4 -> S_ALL
                else -> T_NONE
            }
        }
        return when (fromRight) {
            0 -> T_SETTINGS
            1 -> T_COLUMNS
            2 -> if (model.query.isEmpty()) T_SEARCH else pillKeyAt(px)
            else -> if (model.query.isNotEmpty()) pillKeyAt(px) else T_NONE
        }
    }

    private fun pillKeyAt(px: Float): Int {
        val right = width - btnW * 2f - dp(4f)
        return if (px > right - dp(40f)) T_PILL_CLEAR else T_PILL
    }

    private fun toolbarTap(key: Int) {
        haptic()
        when (key) {
            T_SEARCH, T_PILL -> listener?.onSearchTap()
            T_PILL_CLEAR -> listener?.onSearchClear()
            T_COLUMNS -> model.toggleColumns()
            T_SETTINGS -> model.openSettings()
            S_CLEAR -> model.clearSelection()
            S_ALL -> model.selectAll()
            S_PIN -> model.pinSelected()
            S_EXPORT -> model.exportSelected()
            S_DELETE -> model.deleteSelected()
            S_FOLDER -> {
                anchorL = width - btnW * 3f
                anchorR = width - btnW * 2f
                anchorT = 0f
                anchorB = barH
                model.openFolderPicker()
            }
        }
    }

    private fun chipTap(px: Float) {
        val n = model.chips.size
        val x = px + chipScroll
        for (i in 0 until n) {
            if (x >= chipX[i] && x <= chipX[i + 1] - dp(8f)) {
                haptic()
                model.selectChip(i)
                scrollPos = 0f
                return
            }
        }
    }

    private fun scrollChipIntoView(i: Int) {
        if (i < 0 || i >= model.chips.size) return
        val x0 = chipX[i]
        val x1 = chipX[i + 1] - dp(8f)
        if (x0 - chipScroll < 0f) chipScroll = x0 - dp(10f) else if (x1 - chipScroll > width) chipScroll = x1 - width + dp(10f)
        chipScroll = chipScroll.coerceIn(0f, chipMax)
    }

    private fun openMenuFor(i: Int) {
        anchorL = grid.left(i) + grid.cardW - dp(40f)
        anchorR = grid.left(i) + grid.cardW
        anchorT = listTop + grid.top(i) - scrollPos + grid.heightOf(i) - dp(34f)
        anchorB = listTop + grid.top(i) - scrollPos + grid.heightOf(i)
        model.openMenu(i)
    }

    private fun popItemAt(px: Float, py: Float): Int {
        if (popKind == P_NONE || !popRect.contains(px, py)) return -1
        val i = ((py - popRect.top - popPad) / popItemH).toInt()
        return if (py < popRect.top + popPad || i < 0 || i >= popCount) -1 else i
    }

    private fun popupSelect(i: Int) {
        if (i < 0 || i >= popCount || popIds[i] == -1L) return
        haptic()
        if (popKind == P_MENU) {
            model.pickMenu(ClipPanelModel.MenuItem.values()[popIds[i].toInt()])
        } else if (popKind == P_FOLDER) {
            model.moveSelected(popIds[i])
        }
    }

    private fun queryBarTap(px: Float) {
        haptic()
        if (px < dp(56f)) listener?.onSearchBack()
        else if (model.query.isNotEmpty() && px > width - dp(52f)) listener?.onSearchClear()
    }

    private fun bottomKeyAt(px: Float): Int = when {
        px < sideW -> B_ABC
        px > width - sideW -> B_BKSP
        else -> B_RECENT + ((px - sideW) / botW).toInt().coerceIn(0, 3)
    }

    private fun bottomTap(k: Int) {
        haptic()
        when (k) {
            B_ABC -> listener?.onClose()
            B_RECENT, B_CLIP -> {
                model.showRecent()
                scrollPos = 0f
                scrollChipIntoView(0)
            }
            B_PINNED -> {
                model.showPinned()
                scrollPos = 0f
                scrollChipIntoView(1)
            }
            B_EMOJI -> listener?.onEmoji()
            else -> Unit // backspace was typed on touch-down
        }
        invalidate()
    }

    private companion object {
        const val MAX_POP = 10
        const val R_NONE = 0
        const val R_TOP = 1
        const val R_CHIPS = 2
        const val R_LIST = 3
        const val R_BOTTOM = 4
        const val R_POPUP = 5
        const val R_RESULTS = 6
        const val R_QUERY = 7
        const val PK_CARD = 0
        const val PK_DOTS = 1
        const val P_NONE = 0
        const val P_MENU = 1
        const val P_FOLDER = 2
        const val T_NONE = 0
        const val T_SEARCH = 1
        const val T_COLUMNS = 2
        const val T_SETTINGS = 3
        const val T_PILL = 4
        const val T_PILL_CLEAR = 5
        const val S_CLEAR = 10
        const val S_ALL = 11
        const val S_PIN = 12
        const val S_FOLDER = 13
        const val S_EXPORT = 14
        const val S_DELETE = 15
        const val B_NONE = -1
        const val B_ABC = 0
        const val B_RECENT = 1
        const val B_PINNED = 2
        const val B_CLIP = 3
        const val B_EMOJI = 4
        const val B_BKSP = 5
        const val LONG_PRESS_MS = 350L
        const val REPEAT_START_MS = 400L
        const val REPEAT_MS = 50L
        const val REPEAT_FAST_MS = 30L
        const val DELETE_RED = 0xFFE05252.toInt()

        const val TITLE = "Clipboard"
        const val HINT_SEARCH = "Search clipboard"
        val MENU_LABELS = arrayOf("Pin", "Unpin", "Edit", "Copy again", "Share", "Delete")
    }
}
