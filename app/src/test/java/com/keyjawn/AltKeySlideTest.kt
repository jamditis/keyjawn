package com.keyjawn

import android.app.Activity
import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AltKeySlideTest {

    private lateinit var container: LinearLayout
    private lateinit var keySender: KeySender
    private lateinit var extraRowManager: ExtraRowManager
    private lateinit var ic: InputConnection
    private lateinit var keyboard: QwertyKeyboard
    private lateinit var activityController: ActivityController<Activity>

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        keySender = mock()
        ic = mock()
        whenever(ic.commitText(any(), any())).thenReturn(true)
        whenever(ic.sendKeyEvent(any())).thenReturn(true)

        val extraRowView = LinearLayout(context).apply { id = R.id.extra_row }
        addExtraRowButtons(extraRowView, context)
        val parentView = LinearLayout(context).apply { addView(extraRowView) }
        extraRowManager = ExtraRowManager(parentView, keySender, { ic })

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(parentView)
            addView(container)
        }
        activityController = Robolectric.buildActivity(Activity::class.java).setup()
        activityController.get().setContentView(root)

        keyboard = QwertyKeyboard(container, keySender, extraRowManager, { ic })
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)

        // Robolectric does not lay out the view tree automatically, so key views
        // report a 0x0 size and the listener's in-bounds check degenerates. Force
        // a measure/layout pass so the drag-off bounds test exercises real sizes.
        val widthSpec = View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        root.measure(widthSpec, heightSpec)
        root.layout(0, 0, 1080, 1920)
    }

    private fun addExtraRowButtons(parent: LinearLayout, context: android.content.Context) {
        val ids = listOf(
            R.id.key_esc, R.id.key_tab, R.id.key_clipboard, R.id.key_ctrl,
            R.id.key_left, R.id.key_down, R.id.key_up, R.id.key_right,
            R.id.key_upload, R.id.key_mic
        )
        for (id in ids) {
            val btn = Button(context)
            btn.id = id
            parent.addView(btn)
        }
    }

    private fun findButton(view: View): Button =
        if (view is FrameLayout) view.getChildAt(0) as Button else view as Button

    private fun charButtonAt(rowIndex: Int, colIndex: Int): Button {
        val row = container.getChildAt(rowIndex) as LinearLayout
        return findButton(row.getChildAt(colIndex))
    }

    private fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun obtain(action: Int, x: Float, y: Float): MotionEvent {
        val now = SystemClock.uptimeMillis()
        return MotionEvent.obtain(now, now, action, x, y, 0)
    }

    /** Fire the pending long-press runnable on a multi-alt key by advancing the looper. */
    private fun openSlideOn(button: Button) {
        val down = obtain(MotionEvent.ACTION_DOWN, button.width / 2f, button.height / 2f)
        button.dispatchTouchEvent(down)
        down.recycle()
        // The long-press runnable is posted with a 500ms delay; advance time so it runs.
        shadowOf(Looper.getMainLooper()).idleFor(600, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    /**
     * Local-x within the anchor key that maps to candidate index [i]'s center in
     * screen space. The listener converts via anchorScreenX + event.getX, so the
     * inverse is candidateRect.centerX - anchorScreenX.
     */
    private fun localXForCandidate(anchor: View, rect: Rect): Float {
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        return rect.centerX().toFloat() - loc[0]
    }

    private fun localYForCandidate(anchor: View, rect: Rect): Float {
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        return rect.centerY().toFloat() - loc[1]
    }

    // ---- End-to-end gesture tests ----

    @Test
    fun `slide onto candidate index 1 and release sends that alt`() {
        // "a" (row 2 index 0) is a multi-alt key.
        val aButton = charButtonAt(1, 0)
        val alts = AltKeyMappings.getAlts("a")!!
        assertTrue("test needs a multi-alt key", alts.size > 1)

        openSlideOn(aButton)
        val session = keyboard.currentSlideSession
        assertNotNull("long-press on a multi-alt key opens a slide session", session)
        assertTrue(session!!.isShowing())

        val rect1 = session.candidateRectsForTest()[1]
        val moveX = localXForCandidate(aButton, rect1)
        val moveY = localYForCandidate(aButton, rect1)

        val move = obtain(MotionEvent.ACTION_MOVE, moveX, moveY)
        aButton.dispatchTouchEvent(move)
        move.recycle()
        assertEquals(1, session.hoveredIndex)

        val up = obtain(MotionEvent.ACTION_UP, moveX, moveY)
        aButton.dispatchTouchEvent(up)
        up.recycle()

        verify(keySender).sendText(any(), eq(alts[1]))
        assertFalse("popup dismissed after release", session.isShowing())
        assertNull(keyboard.currentSlideSession)
    }

    @Test
    fun `release outside all candidates sends nothing`() {
        val aButton = charButtonAt(1, 0)
        openSlideOn(aButton)
        val session = keyboard.currentSlideSession
        assertNotNull(session)

        // Move far away from any candidate, then lift.
        val move = obtain(MotionEvent.ACTION_MOVE, -10000f, -10000f)
        aButton.dispatchTouchEvent(move)
        move.recycle()
        assertEquals(-1, session!!.hoveredIndex)

        val up = obtain(MotionEvent.ACTION_UP, -10000f, -10000f)
        aButton.dispatchTouchEvent(up)
        up.recycle()

        verify(keySender, never()).sendText(any(), any())
        assertFalse(session.isShowing())
        assertNull(keyboard.currentSlideSession)
    }

    @Test
    fun `quick tap before long-press sends the primary character`() {
        val aButton = charButtonAt(1, 0)
        val down = obtain(MotionEvent.ACTION_DOWN, aButton.width / 2f, aButton.height / 2f)
        val up = obtain(MotionEvent.ACTION_UP, aButton.width / 2f, aButton.height / 2f)
        aButton.dispatchTouchEvent(down)
        aButton.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()

        verify(keySender).sendChar(any(), eq("a"), any())
        assertNull("no popup on a quick tap", keyboard.currentSlideSession)
    }

    @Test
    fun `drag off the key before long-press opens no popup and sends nothing`() {
        val aButton = charButtonAt(1, 0)
        val down = obtain(MotionEvent.ACTION_DOWN, aButton.width / 2f, aButton.height / 2f)
        aButton.dispatchTouchEvent(down)
        down.recycle()

        // Drag far below the key (out of the listener's vertical bounds) BEFORE
        // the long-press timer fires.
        val move = obtain(MotionEvent.ACTION_MOVE, aButton.width / 2f, aButton.height * 5f)
        aButton.dispatchTouchEvent(move)
        move.recycle()

        // Advance past the long-press delay; the runnable was cancelled on drag-off.
        shadowOf(Looper.getMainLooper()).idleFor(600, java.util.concurrent.TimeUnit.MILLISECONDS)

        assertNull("drag-off before long-press opens no popup", keyboard.currentSlideSession)

        val up = obtain(MotionEvent.ACTION_UP, aButton.width / 2f, aButton.height * 5f)
        aButton.dispatchTouchEvent(up)
        up.recycle()

        verify(keySender, never()).sendChar(any(), any(), any())
        verify(keySender, never()).sendText(any(), any())
    }

    @Test
    fun `single-alt key long-press sends the single alt with no popup`() {
        // Row 3 is [Shift, z, x, c, v, b, n, m, Del]; "n" (index 6) has exactly
        // one alt, so the long-press sends it directly without opening a popup.
        val nButton = charButtonAt(2, 6)
        assertEquals("n", nButton.text.toString())
        val alts = AltKeyMappings.getAlts("n")!!
        assertEquals(1, alts.size)

        openSlideOn(nButton)

        assertNull("single-alt key opens no slide popup", keyboard.currentSlideSession)
        verify(keySender).sendText(any(), eq(alts[0]))
    }

    @Test
    fun `a second pointer mid-slide does not change the committed candidate`() {
        val aButton = charButtonAt(1, 0)
        val alts = AltKeyMappings.getAlts("a")!!
        openSlideOn(aButton)
        val session = keyboard.currentSlideSession!!

        val rect1 = session.candidateRectsForTest()[1]
        val x1 = localXForCandidate(aButton, rect1)
        val y1 = localYForCandidate(aButton, rect1)

        // Tracked finger hovers candidate 1.
        val move = obtain(MotionEvent.ACTION_MOVE, x1, y1)
        aButton.dispatchTouchEvent(move)
        move.recycle()
        assertEquals(1, session.hoveredIndex)

        // A second finger arrives over candidate 0 via ACTION_POINTER_DOWN. It
        // must not hijack the hovered candidate.
        val rect0 = session.candidateRectsForTest()[0]
        val x0 = localXForCandidate(aButton, rect0)
        val y0 = localYForCandidate(aButton, rect0)
        val pointerDown = MotionEvent.obtain(
            SystemClock.uptimeMillis(), SystemClock.uptimeMillis(),
            MotionEvent.ACTION_POINTER_DOWN, x0, y0, 0
        )
        aButton.dispatchTouchEvent(pointerDown)
        pointerDown.recycle()
        assertEquals("second pointer must not move the highlight", 1, session.hoveredIndex)

        // The tracked finger lifts, still over candidate 1.
        val up = obtain(MotionEvent.ACTION_UP, x1, y1)
        aButton.dispatchTouchEvent(up)
        up.recycle()

        verify(keySender).sendText(any(), eq(alts[1]))
    }

    // ---- Fix A: popup clamping keeps hit-test rects on the visible buttons ----

    @Test
    fun `clampPopupLeft pins a negative requested left to zero`() {
        // A wide candidate row centered over a narrow edge key requests a negative
        // left; the clamp must pin it to 0 so the visible popup and the rects agree.
        assertEquals(0, AltKeyPopup.clampPopupLeft(requestedLeft = -120, popupWidth = 300, screenWidth = 1080))
    }

    @Test
    fun `clampPopupLeft slides an overflowing-right popup back on screen`() {
        // Requested right edge (1000 + 300 = 1300) overflows the 1080 screen, so the
        // left clamps to screenWidth - popupWidth = 780.
        assertEquals(780, AltKeyPopup.clampPopupLeft(requestedLeft = 1000, popupWidth = 300, screenWidth = 1080))
    }

    @Test
    fun `clampPopupLeft leaves a popup that already fits unchanged`() {
        assertEquals(400, AltKeyPopup.clampPopupLeft(requestedLeft = 400, popupWidth = 300, screenWidth = 1080))
    }

    @Test
    fun `clampPopupLeft pins a popup wider than the screen to zero`() {
        assertEquals(0, AltKeyPopup.clampPopupLeft(requestedLeft = -50, popupWidth = 1200, screenWidth = 1080))
    }

    @Test
    fun `slide popup on a far-left wide-alt key keeps every rect on screen and ordered`() {
        // "a" sits at the far-left column and carries ~6 accented alts, so the
        // centered candidate row is far wider than the key and its requested left
        // goes negative. Before clamping, the rects started off screen and a slide
        // onto the leftmost visible candidate hit-tested the wrong index or -1.
        val aButton = charButtonAt(1, 0)
        val alts = AltKeyMappings.getAlts("a")!!
        assertTrue("test needs a wide-alt key", alts.size >= 4)

        openSlideOn(aButton)
        val session = keyboard.currentSlideSession
        assertNotNull("long-press on a multi-alt key opens a slide session", session)

        val screenWidth = aButton.context.resources.displayMetrics.widthPixels
        val rects = session!!.candidateRectsForTest()
        assertEquals(alts.size, rects.size)

        var previousRight = Int.MIN_VALUE
        for ((i, rect) in rects.withIndex()) {
            assertTrue("rect $i must not start off the left edge: ${rect.left}", rect.left >= 0)
            assertTrue("rect $i must not run off the right edge: ${rect.right} > $screenWidth", rect.right <= screenWidth)
            assertTrue("rects must be left-to-right ordered", rect.left > previousRight)
            previousRight = rect.right
        }

        // The leftmost visible candidate must map to alts[0]. Against the old code
        // the first rect started negative, so its center hit-tested to -1.
        assertEquals(0, session.indexAt(rects[0].centerX().toFloat(), rects[0].centerY().toFloat()))
    }

    // ---- SlideSession unit tests (pure hit-testing) ----

    private fun synthSession(rects: List<Rect>): AltKeyPopup.SlideSession {
        val context = RuntimeEnvironment.getApplication()
        val buttons = rects.indices.map { Button(context).apply { text = "x$it" } }
        return AltKeyPopup.SlideSession(buttons, rects, null) { }
    }

    @Test
    fun `indexAt returns the candidate containing the point`() {
        val rects = listOf(
            Rect(0, 0, 40, 44),
            Rect(50, 0, 90, 44),
            Rect(100, 0, 140, 44)
        )
        val s = synthSession(rects)
        assertEquals(0, s.indexAt(20f, 22f))
        assertEquals(1, s.indexAt(70f, 22f))
        assertEquals(2, s.indexAt(120f, 22f))
    }

    @Test
    fun `indexAt returns -1 outside all candidates`() {
        val rects = listOf(Rect(0, 0, 40, 44), Rect(50, 0, 90, 44))
        val s = synthSession(rects)
        assertEquals(-1, s.indexAt(45f, 22f)) // in the gap
        assertEquals(-1, s.indexAt(-5f, 22f)) // left of all
        assertEquals(-1, s.indexAt(200f, 22f)) // right of all
        assertEquals(-1, s.indexAt(20f, 100f)) // below
    }

    @Test
    fun `onMove updates hoveredIndex and onRelease returns the alt`() {
        val rects = listOf(Rect(0, 0, 40, 44), Rect(50, 0, 90, 44))
        val s = synthSession(rects)
        s.onMove(70f, 22f)
        assertEquals(1, s.hoveredIndex)
        assertEquals("x1", s.onRelease(70f, 22f))
    }

    @Test
    fun `onRelease outside all candidates returns null`() {
        val rects = listOf(Rect(0, 0, 40, 44), Rect(50, 0, 90, 44))
        val s = synthSession(rects)
        s.onMove(20f, 22f)
        assertEquals(0, s.hoveredIndex)
        assertNull(s.onRelease(45f, 22f))
    }

    @Test
    fun `dismiss makes the session stop showing and reports no hover`() {
        val rects = listOf(Rect(0, 0, 40, 44))
        val s = synthSession(rects)
        s.onMove(20f, 22f)
        assertTrue(s.isShowing())
        s.dismiss()
        assertFalse(s.isShowing())
        assertEquals(-1, s.hoveredIndex)
        // After dismissal, further moves are inert.
        s.onMove(20f, 22f)
        assertEquals(-1, s.hoveredIndex)
        assertNull(s.onRelease(20f, 22f))
    }
}
