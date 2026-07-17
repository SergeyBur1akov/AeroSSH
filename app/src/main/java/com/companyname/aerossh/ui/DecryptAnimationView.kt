package com.companyname.aerossh.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class DecryptAnimationView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : View(context, attrs, defStyle) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = Color.parseColor("#58A6FF") }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 12f; color = Color.parseColor("#2058A6FF") }
    private val hexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.parseColor("#3058A6FF") }
    private val hexFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.parseColor("#1558A6FF") }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.parseColor("#58A6FF") }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f; color = Color.parseColor("#8B949E"); textAlign = Paint.Align.CENTER; typeface = Typeface.MONOSPACE }
    private val lockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 72f; textAlign = Paint.Align.CENTER; color = Color.WHITE }
    private val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; shader = LinearGradient(0f, 0f, 0f, 20f, intArrayOf(Color.TRANSPARENT, Color.parseColor("#3058A6FF"), Color.TRANSPARENT), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP) }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f; strokeCap = Paint.Cap.ROUND; color = Color.parseColor("#58A6FF"); alpha = 150 }

    private var rotation = 0f
    private var pulse = 0f
    private var hexPhase = 0f
    private var scanY = 0f
    private var particleAngle = 0f
    private var dotsAlpha = 0f
    private var progress = 0f
    private val particles = Array(8) { ParticleData() }

    private var animator: ValueAnimator? = null

    class ParticleData {
        var angle = 0f; var radius = 0f; var speed = 0f; var size = 0f; var alpha = 0f
    }

    fun startAnimation() {
        resetState()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { update(it.animatedFraction); invalidate() }
            start()
        }
    }

    fun stopAnimation() {
        animator?.cancel(); animator = null
    }

    private fun resetState() {
        rotation = 0f; pulse = 0f; hexPhase = 0f; scanY = 0f; particleAngle = 0f; dotsAlpha = 0f; progress = 0f
        particles.forEachIndexed { i, p ->
            p.angle = (i * 45f); p.radius = 0f; p.speed = 1.5f + (i % 3) * 0.5f
            p.size = 3f + (i % 4); p.alpha = 0.5f + (i % 3) * 0.2f
        }
    }

    private fun update(fraction: Float) {
        rotation += 3f
        pulse = (sin(fraction * Math.PI * 6).toFloat() + 1f) / 2f
        hexPhase += 0.03f
        scanY = if (height > 0) (scanY + 3f) % height else 0f
        particleAngle += 2f
        dotsAlpha = (sin(fraction * Math.PI * 4).toFloat() + 1f) / 2f
        progress = fraction
        particles.forEach { p ->
            p.radius += p.speed
            if (p.radius > min(width, height) * 0.45f) { p.radius = 10f; p.angle += 45f }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f; val baseR = min(cx, cy) * 0.7f

        drawOuterRing(canvas, cx, cy, baseR)
        drawHexGrid(canvas, cx, cy, baseR)
        drawParticles(canvas, cx, cy)
        drawScanLine(canvas, cx, cy, baseR)
        drawProgressArc(canvas, cx, cy, baseR)
        drawCenterLock(canvas, cx, cy)
        drawDecryptText(canvas, cx, cy, baseR)
        drawDataFlowDots(canvas, cx, cy, baseR)
    }

    private fun drawOuterRing(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        glowPaint.alpha = (40 + pulse * 60).toInt()
        canvas.drawCircle(cx, cy, r + 10 + pulse * 5, glowPaint)
        ringPaint.alpha = 180 + (pulse * 75).toInt()
        canvas.drawCircle(cx, cy, r, ringPaint)
        ringPaint.strokeWidth = 2f; ringPaint.alpha = 60
        canvas.drawCircle(cx, cy, r + 20, ringPaint)
        ringPaint.strokeWidth = 4f
    }

    private fun drawHexGrid(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val hexR = r * 0.22f; val rings = 2
        for (ring in 0..rings) {
            val ringR = hexR * 1.8f * (ring + 1)
            val count = 6 * (ring + 1)
            for (i in 0 until count) {
                val angle = Math.toRadians((360.0 / count * i + rotation).toDouble())
                val hx = cx + (ringR * cos(angle)).toFloat()
                val hy = cy + (ringR * sin(angle)).toFloat()
                val fillAlpha = (20 + pulse * 30 * ((ring + 1).toFloat() / (rings + 1))).toInt()
                hexFillPaint.alpha = fillAlpha
                hexPaint.alpha = (40 + pulse * 40).toInt()
                drawHexagon(canvas, hx, hy, hexR * (0.6f + pulse * 0.15f), hexPaint, hexFillPaint)
            }
        }
    }

    private fun drawHexagon(canvas: Canvas, cx: Float, cy: Float, r: Float, stroke: Paint, fill: Paint) {
        val path = Path(); val angle = Math.toRadians(30.0)
        for (i in 0..5) {
            val a = angle + Math.toRadians(60.0 * i)
            val px = cx + (r * cos(a)).toFloat(); val py = cy + (r * sin(a)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close(); canvas.drawPath(path, fill); canvas.drawPath(path, stroke)
    }

    private fun drawParticles(canvas: Canvas, cx: Float, cy: Float) {
        particles.forEach { p ->
            val a = Math.toRadians((p.angle + particleAngle).toDouble())
            val px = cx + (p.radius * cos(a)).toFloat()
            val py = cy + (p.radius * sin(a)).toFloat()
            dotPaint.alpha = (p.alpha * 255 * (1f - p.radius / (min(width, height) * 0.5f))).toInt().coerceIn(0, 255)
            canvas.drawCircle(px, py, p.size, dotPaint)
        }
    }

    private fun drawScanLine(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val shader = LinearGradient(cx - r, scanY, cx + r, scanY,
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#4058A6FF"), Color.parseColor("#6058A6FF"), Color.parseColor("#4058A6FF"), Color.TRANSPARENT),
            floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f), Shader.TileMode.CLAMP)
        scanPaint.shader = shader
        canvas.drawRect(cx - r, scanY - 2, cx + r, scanY + 2, scanPaint)
    }

    private fun drawProgressArc(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val rect = RectF(cx - r - 30, cy - r - 30, cx + r + 30, cy + r + 30)
        arcPaint.alpha = 150; arcPaint.strokeWidth = 3f
        canvas.drawArc(rect, rotation, 120f, false, arcPaint)
        arcPaint.alpha = 80; arcPaint.strokeWidth = 2f
        canvas.drawArc(rect, -rotation, 80f, false, arcPaint)
    }

    private fun drawCenterLock(canvas: Canvas, cx: Float, cy: Float) {
        val lockSize = 36f + pulse * 4f
        lockPaint.textSize = lockSize
        canvas.drawText("\uD83D\uDD12", cx, cy + lockSize * 0.35f, lockPaint)
    }

    private fun drawDecryptText(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val dots = ".".repeat((progress * 9 % 3).toInt() + 1)
        textPaint.textSize = 26f
        canvas.drawText("DECRYPTING$dots", cx, cy + r + 50, textPaint)
    }

    private fun drawDataFlowDots(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val count = 24
        for (i in 0 until count) {
            val angle = Math.toRadians((360.0 / count * i + rotation * 2).toDouble())
            val innerR = r * 0.85f; val outerR = r * 0.95f
            val t = ((i + progress * count) % count) / count.toFloat()
            val rr = innerR + (outerR - innerR) * t
            val px = cx + (rr * cos(angle)).toFloat()
            val py = cy + (rr * sin(angle)).toFloat()
            val alpha = (200 * t).toInt()
            dotPaint.alpha = alpha; dotPaint.color = if (i % 3 == 0) Color.parseColor("#3FB950") else Color.parseColor("#58A6FF")
            canvas.drawCircle(px, py, 2f + t * 2, dotPaint)
        }
        dotPaint.color = Color.parseColor("#58A6FF")
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); stopAnimation() }
}
