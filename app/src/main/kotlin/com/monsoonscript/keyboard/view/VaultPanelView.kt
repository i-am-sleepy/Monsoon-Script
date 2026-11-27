package com.monsoonscript.keyboard.view

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.vault.VaultPickerModel
import com.monsoonscript.keyboard.vault.VaultPickerModel.ChipKind
import com.monsoonscript.keyboard.vault.VaultPickerModel.Row
import com.monsoonscript.keyboard.vault.VaultPickerModel.State
import com.monsoonscript.keyboard.vault.VaultPickerModel.Tab
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The password picker panel (E1.14, D27 / D29 / D87): one Canvas view like the clipboard panel, no RecyclerView. It only DRAWS [model] and turns touches into
 * model calls; what is listed and typed lives in the unit-tested [VaultPickerModel].
 *
 * Browse mode (replaces the keys, same height): toolbar (key + title, search, lock now, open the app), chips (This app / Recent / All), the list (a folder, an
 * entry or "back" per row; a tapped entry opens in place with its Username / Password chips), a bottom bar (ABC, backspace). States without a list (no vault, locked,
 * loading, file changed, error) show a message and one button. Search mode ([setSearching]): the query bar on top, three rows of results below it, the real keys under that.
 *
 * Nothing secret is drawn or kept here (D43): rows show titles and user names only.
 */
internal class VaultPanelView(
    context: Context,
    private var pal: KeyboardView.Palette,
    private val model: VaultPickerModel,
) : View(context), SearchablePanel {

    interface Listener {
        fun onBackspace()

        /** The ABC key: back to the letter keys. */
        fun onClose()

        /** The search button or the query pill: the keyboard starts routing typed text into the query. */
        fun onSearchTap()

        /** The arrow in the query bar: leave search and clear the query. */
        fun onSearchBack()

        /** The x in the query bar / pill: clear the query. */
        fun onSearchClear()

        /** "Unlock": the see-through master password screen (D37). */
        fun onUnlock()

        /** "Open Monsoon" / "Review" / the gear: the app's vault screen. */
        fun onOpenApp()

        /** The lock button: lock the vault now. */
        fun onLockNow()

        /** "Try again" after an error. */
        fun onRetry()
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

    // ---- words (read once)
    private val sTitle = context.getString(R.string.picker_title)
    private val sThisApp = context.getString(R.string.picker_this_app)
    private val sRecent = context.getString(R.string.picker_recent)
    private val sAll = context.getString(R.string.picker_all)
    private val sUsername = context.getString(R.string.picker_username)
    private val sPassword = context.getString(R.string.picker_password)
    private val sCode = context.getString(R.string.picker_code)
    private val sCodeWaiting = context.getString(R.string.picker_code_waiting)
    private val sHint = context.getString(R.string.picker_search_hint)
    private val sBack = context.getString(R.string.picker_back)
    private val sNoVault = context.getString(R.string.picker_no_vault)
    private val sOpenApp = context.getString(R.string.picker_open_app)
    private val sLocked = context.getString(R.string.picker_locked)
    private val sUnlock = context.getString(R.string.picker_unlock)
    private val sConflict = context.getString(R.string.picker_conflict)
    private val sReview = context.getString(R.string.picker_review)
    private val sLoading = context.getString(R.string.picker_loading)
    private val sRetry = context.getString(R.string.picker_retry)
    private val sNothingRecent = context.getString(R.string.picker_nothing_recent)
    private val sEmptyFolder = context.getString(R.string.picker_empty_folder)
    private val sNoMatch = context.getString(R.string.picker_no_match)
    private val sAsk1 = context.getString(R.string.picker_ask_1)
    private val sAsk2 = context.getString(R.string.picker_ask_2)
    private val sCancel = context.getString(R.string.picker_cancel)
    private val sTypeIt = context.getString(R.string.picker_type_it)

    // ---- paints (built once)
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textSize = dp(15f)
    }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(14f) }
    private val smallPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(12f) }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(13f) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = dp(13f)
    }
    private val letterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
        textSize = dp(16f)
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
    private val iconGear = Path().apply {
        addCircle(.5f, .5f, .15f, Path.Direction.CW)
        addCircle(.5f, .5f, .32f, Path.Direction.CW)
        for (k in 0 until 8) {
            val a = k * Math.PI / 4.0
            moveTo(.5f + .32f * cos(a).toFloat(), .5f + .32f * sin(a).toFloat())
            lineTo(.5f + .44f * cos(a).toFloat(), .5f + .44f * sin(a).toFloat())
        }
    }
    private val iconFolder = Path().apply {
        moveTo(.08f, .26f); lineTo(.38f, .26f); lineTo(.47f, .38f); lineTo(.92f, .38f)
        lineTo(.92f, .8f); lineTo(.08f, .8f); close()
    }
    private val iconLock = Path().apply {
        addRoundRect(RectF(.2f, .44f, .8f, .9f), .08f, .08f, Path.Direction.CW)
        moveTo(.32f, .44f); lineTo(.32f, .3f)
        addArc(RectF(.32f, .1f, .68f, .5f), 180f, 180f)
        lineTo(.68f, .44f)
    }
    private val iconKey = Path().apply {
        addCircle(.3f, .5f, .2f, Path.Direction.CW)
        moveTo(.5f, .5f); lineTo(.92f, .5f)
        moveTo(.76f, .5f); lineTo(.76f, .7f)
        moveTo(.9f, .5f); lineTo(.9f, .66f)
    }

    // ---- geometry (relayout)
    private var barH = 0f
    private var chipH = 0f
    private var botH = 0f
    private var rowH = 0f
    private var chipRowH = 0f
    private var listTop = 0f
    private var listBottom = 0f
    private val btnW = dp(44f)
    private val sideW = dp(72f)
    private var rowTop = FloatArray(32)
    private var rowCount = 0
    private var total = 0f
    private var scrollPos = 0f
    private var labels = arrayOfNulls<String>(0)
    private var subs = arrayOfNulls<String>(0)
    private var expChips: List<ChipKind> = emptyList()
    private var expIndex = -1
    private val tabs = arrayOfNulls<Tab>(3)
    private val tabX = FloatArray(3)
    private val tabW = FloatArray(3)
    private var tabCount = 0
    private var lastTab: Tab? = null
    private var lastQuery = ""
    private var lastState: State? = null
    private var searching = false

    // ---- touch state
    private var region = R_NONE
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false
    private var pressedRow = -1
    private var pressedKey = K_NONE
    private var repeatCount = 0
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

    /** The panel is about to be shown over the app [pkg]: fresh list, back to the top. */
    fun open(pkg: String) {
        cancelAll()
        scrollPos = 0f
        lastTab = null
        lastQuery = ""
        lastState = null
        model.open(pkg)
        relayout()
        invalidate()
    }

    /** The panel is being hidden: nothing stays in memory. */
    fun close() {
        cancelAll()
        model.close()
        rowCount = 0
        labels = arrayOfNulls(0)
        subs = arrayOfNulls(0)
    }

    /** The model changed (list, state, expanded entry): lay out again and redraw. Called by the keyboard service's host. */
    fun onModelChanged() {
        relayout()
        invalidate()
    }

    override fun compactHeight(): Int {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return (dp(if (land) 38f else 44f) + 3f * dp(if (land) 46f else 54f)).toInt()
    }

    override fun setQuery(q: String) {
        model.setQuery(q) // the model calls back through onModelChanged
    }

    override fun setSearching(on: Boolean) {
        if (on == searching) return
        cancelAll()
        searching = on
        scrollPos = 0f
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

    private fun heightOf(i: Int): Float {
        val r = model.rows[i]
        return if (r is Row.Item && r.entry.id == model.expandedId && expChips.isNotEmpty()) rowH + chipRowH else rowH
    }

    private fun relayout() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f) return
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        barH = dp(if (land) 38f else 44f)
        chipH = dp(if (land) 36f else 40f)
        botH = barH
        rowH = dp(if (land) 46f else 54f)
        chipRowH = dp(if (land) 42f else 48f)
        listTop = if (searching) barH else barH + chipH
        listBottom = if (searching) h else h - botH

        val n = model.rows.size
        expChips = model.expandedEntry()?.let { model.chipsFor(it) } ?: emptyList()
        if (rowTop.size < n + 1) rowTop = FloatArray(n + 17)
        var y = 0f
        for (i in 0 until n) {
            rowTop[i] = y
            y += heightOf(i)
        }
        rowTop[n] = y
        rowCount = n
        total = y
        if (labels.size != n) {
            labels = arrayOfNulls(n)
            subs = arrayOfNulls(n)
        } else {
            java.util.Arrays.fill(labels, null)
            java.util.Arrays.fill(subs, null)
        }
        expIndex = -1
        val id = model.expandedId
        if (id != null) for (i in 0 until n) {
            val r = model.rows[i]
            if (r is Row.Item && r.entry.id == id) expIndex = i
        }
        layoutTabs()
        if (lastTab != model.tab || lastQuery != model.query || lastState != model.state) {
            lastTab = model.tab
            lastQuery = model.query
            lastState = model.state
            scrollPos = 0f
            scroller.abortAnimation()
        }
        scrollPos = scrollPos.coerceIn(0f, maxScroll())
    }

    private fun maxScroll(): Float = (total - (listBottom - listTop)).coerceAtLeast(0f)

    private fun layoutTabs() {
        tabCount = 0
        if (model.hasApp) tabs[tabCount++] = Tab.APP
        tabs[tabCount++] = Tab.RECENT
        tabs[tabCount++] = Tab.FOLDERS
        var x = dp(10f)
        for (i in 0 until tabCount) {
            tabX[i] = x
            tabW[i] = chipPaint.measureText(tabLabel(tabs[i]!!)) + dp(28f)
            x += tabW[i] + dp(8f)
        }
    }

    private fun tabLabel(t: Tab): String = when (t) {
        Tab.APP -> sThisApp
        Tab.RECENT -> sRecent
        Tab.FOLDERS -> sAll
    }

    /** First row whose bottom is below [y] (rows are sorted by top). */
    private fun rowAt(y: Float): Int {
        if (rowCount == 0 || y < 0f || y >= total) return -1
        var lo = 0
        var hi = rowCount - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (rowTop[mid] <= y) lo = mid else hi = mid - 1
        }
        return lo
    }

    // ---------------------------------------------------------------- drawing

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(pal.bg)
        val showList = model.state == State.READY && rowCount > 0
        if (showList) drawList(canvas) else drawCenter(canvas, if (searching) barH else barH + chipH, if (searching) height.toFloat() else height - botH)
        if (searching) {
            drawQueryBar(canvas)
        } else {
            drawToolbar(canvas)
            if (model.state == State.READY) drawChips(canvas)
            drawBottomBar(canvas)
        }
        if (model.asking) drawAsk(canvas)
    }

    private fun drawList(c: Canvas) {
        val w = width.toFloat()
        c.save()
        c.clipRect(0f, listTop, w, listBottom)
        var i = rowAt(scrollPos).coerceAtLeast(0)
        while (i < rowCount) {
            val y = listTop + rowTop[i] - scrollPos
            if (y >= listBottom) break
            drawRow(c, i, y, w)
            i++
        }
        c.restore()
    }

    private fun drawRow(c: Canvas, i: Int, y: Float, w: Float) {
        val row = model.rows[i]
        val opened = i == expIndex
        val h = heightOf(i)
        if (opened || (i == pressedRow && region == R_LIST && !dragging)) {
            fillPaint.color = if (opened) pal.key else pal.keyPressed
            c.drawRect(0f, y, w, y + h, fillPaint)
        }
        val cy = y + rowH / 2f
        val left = dp(14f)
        val textLeft = dp(64f)
        when (row) {
            is Row.Folder -> {
                drawIcon(c, iconFolder, left + dp(18f), cy, dp(24f), pal.textMuted)
                titlePaint.color = pal.text
                c.drawText(label(i, row.ref.name, titlePaint, w - textLeft - dp(56f)), textLeft, cy + baselineOf(titlePaint), titlePaint)
                smallPaint.color = pal.textMuted
                val count = (row.ref.entries + row.ref.groups).toString()
                c.drawText(count, w - dp(16f) - smallPaint.measureText(count), cy + baselineOf(smallPaint), smallPaint)
            }
            is Row.Up -> {
                drawIcon(c, iconBack, left + dp(18f), cy, dp(22f), pal.textMuted)
                titlePaint.color = pal.text
                c.drawText(label(i, row.name ?: sAll, titlePaint, w - textLeft - dp(16f)), textLeft, cy + baselineOf(titlePaint), titlePaint)
            }
            is Row.Item -> {
                val e = row.entry
                fillPaint.color = pal.special
                c.drawCircle(left + dp(18f), cy, dp(18f), fillPaint)
                letterPaint.color = pal.text
                c.drawText(letterOf(e.title), left + dp(18f), cy + baselineOf(letterPaint), letterPaint)
                titlePaint.color = pal.text
                val avail = w - textLeft - dp(16f)
                if (e.username.isEmpty()) {
                    c.drawText(label(i, e.title, titlePaint, avail), textLeft, cy + baselineOf(titlePaint), titlePaint)
                } else {
                    c.drawText(label(i, e.title, titlePaint, avail), textLeft, cy - dp(8f) + baselineOf(titlePaint), titlePaint)
                    smallPaint.color = pal.textMuted
                    c.drawText(sub(i, e.username, smallPaint, avail), textLeft, cy + dp(11f) + baselineOf(smallPaint), smallPaint)
                }
                if (opened) drawChipsRow(c, y + rowH, textLeft)
            }
        }
    }

    /** The words on a chip; the Code chip says it is waiting while the next one-time code is awaited (D98). */
    private fun chipLabel(kind: ChipKind): String = when (kind) {
        ChipKind.USERNAME -> sUsername
        ChipKind.PASSWORD -> sPassword
        ChipKind.CODE -> if (model.waitingForCode) sCodeWaiting else sCode
    }

    private fun drawChipsRow(c: Canvas, y: Float, x0: Float) {
        val cy = y + chipRowH / 2f - dp(2f)
        val ph = dp(34f)
        var x = x0
        for (k in expChips.indices) {
            val label = chipLabel(expChips[k])
            val pw = chipPaint.measureText(label) + dp(28f)
            rect.set(x, cy - ph / 2f, x + pw, cy + ph / 2f)
            fillPaint.color = if (pressedKey == K_CHIP + k) pal.accentPressed else pal.accent
            c.drawRoundRect(rect, ph / 2f, ph / 2f, fillPaint)
            chipPaint.color = pal.onAccent
            c.drawText(label, x + dp(14f), cy + baselineOf(chipPaint), chipPaint)
            x += pw + dp(8f)
        }
    }

    private fun label(i: Int, text: String, p: TextPaint, avail: Float): String {
        labels[i]?.let { return it }
        val s = TextUtils.ellipsize(text.replace('\n', ' '), p, avail, TextUtils.TruncateAt.END).toString()
        labels[i] = s
        return s
    }

    private fun sub(i: Int, text: String, p: TextPaint, avail: Float): String {
        subs[i]?.let { return it }
        val s = TextUtils.ellipsize(text.replace('\n', ' '), p, avail, TextUtils.TruncateAt.END).toString()
        subs[i] = s
        return s
    }

    private fun letterOf(title: String): String {
        val t = title.trim()
        if (t.isEmpty()) return "?"
        val cp = t.codePointAt(0)
        return String(Character.toChars(cp)).uppercase()
    }

    private fun drawCenter(c: Canvas, top: Float, bottom: Float) {
        val text: String
        var button: String? = null
        when (model.state) {
            State.NO_VAULT -> { text = sNoVault; button = sOpenApp }
            State.LOCKED -> { text = sLocked; button = sUnlock }
            State.LOADING -> text = sLoading
            State.CONFLICT -> { text = sConflict; button = sReview }
            State.ERROR -> { text = model.message; button = sRetry }
            State.READY -> text = when {
                model.searching -> sNoMatch
                model.tab == Tab.FOLDERS -> sEmptyFolder
                else -> sNothingRecent
            }
        }
        val mid = (top + bottom) / 2f
        val lineH = textPaint.fontSpacing
        val lines = text.split('\n')
        val blockH = lines.size * lineH + if (button != null) dp(52f) else 0f
        var y = mid - blockH / 2f + lineH / 2f
        textPaint.color = pal.textMuted
        for (line in lines) {
            val clipped = TextUtils.ellipsize(line, textPaint, width - dp(32f), TextUtils.TruncateAt.END).toString()
            c.drawText(clipped, (width - textPaint.measureText(clipped)) / 2f, y + baselineOf(textPaint), textPaint)
            y += lineH
        }
        if (button != null) {
            val bw = labelPaint.measureText(button) + dp(40f)
            val by = y - lineH / 2f + dp(12f)
            rect.set((width - bw) / 2f, by, (width + bw) / 2f, by + dp(40f))
            fillPaint.color = if (pressedKey == K_CENTER) pal.accentPressed else pal.accent
            c.drawRoundRect(rect, dp(20f), dp(20f), fillPaint)
            labelPaint.color = pal.onAccent
            c.drawText(button, rect.centerX(), rect.centerY() + baselineOf(labelPaint), labelPaint)
            centerBtn.set(rect)
        } else {
            centerBtn.setEmpty()
        }
    }

    private val centerBtn = RectF()

    private fun drawToolbar(c: Canvas) {
        val w = width.toFloat()
        fillPaint.color = pal.bg
        c.drawRect(0f, 0f, w, barH, fillPaint)
        val cy = barH / 2f
        val icon = dp(22f)
        val ready = model.state == State.READY
        drawKeyBg(c, K_GEAR, w - btnW * 0.5f, cy)
        drawIcon(c, iconGear, w - btnW * 0.5f, cy, icon, pal.text)
        var shown = 1
        if (ready) {
            drawKeyBg(c, K_LOCK, w - btnW * 1.5f, cy)
            drawIcon(c, iconLock, w - btnW * 1.5f, cy, icon, pal.text)
            shown = 2
            if (model.query.isEmpty()) {
                drawKeyBg(c, K_SEARCH, w - btnW * 2.5f, cy)
                drawIcon(c, iconSearch, w - btnW * 2.5f, cy, icon, pal.text)
                shown = 3
            }
        }
        if (model.query.isEmpty() || !ready) {
            drawIcon(c, iconKey, dp(24f), cy, dp(22f), pal.text)
            titlePaint.color = pal.text
            c.drawText(sTitle, dp(42f), cy + baselineOf(titlePaint), titlePaint)
        } else {
            // a query is active: the pill replaces the title, tap it to keep typing, the x clears
            val left = dp(10f)
            val right = w - btnW * shown - dp(4f)
            rect.set(left, dp(5f), right, barH - dp(5f))
            fillPaint.color = if (pressedKey == K_PILL) pal.keyPressed else pal.key
            c.drawRoundRect(rect, rect.height() / 2f, rect.height() / 2f, fillPaint)
            drawIcon(c, iconSearch, left + dp(20f), cy, dp(16f), pal.textMuted)
            val textLeft = left + dp(38f)
            val textRight = right - dp(38f)
            titlePaint.color = pal.text
            c.save()
            c.clipRect(textLeft, rect.top, textRight, rect.bottom)
            val tw = titlePaint.measureText(model.query)
            c.drawText(model.query, if (tw > textRight - textLeft) textRight - tw else textLeft, cy + baselineOf(titlePaint), titlePaint)
            c.restore()
            drawIcon(c, iconClear, right - dp(20f), cy, dp(13f), pal.textMuted)
        }
    }

    private fun drawKeyBg(c: Canvas, key: Int, cx: Float, cy: Float) {
        if (pressedKey != key) return
        fillPaint.color = pal.specialPressed
        c.drawCircle(cx, cy, barH * 0.42f, fillPaint)
    }

    private fun drawChips(c: Canvas) {
        fillPaint.color = pal.bg
        c.drawRect(0f, barH, width.toFloat(), barH + chipH, fillPaint)
        val cy = barH + chipH / 2f
        val ph = chipH - dp(8f)
        for (i in 0 until tabCount) {
            val active = tabs[i] == model.tab && !model.searching
            rect.set(tabX[i], cy - ph / 2f, tabX[i] + tabW[i], cy + ph / 2f)
            fillPaint.color = when {
                active -> pal.accent
                pressedKey == K_TAB + i -> pal.keyPressed
                else -> pal.key
            }
            c.drawRoundRect(rect, ph / 2f, ph / 2f, fillPaint)
            chipPaint.color = if (active) pal.onAccent else pal.text
            c.drawText(tabLabel(tabs[i]!!), tabX[i] + dp(14f), cy + baselineOf(chipPaint), chipPaint)
        }
    }

    private fun drawBottomBar(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val y0 = h - botH
        fillPaint.color = pal.bg
        c.drawRect(0f, y0, w, h, fillPaint)
        val cy = y0 + botH / 2f
        if (pressedKey == K_ABC) {
            fillPaint.color = pal.specialPressed
            c.drawCircle(sideW / 2f, cy, botH * 0.42f, fillPaint)
        }
        labelPaint.color = pal.text
        c.drawText("ABC", sideW / 2f, cy + baselineOf(labelPaint), labelPaint)
        if (pressedKey == K_BKSP) {
            fillPaint.color = pal.specialPressed
            c.drawCircle(w - sideW / 2f, cy, botH * 0.42f, fillPaint)
        }
        drawIcon(c, iconBackspace, w - sideW / 2f, cy, dp(22f), pal.text)
        // the key marks the panel we are in
        drawIcon(c, iconKey, w / 2f, cy, dp(20f), pal.text)
        fillPaint.color = pal.accent
        rect.set(w / 2f - dp(9f), h - dp(4f), w / 2f + dp(9f), h - dp(2f))
        c.drawRoundRect(rect, dp(1f), dp(1f), fillPaint)
    }

    /** The query bar on top while the keys sit below: back arrow, the text with a caret, an x. */
    private fun drawQueryBar(c: Canvas) {
        val w = width.toFloat()
        fillPaint.color = pal.bg
        c.drawRect(0f, 0f, w, barH, fillPaint)
        val left = dp(10f)
        val right = w - dp(10f)
        rect.set(left, dp(5f), right, barH - dp(5f))
        fillPaint.color = if (pressedKey == K_QUERY) pal.keyPressed else pal.key
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
            c.drawText(query, if (tw > avail) textRight - tw else textLeft, cy + baselineOf(titlePaint), titlePaint)
            c.restore()
            caretX = if (tw > avail) textRight else textLeft + tw
            drawIcon(c, iconClear, right - dp(22f), cy, dp(14f), pal.textMuted)
        } else {
            titlePaint.color = pal.textMuted
            c.drawText(sHint, textLeft, cy + baselineOf(titlePaint), titlePaint)
        }
        fillPaint.color = pal.accent
        c.drawRect(caretX + dp(1f), cy - dp(10f), caretX + dp(2.5f), cy + dp(10f), fillPaint)
    }

    /** "This is not a password field. Type the password here anyway?" over the list (D40). */
    private fun drawAsk(c: Canvas) {
        val w = width.toFloat()
        val top = listTop
        val bottom = listBottom
        fillPaint.color = (pal.bg and 0x00FFFFFF) or 0xCC000000.toInt()
        c.drawRect(0f, top, w, bottom, fillPaint)
        val bw = w - dp(32f)
        val bh = dp(150f).coerceAtMost(bottom - top - dp(8f))
        val left = (w - bw) / 2f
        val t = (top + bottom) / 2f - bh / 2f
        rect.set(left, t, left + bw, t + bh)
        fillPaint.color = pal.key
        c.drawRoundRect(rect, dp(14f), dp(14f), fillPaint)
        textPaint.color = pal.text
        val cx = w / 2f
        c.drawText(sAsk1, cx - textPaint.measureText(sAsk1) / 2f, t + bh * 0.28f + baselineOf(textPaint), textPaint)
        c.drawText(sAsk2, cx - textPaint.measureText(sAsk2) / 2f, t + bh * 0.28f + textPaint.fontSpacing + baselineOf(textPaint), textPaint)
        val by = t + bh - dp(52f)
        askCancel.set(left + dp(12f), by, cx - dp(6f), by + dp(40f))
        askType.set(cx + dp(6f), by, left + bw - dp(12f), by + dp(40f))
        fillPaint.color = if (pressedKey == K_ASK_NO) pal.keyPressed else pal.special
        c.drawRoundRect(askCancel, dp(20f), dp(20f), fillPaint)
        fillPaint.color = if (pressedKey == K_ASK_YES) pal.accentPressed else pal.accent
        c.drawRoundRect(askType, dp(20f), dp(20f), fillPaint)
        labelPaint.color = pal.text
        c.drawText(sCancel, askCancel.centerX(), askCancel.centerY() + baselineOf(labelPaint), labelPaint)
        labelPaint.color = pal.onAccent
        c.drawText(sTypeIt, askType.centerX(), askType.centerY() + baselineOf(labelPaint), labelPaint)
    }

    private val askCancel = RectF()
    private val askType = RectF()

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
            scrollPos = scroller.currY.toFloat().coerceIn(0f, maxScroll())
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
        lastY = py
        if (model.asking) {
            region = R_ASK
            pressedKey = when {
                askCancel.contains(px, py) -> K_ASK_NO
                askType.contains(px, py) -> K_ASK_YES
                else -> K_NONE
            }
            invalidate()
            return
        }
        when {
            py < barH -> {
                region = R_TOP
                pressedKey = if (searching) K_QUERY else toolbarKeyAt(px)
            }
            !searching && py < listTop -> {
                region = R_CHIPS
                pressedKey = tabAt(px)
            }
            !searching && py >= height - botH -> {
                region = R_BOTTOM
                pressedKey = bottomKeyAt(px)
                if (pressedKey == K_BKSP) {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    listener?.onBackspace()
                    repeatCount = 0
                    postDelayed(repeatRunnable, REPEAT_START_MS)
                }
            }
            else -> {
                region = R_LIST
                if (model.state == State.READY && rowCount > 0) {
                    val cy = py - listTop + scrollPos
                    pressedRow = rowAt(cy)
                    pressedKey = chipKeyAt(px, cy)
                } else if (centerBtn.contains(px, py)) {
                    pressedKey = K_CENTER
                }
            }
        }
        invalidate()
    }

    private fun move(e: MotionEvent) {
        vt?.addMovement(e)
        val py = e.y
        if (region == R_LIST) {
            if (!dragging && abs(py - downY) > slop) dragging = true
            if (dragging) {
                scrollPos = (scrollPos + (lastY - py)).coerceIn(0f, maxScroll())
                lastY = py
                invalidate()
            }
        }
    }

    private fun up(e: MotionEvent) {
        vt?.addMovement(e)
        removeCallbacks(repeatRunnable)
        val px = e.x
        val py = e.y
        when (region) {
            R_ASK -> {
                val k = when {
                    askCancel.contains(px, py) -> K_ASK_NO
                    askType.contains(px, py) -> K_ASK_YES
                    else -> K_ASK_NO // a tap outside the box means no
                }
                haptic()
                model.answerAsk(k == K_ASK_YES && pressedKey == K_ASK_YES)
            }
            R_TOP -> if (searching) queryBarTap(px) else if (toolbarKeyAt(px) == pressedKey && pressedKey != K_NONE) toolbarTap(pressedKey)
            R_CHIPS -> if (tabAt(px) == pressedKey && pressedKey != K_NONE) tabTap(pressedKey - K_TAB)
            R_BOTTOM -> if (bottomKeyAt(px) == pressedKey) {
                when (pressedKey) {
                    K_ABC -> listener?.onClose()
                }
            }
            R_LIST -> {
                if (dragging) {
                    fling()
                } else if (model.state == State.READY && rowCount > 0) {
                    val cy = py - listTop + scrollPos
                    val k = chipKeyAt(px, cy)
                    if (k >= K_CHIP && k == pressedKey) {
                        haptic()
                        chipTap(k - K_CHIP)
                    } else if (pressedRow >= 0 && pressedRow == rowAt(cy) && k == K_NONE) {
                        haptic()
                        model.tapRow(pressedRow)
                    }
                } else if (pressedKey == K_CENTER && centerBtn.contains(px, py)) {
                    haptic()
                    centerTap()
                }
            }
        }
        endGesture()
    }

    private fun cancelGesture() {
        removeCallbacks(repeatRunnable)
        endGesture()
    }

    private fun endGesture() {
        vt?.recycle()
        vt = null
        region = R_NONE
        dragging = false
        pressedRow = -1
        pressedKey = K_NONE
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
        scroller.fling(0, scrollPos.toInt(), 0, (-vy).toInt(), 0, 0, 0, maxScroll().toInt())
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(repeatRunnable)
    }

    private fun haptic() = performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

    private fun toolbarKeyAt(px: Float): Int {
        val w = width.toFloat()
        val ready = model.state == State.READY
        return when {
            px >= w - btnW -> K_GEAR
            ready && px >= w - btnW * 2f -> K_LOCK
            ready && model.query.isEmpty() && px >= w - btnW * 3f -> K_SEARCH
            ready && model.query.isNotEmpty() && px >= dp(10f) -> K_PILL
            else -> K_NONE
        }
    }

    private fun toolbarTap(k: Int) {
        haptic()
        when (k) {
            K_GEAR -> listener?.onOpenApp()
            K_LOCK -> listener?.onLockNow()
            K_SEARCH, K_PILL -> listener?.onSearchTap()
        }
    }

    private fun queryBarTap(px: Float) {
        haptic()
        val w = width.toFloat()
        if (px < dp(10f) + dp(48f)) listener?.onSearchBack() else if (model.query.isNotEmpty() && px > w - dp(10f) - dp(44f)) listener?.onSearchClear()
    }

    private fun tabAt(px: Float): Int {
        for (i in 0 until tabCount) if (px >= tabX[i] && px <= tabX[i] + tabW[i]) return K_TAB + i
        return K_NONE
    }

    private fun tabTap(i: Int) {
        val t = tabs[i] ?: return
        haptic()
        if (model.query.isNotEmpty()) listener?.onSearchClear() // the keyboard's typed text goes too
        model.selectTab(t)
    }

    private fun bottomKeyAt(px: Float): Int = when {
        px < sideW -> K_ABC
        px >= width - sideW -> K_BKSP
        else -> K_NONE
    }

    /** The chip (Username / Password) under a touch in the opened row, or K_NONE. [cy] is measured from the top of the list's content. */
    private fun chipKeyAt(px: Float, cy: Float): Int {
        if (expIndex < 0 || expChips.isEmpty()) return K_NONE
        val top = rowTop[expIndex] + rowH
        if (cy < top || cy > top + chipRowH) return K_NONE
        var x = dp(64f)
        for (k in expChips.indices) {
            val label = chipLabel(expChips[k])
            val pw = chipPaint.measureText(label) + dp(28f)
            if (px >= x && px <= x + pw) return K_CHIP + k
            x += pw + dp(8f)
        }
        return K_NONE
    }

    private fun chipTap(k: Int) {
        val kind = expChips.getOrNull(k) ?: return
        model.tapChip(kind)
    }

    private fun centerTap() {
        when (model.state) {
            State.NO_VAULT, State.CONFLICT -> listener?.onOpenApp()
            State.LOCKED -> listener?.onUnlock()
            State.ERROR -> listener?.onRetry()
            else -> Unit
        }
    }

    private companion object {
        const val R_NONE = 0
        const val R_TOP = 1
        const val R_CHIPS = 2
        const val R_LIST = 3
        const val R_BOTTOM = 4
        const val R_ASK = 5

        const val K_NONE = 0
        const val K_GEAR = 1
        const val K_LOCK = 2
        const val K_SEARCH = 3
        const val K_PILL = 4
        const val K_ABC = 5
        const val K_BKSP = 6
        const val K_QUERY = 7
        const val K_CENTER = 8
        const val K_ASK_NO = 9
        const val K_ASK_YES = 10
        const val K_TAB = 20 // + tab index (0..2)
        const val K_CHIP = 40 // + chip index in the opened row

        const val REPEAT_START_MS = 400L
        const val REPEAT_MS = 50L
        const val REPEAT_FAST_MS = 30L
    }
}
