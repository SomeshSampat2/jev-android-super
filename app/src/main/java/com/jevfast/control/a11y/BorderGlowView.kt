package com.jevfast.control.a11y

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

/**
 * Bluish animated border shown while the agent controls the device.
 * Drawn as a TYPE_ACCESSIBILITY_OVERLAY — non-touchable, non-focusable,
 * and outside the active window so it never enters the captured node tree.
 * One motion: a breathing edge glow.
 */
class BorderGlowView(context: Context) : View(context) {

    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = RIM_WIDTH
        strokeCap = Paint.Cap.ROUND
    }
    private val rimRect = RectF()
    private val rimPath = Path()

    private var breath = 0f   // 0..1 pulse phase

    private val breathAnim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { breath = it.animatedValue as Float; invalidate() }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        setWillNotDraw(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        breathAnim.start()
    }

    override fun onDetachedFromWindow() {
        breathAnim.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        rimRect.set(RIM_WIDTH / 2, RIM_WIDTH / 2, w - RIM_WIDTH / 2, h - RIM_WIDTH / 2)
        rimPath.reset()
        rimPath.addRoundRect(rimRect, CORNER_RADIUS, CORNER_RADIUS, Path.Direction.CW)
        rimPath.close()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        // Breathing edge glow — gradient bands fading inward from each edge.
        val glowAlpha = (GLOW_MIN + (GLOW_MAX - GLOW_MIN) * breath * 255).toInt() shl 24
        drawEdge(canvas, 0f, 0f, 0f, GLOW_DEPTH, w, glowAlpha, false)              // top
        drawEdge(canvas, 0f, h, 0f, h - GLOW_DEPTH, w, glowAlpha, false)           // bottom
        drawEdge(canvas, 0f, 0f, GLOW_DEPTH, 0f, h, glowAlpha, true)               // left
        drawEdge(canvas, w, 0f, w - GLOW_DEPTH, 0f, h, glowAlpha, true)            // right

        // Thin rim line.
        rimPaint.color = RIM_COLOR
        rimPaint.alpha = (150 + 90 * breath).toInt()
        canvas.drawPath(rimPath, rimPaint)
    }

    private fun drawEdge(
        canvas: Canvas,
        x0: Float, y0: Float, x1: Float, y1: Float,
        span: Float, alphaColor: Int, vertical: Boolean,
    ) {
        edgePaint.shader = LinearGradient(
            x0, y0, x1, y1,
            glowColor(alphaColor ushr 24), Color.TRANSPARENT,
            Shader.TileMode.CLAMP,
        )
        if (vertical) canvas.drawRect(
            minOf(x0, x1), 0f, maxOf(x0, x1), span, edgePaint
        ) else canvas.drawRect(
            0f, minOf(y0, y1), span, maxOf(y0, y1), edgePaint
        )
    }

    private fun glowColor(alpha: Int): Int =
        Color.argb(alpha, Color.red(GLOW_COLOR), Color.green(GLOW_COLOR), Color.blue(GLOW_COLOR))

    private companion object {
        const val GLOW_COLOR = 0xFF3B82F6.toInt()      // electric blue
        const val RIM_COLOR = 0xFF60A5FA.toInt()
        const val GLOW_DEPTH = 110f
        const val RIM_WIDTH = 6f
        const val CORNER_RADIUS = 28f
        const val GLOW_MIN = 0.30f
        const val GLOW_MAX = 0.78f
    }
}
