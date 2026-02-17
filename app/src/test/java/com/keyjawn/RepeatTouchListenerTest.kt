package com.keyjawn

import android.os.Looper
import android.view.MotionEvent
import android.view.View
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RepeatTouchListenerTest {

    private fun makeView(): View = View(RuntimeEnvironment.getApplication())

    private fun downEvent(): MotionEvent =
        MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0f, 0f, 0)

    private fun upEvent(): MotionEvent =
        MotionEvent.obtain(0, 0, MotionEvent.ACTION_UP, 0f, 0f, 0)

    private fun cancelEvent(): MotionEvent =
        MotionEvent.obtain(0, 0, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)

    @Test
    fun `fires action immediately on touch down`() {
        var count = 0
        val listener = RepeatTouchListener(initialDelayMs = 400, repeatIntervalMs = 50) { count++ }
        val view = makeView()

        listener.onTouch(view, downEvent())
        assertEquals(1, count)

        listener.onTouch(view, upEvent())
    }

    @Test
    fun `repeats action after initial delay`() {
        var count = 0
        val listener = RepeatTouchListener(initialDelayMs = 100, repeatIntervalMs = 50) { count++ }
        val view = makeView()

        listener.onTouch(view, downEvent())
        assertEquals(1, count)

        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
        assertEquals(2, count)

        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
        assertEquals(3, count)

        listener.onTouch(view, upEvent())
    }

    @Test
    fun `stops repeating on touch up`() {
        var count = 0
        val listener = RepeatTouchListener(initialDelayMs = 100, repeatIntervalMs = 50) { count++ }
        val view = makeView()

        listener.onTouch(view, downEvent())
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
        assertEquals(2, count)

        listener.onTouch(view, upEvent())
        val countAfterUp = count

        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200))
        assertEquals(countAfterUp, count)
    }

    @Test
    fun `stops repeating on cancel`() {
        var count = 0
        val listener = RepeatTouchListener(initialDelayMs = 100, repeatIntervalMs = 50) { count++ }
        val view = makeView()

        listener.onTouch(view, downEvent())
        listener.onTouch(view, cancelEvent())
        val countAfterCancel = count

        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200))
        assertEquals(countAfterCancel, count)
    }

    @Test
    fun `returns true for down and up events`() {
        val listener = RepeatTouchListener { }
        val view = makeView()

        assertTrue(listener.onTouch(view, downEvent()))
        assertTrue(listener.onTouch(view, upEvent()))
    }

    @Test
    fun `returns false for unhandled events`() {
        val listener = RepeatTouchListener { }
        val view = makeView()
        val moveEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_MOVE, 0f, 0f, 0)

        assertFalse(listener.onTouch(view, moveEvent))
        moveEvent.recycle()
    }

    @Test
    fun `stop cancels pending repeats`() {
        var count = 0
        val listener = RepeatTouchListener(initialDelayMs = 100, repeatIntervalMs = 50) { count++ }
        val view = makeView()

        listener.onTouch(view, downEvent())
        assertEquals(1, count)

        listener.stop()
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200))
        assertEquals(1, count)
    }

    @Test
    fun `accelerates after threshold repeats`() {
        var count = 0
        val listener = RepeatTouchListener(
            initialDelayMs = 100,
            repeatIntervalMs = 50,
            fastIntervalMs = 25,
            accelerateAfter = 3
        ) { count++ }
        val view = makeView()

        listener.onTouch(view, downEvent())
        assertEquals(1, count) // immediate fire

        // After initial delay: first repeat
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
        assertEquals(2, count) // repeatCount=1

        // 3 more repeats at 50ms each (repeatCount 2, 3, 4 -- accelerates after 3)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
        assertEquals(3, count)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
        assertEquals(4, count)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
        assertEquals(5, count) // repeatCount=4, which is > 3, so next uses fast interval

        // Now at fast interval (25ms)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(25))
        assertEquals(6, count)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(25))
        assertEquals(7, count)

        listener.onTouch(view, upEvent())
    }

    @Test
    fun `resets repeat count on new touch down`() {
        var count = 0
        val listener = RepeatTouchListener(
            initialDelayMs = 100,
            repeatIntervalMs = 50,
            fastIntervalMs = 25,
            accelerateAfter = 2
        ) { count++ }
        val view = makeView()

        // First touch: accelerate past threshold
        listener.onTouch(view, downEvent())
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
        // Should be accelerated now
        listener.onTouch(view, upEvent())

        val countBefore = count

        // Second touch: should start fresh at slow interval
        listener.onTouch(view, downEvent())
        assertEquals(countBefore + 1, count) // immediate fire

        // Should use slow interval again (50ms), not fast (25ms)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100))
        assertEquals(countBefore + 2, count)

        // At 25ms nothing should fire (still in slow mode)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(25))
        assertEquals(countBefore + 2, count)

        // At 50ms total, next repeat fires
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(25))
        assertEquals(countBefore + 3, count)

        listener.onTouch(view, upEvent())
    }
}
