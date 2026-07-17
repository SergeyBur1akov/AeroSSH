package com.companyname.aerossh

class AnsiParser(private val buffer: TerminalBuffer) {
    private val escapeBuffer = StringBuilder(64)
    private var inEscape = false
    private var escapeLength = 0

    fun feed(data: ByteArray) {
        for (ch in String(data, Charsets.UTF_8)) {
            if (inEscape) handleEscape(ch)
            else if (ch == '\u001B') { inEscape = true; escapeBuffer.clear(); escapeLength = 0 }
            else buffer.processChar(ch)
        }
    }

    private fun handleEscape(ch: Char) {
        escapeLength++
        if (escapeLength > 256) { inEscape = false; escapeBuffer.clear(); return }
        if (escapeBuffer.isEmpty() && ch == '[') { escapeBuffer.append(ch); return }
        if (escapeBuffer.startsWith("[")) {
            if (ch in 'A'..'Z' || ch in 'a'..'z') { parseCSI(ch); inEscape = false; escapeBuffer.clear() }
            else escapeBuffer.append(ch)
        } else if (escapeBuffer.isEmpty() && ch == ']') { escapeBuffer.append(ch) }
        else if (escapeBuffer.startsWith("]")) {
            if (ch == '\u0007' || (escapeBuffer.endsWith("\u001B") && ch == '\\')) { inEscape = false; escapeBuffer.clear() }
            else escapeBuffer.append(ch)
        } else { inEscape = false; escapeBuffer.clear() }
    }

    private fun parseCSI(finalChar: Char) { buffer.processCSI(escapeBuffer.toString().removePrefix("[").split(';'), finalChar) }
}
