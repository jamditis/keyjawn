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

    private data class Pointer(val id: Int, val x: Float, val y: Float)

    /**
     * Build a genuine multi-pointer [MotionEvent] from the given pointers, with
     * [action] (already packed with its actionIndex for pointer up/down events).
     * Single-pointer MotionEvent.obtain calls can never exercise the pointer-id
     * routing, so the hijack and tracked-pointer-up tests must drive real
     * PointerProperties/PointerCoords arrays.
     */
    private fun obtainMulti(action: Int, vararg pointers: Pointer): MotionEvent {
        val now = SystemClock.uptimeMillis()
        val props = Array(pointers.size) { i ->
            MotionEvent.PointerProperties().apply {
                id = pointers[i].id
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        val coords = Array(pointers.size) { i ->
            MotionEvent.PointerCoords().apply {
                x = pointers[i].x
                y = pointers[i].y
                pressure = 1f
                size = 1f
            }
        }
        return MotionEvent.obtain(
            now, now, action, pointers.size, props, coords,
            0, 0, 1f, 1f, 0, 0, 0, 0
        )
    }

    /** Pack an ACTION_POINTER_DOWN/UP action with the pointer slot index that moved. */
    private fun pointerAction(maskedAction: Int, pointerIndex: Int): Int =
        maskedAction or (pointerIndex shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

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
    fun `a second pointer mid-slide does not move or commit the tracked candidate`() {
        // Genuine multi-pointer gesture: pointer 0 (tracked) drives candidate 1; a
        // second pointer goes down over candidate 3 and then MOVES across the row.
        // The activePointerId gate must keep the highlight on pointer 0's candidate.
        // The mutation `findPointerIndex(activePointerId) -> 0` (or dropping the
        // gate) makes this fail, because the second pointer would hijack the hover.
        val aButton = charButtonAt(1, 0)
        val alts = AltKeyMappings.getAlts("a")!!
        assertTrue("test needs at least four alts", alts.size >= 4)
        openSlideOn(aButton)
        val session = keyboard.currentSlideSession!!

        val rect1 = session.candidateRectsForTest()[1]
        val x1 = localXForCandidate(aButton, rect1)
        val y1 = localYForCandidate(aButton, rect1)
        val rect3 = session.candidateRectsForTest()[3]
        val x3 = localXForCandidate(aButton, rect3)
        val y3 = localYForCandidate(aButton, rect3)

        // Tracked finger (pointer 0) hovers candidate 1.
        val move0 = obtain(MotionEvent.ACTION_MOVE, x1, y1)
        aButton.dispatchTouchEvent(move0)
        move0.recycle()
        assertEquals(1, session.hoveredIndex)

        // A second finger (pointer id 1) goes down over candidate 3.
        val pointerDown = obtainMulti(
            pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1),
            Pointer(0, x1, y1), Pointer(1, x3, y3)
        )
        aButton.dispatchTouchEvent(pointerDown)
        pointerDown.recycle()
        assertEquals("second pointer down must not move the highlight", 1, session.hoveredIndex)

        // The second finger MOVES to candidate 0 while pointer 0 holds at candidate
        // 1. The hijacker is placed at slot index 0 and the tracked finger at slot
        // index 1, so a naive `idx = 0` (instead of findPointerIndex(activePointerId))
        // would read the hijacker's coords and move the highlight to candidate 0.
        // The activePointerId gate must keep it on candidate 1.
        val rect0 = session.candidateRectsForTest()[0]
        val x0 = localXForCandidate(aButton, rect0)
        val y0 = localYForCandidate(aButton, rect0)
        val twoFingerMove = obtainMulti(
            MotionEvent.ACTION_MOVE,
            Pointer(1, x0, y0), Pointer(0, x1, y1)
        )
        aButton.dispatchTouchEvent(twoFingerMove)
        twoFingerMove.recycle()
        assertEquals("second pointer move must not hijack the highlight", 1, session.hoveredIndex)

        // The tracked finger lifts, still over candidate 1, as the last pointer.
        val up = obtain(MotionEvent.ACTION_UP, x1, y1)
        aButton.dispatchTouchEvent(up)
        up.recycle()

        verify(keySender).sendText(any(), eq(alts[1]))
    }

    @Test
    fun `ACTION_CANCEL during a slide dismisses without sending`() {
        val aButton = charButtonAt(1, 0)
        openSlideOn(aButton)
        val session = keyboard.currentSlideSession
        assertNotNull(session)
        assertTrue(session!!.isShowing())

        val cancel = obtain(MotionEvent.ACTION_CANCEL, aButton.width / 2f, aButton.height / 2f)
        aButton.dispatchTouchEvent(cancel)
        cancel.recycle()

        verify(keySender, never()).sendText(any(), any())
        assertFalse("cancel dismisses the slide popup", session.isShowing())
        assertNull(keyboard.currentSlideSession)
    }

    @Test
    fun `tracked finger lifting as a non-primary pointer commits its candidate`() {
        // Gates the commit-via-ACTION_POINTER_UP branch: a second finger is down, so
        // the tracked finger lifts as a non-primary pointer (ACTION_POINTER_UP whose
        // actionIndex is the tracked slot). The slide must commit the tracked
        // finger's hovered candidate, not ignore the lift.
        val aButton = charButtonAt(1, 0)
        val alts = AltKeyMappings.getAlts("a")!!
        openSlideOn(aButton)
        val session = keyboard.currentSlideSession!!

        val rect1 = session.candidateRectsForTest()[1]
        val x1 = localXForCandidate(aButton, rect1)
        val y1 = localYForCandidate(aButton, rect1)
        val rect2 = session.candidateRectsForTest()[2]
        val x2 = localXForCandidate(aButton, rect2)
        val y2 = localYForCandidate(aButton, rect2)

        // Tracked finger (pointer 0) hovers candidate 1.
        val move0 = obtain(MotionEvent.ACTION_MOVE, x1, y1)
        aButton.dispatchTouchEvent(move0)
        move0.recycle()
        assertEquals(1, session.hoveredIndex)

        // A second finger (id 1) goes down over candidate 2.
        val pointerDown = obtainMulti(
            pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1),
            Pointer(0, x1, y1), Pointer(1, x2, y2)
        )
        aButton.dispatchTouchEvent(pointerDown)
        pointerDown.recycle()

        // The tracked finger (pointer 0, slot index 0) lifts as a NON-primary
        // pointer while pointer 1 is still down, still over candidate 1.
        val pointerUp = obtainMulti(
            pointerAction(MotionEvent.ACTION_POINTER_UP, 0),
            Pointer(0, x1, y1), Pointer(1, x2, y2)
        )
        aButton.dispatchTouchEvent(pointerUp)
        pointerUp.recycle()

        verify(keySender).sendText(any(), eq(alts[1]))
        assertFalse(session.isShowing())
        assertNull(keyboard.currentSlideSession)
    }

    // ---- Fix A: popup clamping keeps hit-test rects on the visible buttons ----

    @Test
    fun `clampPopupLeft pins a negative requested left to the frame left`() {
        // A wide candidate row centered over a narrow edge key requests a negative
        // left; the clamp must pin it to the frame left so the visible popup and the
        // rects agree. Portrait full-width IME: frame is [0, 1080].
        assertEquals(0, AltKeyPopup.clampPopupLeft(requestedLeft = -120, popupWidth = 300, frameLeft = 0, frameRight = 1080))
    }

    @Test
    fun `clampPopupLeft slides an overflowing-right popup back inside the frame`() {
        // Requested right edge (1000 + 300 = 1300) overflows the [0, 1080] frame, so
        // the left clamps to frameRight - popupWidth = 780.
        assertEquals(780, AltKeyPopup.clampPopupLeft(requestedLeft = 1000, popupWidth = 300, frameLeft = 0, frameRight = 1080))
    }

    @Test
    fun `clampPopupLeft leaves a popup that already fits unchanged`() {
        assertEquals(400, AltKeyPopup.clampPopupLeft(requestedLeft = 400, popupWidth = 300, frameLeft = 0, frameRight = 1080))
    }

    @Test
    fun `clampPopupLeft pins a popup wider than the frame to the frame left`() {
        assertEquals(0, AltKeyPopup.clampPopupLeft(requestedLeft = -50, popupWidth = 1200, frameLeft = 0, frameRight = 1080))
    }

    @Test
    fun `clampPopupLeft pins a left-overflowing popup to a non-zero frame left`() {
        // Split-screen/freeform: the anchor window's visible frame is [200, 1000].
        // A requested left of 150 falls left of the frame, so it pins to frameLeft.
        // The old [0, screenWidth - popupWidth] clamp ignored frameLeft and returned
        // 150 unchanged, leaving the rects off the left edge of the window.
        assertEquals(200, AltKeyPopup.clampPopupLeft(requestedLeft = 150, popupWidth = 300, frameLeft = 200, frameRight = 1000))
    }

    @Test
    fun `clampPopupLeft slides a right-overflowing popup to the non-zero frame right`() {
        // Frame [200, 1000], popupWidth 300: requested left 850 would push the right
        // edge to 1150, past frameRight, so it clamps to frameRight - popupWidth = 700.
        assertEquals(700, AltKeyPopup.clampPopupLeft(requestedLeft = 850, popupWidth = 300, frameLeft = 200, frameRight = 1000))
    }

    @Test
    fun `clampPopupLeft pins a popup wider than a non-zero frame to the frame left`() {
        // Frame [200, 1000] is 800 wide; a 900-wide popup cannot fit, so it pins to
        // frameLeft (200), not 0.
        assertEquals(200, AltKeyPopup.clampPopupLeft(requestedLeft = 250, popupWidth = 900, frameLeft = 200, frameRight = 1000))
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

        // Assert against the anchor window's visible display frame -- the same Rect
        // showAsDropDown clips against -- not raw displayMetrics.widthPixels, so the
        // bounds match what the popup is actually clamped to in any window mode.
        val displayFrame = Rect()
        aButton.getWindowVisibleDisplayFrame(displayFrame)
        val rects = session!!.candidateRectsForTest()
        assertEquals(alts.size, rects.size)

        var previousRight = Int.MIN_VALUE
        for ((i, rect) in rects.withIndex()) {
            assertTrue("rect $i must not start off the left edge: ${rect.left} < ${displayFrame.left}", rect.left >= displayFrame.left)
            assertTrue("rect $i must not run off the right edge: ${rect.right} > ${displayFrame.right}", rect.right <= displayFrame.right)
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
