package com.keyjawn

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View

class RepeatTouchListener(
    private val initialDelayMs: Long = 400L,
    private val repeatIntervalMs: Long = 50L,
    private val action: () -> Unit
) : View.OnTouchListener {

    private val handler = Handler(Looper.getMainLooper())
    private var isRepeating = false

    private val repeatRunnable = object : Runnable {
        override fun run() {
            action()
            handler.postDelayed(this, repeatIntervalMs)
        }
    }

    override fun onTouch(v: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isRepeating = true
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
