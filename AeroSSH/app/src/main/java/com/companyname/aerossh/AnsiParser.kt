package com.companyname.aerossh

class AnsiParser(private val buffer: TerminalBuffer) {

    private val escapeBuffer = StringBuilder(64)
    private var inEscape = false
    private var escapeLength = 0

    private companion object {
        const val MAX_ESCAPE_LENGTH = 256  // Prevent OOM from malicious escape sequences
    }

    fun feed(data: ByteArray) {
        val text = String(data, Charsets.UTF_8)
        for (ch in text) {
            if (inEscape) {
                handleEscape(ch)
            } else if (ch == '\u001B') {
                inEscape = true
                escapeBuffer.clear()
                escapeLength = 0
            } else {
                buffer.processChar(ch)
            }
        }
    }

    private fun handleEscape(ch: Char) {
        escapeLength++
        if (escapeLength > MAX_ESCAPE_LENGTH) {
            // Discard oversized escape sequence
            inEscape = false
            escapeBuffer.clear()
            return
        }

        if (escapeBuffer.isEmpty() && ch == '[') {
            escapeBuffer.append(ch)
            return
        }

        if (escapeBuffer.startsWith("[")) {
            if (ch in 'A'..'Z' || ch in 'a'..'z') {
                parseCSI(ch)
                inEscape = false
                escapeBuffer.clear()
            } else {
                escapeBuffer.append(ch)
            }
        } else if (escapeBuffer.isEmpty() && ch == ']') {
            escapeBuffer.append(ch)
        } else if (escapeBuffer.startsWith("]")) {
            if (ch == '\u0007' || (escapeBuffer.endsWith("\u001B") && ch == '\\')) {
                inEscape = false
                escapeBuffer.clear()
            } else {
                escapeBuffer.append(ch)
            }
        } else {
            inEscape = false
            escapeBuffer.clear()
        }
    }

    private fun parseCSI(finalChar: Char) {
        val csi = escapeBuffer.toString().removePrefix("[")
        val parts = csi.split(';')
        buffer.processCSI(parts, finalChar)
    }
}
