package com.keyjawn

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

/**
 * Backspace behaviour, which on a phone keyboard is the difference between
 * fixing a typo and retyping a line.
 *
 * Three gears, in the order a finger discovers them:
 *
 *  1. **Tap** deletes one character.
 *  2. **Hold** repeats per character, accelerating, and after
 *     [wordDeleteAfter] ticks escalates to deleting a whole word per tick.
 *     Clearing a mistyped path one character at a time is the slowest thing a
 *     terminal keyboard can ask of you.
 *  3. **Swipe left** deletes a word per [swipeStepDp] travelled, so the user
 *     can dial back exactly as far as they meant to and stop.
 *
 * Deletion is injected rather than performed here: the listener owns the
 * gesture and the timing, [KeySender] owns what a "word" is.
 */
class BackspaceTouchListener(
    private val onDeleteChar: () -> Unit,
    private val onDeleteWord: () -> Unit,
    private val onHaptic: () -> Unit = {},
    private val initialDelayMs: Long = 250L,
    private val repeatIntervalMs: Long = 55L,
    private val fastIntervalMs: Long = 28L,
    private val accelerateAfter: Int = 6,
    private val wordDeleteAfter: Int = 16,
    private val wordIntervalMs: Long = 110L,
    private val swipeStepDp: Float = 34f,
    private val swipeSlopDp: Float = 18f
) : View.OnTouchListener {

    private val handler = Handler(Looper.getMainLooper())
    private var repeatCount = 0

    /** The hold gesture became a horizontal swipe; the timer no longer drives. */
    private var swiping = false
    private var startX = 0f
    private var swipeSteps = 0

    // Resolved once from the touched view's density, which is fixed for the
    // listener's lifetime, instead of re-reading resources on every move.
    private var thresholdsResolved = false
    private var swipeStepPx = 0f
    private var swipeSlopPx = 0f

    private val repeatRunnable = object : Runnable {
        override fun run() {
            repeatCount++
            val interval: Long
            if (repeatCount >= wordDeleteAfter) {
                onDeleteWord()
                // Word deletes are rare and consequential, so each one gets a
                // tick; per-character repeats do not, or a long hold turns into
                // one continuous buzz.
                onHaptic()
                interval = wordIntervalMs
            } else {
                onDeleteChar()
                interval = if (repeatCount > accelerateAfter) fastIntervalMs else repeatIntervalMs
            }
            handler.postDelayed(this, interval)
        }
    }

    private fun resolveThresholds(v: View) {
        if (thresholdsResolved) return
        val density = v.context.resources.displayMetrics.density
        swipeStepPx = swipeStepDp * density
        swipeSlopPx = swipeSlopDp * density
        thresholdsResolved = true
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        resolveThresholds(v)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                repeatCount = 0
                swiping = false
                swipeSteps = 0
                startX = event.rawX
                v.isPressed = true
                onHaptic()
                onDeleteChar()
                handler.postDelayed(repeatRunnable, initialDelayMs)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val travelledLeft = startX - event.rawX
                if (!swiping && travelledLeft > swipeSlopPx) {
                    // The finger is dragging, not resting: hand the gesture to
                    // the swipe and stop the timer so the two never both delete.
                    swiping = true
                    handler.removeCallbacks(repeatRunnable)
                }
                if (swiping) {
                    val steps = ((travelledLeft - swipeSlopPx) / swipeStepPx).toInt()
                    while (swipeSteps < steps) {
                        swipeSteps++
                        onDeleteWord()
                        onHaptic()
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                stop()
                v.isPressed = false
                return true
            }
        }
        return false
    }

    fun stop() {
        swiping = false
        handler.removeCallbacks(repeatRunnable)
    }
}
