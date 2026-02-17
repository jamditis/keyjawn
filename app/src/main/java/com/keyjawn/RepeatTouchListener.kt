package com.keyjawn

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

class RepeatTouchListener(
    private val initialDelayMs: Long = 250L,
    private val repeatIntervalMs: Long = 50L,
    private val fastIntervalMs: Long = 25L,
    private val accelerateAfter: Int = 5,
    private val action: () -> Unit
) : View.OnTouchListener {

    private val handler = Handler(Looper.getMainLooper())
    private var isRepeating = false
    private var repeatCount = 0

    private val repeatRunnable = object : Runnable {
        override fun run() {
            action()
            repeatCount++
            val interval = if (repeatCount > accelerateAfter) fastIntervalMs else repeatIntervalMs
            handler.postDelayed(this, interval)
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isRepeating = true
                repeatCount = 0
                action()
                handler.postDelayed(repeatRunnable, initialDelayMs)
                v.isPressed = true
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isRepeating = false
                handler.removeCallbacks(repeatRunnable)
                v.isPressed = false
                return true
            }
        }
        return false
    }

    fun stop() {
        isRepeating = false
        handler.removeCallbacks(repeatRunnable)
    }
}
