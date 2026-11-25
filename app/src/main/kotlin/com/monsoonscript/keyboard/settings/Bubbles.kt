package com.monsoonscript.keyboard.settings

import android.animation.LayoutTransition
import android.annotation.SuppressLint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.theme.ThemePreview
import com.monsoonscript.keyboard.view.KeyboardView
import kotlin.math.abs

/**
 * The floating things of an app screen (L12 R3, D83/D85/D86): round bubbles the person can drag around and tap.
 *  - "Try it" ([addTryIt]): opens a text box in a floating window at the top, to try the REAL keyboard right where the settings are (the keyboard follows every
 *    setting and theme change at once).
 *  - A preview ([addPreview], only the theme editor uses it): a keyboard to look at, DOCKED at the bottom like the real one; the page above gets smaller to make room.
 * The screen's content goes into [root] (instead of being the content itself). The open windows survive a rebuild of the screen ([saveState] / [restoreState]).
 */
internal class BubbleHost(private val activity: ThemedActivity, private val ui: SettingsUi, content: View) {
    val root: LinearLayout = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        layoutTransition = LayoutTransition().apply { setDuration(220) }
    }

    /** The part of the screen above the dock: the content, the bubbles and the "Try it" window. */
    private val area = FrameLayout(activity)
    private val colors = ui.colors
    private val margin = ui.dp(16)
    private val bubbles = ArrayList<View>()
    private var tryWindow: View? = null
    private var tryField: EditText? = null
    private var tryBubble: ImageView? = null
    private var dock: LinearLayout? = null
    private var previewBubble: ImageView? = null
    private var previewIcon = Icons.Kind.PALETTE

    init {
        area.addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(area, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        // when the area changes size (the keyboard opens, the dock appears) keep every bubble inside it
        area.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> for (b in bubbles) clampInside(b) }
    }

    // ---- "Try it"

    /** The "Try it" bubble; [slot] 0 is the lowest place, 1 the one above it. */
    fun addTryIt(slot: Int = 0) {
        tryBubble = bubble(Icons.Kind.KEYBOARD, activity.getString(R.string.bubble_try_desc), slot) { if (tryWindow == null) openTry() else closeTry() }
    }

    private fun openTry() {
        if (tryWindow != null) return
        val field = EditText(activity).apply {
            hint = activity.getString(R.string.settings_try_hint)
            textSize = 17f
            setTextColor(ui.primary)
            setHintTextColor(ui.secondary)
            background = null
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 4
            gravity = Gravity.TOP or Gravity.START
            setPadding(ui.dp(20), ui.dp(4), ui.dp(20), ui.dp(16))
        }
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = ui.rounded(colors.card, 20, colors.cardStroke)
            elevation = ui.dp(8).toFloat()
            addView(windowTitle(activity.getString(R.string.settings_try_header)) { closeTry() })
            addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            alpha = 0f
            translationY = -ui.dp(16).toFloat()
        }
        area.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            setMargins(margin, ui.dp(72), margin, 0)
        })
        card.animate().alpha(1f).translationY(0f).setDuration(200).start()
        for (b in bubbles) b.bringToFront()
        tryWindow = card
        tryField = field
        tryBubble?.setImageDrawable(ui.icon(Icons.Kind.CLOSE, colors.onAccent, 26))
        field.requestFocus()
        field.post { activity.getSystemService(InputMethodManager::class.java).showSoftInput(field, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun closeTry() {
        val card = tryWindow ?: return
        val field = tryField
        tryWindow = null
        tryField = null
        if (field != null) activity.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(field.windowToken, 0)
        tryBubble?.setImageDrawable(ui.icon(Icons.Kind.KEYBOARD, colors.onAccent, 26))
        card.animate().alpha(0f).translationY(-ui.dp(16).toFloat()).setDuration(160).withEndAction { area.removeView(card) }.start()
    }

    // ---- "Live preview"

    /** The preview bubble ([icon], a palette by default) and its dock for [kb] (the screen keeps applying the theme to [kb]); [slot] 1 is the place above "Try it". */
    fun addPreview(kb: KeyboardView, slot: Int = 1, icon: Icons.Kind = Icons.Kind.PALETTE) {
        previewIcon = icon
        val radius = ui.dp(20).toFloat()
        val fill = colors.card
        val outline = colors.cardStroke
        val d = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(fill)
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f)
                setStroke(maxOf(1, ui.dp(1) / 2), outline)
            }
            visibility = View.GONE
            addView(windowTitle(activity.getString(R.string.bubble_preview_title)) { setDock(false) })
            addView(ThemePreview.frame(activity, kb))
        }
        root.addView(d, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        dock = d
        previewBubble = bubble(icon, activity.getString(R.string.bubble_preview_desc), slot) { setDock(d.visibility != View.VISIBLE) }
    }

    fun setDock(open: Boolean) {
        val d = dock ?: return
        d.visibility = if (open) View.VISIBLE else View.GONE
        previewBubble?.setImageDrawable(ui.icon(if (open) Icons.Kind.CLOSE else previewIcon, colors.onAccent, 26))
    }

    // ---- state across a rebuild of the screen (a theme change rebuilds it)

    fun saveState(out: Bundle) {
        out.putBoolean(KEY_DOCK, dock?.visibility == View.VISIBLE)
        out.putBoolean(KEY_TRY, tryWindow != null)
    }

    fun restoreState(saved: Bundle?) {
        if (saved == null) return
        if (saved.getBoolean(KEY_DOCK)) setDock(true)
        if (saved.getBoolean(KEY_TRY) && tryBubble != null) openTry()
    }

    // ---- pieces

    private fun windowTitle(title: String, onClose: () -> Unit): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(ui.dp(20), ui.dp(6), ui.dp(6), 0)
        addView(TextView(activity).apply {
            text = title.uppercase()
            textSize = 12.5f
            letterSpacing = 0.08f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(colors.accent)
        }, ui.weighted())
        addView(ImageView(activity).apply {
            setImageDrawable(ui.icon(Icons.Kind.CLOSE, colors.muted, 20))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = activity.getString(R.string.bubble_close)
            background = ui.ripple()
            setOnClickListener { onClose() }
        }, LinearLayout.LayoutParams(ui.dp(44), ui.dp(44)))
    }

    private fun bubble(kind: Icons.Kind, description: String, slot: Int, onClick: () -> Unit): ImageView {
        val size = ui.dp(56)
        val fill = colors.accent
        val ring = colors.bg
        val b = ImageView(activity).apply {
            setImageDrawable(ui.icon(kind, colors.onAccent, 26))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = description
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(fill)
                setStroke(ui.dp(2), ring)
            }
            elevation = ui.dp(6).toFloat()
            setOnClickListener { onClick() }
        }
        area.addView(b, FrameLayout.LayoutParams(size, size, Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, margin, margin + slot * ui.dp(68))
        })
        bubbles += b
        makeDraggable(b)
        return b
    }

    /** A drag moves the bubble (it settles at the nearer side edge); a tap without moving is a click. */
    @SuppressLint("ClickableViewAccessibility")
    private fun makeDraggable(v: View) {
        val slop = ViewConfiguration.get(activity).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startTx = 0f
        var startTy = 0f
        var dragging = false
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startTx = view.translationX
                    startTy = view.translationY
                    dragging = false
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        view.translationX = (startTx + dx).coerceIn(minTx(view), maxTx(view))
                        view.translationY = (startTy + dy).coerceIn(minTy(view), maxTy(view))
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) settle(view) else view.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) settle(view)
                    true
                }
                else -> false
            }
        }
    }

    private fun minTx(v: View): Float = (margin - v.left).toFloat()

    private fun maxTx(v: View): Float = maxOf(minTx(v), (area.width - v.width - v.left - margin).toFloat())

    private fun minTy(v: View): Float = (margin - v.top).toFloat()

    private fun maxTy(v: View): Float = maxOf(minTy(v), (area.height - v.height - v.top - margin).toFloat())

    private fun clampInside(v: View) {
        v.translationX = v.translationX.coerceIn(minTx(v), maxTx(v))
        v.translationY = v.translationY.coerceIn(minTy(v), maxTy(v))
    }

    private fun settle(v: View) {
        val centre = v.left + v.translationX + v.width / 2f
        val target = if (centre < area.width / 2f) minTx(v) else maxTx(v)
        v.animate().translationX(target).setDuration(180).start()
    }

    private companion object {
        const val KEY_DOCK = "bubble_dock_open"
        const val KEY_TRY = "bubble_try_open"
    }
}
