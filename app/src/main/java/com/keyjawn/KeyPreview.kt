package com.keyjawn

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView

class KeyPreview(
    private val container: FrameLayout,
    private val themeManager: ThemeManager
) {

    val previewView: TextView

    private val density = container.context.resources.displayMetrics.density
    private val previewSize = (48 * density + 0.5f).toInt()

    // The background drawable and bounce animators depend only on the theme,
    // which does not change between presses, so build them once and reuse
    // them on every show() instead of reallocating per keystroke.
    private val bounceAnimator: AnimatorSet

    init {
        previewView = TextView(container.context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(themeManager.keyText())
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(previewSize, previewSize)
            elevation = 12 * density
        }
        previewView.background = buildBackground()

        val scaleX = ObjectAnimator.ofFloat(previewView, "scaleX", 0.8f, 1f)
        val scaleY = ObjectAnimator.ofFloat(previewView, "scaleY", 0.8f, 1f)
        bounceAnimator = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 100
            interpolator = OvershootInterpolator(1.5f)
        }

        container.addView(previewView)
    }

    private fun buildBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8 * density
            setColor(themeManager.keyBg())
            setStroke((1 * density + 0.5f).toInt(), themeManager.keyHint())
        }
    }

    fun show(anchor: View, label: String) {
        bounceAnimator.cancel()

        previewView.text = label

        // Position above the anchor key, centered horizontally
        val anchorLoc = IntArray(2)
        val containerLoc = IntArray(2)
        anchor.getLocationInWindow(anchorLoc)
        container.getLocationInWindow(containerLoc)

        val anchorCenterX = anchorLoc[0] - containerLoc[0] + anchor.width / 2
        val anchorTop = anchorLoc[1] - containerLoc[1]

        val params = previewView.layoutParams as FrameLayout.LayoutParams
        params.leftMargin = anchorCenterX - previewSize / 2
        params.topMargin = anchorTop - previewSize - (4 * density + 0.5f).toInt()
        if (params.leftMargin < 0) params.leftMargin = 0
        if (params.leftMargin + previewSize > container.width) {
            params.leftMargin = container.width - previewSize
        }
        if (params.topMargin < 0) params.topMargin = 0
        previewView.layoutParams = params

        previewView.visibility = View.VISIBLE

        // Bounce animation: scale from 0.8 to 1.0 with overshoot. The animator
        // is built once in init and replayed here, so no objects are allocated
        // per keystroke.
        previewView.scaleX = 0.8f
        previewView.scaleY = 0.8f
        bounceAnimator.start()
    }

    fun hide() {
        bounceAnimator.cancel()
        previewView.visibility = View.GONE
        previewView.alpha = 1f
    }
}
