package com.companyname.aerossh

import android.graphics.Color

class TerminalBuffer(private val maxLines: Int = 5000) {

    data class Cell(
        val char: Char = ' ',
        var fg: Int = COLOR_DEFAULT_FG,
        var bg: Int = COLOR_DEFAULT_BG,
        var bold: Boolean = false,
        var underline: Boolean = false,
        var reverse: Boolean = false
    )

    data class Line(val cells: MutableList<Cell> = mutableListOf())

    val lines = mutableListOf<Line>()
    var cursorRow = 0
        private set
    var cursorCol = 0
        private set
    var cols = 80
        private set
    var rows = 24
        private set

    private var currentFg = COLOR_DEFAULT_FG
    private var currentBg = COLOR_DEFAULT_BG
    private var currentBold = false
    private var currentUnderline = false
    private var currentReverse = false
    private var savedCursorRow = 0
    private var savedCursorCol = 0
    private var scrollRegionTop = 0
    private var scrollRegionBottom = 23

    // ANSI color palette
    private val ansiColors = intArrayOf(
        Color.rgb(0, 0, 0),         // 0 black
        Color.rgb(204, 0, 0),       // 1 red
        Color.rgb(0, 204, 0),       // 2 green
        Color.rgb(204, 102, 0),     // 3 yellow
        Color.rgb(0, 0, 204),       // 4 blue
        Color.rgb(204, 0, 204),     // 5 magenta
        Color.rgb(0, 204, 204),     // 6 cyan
        Color.rgb(187, 187, 187),   // 7 white
        Color.rgb(85, 85, 85),      // 8 bright black
        Color.rgb(255, 85, 85),     // 9 bright red
        Color.rgb(85, 255, 85),     // 10 bright green
        Color.rgb(255, 255, 85),    // 11 bright yellow
        Color.rgb(85, 85, 255),     // 12 bright blue
        Color.rgb(255, 85, 255),    // 13 bright magenta
        Color.rgb(85, 255, 255),    // 14 bright cyan
        Color.rgb(255, 255, 255),   // 15 bright white
    )

    init {
        ensureLines(24)
    }

    fun resize(newCols: Int, newRows: Int) {
        cols = newCols
        rows = newRows
        scrollRegionBottom = newRows - 1
        ensureLines(newRows)
    }

    private fun ensureLines(count: Int) {
        while (lines.size < count) {
            lines.add(Line(MutableList(cols) { Cell() }))
        }
    }

    fun processBytes(data: ByteArray) {
        val text = String(data, Charsets.UTF_8)
        for (ch in text) {
            processChar(ch)
        }
    }

    private fun processChar(ch: Char) {
        when {
            ch == '\r' -> cursorCol = 0
            ch == '\n' -> newline()
            ch == '\t' -> {
                cursorCol = (cursorCol / 8 + 1) * 8
                if (cursorCol >= cols) cursorCol = cols - 1
            }
            ch == '\b' -> {
                if (cursorCol > 0) cursorCol--
            }
            ch == '\u0007' -> { /* BEL - ignore */ }
            ch == '\u001B' -> { /* ESC handled in CSI parser below */ }
            ch.code < 32 -> { /* other control chars: ignore */ }
            else -> writeChar(ch)
        }
    }

    fun writeChar(ch: Char) {
        ensureLines(cursorRow + 1)
        val line = lines[cursorRow]
        while (line.cells.size <= cursorCol) {
            line.cells.add(Cell())
        }
        var fg = currentFg
        var bg = currentBg
        if (currentReverse) {
            val tmp = fg; fg = bg; bg = tmp
        }
        line.cells[cursorCol] = Cell(ch, fg, bg, currentBold, currentUnderline)
        cursorCol++
        if (cursorCol >= cols) {
            cursorCol = 0
            newline()
        }
    }

    private fun newline() {
        cursorRow++
        if (cursorRow > scrollRegionBottom) {
            scrollUp()
            cursorRow = scrollRegionBottom
        }
        ensureLines(cursorRow + 1)
    }

    private fun scrollUp() {
        val line = if (lines.size > maxLines) {
            lines.removeAt(0)
        } else {
            Line(MutableList(cols) { Cell() })
        }
        lines.add(scrollRegionTop, line)
        ensureLines(rows)
    }

    private fun scrollDown() {
        if (scrollRegionTop < lines.size) {
            lines.removeAt(scrollRegionTop)
        }
        lines.add(scrollRegionBottom, Line(MutableList(cols) { Cell() }))
        ensureLines(rows)
    }

    fun processCSI(params: List<String>, finalChar: Char) {
        val p = params.map { it.toIntOrNull() ?: 0 }
        when (finalChar) {
            'm' -> processSGR(p)
            'H', 'f' -> {
                val row = (p.getOrElse(0) { 1 } - 1).coerceIn(0, rows - 1)
                val col = (p.getOrElse(1) { 1 } - 1).coerceIn(0, cols - 1)
                cursorRow = row
                cursorCol = col
            }
            'A' -> cursorRow = (cursorRow - (p.getOrElse(0) { 1 })).coerceAtLeast(0)
            'B' -> cursorRow = (cursorRow + (p.getOrElse(0) { 1 })).coerceAtMost(rows - 1)
            'C' -> cursorCol = (cursorCol + (p.getOrElse(0) { 1 })).coerceAtMost(cols - 1)
            'D' -> cursorCol = (cursorCol - (p.getOrElse(0) { 1 })).coerceAtLeast(0)
            'J' -> eraseDisplay(p.getOrElse(0) { 0 })
            'K' -> eraseLine(p.getOrElse(0) { 0 })
            'S' -> repeat(p.getOrElse(0) { 1 }) { scrollUp() }
            'T' -> repeat(p.getOrElse(0) { 1 }) { scrollDown() }
            's' -> { savedCursorRow = cursorRow; savedCursorCol = cursorCol }
            'u' -> { cursorRow = savedCursorRow; cursorCol = savedCursorCol }
            'r' -> {
                scrollRegionTop = (p.getOrElse(0) { 1 } - 1).coerceAtLeast(0)
                scrollRegionBottom = (p.getOrElse(1) { rows } - 1).coerceAtMost(rows - 1)
            }
        }
    }

    private fun processSGR(params: List<Int>) {
        if (params.isEmpty()) {
            resetAttributes()
            return
        }
        for (code in params) {
            when (code) {
                0 -> resetAttributes()
                1 -> currentBold = true
                2 -> { /* dim */ }
                4 -> currentUnderline = true
                5 -> { /* blink */ }
                7 -> currentReverse = true
                21 -> currentBold = false
                22 -> currentBold = false
                24 -> currentUnderline = false
                27 -> currentReverse = false
                in 30..37 -> {
                    currentFg = if (currentBold && code < 38) {
                        ansiColors[code - 30 + 8]
                    } else {
                        ansiColors[code - 30]
                    }
                }
                38 -> { /* extended fg — ignore for now */ }
                39 -> currentFg = COLOR_DEFAULT_FG
                in 40..47 -> {
                    currentBg = if (currentBold && code < 48) {
                        ansiColors[code - 40 + 8]
                    } else {
                        ansiColors[code - 40]
                    }
                }
                48 -> { /* extended bg — ignore for now */ }
                49 -> currentBg = COLOR_DEFAULT_BG
                in 90..97 -> currentFg = ansiColors[code - 90 + 8]
                in 100..107 -> currentBg = ansiColors[code - 100 + 8]
            }
        }
    }

    private fun resetAttributes() {
        currentFg = COLOR_DEFAULT_FG
        currentBg = COLOR_DEFAULT_BG
        currentBold = false
        currentUnderline = false
        currentReverse = false
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                eraseLine(0, cursorCol, cols)
                for (r in (cursorRow + 1)..(rows - 1).coerceAtMost(lines.size - 1)) {
                    if (r < lines.size) {
                        lines[r].cells.clear()
                        lines[r].cells.addAll(MutableList(cols) { Cell() })
                    }
                }
            }
            1 -> {
                for (r in 0 until cursorRow.coerceAtMost(lines.size)) {
                    lines[r].cells.clear()
                    lines[r].cells.addAll(MutableList(cols) { Cell() })
                }
                eraseLine(2, 0, cursorCol + 1)
            }
            2, 3 -> {
                for (r in lines.indices) {
                    lines[r].cells.clear()
                    lines[r].cells.addAll(MutableList(cols) { Cell() })
                }
            }
        }
    }

    private fun eraseLine(mode: Int, from: Int = 0, to: Int = cols) {
        if (cursorRow >= lines.size) return
        val line = lines[cursorRow]
        val start = when (mode) {
            0 -> cursorCol
            1 -> 0
            2 -> 0
            else -> cursorCol
        }
        val end = when (mode) {
            0 -> cols
            2 -> cols
            else -> cursorCol + 1
        }
        for (c in start until end) {
            while (line.cells.size <= c) line.cells.add(Cell())
            line.cells[c] = Cell()
        }
    }

    fun getDisplayLines(maxVisible: Int): List<List<Cell>> {
        val result = mutableListOf<List<Cell>>()
        val start = (lines.size - maxVisible).coerceAtLeast(0)
        for (i in start until lines.size) {
            result.add(lines[i].cells.toList())
        }
        return result
    }

    companion object {
        const val COLOR_DEFAULT_FG = 0xFFCCCCCC.toInt()  // Fixed: use toInt() on full value
        const val COLOR_DEFAULT_BG = 0xFF0D1117.toInt()
    }
}
