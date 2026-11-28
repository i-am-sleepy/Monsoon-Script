package com.monsoonscript.keyboard.autofill

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.autofill.AutofillManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import com.monsoonscript.keyboard.R
import com.monsoonscript.keyboard.vault.VaultBaseActivity
import com.monsoonscript.keyboard.vault.letterChip
import com.monsoonscript.keyboard.vault.textBox
import com.monsoonscript.vault.EntrySummary

/**
 * "Search vault…" from the Autofill dropdown (E1.19, D100): for a login with no entry of its own (or the wrong one), the owner searches the whole vault and
 * taps the entry to fill. Opens only with the vault open (the dropdown shows this item only then); if it locks meanwhile the screen simply closes.
 */
internal class AutofillSearchActivity : VaultBaseActivity() {
    private var form: ParsedForm? = null
    private lateinit var query: EditText
    private lateinit var results: LinearLayout
    private var token = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val f = intent.form()
        if (f == null || savedInstanceState != null) {
            cancel()
            return
        }
        form = f
        val page = ui.page(getString(R.string.autofill_search_title), onBack = { cancel() })
        query = ui.textBox(getString(R.string.autofill_search_hint))
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        with(ui) {
            page.content.add(query)
            page.content.add(results)
        }
        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = search(s?.toString().orEmpty().trim())
        })
        setContentView(page.root)
        query.requestFocus()
        search("")
    }

    /** Locked meanwhile (auto-lock, or the vault process stopped): back to the dropdown's app, the owner starts again from there. */
    override fun onLocked() = cancel()

    private fun search(text: String) {
        val mine = ++token
        if (text.isEmpty()) {
            show(emptyList(), getString(R.string.autofill_search_empty))
            return
        }
        call({ it.search(text) }) { list ->
            if (mine == token) show(list, getString(R.string.autofill_search_none))
        }
    }

    private fun show(list: List<EntrySummary>, whenEmpty: String) {
        results.removeAllViews()
        if (list.isEmpty()) {
            results.addView(ui.note(whenEmpty))
            return
        }
        for (e in list.take(MAX_SHOWN)) results.addView(row(e))
    }

    private fun row(e: EntrySummary): LinearLayout = ui.row().apply {
        val title = e.title.ifBlank { e.url }.ifBlank { getString(R.string.autofill_untitled) }
        addView(ui.letterChip(title.take(1).uppercase()), LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)).apply { rightMargin = ui.dp(16) })
        addView(ui.texts(title, e.username), ui.weighted())
        setOnClickListener { pick(e) }
    }

    private fun pick(e: EntrySummary) {
        val f = form ?: return
        busy.show(getString(R.string.autofill_filling))
        call({ AutofillResponses.filled(it, e.id, f) }) { dataset ->
            busy.hide()
            if (dataset == null) {
                Toast.makeText(this, R.string.autofill_nothing, Toast.LENGTH_LONG).show()
            } else {
                setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
                finish()
            }
        }
    }

    private fun cancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private companion object {
        const val MAX_SHOWN = 30
    }
}
