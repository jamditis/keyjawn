package com.keyjawn

import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View

class SwipeGestureDetector(
    private val onSwipe: (SwipeDirection) -> Boolean
) : View.OnTouchListener {

    enum class SwipeDirection { LEFT, RIGHT, UP, DOWN }

    private var startX = 0f
    private var startY = 0f
    private var velocityTracker: VelocityTracker? = null
    private var tracking = false

    private val minDistanceDp = 60f
    private val minVelocityDp = 200f
    private val minVerticalDistanceDp = 90f
    private val minVerticalVelocityDp = 300f

    // px thresholds resolved once from the touched view's density (constant for
    // the listener's lifetime) instead of re-reading resources on every event.
    private var thresholdsResolved = false
    private var minDistancePx = 0f
    private var minVelocityPx = 0f
    private var minVerticalDistancePx = 0f
    private var minVerticalVelocityPx = 0f

    private fun resolveThresholds(view: View) {
        if (thresholdsResolved) return
        val density = view.context.resources.displayMetrics.density
        minDistancePx = minDistanceDp * density
        minVelocityPx = minVelocityDp * density
        minVerticalDistancePx = minVerticalDistanceDp * density
        minVerticalVelocityPx = minVerticalVelocityDp * density
        thresholdsResolved = true
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        resolveThresholds(view)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                tracking = true
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                return false
            }
            MotionEvent.ACTION_UP -> {
                if (!tracking) return false
                tracking = false
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)

                val dx = event.x - startX
                val dy = event.y - startY
                val vx = velocityTracker?.xVelocity ?: 0f
                val vy = velocityTracker?.yVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null

                val absDx = kotlin.math.abs(dx)
                val absDy = kotlin.math.abs(dy)

                val direction: SwipeDirection?
                if (absDx > absDy && absDx > minDistancePx && kotlin.math.abs(vx) > minVelocityPx) {
                    direction = if (dx < 0) SwipeDirection.LEFT else SwipeDirection.RIGHT
                } else if (absDy > absDx * 1.5f && absDy > minVerticalDistancePx && kotlin.math.abs(vy) > minVerticalVelocityPx) {
                    direction = if (dy < 0) SwipeDirection.UP else SwipeDirection.DOWN
                } else {
                    direction = null
                }

                if (direction != null) {
                    return onSwipe(direction)
                }
                return false
            }
            MotionEvent.ACTION_CANCEL -> {
                tracking = false
                velocityTracker?.recycle()
                velocityTracker = null
                return false
            }
        }
        return false
    }
}
