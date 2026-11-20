package com.monsoonscript.keyboard.settings

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.monsoonscript.keyboard.prefs.Prefs

/**
 * Builds the settings screens out of plain framework views (no libraries): a page with a title and a back arrow, section headers and rows
 * (switch, navigation, button, info). Colors come from the activity theme, so light and dark follow the phone. Rows write straight to [Prefs].
 */
internal class SettingsUi(private val context: Context) {
    private val density = context.resources.displayMetrics.density

    fun dp(v: Int): Int = (v * density + 0.5f).toInt()

    val primary: ColorStateList = themeColors(android.R.attr.textColorPrimary) ?: ColorStateList.valueOf(Color.BLACK)
    val secondary: ColorStateList = themeColors(android.R.attr.textColorSecondary) ?: ColorStateList.valueOf(Color.GRAY)
    private val accent: Int = context.obtainStyledAttributes(intArrayOf(android.R.attr.colorAccent)).let {
        val c = it.getColor(0, 0xFF00796B.toInt())
        it.recycle()
        c
    }

    private fun themeColors(attr: Int): ColorStateList? {
        val a = context.obtainStyledAttributes(intArrayOf(attr))
        try {
            return a.getColorStateList(0)
        } finally {
            a.recycle()
        }
    }

    /** The theme's touch-feedback background (a fresh instance per row). */
    private fun ripple(): Drawable? {
        val tv = TypedValue()
        if (!context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) return null
        return context.getDrawable(tv.resourceId)
    }

    /** A scrolling page: [content] is where the rows go. [onBack] null = no back arrow (the main page). */
    class Page(val root: View, val content: LinearLayout)

    fun page(title: String, onBack: (() -> Unit)?): Page {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(32))
        }
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(if (onBack == null) 20 else 8), dp(20), dp(20), dp(12))
        }
        if (onBack != null) {
            bar.addView(TextView(context).apply {
                text = "‹" // ‹
                textSize = 34f
                setTextColor(primary)
                setPadding(dp(12), 0, dp(16), dp(4))
                contentDescription = context.getString(com.monsoonscript.keyboard.R.string.settings_back)
                background = ripple()
                setOnClickListener { onBack() }
            })
        }
        bar.addView(TextView(context).apply {
            text = title
            textSize = 26f
            setTextColor(primary)
        })
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar)
            addView(content)
        }
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        return Page(scroll, content)
    }

    /** Add [view] to the page content at full width. */
    fun LinearLayout.add(view: View) {
        addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    fun header(text: String): View = TextView(context).apply {
        this.text = text.uppercase()
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        letterSpacing = 0.08f
        setTextColor(accent)
        setPadding(dp(20), dp(24), dp(20), dp(6))
    }

    private fun row(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(60)
        setPadding(dp(20), dp(10), dp(20), dp(10))
        background = ripple()
    }

    private fun texts(title: String, summary: String?, titleColor: ColorStateList? = null): LinearLayout =
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

    private fun weighted(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

    /** A row with a switch that reads and writes the boolean preference [key]. */
    fun switchRow(title: String, summary: String?, key: String, default: Boolean): View {
        val prefs = Prefs.of(context)
        val sw = Switch(context).apply {
            isChecked = prefs.getBoolean(key, default)
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
        val prefs = Prefs.of(context)
        fun current(): Int = prefs.getInt(key, default).coerceIn(0, options.size - 1)
        val labels = texts(title, options[current()])
        val summary = labels.getChildAt(1) as TextView
        return row().apply {
            addView(labels, weighted())
            setOnClickListener {
                AlertDialog.Builder(context)
                    .setTitle(title)
                    .setSingleChoiceItems(options.toTypedArray(), current()) { dialog, which ->
                        prefs.edit().putInt(key, which).apply()
                        summary.text = options[which]
                        dialog.dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    /** A row with a slider for the Int preference [key] from [minValue] to [maxValue]; [format] writes the current value next to the title. */
    fun sliderRow(title: String, key: String, minValue: Int, maxValue: Int, default: Int, format: (Int) -> String): View {
        val prefs = Prefs.of(context)
        val start = prefs.getInt(key, default).coerceIn(minValue, maxValue)
        val valueText = TextView(context).apply {
            text = format(start)
            textSize = 14f
            setTextColor(secondary)
        }
        val bar = SeekBar(context).apply {
            max = maxValue - minValue
            progress = start - minValue
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    valueText.text = format(progress + minValue)
                    if (fromUser) prefs.edit().putInt(key, progress + minValue).apply()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(10))
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
    fun tryField(hint: String): View = LinearLayout(context).apply {
        setPadding(dp(20), dp(4), dp(20), dp(8))
        addView(EditText(context).apply {
            this.hint = hint
            textSize = 17f
            setTextColor(primary)
            setHintTextColor(secondary)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** A row that opens another page. */
    fun navRow(title: String, summary: String?, onClick: () -> Unit): View = row().apply {
        addView(texts(title, summary), weighted())
        addView(TextView(context).apply {
            text = "›" // ›
            textSize = 26f
            setTextColor(secondary)
            setPadding(dp(12), 0, 0, 0)
        })
        setOnClickListener { onClick() }
    }

    /** A row that does something when tapped; the title is drawn in the accent color. */
    fun buttonRow(title: String, summary: String?, onClick: () -> Unit): View = row().apply {
        addView(texts(title, summary, ColorStateList.valueOf(accent)), weighted())
        setOnClickListener { onClick() }
    }

    fun infoRow(title: String, summary: String?): View = row().apply {
        addView(texts(title, summary), weighted())
    }

    fun note(text: String): View = TextView(context).apply {
        this.text = text
        textSize = 14f
        setTextColor(secondary)
        setPadding(dp(20), dp(8), dp(20), dp(8))
    }
}
