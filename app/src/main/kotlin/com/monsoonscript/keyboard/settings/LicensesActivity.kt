package com.monsoonscript.keyboard.settings

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.monsoonscript.keyboard.R
import java.io.IOException

/** Licenses and credits (D25 app license, MPL-2.0 Avro parts, CC BY / CC BY-SA word data): plain text assets, shown one after the other. */
class LicensesActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val ui = SettingsUi(this)
        val page = ui.page(getString(R.string.settings_licenses)) { finish() }
        val text = TextView(this).apply {
            textSize = 14f
            setTextColor(ui.primary)
            setTextIsSelectable(true)
            setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(8))
            text = FILES.joinToString("\n\n———\n\n") { read(it) }
        }
        with(ui) { page.content.add(text) }
        setContentView(page.root)
    }

    private fun read(path: String): String = try {
        assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText().trim() }
    } catch (e: IOException) {
        ""
    }

    private companion object {
        val FILES = listOf("licenses/app.txt", "licenses/avro.txt", "licenses/prediction-data.txt", "licenses/emoji.txt")
    }
}
