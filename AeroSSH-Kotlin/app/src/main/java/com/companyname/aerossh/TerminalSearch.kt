package com.companyname.aerossh

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.lang.ref.WeakReference

class TerminalSearch(private val contextRef: WeakReference<Context>, private val terminalView: TerminalView) {
    private var dialog: BottomSheetDialog? = null; private var searchInput: EditText? = null; private var resultText: TextView? = null
    private var currentQuery = ""; private var currentMatch = -1; private var matches = mutableListOf<Pair<Int, Int>>()

    fun show() {
        val ctx = contextRef.get() ?: return; dialog = BottomSheetDialog(ctx)
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_terminal_search, null); dialog?.setContentView(view)
        searchInput = view.findViewById(R.id.searchInput); resultText = view.findViewById(R.id.resultText)
        view.findViewById<ImageButton>(R.id.btnPrev).setOnClickListener { findPrev() }
        view.findViewById<ImageButton>(R.id.btnNext).setOnClickListener { findNext() }
        view.findViewById<ImageButton>(R.id.btnClose).setOnClickListener { dismiss() }
        searchInput?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { currentQuery = s?.toString() ?: ""; search() }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        dialog?.show()
    }

    private fun search() { matches.clear(); currentMatch = -1; if (currentQuery.isEmpty()) { resultText?.text = ""; terminalView.clearHighlight(); return }
        for (row in terminalView.getBuffer().lines.indices) { val lt = terminalView.getBuffer().lines[row].cells.joinToString("") { it.char.toString() }; var idx = 0; while (true) { val f = lt.indexOf(currentQuery, idx, ignoreCase = true); if (f == -1) break; matches.add(row to f); idx = f + 1 } }
        resultText?.text = if (matches.isEmpty()) "No matches" else "1/${matches.size}"; if (matches.isNotEmpty()) { currentMatch = 0; highlightCurrent() } }

    private fun findNext() { if (matches.isEmpty()) return; currentMatch = (currentMatch + 1) % matches.size; resultText?.text = "${currentMatch + 1}/${matches.size}"; highlightCurrent() }
    private fun findPrev() { if (matches.isEmpty()) return; currentMatch = (currentMatch - 1 + matches.size) % matches.size; resultText?.text = "${currentMatch + 1}/${matches.size}"; highlightCurrent() }
    private fun highlightCurrent() { if (currentMatch < 0 || currentMatch >= matches.size) return; val (r, c) = matches[currentMatch]; terminalView.highlightMatch(r, c, currentQuery.length) }
    fun dismiss() { dialog?.dismiss(); dialog = null; terminalView.clearHighlight() }
}
