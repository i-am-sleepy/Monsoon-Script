package com.monsoonscript.keyboard.autofill

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InlineSuggestion
import android.view.inputmethod.InlineSuggestionsRequest
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.inline.InlineContentView
import android.widget.inline.InlinePresentationSpec
import android.annotation.TargetApi
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi

/** The keyboard's side of Autofill chips (E1.21, D102): what it asks the Autofill service for, and the strip-high bar that shows what comes back. Android 11+. */
@TargetApi(Build.VERSION_CODES.R)
internal object InlineStrip {
    private const val MAX_CHIPS = 6

    /** What the keyboard can show: chips between 80 and 260 dp wide, as high as the strip allows, in Android's default chip style (rounded, readable on any theme). */
    fun request(context: Context, uiExtras: Bundle, stripPx: Int): InlineSuggestionsRequest? {
        if (!UiVersions.getVersions(uiExtras).contains(UiVersions.INLINE_UI_VERSION_1)) return null // the system's chip renderer does not know the format we speak
        val styles = UiVersions.newStylesBuilder()
        styles.addStyle(InlineSuggestionUi.newStyleBuilder().build())
        val d = context.resources.displayMetrics.density
        val maxH = (stripPx - 8 * d).toInt().coerceAtLeast((32 * d).toInt())
        val spec = InlinePresentationSpec.Builder(Size((80 * d).toInt(), (28 * d).toInt()), Size((260 * d).toInt(), maxH)).setStyle(styles.build()).build()
        return InlineSuggestionsRequest.Builder(listOf(spec)).setMaxSuggestionCount(MAX_CHIPS).build()
    }
}

/**
 * A bar as high as the keyboard's strip that sits over it while Autofill has chips to show, with a close button at the end. The owner's typing, the close button,
 * a new field or an empty answer take it away again and the strip is back. Nothing here knows an entry: the chips are drawn by Android from what the Autofill
 * service gave, and a tap on one goes straight to the service.
 */
@TargetApi(Build.VERSION_CODES.R)
internal class InlineStripHost(context: Context, private val colors: () -> Pair<Int, Int>) : FrameLayout(context) {
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val close = TextView(context).apply {
        text = "×"
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        gravity = Gravity.CENTER
        contentDescription = context.getString(com.monsoonscript.keyboard.R.string.autofill_chips_close)
        setOnClickListener { dismiss() }
    }
    private val density = context.resources.displayMetrics.density
    private var generation = 0

    /** True after the owner typed or closed the bar: later answers for this field are ignored until the next field. */
    var dismissed = false
        private set

    init {
        visibility = View.GONE
        isClickable = true // the keys below must not receive touches that land on the bar
        val scroller = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(row, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        addView(scroller, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply { rightMargin = (44 * density).toInt() })
        addView(close, LayoutParams((44 * density).toInt(), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END))
    }

    /** New chips from the Autofill service: draws them, replacing the old ones. */
    fun show(suggestions: List<InlineSuggestion>) {
        if (dismissed) return
        if (suggestions.isEmpty()) {
            clear()
            return
        }
        val (background, text) = colors()
        setBackgroundColor(background)
        close.setTextColor(text)
        val mine = ++generation
        val views = arrayOfNulls<InlineContentView>(suggestions.size)
        var pending = suggestions.size
        val any = Size(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        for ((i, s) in suggestions.withIndex()) {
            s.inflate(context, any, context.mainExecutor) { view ->
                if (mine != generation) return@inflate // a newer answer came meanwhile
                views[i] = view
                if (--pending == 0) fill(views)
            }
        }
    }

    private fun fill(views: Array<InlineContentView?>) {
        row.removeAllViews()
        val gap = (6 * density).toInt()
        for (v in views) {
            if (v == null) continue
            row.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_VERTICAL
                setMargins(gap, 0, 0, 0)
            })
        }
        visibility = if (row.childCount > 0) View.VISIBLE else View.GONE
    }

    /** The owner typed, or the field is another one: the strip is back; chips already on their way are dropped. */
    fun clear() {
        generation++
        row.removeAllViews()
        visibility = View.GONE
    }

    /** The owner closed the bar or started typing: no chips until the next field. */
    fun dismiss() {
        dismissed = true
        clear()
    }

    /** A new field: chips are welcome again. */
    fun reset() {
        dismissed = false
        clear()
    }

    val showing: Boolean get() = visibility == View.VISIBLE
}
