package com.keyjawn

import android.os.Looper
import android.view.MotionEvent
import android.view.View
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackspaceTouchListenerTest {

    private val context = RuntimeEnvironment.getApplication()
    private val density = context.resources.displayMetrics.density

    private fun makeView(): View = View(context)

    private fun event(action: Int, x: Float = 0f): MotionEvent =
        MotionEvent.obtain(0, 0, action, x, 0f, 0)

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    private class Counts {
        var chars = 0
        var words = 0
    }

    private fun listener(
        counts: Counts,
        initialDelayMs: Long = 100,
        repeatIntervalMs: Long = 50,
        fastIntervalMs: Long = 50,
        accelerateAfter: Int = 100,
        wordDeleteAfter: Int = 3,
        wordIntervalMs: Long = 50
    ) = BackspaceTouchListener(
        onDeleteChar = { counts.chars++ },
        onDeleteWord = { counts.words++ },
        initialDelayMs = initialDelayMs,
        repeatIntervalMs = repeatIntervalMs,
        fastIntervalMs = fastIntervalMs,
        accelerateAfter = accelerateAfter,
        wordDeleteAfter = wordDeleteAfter,
        wordIntervalMs = wordIntervalMs
    )

    @Test
    fun `a tap deletes exactly one character`() {
        val counts = Counts()
        val l = listener(counts)
        val v = makeView()

        l.onTouch(v, event(MotionEvent.ACTION_DOWN))
        l.onTouch(v, event(MotionEvent.ACTION_UP))

        assertEquals(1, counts.chars)
        assertEquals(0, counts.words)
    }

    @Test
    fun `holding repeats per character before escalating`() {
        val counts = Counts()
        val l = listener(counts)
        val v = makeView()

        l.onTouch(v, event(MotionEvent.ACTION_DOWN))
        assertEquals(1, counts.chars)

        idle(100)
        assertEquals(2, counts.chars)
        idle(50)
        assertEquals(3, counts.chars)
        assertEquals(0, counts.words)

        l.onTouch(v, event(MotionEvent.ACTION_UP))
    }

    @Test
    fun `a long hold escalates from characters to whole words`() {
        val counts = Counts()
        val l = listener(counts)
        val v = makeView()

        l.onTouch(v, event(MotionEvent.ACTION_DOWN))
        idle(100) // repeat 1
        idle(50)  // repeat 2
        assertEquals(0, counts.words)

        idle(50) // repeat 3 == wordDeleteAfter
        assertEquals(1, counts.words)
        idle(50)
        assertEquals(2, counts.words)
        // The character gear stopped once the word gear took over.
        assertEquals(3, counts.chars)

        l.onTouch(v, event(MotionEvent.ACTION_UP))
    }

    @Test
    fun `releasing stops the repeat`() {
        val counts = Counts()
        val l = listener(counts)
        val v = makeView()

        l.onTouch(v, event(MotionEvent.ACTION_DOWN))
        l.onTouch(v, event(MotionEvent.ACTION_UP))
        idle(1000)

        assertEquals(1, counts.chars)
        assertEquals(0, counts.words)
    }

    @Test
    fun `cancelling stops the repeat`() {
        val counts = Counts()
        val l = listener(counts)
        val v = makeView()

        l.onTouch(v, event(MotionEvent.ACTION_DOWN))
        l.onTouch(v, event(MotionEvent.ACTION_CANCEL))
        idle(1000)

        assertEquals(1, counts.chars)
    }

    @Test
    fun `swiping left deletes one word per step`() {
        val counts = Counts()
        val l = listener(counts)
        val v = makeView()
        // Defaults: 18dp of slop, then a word per 34dp travelled.
        val slop = 18f * density
        val step = 34f * density

        l.onTouch(v, event(MotionEvent.ACTION_DOWN, x = 0f))
        // Inside the slop the gesture is still a hold, not a swipe.
        l.onTouch(v, event(MotionEvent.ACTION_MOVE, x = -slop / 2))
        assertEquals(0, counts.words)

        l.onTouch(v, event(MotionEvent.ACTION_MOVE, x = -(slop + step)))
        assertEquals(1, counts.words)

        l.onTouch(v, event(MotionEvent.ACTION_MOVE, x = -(slop + step * 3)))
        assertEquals(3, counts.words)

        l.onTouch(v, event(MotionEvent.ACTION_UP))
    }

    @Test
    fun `a swipe suppresses the hold repeat so the two never both delete`() {
        val counts = Counts()
        val l = listener(counts)
        val v = makeView()
        val slop = 18f * density

        l.onTouch(v, event(MotionEvent.ACTION_DOWN, x = 0f))
        l.onTouch(v, event(MotionEvent.ACTION_MOVE, x = -(slop * 2)))
        idle(1000)

        // Only the initial press typed a character; the timer was handed off.
        assertEquals(1, counts.chars)
    }

    @Test
    fun `dragging back does not re-delete on the way out`() {
        val counts = Counts()
        val l = listener(counts)
        val v = makeView()
        val slop = 18f * density
        val step = 34f * density

        l.onTouch(v, event(MotionEvent.ACTION_DOWN, x = 0f))
        l.onTouch(v, event(MotionEvent.ACTION_MOVE, x = -(slop + step * 2)))
        assertEquals(2, counts.words)

        // Pulling back toward the key must not fire again when the finger
        // re-crosses a step it already passed.
        l.onTouch(v, event(MotionEvent.ACTION_MOVE, x = -(slop + step)))
        l.onTouch(v, event(MotionEvent.ACTION_MOVE, x = -(slop + step * 2)))
        assertEquals(2, counts.words)

        l.onTouch(v, event(MotionEvent.ACTION_UP))
    }
}
