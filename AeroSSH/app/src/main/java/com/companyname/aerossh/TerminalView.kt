package com.companyname.aerossh

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.max
import kotlin.math.min

class TerminalView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val buffer = TerminalBuffer()
    private val ansiParser = AnsiParser(buffer)

    private val textPaint = Paint().apply {
        typeface = Typeface.MONOSPACE
        textSize = 32f
        isAntiAlias = true
        color = TerminalBuffer.COLOR_DEFAULT_FG
    }

    private val bgPaint = Paint()
    private val cursorPaint = Paint().apply {
        color = Color.rgb(0x58, 0xA6, 0xFF)
        alpha = 180
    }
    private val selectionPaint = Paint().apply {
        color = Color.rgb(0x58, 0xA6, 0xFF)
        alpha = 60
    }

    private var charWidth = 0f
    private var charHeight = 0f
    private var visibleRows = 0
    private var visibleCols = 0
    private var fontSize = 14f

    private var scrollOffset = 0
    private var maxScroll = 0

    var onTextSelected: ((String) -> Unit)? = null
    var onTerminalResize: ((Int, Int) -> Unit)? = null

    // Selection
    private var selStartRow = -1
    private var selStartCol = -1
    private var selEndRow = -1
    private var selEndCol = -1
    private var isSelecting = false

    // Search highlight
    private var highlightRow = -1
    private var highlightCol = -1
    private var highlightLength = 0
    private val highlightPaint = Paint().apply {
        color = Color.rgb(0xFF, 0xD6, 0x00)
        alpha = 120
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val col = (e.x / charWidth).toInt()
            val row = (e.y / charHeight).toInt() + scrollOffset
            if (row in 0 until buffer.lines.size) {
                selectWordAt(row, col)
            }
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            val col = (e.x / charWidth).toInt()
            val row = (e.y / charHeight).toInt() + scrollOffset
            selStartRow = row
            selStartCol = col
            selEndRow = row
            selEndCol = col
            isSelecting = true
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            scrollOffset = (scrollOffset + (distanceY / charHeight).toInt())
                .coerceIn(0, maxScroll)
            invalidate()
            return true
        }
    })

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val newFontSize = (fontSize * detector.scaleFactor).coerceIn(10f, 32f)
            if (newFontSize != fontSize) {
                fontSize = newFontSize
                textPaint.textSize = fontSize * resources.displayMetrics.density
                recalcDimensions()
                invalidate()
            }
            return true
        }
    })

    init {
        textPaint.textSize = fontSize * resources.displayMetrics.density
        post { recalcDimensions() }
    }

    private fun recalcDimensions() {
        charWidth = textPaint.measureText("M")
        charHeight = textPaint.textSize * 1.2f
        if (width > 0 && height > 0) {
            visibleCols = (width / charWidth).toInt().coerceAtLeast(1)
            visibleRows = (height / charHeight).toInt().coerceAtLeast(1)
            buffer.resize(visibleCols, visibleRows)
            maxScroll = max(0, buffer.lines.size - visibleRows)
            scrollOffset = maxScroll
            onTerminalResize?.invoke(visibleCols, visibleRows)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalcDimensions()
    }

    fun feedData(data: ByteArray) {
        ansiParser.feed(data)
        maxScroll = max(0, buffer.lines.size - visibleRows)
        scrollOffset = maxScroll
        postInvalidate()
    }

    fun highlightMatch(row: Int, col: Int, length: Int) {
        highlightRow = row
        highlightCol = col
        highlightLength = length
        // Scroll to show the match
        if (row < scrollOffset || row >= scrollOffset + visibleRows) {
            scrollOffset = max(0, row - visibleRows / 2)
        }
        invalidate()
    }

    fun clearHighlight() {
        highlightRow = -1
        highlightCol = -1
        highlightLength = 0
        invalidate()
    }

    fun getBuffer(): TerminalBuffer = buffer

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Background
        canvas.drawColor(TerminalBuffer.COLOR_DEFAULT_BG)

        val displayLines = buffer.getDisplayLines(visibleRows + scrollOffset)
        val startLine = displayLines.size - visibleRows - scrollOffset
        val endLine = displayLines.size

        for (i in startLine.coerceAtLeast(0) until endLine) {
            val lineIndex = i - startLine
            val line = displayLines[i]
            val y = lineIndex * charHeight

            for (col in line.indices) {
                val cell = line[col]
                val x = col * charWidth

                // Cell background
                val cellBg = if (cell.reverse) cell.fg else cell.bg
                if (cellBg != TerminalBuffer.COLOR_DEFAULT_BG) {
                    bgPaint.color = cellBg
                    canvas.drawRect(x, y, x + charWidth, y + charHeight, bgPaint)
                }

                // Selection highlight
                val absRow = i
                if (isInSelection(absRow, col)) {
                    canvas.drawRect(x, y, x + charWidth, y + charHeight, selectionPaint)
                }

                // Search highlight
                if (highlightRow >= 0 && absRow == highlightRow && col in highlightCol until highlightCol + highlightLength) {
                    canvas.drawRect(x, y, x + charWidth, y + charHeight, highlightPaint)
                }

                // Character
                if (cell.char != ' ') {
                    val fg = if (cell.reverse) cell.bg else cell.fg
                    textPaint.color = if (cell.bold) brighten(fg) else fg
                    textPaint.isFakeBoldText = cell.bold
                    canvas.drawText(cell.char.toString(), x, y + charHeight - (charHeight - textPaint.textSize) / 2, textPaint)
                }

                // Underline
                if (cell.underline) {
                    textPaint.color = if (cell.reverse) cell.bg else cell.fg
                    canvas.drawLine(x, y + charHeight - 2, x + charWidth, y + charHeight - 2, textPaint)
                }
            }
        }

        // Cursor
        val cursorLine = buffer.cursorRow - scrollOffset
        if (cursorLine in 0 until visibleRows) {
            val cx = buffer.cursorCol * charWidth
            val cy = cursorLine * charHeight
            canvas.drawRect(cx, cy, cx + charWidth, cy + charHeight, cursorPaint)
            val cursorChar = if (buffer.cursorCol < displayLines.getOrNull(buffer.cursorRow)?.size ?: 0) {
                displayLines[buffer.cursorRow][buffer.cursorCol].char.toString()
            } else " "
            textPaint.color = TerminalBuffer.COLOR_DEFAULT_BG
            canvas.drawText(cursorChar, cx, cy + charHeight - (charHeight - textPaint.textSize) / 2, textPaint)
        }
    }

    private fun isInSelection(row: Int, col: Int): Boolean {
        if (selStartRow == -1) return false
        val (sr, sc, er, ec) = normalizeSelection()
        return when {
            row < sr || row > er -> false
            row == sr && row == er -> col in sc..ec
            row == sr -> col >= sc
            row == er -> col <= ec
            else -> true
        }
    }

    private fun normalizeSelection(): IntArray {
        val r1 = min(selStartRow, selEndRow)
        val c1 = if (r1 == selStartRow) min(selStartCol, selEndCol) else min(selStartCol, selEndCol)
        val r2 = max(selStartRow, selEndRow)
        val c2 = if (r2 == selStartRow) max(selStartCol, selEndCol) else max(selStartCol, selEndCol)
        return intArrayOf(r1, c1, r2, c2)
    }

    private fun selectWordAt(row: Int, col: Int) {
        if (row >= buffer.lines.size) return
        val line = buffer.lines[row]
        if (col >= line.cells.size) return

        var start = col
        var end = col
        while (start > 0 && line.cells[start - 1].char != ' ') start--
        while (end < line.cells.size - 1 && line.cells[end + 1].char != ' ') end++

        selStartRow = row; selStartCol = start
        selEndRow = row; selEndCol = end
        isSelecting = false

        val text = line.cells.subList(start, end + 1).joinToString("") { it.char.toString() }
        onTextSelected?.invoke(text)
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_UP -> {
                if (isSelecting) {
                    isSelecting = false
                    val (sr, sc, er, ec) = normalizeSelection()
                    val sb = StringBuilder()
                    for (r in sr..er) {
                        val line = buffer.lines.getOrNull(r) ?: continue
                        val from = if (r == sr) sc else 0
                        val to = if (r == er) ec.coerceAtMost(line.cells.size - 1) else line.cells.size - 1
                        for (c in from..to) {
                            sb.append(line.cells.getOrNull(c)?.char ?: ' ')
                        }
                        if (r < er) sb.append('\n')
                    }
                    if (sb.isNotEmpty()) onTextSelected?.invoke(sb.toString())
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (isSelecting) {
                    selEndRow = (event.y / charHeight).toInt() + scrollOffset
                    selEndCol = (event.x / charWidth).toInt()
                    invalidate()
                }
            }
        }
        return true
    }

    private fun brighten(color: Int): Int {
        val r = min(255, Color.red(color) + 60)
        val g = min(255, Color.green(color) + 60)
        val b = min(255, Color.blue(color) + 60)
        return Color.rgb(r, g, b)
    }
}
