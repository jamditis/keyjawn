package com.keyjawn

import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputConnection

class SpacebarCursorController(
    private val keySender: KeySender,
    private val inputConnectionProvider: () -> InputConnection?,
    private val onTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val hapticView: View,
    private val appPrefs: AppPrefs?
) : View.OnTouchListener {

    private var startX = 0f
    private var startY = 0f
    private var inCursorMode = false
    private var cursorSteps = 0
    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var touchActive = false

    companion object {
        private const val CURSOR_THRESHOLD_DP = 10f
        private const val STEP_SIZE_DP = 8f
        private const val LONG_PRESS_MS = 500L
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        val density = v.context.resources.displayMetrics.density
        val cursorThresholdPx = CURSOR_THRESHOLD_DP * density
        val stepSizePx = STEP_SIZE_DP * density

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                inCursorMode = false
                cursorSteps = 0
                touchActive = true
                v.isPressed = true

                val runnable = Runnable {
                    if (touchActive && !inCursorMode) {
                        touchActive = false
                        v.isPressed = false
                        onLongPress()
                    }
                }
                longPressRunnable = runnable
                handler.postDelayed(runnable, LONG_PRESS_MS)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touchActive) return true
                val dx = event.rawX - startX
                val dy = event.rawY - startY
                val absDx = kotlin.math.abs(dx)
                val absDy = kotlin.math.abs(dy)

                // Only enter cursor mode on horizontal movement that dominates vertical
                if (!inCursorMode && absDx > cursorThresholdPx && absDx > absDy * 2) {
                    inCursorMode = true
                    longPressRunnable?.let { handler.removeCallbacks(it) }
                    longPressRunnable = null
                }

                if (inCursorMode) {
                    val newSteps = (dx / stepSizePx).toInt()
                    val delta = newSteps - cursorSteps
                    if (delta != 0) {
                        val ic = inputConnectionProvider()
                        if (ic != null) {
                            val keyCode = if (delta > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                            val count = kotlin.math.abs(delta)
                            for (i in 0 until count) {
                                keySender.sendKey(ic, keyCode)
                            }
                        }
                        cursorSteps = newSteps
                        if (appPrefs?.isHapticEnabled() != false) {
                            hapticView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }
                longPressRunnable = null
                v.isPressed = false

                if (touchActive && !inCursorMode) {
                    onTap()
                }
                touchActive = false
                inCursorMode = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }
                longPressRunnable = null
                touchActive = false
                inCursorMode = false
                v.isPressed = false
                return true
            }
        }
        return false
    }
}
