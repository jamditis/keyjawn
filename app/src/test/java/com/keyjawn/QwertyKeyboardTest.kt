package com.keyjawn

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.FrameLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.atLeastOnce
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
class QwertyKeyboardTest {

    private lateinit var surface: QwertyKeyboardView
    private lateinit var keySender: KeySender
    private lateinit var extraRowManager: ExtraRowManager
    private lateinit var inputConnection: InputConnection
    private lateinit var keyboard: QwertyKeyboard
    private lateinit var activityController: ActivityController<Activity>

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        surface = QwertyKeyboardView(context)
        keySender = mock()
        extraRowManager = mock()
        inputConnection = mock()
        whenever(extraRowManager.isCtrlActive()).thenReturn(false)
        whenever(inputConnection.commitText(any(), any())).thenReturn(true)
        whenever(inputConnection.sendKeyEvent(any())).thenReturn(true)

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
            { inputConnection }
        )
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
    }

    @After
    fun tearDown() {
        activityController.pause().stop().destroy()
    }

    private fun tap(row: Int, col: Int) {
        val bounds = requireNotNull(surface.keyBounds(row, col))
        val now = SystemClock.uptimeMillis()
        dispatch(now, now, MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY())
        dispatch(now, now + 10, MotionEvent.ACTION_UP, bounds.centerX(), bounds.centerY())
    }

    private fun press(row: Int, col: Int, downTime: Long = SystemClock.uptimeMillis()): Long {
        val bounds = requireNotNull(surface.keyBounds(row, col))
        dispatch(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            bounds.centerX(),
            bounds.centerY()
        )
        return downTime
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

    private fun swipeGap(startX: Float, startY: Float, endX: Float, endY: Float) {
        val listener = requireNotNull(surface.listener)
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            now,
            now,
            MotionEvent.ACTION_DOWN,
            startX,
            startY,
            0
        )
        val up = MotionEvent.obtain(
            now,
            now + 100,
            MotionEvent.ACTION_UP,
            endX,
            endY,
            0
        )
        val move = MotionEvent.obtain(
            now,
            now + 50,
            MotionEvent.ACTION_MOVE,
            (startX + endX) / 2f,
            (startY + endY) / 2f,
            0
        )
        try {
            listener.onGapTouch(surface, down)
            listener.onGapTouch(surface, move)
            listener.onGapTouch(surface, up)
        } finally {
            down.recycle()
            move.recycle()
            up.recycle()
        }
    }

    private fun newKeyboard(prefs: AppPrefs): QwertyKeyboard {
        val result = QwertyKeyboard(
            surface,
            keySender,
            extraRowManager,
            { inputConnection },
            prefs
        )
        result.setLayer(KeyboardLayouts.LAYER_LOWER)
        return result
    }

    @Test
    fun `the model starts lower and renders four rows on one surface`() {
        assertEquals(KeyboardLayouts.LAYER_LOWER, keyboard.currentLayer)
        assertEquals(ShiftState.OFF, keyboard.shiftState)
        assertEquals(listOf(10, 9, 9, 5), surface.allCells().groupingBy { it.rowIndex }.eachCount().values.toList())
        assertEquals("q", surface.renderedKey(0, 0)?.label)
    }

    @Test
    fun `character output fires on press and does not repeat on release`() {
        val downTime = press(0, 0)
        verify(keySender).sendChar(inputConnection, "q")
        release(0, 0, downTime)
        verify(keySender).sendChar(inputConnection, "q")
    }

    @Test
    fun `space enter and quick key retain their outputs`() {
        tap(3, 2)
        verify(keySender).sendChar(inputConnection, " ")

        tap(3, 4)
        verify(keySender).sendKey(inputConnection, KeyEvent.KEYCODE_ENTER)

        tap(3, 3)
        verify(keySender).sendChar(inputConnection, "/")
    }

    @Test
    fun `symbol and abc keys switch layers without replacing the surface`() {
        val originalSurface = surface
        tap(3, 0)
        assertEquals(KeyboardLayouts.LAYER_SYMBOLS, keyboard.currentLayer)
        assertEquals("@", surface.renderedKey(0, 0)?.label)

        tap(3, 0)
        assertEquals(KeyboardLayouts.LAYER_LOWER, keyboard.currentLayer)
        assertSame(originalSurface, surface)
    }

    @Test
    fun `one shot shift reuses geometry and emits uppercase before resetting`() {
        val qBounds = surface.keyBounds(0, 0)
        val generation = surface.geometryGeneration

        tap(2, 0)
        assertEquals(ShiftState.SINGLE, keyboard.shiftState)
        assertEquals("Q", surface.renderedKey(0, 0)?.label)
        assertEquals(generation, surface.geometryGeneration)
        assertSame(qBounds, surface.keyBounds(0, 0))

        tap(0, 0)
        verify(keySender).sendChar(inputConnection, "Q")
        assertEquals(ShiftState.OFF, keyboard.shiftState)
        assertEquals("q", surface.renderedKey(0, 0)?.label)
    }

    @Test
    fun `caps lock keeps uppercase output until shift is tapped again`() {
        tap(2, 0)
        tap(2, 0)
        assertEquals(ShiftState.CAPS_LOCK, keyboard.shiftState)

        tap(0, 0)
        tap(0, 1)
        verify(keySender).sendChar(inputConnection, "Q")
        verify(keySender).sendChar(inputConnection, "W")
        assertEquals(ShiftState.CAPS_LOCK, keyboard.shiftState)

        tap(2, 0)
        assertEquals(ShiftState.OFF, keyboard.shiftState)
        assertEquals("q", surface.renderedKey(0, 0)?.label)
    }

    @Test
    fun `ctrl character sends a key combo and consumes ctrl`() {
        whenever(extraRowManager.isCtrlActive()).thenReturn(true)
        tap(1, 0)
        verify(keySender).sendKey(inputConnection, KeyEvent.KEYCODE_A, ctrl = true)
        verify(extraRowManager).consumeCtrl()
    }

    @Test
    fun `adaptive enter and input type quick key update in place`() {
        val enterBounds = surface.keyBounds(3, 4)
        val quickBounds = surface.keyBounds(3, 3)

        keyboard.updateImeAction(EditorInfo.IME_ACTION_SEARCH, 0)
        keyboard.updateInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        keyboard.updatePackage("com.example")

        assertEquals("Search", surface.renderedKey(3, 4)?.label)
        assertEquals("@", surface.renderedKey(3, 3)?.label)
        assertSame(enterBounds, surface.keyBounds(3, 4))
        assertSame(quickBounds, surface.keyBounds(3, 3))
    }

    @Test
    fun `a same package refocus with unchanged editor state keeps geometry`() {
        keyboard.updatePackage("com.example")
        val generation = surface.geometryGeneration
        val qBounds = surface.keyBounds(0, 0)

        keyboard.updatePackage("com.example")

        assertEquals(generation, surface.geometryGeneration)
        assertSame(qBounds, surface.keyBounds(0, 0))
    }

    @Test
    fun `instant output can move character emission back to release`() {
        val prefs = mock<AppPrefs>()
        whenever(prefs.isFastKeyOutput()).thenReturn(false)
        whenever(prefs.isAutocorrectEnabled(any())).thenReturn(false)
        keyboard = newKeyboard(prefs)
        reset(keySender)

        val downTime = press(0, 0)
        verifyNoInteractions(keySender)
        release(0, 0, downTime)
        verify(keySender).sendChar(inputConnection, "q")
    }

    @Test
    fun `two pointer sessions can emit rollover characters before either lifts`() {
        val listener = requireNotNull(surface.listener)
        val q = requireNotNull(surface.keyAt(50f, requireNotNull(surface.keyBounds(0, 0)).centerY()))
        val w = requireNotNull(surface.keyAt(160f, requireNotNull(surface.keyBounds(0, 1)).centerY()))
        val now = SystemClock.uptimeMillis()

        listener.onKeyPointer(
            q,
            QwertyKeyboardView.PointerSample(0, MotionEvent.ACTION_DOWN, q.bounds.centerX(), q.bounds.centerY(), now, now)
        )
        listener.onKeyPointer(
            w,
            QwertyKeyboardView.PointerSample(1, MotionEvent.ACTION_DOWN, w.bounds.centerX(), w.bounds.centerY(), now, now)
        )

        verify(keySender).sendChar(inputConnection, "q")
        verify(keySender).sendChar(inputConnection, "w")
    }

    @Test
    fun `single alt long press takes back the base character and sends the alt`() {
        val downTime = press(2, 3)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(550))
        release(2, 3, downTime)

        verify(keySender).sendChar(inputConnection, "c")
        verify(inputConnection).deleteSurroundingText(1, 0)
        verify(keySender).sendText(inputConnection, "ç")
    }

    @Test
    fun `shifted long press resolves uppercase alts from the pressed label`() {
        tap(2, 0)
        reset(keySender, inputConnection)
        whenever(inputConnection.commitText(any(), any())).thenReturn(true)

        val downTime = press(2, 3)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(550))
        release(2, 3, downTime)

        verify(keySender).sendChar(inputConnection, "C")
        verify(inputConnection).deleteSurroundingText(1, 0)
        verify(keySender).sendText(inputConnection, "Ç")
    }

    @Test
    fun `ctrl output never schedules an alt that cannot take the combo back`() {
        whenever(extraRowManager.isCtrlActive()).thenReturn(true)
        val downTime = press(2, 3)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(550))
        release(2, 3, downTime)

        verify(keySender).sendKey(inputConnection, KeyEvent.KEYCODE_C, ctrl = true)
        verify(keySender, never()).sendText(any(), any())
        verify(inputConnection, never()).deleteSurroundingText(any(), any())
    }

    @Test
    fun `space drag enters cursor mode and moves the cursor`() {
        val bounds = requireNotNull(surface.keyBounds(3, 2))
        val now = SystemClock.uptimeMillis()
        dispatch(now, now, MotionEvent.ACTION_DOWN, bounds.centerX(), bounds.centerY())
        dispatch(now, now + 20, MotionEvent.ACTION_MOVE, bounds.centerX() + 40f, bounds.centerY())
        dispatch(now, now + 30, MotionEvent.ACTION_UP, bounds.centerX() + 40f, bounds.centerY())

        verify(keySender, atLeastOnce()).sendKey(inputConnection, KeyEvent.KEYCODE_DPAD_RIGHT)
        verify(keySender, never()).sendChar(inputConnection, " ")
    }

    @Test
    fun `row gap swipes retain all four actions`() {
        swipeGap(900f, 50f, 100f, 50f)
        verify(keySender).sendKey(inputConnection, KeyEvent.KEYCODE_DEL, ctrl = true)

        swipeGap(100f, 50f, 900f, 50f)
        verify(keySender).sendChar(inputConnection, " ")

        swipeGap(500f, 700f, 500f, 100f)
        assertEquals(KeyboardLayouts.LAYER_SYMBOLS, keyboard.currentLayer)

        swipeGap(500f, 100f, 500f, 700f)
        assertEquals(KeyboardLayouts.LAYER_LOWER, keyboard.currentLayer)
    }

    @Test
    fun `autocap arms at a sentence start and the next key resets it`() {
        val prefs = mock<AppPrefs>()
        whenever(prefs.isFastKeyOutput()).thenReturn(true)
        whenever(prefs.isAutocorrectEnabled("com.example")).thenReturn(true)
        whenever(inputConnection.getTextBeforeCursor(2, 0)).thenReturn("")
        keyboard = newKeyboard(prefs)
        keyboard.updatePackage("com.example")

        keyboard.applyAutoCapitalize()
        assertEquals(ShiftState.SINGLE, keyboard.shiftState)
        assertEquals("Q", surface.renderedKey(0, 0)?.label)

        tap(0, 0)
        verify(keySender).sendChar(inputConnection, "Q")
        assertEquals(ShiftState.OFF, keyboard.shiftState)
    }

    @Test
    fun `backspace tap deletes immediately and hold escalates to words`() {
        whenever(keySender.deleteWordBefore(inputConnection)).thenReturn(true)
        val downTime = press(2, 8)
        verify(keySender).sendKey(inputConnection, KeyEvent.KEYCODE_DEL)

        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(1400))
        release(2, 8, downTime)
        verify(keySender, atLeastOnce()).deleteWordBefore(inputConnection)
    }

    @Test
    fun `sentence and ctrl helpers retain their pure contracts`() {
        assertTrue(QwertyKeyboard.isSentenceStart(null))
        assertTrue(QwertyKeyboard.isSentenceStart("! "))
        assertTrue(QwertyKeyboard.isSentenceStart("x\n"))
        assertFalse(QwertyKeyboard.isSentenceStart("ab"))
        assertEquals(KeyEvent.KEYCODE_A, QwertyKeyboard.ctrlKeyCode('A'))
        assertEquals(KeyEvent.KEYCODE_Z, QwertyKeyboard.ctrlKeyCode('z'))
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, QwertyKeyboard.ctrlKeyCode('/'))
    }
}
