package com.monsoonscript.keyboard.settings

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.prefs.Prefs
import com.monsoonscript.keyboard.theme.ColorMath

/**
 * Builds the app screens out of plain framework views (no libraries): a page with a title and a back arrow, section headers and rows (switch, navigation,
 * button, info). The colors are the keyboard theme in use ([colors], L12, D83): rows that follow each other are drawn together in one rounded card, headers
 * and notes sit between the cards. Rows write straight to [prefs] (the keyboard's [Prefs] unless another file is given, e.g. the clipboard's).
 */
internal class SettingsUi(private val context: Context, private val prefs: SharedPreferences = Prefs.of(context)) {
    private val density = context.resources.displayMetrics.density

    val ctx: Context get() = context

    fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    val colors: AppColors = (context as? ThemedActivity)?.colors ?: AppStyle.colors(context)
    val primary: ColorStateList = ColorStateList.valueOf(colors.text)
    val secondary: ColorStateList = ColorStateList.valueOf(colors.muted)
    val accent: Int = colors.accent
    val bg: Int = colors.bg
    val card: Int = colors.card

    /** The theme's touch feedback (a fresh instance per row). */
    fun ripple(): Drawable? = RippleDrawable(ColorStateList.valueOf(colors.ripple), null, ColorDrawable(Color.WHITE))

    /** A rounded rectangle of [color], with an optional outline. */
    fun rounded(color: Int, radiusDp: Int, stroke: Int? = null): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != null) setStroke(maxOf(1, dp(1) / 2), stroke)
    }

    fun icon(kind: Icons.Kind, color: Int = colors.text, sizeDp: Int = 24): Drawable = Icons.drawable(kind, color, dp(sizeDp))

    /** A small rounded square with an icon in it, for the start of a row. */
    fun iconChip(kind: Icons.Kind, chipDp: Int = 40): View = FrameLayout(context).apply {
        background = rounded(colors.chip, 12)
        addView(ImageView(context).apply { setImageDrawable(icon(kind, colors.accent, chipDp * 11 / 20)) },
            FrameLayout.LayoutParams(dp(chipDp * 11 / 20), dp(chipDp * 11 / 20), Gravity.CENTER))
    }

    /** The app's logo in the color of the theme's text ([sizeDp] square). */
    fun logoView(sizeDp: Int): ImageView = ImageView(context).apply {
        setImageResource(R.drawable.logo_art)
        setColorFilter(colors.text)
        scaleType = ImageView.ScaleType.FIT_CENTER
        contentDescription = context.getString(R.string.app_name)
        minimumWidth = dp(sizeDp)
        minimumHeight = dp(sizeDp)
    }

    /** The back arrow of a page. */
    fun backButton(onBack: () -> Unit): View = ImageView(context).apply {
        setImageDrawable(icon(Icons.Kind.BACK, colors.text, 24))
        scaleType = ImageView.ScaleType.CENTER
        contentDescription = context.getString(R.string.settings_back)
        background = ripple()
        setOnClickListener { onBack() }
    }.also { it.layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(4) } }

    /** A row (the start of one): [RowView]s that follow each other are drawn together in one card. */
    class RowView(context: Context) : LinearLayout(context)

    /** A note under rows: it ends the card above it. */
    class NoteView(context: Context) : TextView(context)

    /** The column of a page: puts [RowView]s that follow each other into one rounded card, with a thin line between them. */
    internal class GroupedColumn(private val ui: SettingsUi) : LinearLayout(ui.ctx) {
        private var card: LinearLayout? = null

        fun addSmart(view: View) {
            if (view is RowView) {
                val c = card ?: newCard()
                if (c.childCount > 0) {
                    c.addView(View(ui.ctx).apply { setBackgroundColor(ui.colors.divider) },
                        LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxOf(1, ui.dp(1) / 2)).apply { leftMargin = ui.dp(20) })
                }
                c.addView(view, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            } else {
                card = null
                addView(view, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }

        /** A card of its own for [view] (a highlighted card, for example); it ends the card above it. */
        fun addCard(view: View, color: Int = ui.colors.card, marginBottomDp: Int = 8) {
            card = null
            val holder = LinearLayout(ui.ctx).apply {
                orientation = VERTICAL
                background = ui.rounded(color, 16, ui.colors.cardStroke)
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
                addView(view, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            addView(holder, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = ui.dp(16)
                rightMargin = ui.dp(16)
                bottomMargin = ui.dp(marginBottomDp)
            })
        }

        private fun newCard(): LinearLayout {
            val c = LinearLayout(ui.ctx).apply {
                orientation = VERTICAL
                background = ui.rounded(ui.colors.card, 16, ui.colors.cardStroke)
                clipToOutline = true
                outlineProvider = ViewOutlineProvider.BACKGROUND
            }
            addView(c, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = ui.dp(16)
                rightMargin = ui.dp(16)
                bottomMargin = ui.dp(8)
            })
            card = c
            return c
        }

        override fun removeAllViews() {
            card = null
            super.removeAllViews()
        }
    }

    /** A scrolling page: [content] is where the rows go, [bar] is its top bar (back arrow and title). [onBack] null = no back arrow (the main page). */
    class Page(val root: View, val content: LinearLayout, val bar: LinearLayout? = null, val logo: View? = null)

    /** [logo] puts the app's logo in front of the title (the main page). */
    fun page(title: String, logo: Boolean = false, onBack: (() -> Unit)?): Page {
        val content = GroupedColumn(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(32))
        }
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(if (onBack == null) 20 else 8), dp(10), dp(20), dp(8))
        }
        if (onBack != null) bar.addView(backButton(onBack))
        val logoView: View? = if (logo) logoView(44).also { bar.addView(it, LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(12) }) } else null
        bar.addView(TextView(context).apply {
            text = title
            textSize = if (onBack == null) 28f else 24f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(colors.text)
        })
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar)
            addView(content)
        }
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            setBackgroundColor(colors.bg)
            addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return Page(scroll, content, bar, logoView)
    }

    /** Add [view] to the page content at full width (rows go into cards on a page). */
    fun LinearLayout.add(view: View) {
        if (this is GroupedColumn) addSmart(view) else addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** The search box: a magnifier, a one-line field and a clear button that shows while there is text; [onChange] gets the text on every change. */
    fun searchField(hint: String, onChange: (String) -> Unit): View {
        val field = EditText(context).apply {
            this.hint = hint
            textSize = 16f
            setTextColor(primary)
            setHintTextColor(secondary)
            background = null
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setPadding(0, dp(14), 0, dp(14))
        }
        val clear = ImageView(context).apply {
            setImageDrawable(icon(Icons.Kind.CLOSE, colors.muted, 20))
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = context.getString(android.R.string.cancel)
            visibility = View.GONE
            background = ripple()
            setOnClickListener { field.setText("") }
        }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                clear.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
                onChange(text)
            }
        })
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(6), 0)
            addView(ImageView(context).apply { setImageDrawable(icon(Icons.Kind.SEARCH, colors.muted, 22)) }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { rightMargin = dp(12) })
            addView(field, weighted())
            addView(clear, LinearLayout.LayoutParams(dp(40), dp(40)))
        }
    }

    private fun findText(v: View, title: String): TextView? {
        if (v is TextView && v !is EditText && v.text.toString() == title) return v
        if (v is ViewGroup) for (i in 0 until v.childCount) findText(v.getChildAt(i), title)?.let { return it }
        return null
    }

    /** Scrolls [page] to the row titled [title] and flashes it for a moment (used when a search result is opened). */
    fun focusRow(page: Page, title: String) {
        val scroll = page.root as? ScrollView ?: return
        val column = scroll.getChildAt(0) as? ViewGroup ?: return
        var row: View = findText(page.content, title) ?: return
        while (row !is RowView) row = row.parent as? View ?: return
        val target: View = row
        scroll.postDelayed({
            var y = 0
            var v: View = target
            while (v !== column) {
                y += v.top
                v = v.parent as? View ?: break
            }
            scroll.smoothScrollTo(0, maxOf(0, y - dp(140)))
            val old = target.background
            target.setBackgroundColor(ColorMath.mix(colors.card, colors.accent, 0.35f))
            target.postDelayed({ target.background = old }, 1600)
        }, 300)
    }

    /** Space between two cards. */
    fun gap(heightDp: Int = 12): View = View(context).apply { minimumHeight = dp(heightDp) }
        .also { it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(heightDp)) }

    /** A wide button filled with the accent color. */
    fun filledButton(text: String, onClick: () -> Unit): View = TextView(context).apply {
        this.text = text
        textSize = 16f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(colors.onAccent)
        setPadding(dp(20), dp(14), dp(20), dp(14))
        background = RippleDrawable(ColorStateList.valueOf(0x33000000), rounded(colors.accent, 14), rounded(Color.WHITE, 14))
        setOnClickListener { onClick() }
    }

    /** A wide button with an accent outline. */
    fun outlineButton(text: String, onClick: () -> Unit): View = TextView(context).apply {
        val outline = colors.accent
        this.text = text
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(colors.accent)
        setPadding(dp(20), dp(12), dp(20), dp(12))
        background = RippleDrawable(ColorStateList.valueOf(colors.ripple), GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(14).toFloat()
            setStroke(maxOf(1, dp(1)), outline)
        }, rounded(Color.WHITE, 14))
        setOnClickListener { onClick() }
    }

    /** Fades and slides the parts of [page] in, one after another (the cards "arrive"). Call it right after the page is built, before it is first drawn. */
    fun animateIn(page: Page, startDelayMs: Long = 0L) {
        val column = page.content
        val bar = page.bar
        val parts = ArrayList<View>()
        if (bar != null) parts += bar
        for (i in 0 until column.childCount) parts += column.getChildAt(i)
        for ((i, v) in parts.withIndex()) {
            v.alpha = 0f
            v.translationY = dp(22).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(startDelayMs + 50L * minOf(i, 10)).setDuration(380).setInterpolator(DecelerateInterpolator()).start()
        }
    }

    /** Puts everything of [page] in its place at once (a safety net after [animateIn]). */
    fun settle(page: Page) {
        val parts = ArrayList<View>()
        page.bar?.let { parts += it }
        for (i in 0 until page.content.childCount) parts += page.content.getChildAt(i)
        for (v in parts) {
            v.animate().cancel()
            v.alpha = 1f
            v.translationY = 0f
        }
    }

    fun header(text: String): View = TextView(context).apply {
        this.text = text.uppercase()
        textSize = 12.5f
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.08f
        setTextColor(colors.accent)
        setPadding(dp(32), dp(22), dp(20), dp(8))
    }

    fun row(): RowView = RowView(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(60)
        setPadding(dp(20), dp(10), dp(20), dp(10))
        background = ripple()
    }

    fun texts(title: String, summary: String?, titleColor: ColorStateList? = null): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(context).apply {
                text = title
                textSize = 17f
                setTextColor(titleColor ?: primary)
            })
            if (summary != null) {
                addView(TextView(context).apply {
                    text = summary
                    textSize = 14f
                    setTextColor(secondary)
                    setPadding(0, dp(2), 0, 0)
                })
            }
        }

    fun weighted(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    /** A row with a switch that reads and writes the boolean preference [key]. */
    fun switchRow(title: String, summary: String?, key: String, default: Boolean): View {
        val sw = Switch(context).apply {
            isChecked = prefs.getBoolean(key, default)
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(colors.accent, colors.muted))
            trackTintList = ColorStateList(states, intArrayOf(colors.accent, colors.muted))
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(key, on).apply() }
        }
        return row().apply {
            addView(texts(title, summary), weighted())
            addView(sw)
            setOnClickListener { sw.toggle() }
        }
    }

    /** A row for the Int preference [key] (an index into [options]): the summary shows the current choice, a tap opens a list to pick another. */
    fun choiceRow(title: String, key: String, options: List<String>, default: Int): View {
        fun current(): Int = prefs.getInt(key, default).coerceIn(0, options.size - 1)
        val labels = texts(title, options[current()])
        val summary = labels.getChildAt(1) as TextView
        return row().apply {
            addView(labels, weighted())
            addView(chevron())
            setOnClickListener {
                val dialog = AlertDialog.Builder(context)
                    .setTitle(title)
                    .setSingleChoiceItems(options.toTypedArray(), current()) { d, which ->
                        prefs.edit().putInt(key, which).apply()
                        summary.text = options[which]
                        d.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(colors.accent)
            }
        }
    }

    /** A row with a slider for the Int preference [key] from [minValue] to [maxValue]; [format] writes the current value next to the title. */
    fun sliderRow(title: String, key: String, minValue: Int, maxValue: Int, default: Int, format: (Int) -> String): View {
        val start = prefs.getInt(key, default).coerceIn(minValue, maxValue)
        val valueText = TextView(context).apply {
            text = format(start)
            textSize = 14f
            setTextColor(colors.accent)
        }
        val bar = SeekBar(context).apply {
            max = maxValue - minValue
            progress = start - minValue
            progressTintList = ColorStateList.valueOf(colors.accent)
            thumbTintList = ColorStateList.valueOf(colors.accent)
            progressBackgroundTintList = ColorStateList.valueOf(colors.muted)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    valueText.text = format(progress + minValue)
                    if (fromUser) prefs.edit().putInt(key, progress + minValue).apply()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        return RowView(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(10))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(TextView(context).apply {
                    text = title
                    textSize = 17f
                    setTextColor(primary)
                }, weighted())
                addView(valueText)
            })
            addView(bar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }

    /** A text field for trying the keyboard right on the settings page. */
    fun tryField(hint: String): View = RowView(context).apply {
        setPadding(dp(20), dp(8), dp(20), dp(8))
        addView(EditText(context).apply {
            this.hint = hint
            textSize = 17f
            setTextColor(primary)
            setHintTextColor(secondary)
            background = null
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** The small arrow at the end of a row that opens something. */
    fun chevron(): View = ImageView(context).apply {
        setImageDrawable(icon(Icons.Kind.CHEVRON, colors.muted, 20))
        layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { leftMargin = dp(8) }
    }

    /** A row that opens another page; [icon] puts a small icon square at its start. */
    fun navRow(title: String, summary: String?, icon: Icons.Kind? = null, onClick: () -> Unit): View = row().apply {
        if (icon != null) addView(iconChip(icon), LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(16) })
        addView(texts(title, summary), weighted())
        addView(chevron())
        setOnClickListener { onClick() }
    }

    /** A row that does something when tapped; the title is drawn in the accent color. */
    fun buttonRow(title: String, summary: String?, onClick: () -> Unit): View = row().apply {
        addView(texts(title, summary, ColorStateList.valueOf(colors.accent)), weighted())
        setOnClickListener { onClick() }
    }

    fun infoRow(title: String, summary: String?): View = row().apply {
        addView(texts(title, summary), weighted())
    }

    fun note(text: String): View = NoteView(context).apply {
        this.text = text
        textSize = 13f
        setTextColor(colors.muted)
        setPadding(dp(32), dp(4), dp(24), dp(10))
    }
}
