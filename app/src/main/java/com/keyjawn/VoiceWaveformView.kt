package com.keyjawn

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class VoiceWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barCount = 5
    private val barHeights = FloatArray(barCount) { 0.2f }
    private val targetHeights = FloatArray(barCount) { 0.2f }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.accent)
        style = Paint.Style.FILL
    }
    private val barWidthDp = 3f
    private val barGapDp = 2f
    private val minBarFraction = 0.15f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 120
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            val fraction = it.animatedFraction
            for (i in barHeights.indices) {
                barHeights[i] = barHeights[i] + (targetHeights[i] - barHeights[i]) * fraction
            }
            invalidate()
        }
    }

    fun updateRms(rmsdB: Float) {
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        for (i in targetHeights.indices) {
            val offset = (1f - kotlin.math.abs(i - barCount / 2f) / (barCount / 2f)) * 0.3f
            targetHeights[i] = (normalized + offset).coerceIn(minBarFraction, 1f)
        }
        animator.cancel()
        animator.start()
    }

    fun reset() {
        for (i in targetHeights.indices) {
            targetHeights[i] = minBarFraction
        }
        animator.cancel()
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val barW = barWidthDp * density
        val gap = barGapDp * density
        val totalW = barCount * barW + (barCount - 1) * gap
        var x = (width - totalW) / 2f
        val maxH = height * 0.8f
        val cy = height / 2f

        for (i in 0 until barCount) {
            val h = maxH * barHeights[i]
            canvas.drawRoundRect(x, cy - h / 2, x + barW, cy + h / 2, barW / 2, barW / 2, paint)
            x += barW + gap
        }
    }
}
