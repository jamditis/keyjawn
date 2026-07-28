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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AltKeySlideTest {

    private lateinit var surface: QwertyKeyboardView
    private lateinit var keyboard: QwertyKeyboard
    private lateinit var keySender: KeySender
    private lateinit var inputConnection: InputConnection
    private lateinit var haptics: KeyboardHaptics
    private lateinit var activityController: ActivityController<Activity>

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        surface = QwertyKeyboardView(context)
        keySender = mock()
        inputConnection = mock()
        haptics = mock()
        whenever(inputConnection.commitText(any(), any())).thenReturn(true)
        val extraRowManager = mock<ExtraRowManager>()
        whenever(extraRowManager.isCtrlActive()).thenReturn(false)

        val root = FrameLayout(context)
        root.addView(surface)
        activityController = Robolectric.buildActivity(Activity::class.java).setup()
        activityController.get().setContentView(root)
        surface.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(
                surface.suggestedKeyboardHeight,
                View.MeasureSpec.EXACTLY
            )
        )
        surface.layout(0, 0, 1080, surface.suggestedKeyboardHeight)

        keyboard = QwertyKeyboard(
            surface,
            keySender,
            extraRowManager,
            { inputConnection },
            haptics = haptics
        )
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
    }

    @After
    fun tearDown() {
        keyboard.currentSlideSession?.dismiss()
        activityController.pause().stop().destroy()
    }

    private fun dispatch(
        downTime: Long,
        eventTime: Long,
        action: Int,
        x: Float,
        y: Float
    ) {
        val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
        try {
            surface.dispatchTouchEvent(event)
        } finally {
            event.recycle()
        }
    }

    private fun press(row: Int, col: Int): Long {
        val bounds = requireNotNull(surface.keyBounds(row, col))
        val now = SystemClock.uptimeMillis()
        dispatch(now, now, MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY())
        return now
    }

    private fun release(row: Int, col: Int, downTime: Long) {
        val bounds = requireNotNull(surface.keyBounds(row, col))
        dispatch(
            downTime,
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_UP,
            bounds.centerX(),
            bounds.centerY()
        )
    }

    private fun openSlide(row: Int = 1, col: Int = 0): Long {
        val downTime = press(row, col)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(550))
        assertNotNull(keyboard.currentSlideSession)
        return downTime
    }

    @Test
    fun `quick tap before long press sends only the primary character`() {
        val downTime = press(1, 0)
        release(1, 0, downTime)

        verify(keySender).sendChar(inputConnection, "a")
        verify(keySender, never()).sendText(any(), any())
        assertNull(keyboard.currentSlideSession)
    }

    @Test
    fun `slide onto a candidate and release sends that alt`() {
        val downTime = openSlide()
        val session = requireNotNull(keyboard.currentSlideSession)
        val alts = AltKeyMappings.getAlts("a")!!
        val target = session.candidateRectsForTest()[1]
        val location = IntArray(2)
        surface.getLocationOnScreen(location)
        val x = target.centerX() - location[0].toFloat()
        val y = target.centerY() - location[1].toFloat()

        dispatch(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, x, y)
        dispatch(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y)

        verify(keySender).sendText(inputConnection, alts[1])
        verify(haptics).confirm()
        assertFalse(session.isShowing())
        assertNull(keyboard.currentSlideSession)
    }

    @Test
    fun `release outside all candidates dismisses without an alt`() {
        val downTime = openSlide()
        val session = requireNotNull(keyboard.currentSlideSession)

        dispatch(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 1070f, 500f)

        verify(keySender, never()).sendText(any(), any())
        assertFalse(session.isShowing())
        assertNull(keyboard.currentSlideSession)
    }

    @Test
    fun `dragging off before the timer cancels output and popup`() {
        val bounds = requireNotNull(surface.keyBounds(1, 0))
        val downTime = press(1, 0)
        dispatch(
            downTime,
            downTime + 20,
            MotionEvent.ACTION_MOVE,
            bounds.right + 100f,
            bounds.bottom + bounds.height() * 2f
        )
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(550))
        dispatch(
            downTime,
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_UP,
            bounds.right + 100f,
            bounds.bottom + bounds.height() * 2f
        )

        verify(keySender).sendChar(inputConnection, "a")
        verify(keySender, never()).sendText(any(), any())
        assertNull(keyboard.currentSlideSession)
    }

    @Test
    fun `single alt key long press commits directly without a popup`() {
        val downTime = press(2, 3)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(550))
        release(2, 3, downTime)

        verify(keySender).sendText(inputConnection, "ç")
        verify(haptics).confirm()
        assertNull(keyboard.currentSlideSession)
    }

    @Test
    fun `cancel during a slide dismisses without sending`() {
        val downTime = openSlide()
        val session = requireNotNull(keyboard.currentSlideSession)
        dispatch(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, 0f, 0f)

        verify(keySender, never()).sendText(any(), any())
        assertFalse(session.isShowing())
        assertNull(keyboard.currentSlideSession)
    }

    @Test
    fun `a second pointer cannot disturb an open alt slide`() {
        openSlide()
        val session = requireNotNull(keyboard.currentSlideSession)
        val w = requireNotNull(
            surface.keyAt(
                requireNotNull(surface.keyBounds(0, 1)).centerX(),
                requireNotNull(surface.keyBounds(0, 1)).centerY()
            )
        )
        reset(keySender)
        val now = SystemClock.uptimeMillis()

        requireNotNull(surface.listener).onKeyPointer(
            w,
            QwertyKeyboardView.PointerSample(
                pointerId = 1,
                action = MotionEvent.ACTION_DOWN,
                x = w.bounds.centerX(),
                y = w.bounds.centerY(),
                downTime = now,
                eventTime = now
            )
        )

        verifyNoInteractions(keySender)
        assertEquals(-1, session.hoveredIndex)
        assertTrue(session.isShowing())
        assertTrue(keyboard.currentSlideSession === session)
    }

    @Test
    fun `virtual anchor popup keeps wide candidates in the visible frame`() {
        openSlide()
        val session = requireNotNull(keyboard.currentSlideSession)
        val displayFrame = Rect()
        surface.getWindowVisibleDisplayFrame(displayFrame)
        val rects = session.candidateRectsForTest()
        assertEquals(AltKeyMappings.getAlts("a")!!.size, rects.size)

        var previousRight = Int.MIN_VALUE
        for (rect in rects) {
            assertTrue(rect.left >= displayFrame.left)
            assertTrue(rect.right <= displayFrame.right)
            assertTrue(rect.left > previousRight)
            previousRight = rect.right
        }
    }

    @Test
    fun `popup clamps both axes to nonzero visible frames`() {
        assertEquals(
            200,
            AltKeyPopup.clampPopupLeft(150, 300, frameLeft = 200, frameRight = 1000)
        )
        assertEquals(
            700,
            AltKeyPopup.clampPopupLeft(850, 300, frameLeft = 200, frameRight = 1000)
        )
        assertEquals(
            200,
            AltKeyPopup.clampPopupTop(150, 300, frameTop = 200, frameBottom = 1000)
        )
        assertEquals(
            700,
            AltKeyPopup.clampPopupTop(850, 300, frameTop = 200, frameBottom = 1000)
        )
    }

    @Test
    fun `drop down offset round trips through popup screen top`() {
        val anchorScreenY = 1400
        val anchorHeight = 132
        val clampedTop = 900
        val offset =
            AltKeyPopup.dropDownYOffset(clampedTop, anchorScreenY, anchorHeight)
        assertEquals(
            clampedTop,
            AltKeyPopup.popupScreenTop(anchorScreenY, anchorHeight, offset)
        )
    }

    private fun synthSession(rects: List<Rect>): AltKeyPopup.SlideSession {
        val context = RuntimeEnvironment.getApplication()
        val buttons = rects.indices.map { Button(context).apply { text = "x$it" } }
        return AltKeyPopup.SlideSession(buttons, rects, null) { }
    }

    @Test
    fun `session hit testing follows candidate rectangles`() {
        val rects = listOf(
            Rect(0, 0, 40, 44),
            Rect(50, 0, 90, 44),
            Rect(100, 0, 140, 44)
        )
        val session = synthSession(rects)
        assertEquals(1, session.indexAt(70f, 22f))
        assertEquals(-1, session.indexAt(45f, 22f))

        session.onMove(120f, 22f)
        assertEquals(2, session.hoveredIndex)
        assertEquals("x2", session.onRelease(120f, 22f))
    }

    @Test
    fun `dismiss makes a slide session inert`() {
        val session = synthSession(listOf(Rect(0, 0, 40, 44)))
        session.onMove(20f, 22f)
        assertTrue(session.isShowing())

        session.dismiss()

        assertFalse(session.isShowing())
        assertEquals(-1, session.hoveredIndex)
        assertNull(session.onRelease(20f, 22f))
    }
}
