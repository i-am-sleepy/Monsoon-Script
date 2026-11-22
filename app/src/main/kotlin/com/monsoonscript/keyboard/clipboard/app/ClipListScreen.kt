package com.monsoonscript.keyboard.clipboard.app

import android.app.Activity
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.graphics.Outline
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.clipboard.ClipFormat
import com.monsoonscript.keyboard.clipboard.ClipHub
import com.monsoonscript.keyboard.clipboard.ClipItem
import com.monsoonscript.keyboard.clipboard.ClipListModel
import com.monsoonscript.keyboard.clipboard.ClipStore
import com.monsoonscript.keyboard.clipboard.ImageMime
import com.monsoonscript.keyboard.settings.SettingsUi

/**
 * One list of clips (History, Pinned, or the clips of a folder) as plain list rows (D67): search field on top, rows below, and while a selection is going
 * a count bar on top and Pin / Move / Share / Delete at the bottom. The logic is [ClipListModel] (unit-tested); this class only shows it.
 */
internal class ClipListScreen(
    private val activity: Activity,
    private val ui: SettingsUi,
    private val hub: ClipHub,
    private val actions: ClipActions,
    scope: ClipListModel.Scope,
    folder: Long = 0L,
) : Screen(), ClipStore.Listener {

    private val store = hub.store
    private val model = ClipListModel(store).also { it.show(scope, folder) }
    private val root = LinearLayout(activity)
    override val view: View get() = root

    private val search = EditText(activity)
    private val searchBox = LinearLayout(activity)
    private val selectBar = LinearLayout(activity)
    private val selectCount = TextView(activity)
    private val list = ListView(activity)
    private val emptyText = TextView(activity)
    private val actionBar = LinearLayout(activity)
    private var now = System.currentTimeMillis()
    private var wasSelecting = false
    private var listening = false

    /** A picture's thumbnail was read in the background: show it (several arrive in a burst, so one refresh covers them). */
    private val refreshThumbs = Runnable { adapter.notifyDataSetChanged() }
    private val thumbReady = Runnable {
        list.removeCallbacks(refreshThumbs)
        list.post(refreshThumbs)
    }

    private val adapter = object : BaseAdapter() {
        override fun getCount(): Int = model.rows.size

        override fun getItem(position: Int): Any = model.rows[position]

        override fun getItemId(position: Int): Long = position.toLong()

        override fun getViewTypeCount(): Int = 2

        override fun getItemViewType(position: Int): Int = if (model.rows[position].clip == null) 0 else 1

        override fun areAllItemsEnabled(): Boolean = false

        override fun isEnabled(position: Int): Boolean = model.rows[position].clip != null

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = model.rows[position]
            val clip = row.clip
            if (clip == null) {
                val h = (convertView as? TextView) ?: (ui.header("") as TextView)
                h.text = activity.getString(groupLabel(row.group)).uppercase()
                return h
            }
            val v = (convertView as? RowView) ?: RowView()
            v.bind(clip)
            return v
        }
    }

    private fun groupLabel(g: Int): Int = when (g) {
        ClipListModel.GROUP_PINNED -> R.string.clip_group_pinned
        ClipListModel.GROUP_TODAY -> R.string.clip_group_today
        ClipListModel.GROUP_YESTERDAY -> R.string.clip_group_yesterday
        else -> R.string.clip_group_earlier
    }

    /** One clip: a selection mark (only while selecting), up to two lines of the text, and a small line with pin, time and folder. */
    private inner class RowView : LinearLayout(activity) {
        private val mark = TextView(activity)
        private val thumb = ImageView(activity)
        private val preview = TextView(activity)
        private val meta = TextView(activity)
        private var highlighted = false

        init {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dp(64)
            setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(10))
            background = ui.ripple()
            mark.apply {
                textSize = 20f
                gravity = Gravity.CENTER
            }
            addView(mark, LayoutParams(ui.dp(32), ViewGroup.LayoutParams.WRAP_CONTENT))
            thumb.apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                visibility = GONE
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) {
                        outline.setRoundRect(0, 0, view.width, view.height, ui.dp(8).toFloat())
                    }
                }
                clipToOutline = true
            }
            addView(thumb, LayoutParams(ui.dp(48), ui.dp(48)).apply { rightMargin = ui.dp(12) })
            val column = LinearLayout(activity).apply { orientation = VERTICAL }
            preview.apply {
                textSize = 16f
                setTextColor(ui.primary)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
            meta.apply {
                textSize = 13f
                setTextColor(ui.secondary)
                setPadding(0, ui.dp(3), 0, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            column.addView(preview)
            column.addView(meta)
            addView(column, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        fun bind(clip: ClipItem) {
            val selecting = model.selecting
            val selected = model.isSelected(clip)
            mark.visibility = if (selecting) VISIBLE else GONE
            mark.text = if (selected) "✓" else "○"
            mark.setTextColor(if (selected) ui.accent else ui.secondary.defaultColor)
            if (selected != highlighted) { // only on a change: a new ripple drawable per bind would cost while scrolling
                highlighted = selected
                if (selected) setBackgroundColor((ui.accent and 0x00FFFFFF) or 0x33000000) else background = ui.ripple()
            }
            if (clip.isText) {
                preview.text = ClipFormat.previewSource(clip.text, 200)
                preview.setTextColor(ui.primary)
            } else {
                preview.text = activity.getString(R.string.clip_image) + " · " + ImageMime.label(clip.mime)
                preview.setTextColor(ui.secondary)
            }
            if (clip.isImage) {
                val bmp = clip.image?.let { hub.thumbs.thumb(it, thumbReady) }
                thumb.visibility = VISIBLE
                thumb.setImageBitmap(bmp)
                thumb.setBackgroundColor(0x22808080) // the square shows until the picture is read
            } else {
                thumb.visibility = GONE
                thumb.setImageDrawable(null)
            }
            val sb = StringBuilder()
            if (clip.pinned) sb.append(activity.getString(R.string.clip_group_pinned)).append(" · ")
            sb.append(ClipFormat.ago(clip.used, now))
            if (clip.folder != 0L && model.scope != ClipListModel.Scope.FOLDER) {
                val f = store.folderOf(clip.folder)
                if (f != null) sb.append(" · ").append(f.name)
            }
            meta.text = sb
        }
    }

    init {
        root.orientation = LinearLayout.VERTICAL

        search.apply {
            hint = activity.getString(R.string.clip_search_hint)
            textSize = 16f
            setTextColor(ui.primary)
            setHintTextColor(ui.secondary)
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    model.setQuery(s?.toString() ?: "")
                    refresh()
                }
            })
        }
        searchBox.apply {
            setPadding(ui.dp(20), ui.dp(0), ui.dp(20), ui.dp(4))
            addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        selectBar.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            addView(textButton("✕", ui.primary.defaultColor) { endSelection() })
            selectCount.apply {
                textSize = 17f
                setTextColor(ui.primary)
            }
            addView(selectCount, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(textButton(activity.getString(R.string.clip_select_all), ui.accent) {
                model.selectAll()
                selectionChanged()
            })
        }
        val top = FrameLayout(activity).apply {
            addView(searchBox)
            addView(selectBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        root.addView(top, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        list.apply {
            divider = null
            adapter = this@ClipListScreen.adapter
            clipToPadding = false
            setPadding(0, 0, 0, ui.dp(8))
            setOnItemClickListener { _, _, position, _ ->
                val clip = model.rows.getOrNull(position)?.clip ?: return@setOnItemClickListener
                if (model.selecting) {
                    model.toggle(clip)
                    selectionChanged()
                } else {
                    actions.showMenu(clip)
                }
            }
            setOnItemLongClickListener { _, _, position, _ ->
                val clip = model.rows.getOrNull(position)?.clip ?: return@setOnItemLongClickListener false
                model.toggle(clip)
                selectionChanged()
                true
            }
        }
        emptyText.apply {
            textSize = 16f
            setTextColor(ui.secondary)
            gravity = Gravity.CENTER
            setPadding(ui.dp(32), ui.dp(48), ui.dp(32), 0)
            visibility = View.GONE
        }
        val body = FrameLayout(activity).apply {
            addView(list, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(emptyText, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))
        }
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        actionBar.apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            for ((res, run) in listOf<Pair<Int, () -> Unit>>(
                R.string.clip_bar_pin to { model.pinSelected(); selectionChanged() },
                R.string.clip_bar_move to { actions.pickFolder { model.moveSelected(it); selectionChanged() } },
                R.string.clip_bar_share to { actions.shareSelection(model.selectedImages(), model.exportText()); endSelection() },
                R.string.clip_bar_delete to { actions.confirmDelete(model.selectionCount) { model.deleteSelected(); selectionChanged() } },
            )) {
                addView(textButton(activity.getString(res), ui.accent, run), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            }
        }
        root.addView(actionBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun textButton(text: String, color: Int, onClick: () -> Unit): TextView = TextView(activity).apply {
        this.text = text
        textSize = 16f
        setTextColor(color)
        gravity = Gravity.CENTER
        setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
        background = ui.ripple()
        setOnClickListener { onClick() }
    }

    // ---------------------------------------------------------------- state

    override fun show() {
        model.groupByDate = hub.prefs.groupByDate
        hub.start()
        if (!listening) {
            store.addListener(this)
            listening = true
        }
        refresh()
    }

    override fun hide() {
        if (listening) {
            store.removeListener(this)
            listening = false
        }
        if (model.selecting) {
            model.clearSelection()
            refresh()
        }
    }

    override fun back(): Boolean {
        if (!model.selecting) return false
        endSelection()
        return true
    }

    override fun onClipsChanged() = refresh()

    private fun endSelection() {
        model.clearSelection()
        selectionChanged()
    }

    private fun selectionChanged() = refresh()

    /** Rebuild the rows and bring the bars and the empty message in line with the model. */
    private fun refresh() {
        now = System.currentTimeMillis()
        model.rebuild()
        adapter.notifyDataSetChanged()
        val selecting = model.selecting
        searchBox.visibility = if (selecting) View.GONE else View.VISIBLE
        selectBar.visibility = if (selecting) View.VISIBLE else View.GONE
        actionBar.visibility = if (selecting) View.VISIBLE else View.GONE
        if (selecting) selectCount.text = activity.getString(R.string.clip_selected, model.selectionCount)
        val msg = when (model.empty) {
            ClipListModel.Empty.NONE -> 0
            ClipListModel.Empty.HISTORY -> if (hub.prefs.historyEnabled) R.string.clip_empty_history else R.string.clip_empty_history_off
            ClipListModel.Empty.PINNED -> R.string.clip_empty_pinned
            ClipListModel.Empty.FOLDER -> R.string.clip_empty_folder
            ClipListModel.Empty.NO_MATCH -> R.string.clip_empty_match
        }
        emptyText.visibility = if (msg == 0) View.GONE else View.VISIBLE
        if (msg != 0) emptyText.setText(msg)
        if (selecting != wasSelecting) {
            wasSelecting = selecting
            onSelecting?.invoke(selecting)
        }
    }
}
