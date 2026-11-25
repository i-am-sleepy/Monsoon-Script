package com.monsoonscript.keyboard.voice

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.settings.SettingsUi
import com.monsoonscript.keyboard.settings.ThemedActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings > Voice typing > Problem report (G5): why did the keyboard stop or vanish? Android keeps the reason each time this app's process ended (a crash,
 * a native crash, memory, an ANR...) and we keep the exceptions we caught or saw. One button copies everything so it can be pasted into the chat.
 */
class ProblemsActivity : ThemedActivity() {

    private lateinit var view: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = SettingsUi(this)
        val page = ui.page(getString(R.string.voice_problems_title)) { finish() }
        view = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            textSize = 11f
            setPadding(48, 16, 48, 32)
            setTextIsSelectable(true)
            setTextColor(colors.text)
        }
        with(ui) {
            page.content.add(buttonRow(getString(R.string.voice_problems_copy), getString(R.string.voice_problems_copy_summary)) { copy() })
            page.content.add(buttonRow(getString(R.string.voice_problems_clear), null) {
                CrashLog.clear(this@ProblemsActivity)
                view.text = report()
            })
        }
        page.content.addView(view)
        setContentView(page.root)
        view.text = report()
    }

    private fun copy() {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("problem report", report()))
        Toast.makeText(this, R.string.voice_problems_copied, Toast.LENGTH_SHORT).show()
    }

    private fun reasonName(r: Int) = when (r) {
        1 -> "exit by itself"
        2 -> "killed by a signal"
        3 -> "low memory"
        4 -> "CRASH (Java exception)"
        5 -> "CRASH (native)"
        6 -> "ANR (not responding)"
        7 -> "initialization failure"
        8 -> "permission change"
        9 -> "excessive resource usage"
        10 -> "user requested"
        11 -> "user stopped"
        12 -> "dependency died"
        13 -> "other"
        14 -> "frozen by the system"
        15 -> "package state change"
        16 -> "package updated"
        else -> "unknown ($r)"
    }

    private fun report(): String = buildString {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        append("Monsoon Script problem report\n")
        append("Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n\n")
        append("--- Why this app's processes ended (Android's record, newest first) ---\n")
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                val list = am.getHistoricalProcessExitReasons(packageName, 0, 10)
                if (list.isEmpty()) append("(none recorded)\n")
                for (i in list) {
                    append("${fmt.format(Date(i.timestamp))}  ${i.processName}\n    ${reasonName(i.reason)}, status ${i.status}, ${i.description ?: ""}\n")
                }
            } catch (e: Exception) {
                append("(could not read: ${e.message})\n")
            }
        } else {
            append("(needs Android 11 or newer)\n")
        }
        append("\n--- Exceptions the app saw or caught (newest first) ---\n")
        append(CrashLog.read(this@ProblemsActivity).ifEmpty { "(none)\n" })
    }
}
