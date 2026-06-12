package com.keyjawn

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
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
class QwertyKeyboardTest {

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

        val extraRowView = LinearLayout(context).apply {
            id = R.id.extra_row
        }
        addExtraRowButtons(extraRowView, context)
        val parentView = LinearLayout(context).apply {
            addView(extraRowView)
        }
        extraRowManager = ExtraRowManager(parentView, keySender, { ic })

        // Host the keyboard container in an attached Activity window so the
        // one-shot shift reset and auto-capitalize relabel -- which the keyboard
        // schedules via container.post {} -- route to the main looper. On a
        // detached view those runnables sit in the view's run queue and never
        // fire, so idleMainLooper() could not drain them. Attaching makes the
        // posted work deterministically drainable.
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(parentView)
            addView(container)
        }
        activityController = Robolectric.buildActivity(Activity::class.java).setup()
        activityController.get().setContentView(root)

        keyboard = QwertyKeyboard(container, keySender, extraRowManager, { ic })
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
    }

    private fun addExtraRowButtons(parent: LinearLayout, context: android.content.Context) {
        val ids = listOf(
            R.id.key_esc, R.id.key_tab, R.id.key_clipboard, R.id.key_ctrl,
            R.id.key_left, R.id.key_down, R.id.key_up, R.id.key_right,
            R.id.key_upload, R.id.key_mic
        )
        for (id in ids) {
            val btn = android.widget.Button(context)
            btn.id = id
            parent.addView(btn)
        }
    }

    /** Find the clickable Button inside a key view (handles FrameLayout wrapping for alt keys). */
    private fun findButton(view: View): Button {
        return if (view is FrameLayout) {
            view.getChildAt(0) as Button
        } else {
            view as Button
        }
    }

    /** Simulate a tap via touch events (ACTION_DOWN + ACTION_UP). Required for keys using OnTouchListener. */
    private fun simulateTap(button: Button) {
        val now = SystemClock.uptimeMillis()
        val x = button.width / 2f
        val y = button.height / 2f
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now, MotionEvent.ACTION_UP, x, y, 0)
        button.dispatchTouchEvent(down)
        button.dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }

    /**
     * Drain the main looper so any container.post {} runnables (the one-shot
     * shift auto-reset and the auto-capitalize relabel) run synchronously before
     * assertions. Robolectric defaults to LooperMode.PAUSED, so without this the
     * posted setLayer() never executes and a label/state assertion races against
     * the scheduler -- the source of the prior attempt's non-deterministic flake.
     */
    private fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** The clickable Button for the character at (rowIndex, colIndex). */
    private fun charButtonAt(rowIndex: Int, colIndex: Int): Button {
        val row = container.getChildAt(rowIndex) as LinearLayout
        return findButton(row.getChildAt(colIndex))
    }

    @Test
    fun `starts on lowercase layer`() {
        assertEquals(KeyboardLayouts.LAYER_LOWER, keyboard.currentLayer)
    }

    @Test
    fun `starts with shift OFF`() {
        assertEquals(ShiftState.OFF, keyboard.shiftState)
    }

    @Test
    fun `setLayer updates current layer`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_SYMBOLS)
        assertEquals(KeyboardLayouts.LAYER_SYMBOLS, keyboard.currentLayer)
    }

    @Test
    fun `setLayer to upper switches to uppercase`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_UPPER)
        assertEquals(KeyboardLayouts.LAYER_UPPER, keyboard.currentLayer)
    }

    @Test
    fun `render creates rows matching layer definition`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        assertEquals(4, container.childCount)
    }

    @Test
    fun `render creates correct key count in row 1`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val row1 = container.getChildAt(0) as LinearLayout
        assertEquals(10, row1.childCount)
    }

    @Test
    fun `render creates correct key count in row 2`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val row2 = container.getChildAt(1) as LinearLayout
        assertEquals(9, row2.childCount)
    }

    @Test
    fun `render creates correct key count in row 3`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val row3 = container.getChildAt(2) as LinearLayout
        assertEquals(9, row3.childCount)
    }

    @Test
    fun `render creates correct key count in row 4`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val row4 = container.getChildAt(3) as LinearLayout
        // sym, comma, space, quickkey, enter
        assertEquals(5, row4.childCount)
    }

    @Test
    fun `tapping a character key sends char`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val row1 = container.getChildAt(0) as LinearLayout
        val qButton = findButton(row1.getChildAt(0))
        simulateTap(qButton)

        verify(keySender).sendChar(any(), eq("q"), any())
    }

    @Test
    fun `tapping space sends space character`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val row4 = container.getChildAt(3) as LinearLayout
        // space is at index 2 (sym, comma, space, period, quickkey, enter)
        val spaceButton = findButton(row4.getChildAt(2))
        spaceButton.performClick()

        verify(keySender).sendChar(any(), eq(" "), any())
    }

    @Test
    fun `tapping enter sends KEYCODE_ENTER`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val row4 = container.getChildAt(3) as LinearLayout
        // enter is at index 4
        val enterButton = findButton(row4.getChildAt(4))
        enterButton.performClick()

        verify(keySender).sendKey(any(), eq(KeyEvent.KEYCODE_ENTER), any())
    }

    @Test
    fun `tapping quick key sends default slash character`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val row4 = container.getChildAt(3) as LinearLayout
        // quickkey is at index 3
        val quickKeyButton = findButton(row4.getChildAt(3))
        quickKeyButton.performClick()

        verify(keySender).sendChar(any(), eq("/"), any())
    }

    @Test
    fun `tapping sym key switches to symbols layer`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val row4 = container.getChildAt(3) as LinearLayout
        val symButton = findButton(row4.getChildAt(0))
        symButton.performClick()

        assertEquals(KeyboardLayouts.LAYER_SYMBOLS, keyboard.currentLayer)
    }

    @Test
    fun `tapping abc key on symbols layer returns to lowercase`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_SYMBOLS)
        val row4 = container.getChildAt(3) as LinearLayout
        val abcButton = findButton(row4.getChildAt(0))
        abcButton.performClick()

        assertEquals(KeyboardLayouts.LAYER_LOWER, keyboard.currentLayer)
    }

    @Test
    fun `shift tap from OFF activates one-shot and switches to upper`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val row3 = container.getChildAt(2) as LinearLayout
        val shiftButton = findButton(row3.getChildAt(0))
        shiftButton.performClick()

        assertEquals(ShiftState.SINGLE, keyboard.shiftState)
        assertEquals(KeyboardLayouts.LAYER_UPPER, keyboard.currentLayer)
    }

    @Test
    fun `one-shot shift reverts to lowercase after typing a character`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val row3 = container.getChildAt(2) as LinearLayout
        val shiftButton = findButton(row3.getChildAt(0))
        shiftButton.performClick()

        assertEquals(ShiftState.SINGLE, keyboard.shiftState)
        assertEquals(KeyboardLayouts.LAYER_UPPER, keyboard.currentLayer)

        val row1 = container.getChildAt(0) as LinearLayout
        val qButton = findButton(row1.getChildAt(0))
        simulateTap(qButton)

        assertEquals(ShiftState.OFF, keyboard.shiftState)
        // Layer reverts via post(), check state directly
    }

    @Test
    fun `shift tap from CAPS_LOCK returns to OFF and lowercase`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val row3 = container.getChildAt(2) as LinearLayout
        val shiftButton = findButton(row3.getChildAt(0))

        // First tap -> SINGLE
        shiftButton.performClick()

        // Quick second tap -> CAPS_LOCK (need to re-get shift button after re-render)
        val row3After = container.getChildAt(2) as LinearLayout
        val shiftAfter = findButton(row3After.getChildAt(0))
        shiftAfter.performClick()

        assertEquals(ShiftState.CAPS_LOCK, keyboard.shiftState)

        // Third tap -> OFF
        val row3Final = container.getChildAt(2) as LinearLayout
        val shiftFinal = findButton(row3Final.getChildAt(0))
        shiftFinal.performClick()

        assertEquals(ShiftState.OFF, keyboard.shiftState)
        assertEquals(KeyboardLayouts.LAYER_LOWER, keyboard.currentLayer)
    }

    @Test
    fun `caps lock stays on upper layer after typing`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val row3 = container.getChildAt(2) as LinearLayout
        val shiftButton = findButton(row3.getChildAt(0))

        // First tap -> SINGLE
        shiftButton.performClick()
        // Quick second tap -> CAPS_LOCK
        val row3After = container.getChildAt(2) as LinearLayout
        val shiftAfter = findButton(row3After.getChildAt(0))
        shiftAfter.performClick()

        assertEquals(ShiftState.CAPS_LOCK, keyboard.shiftState)
        assertEquals(KeyboardLayouts.LAYER_UPPER, keyboard.currentLayer)

        // Type a character
        val row1 = container.getChildAt(0) as LinearLayout
        val aButton = findButton(row1.getChildAt(0))
        simulateTap(aButton)

        // Should stay in CAPS_LOCK
        assertEquals(ShiftState.CAPS_LOCK, keyboard.shiftState)
    }

    @Test
    fun `abc switch resets shift state`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_SYMBOLS)
        val row4 = container.getChildAt(3) as LinearLayout
        val abcButton = findButton(row4.getChildAt(0))
        abcButton.performClick()

        assertEquals(ShiftState.OFF, keyboard.shiftState)
        assertEquals(KeyboardLayouts.LAYER_LOWER, keyboard.currentLayer)
    }

    @Test
    fun `switching layers clears and rebuilds container`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        assertEquals(4, container.childCount)

        keyboard.setLayer(KeyboardLayouts.LAYER_SYMBOLS)
        assertEquals(4, container.childCount)

        val row1 = container.getChildAt(0) as LinearLayout
        val firstKey = findButton(row1.getChildAt(0))
        assertEquals("@", firstKey.text.toString())
    }

    @Test
    fun `character key with ctrl active sends as ctrl combo`() {
        extraRowManager.ctrlState.tap()
        assertTrue(extraRowManager.isCtrlActive())

        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val row1 = container.getChildAt(0) as LinearLayout
        val qButton = findButton(row1.getChildAt(0))
        simulateTap(qButton)

        verify(keySender).sendKey(any(), any(), eq(true))
        verify(keySender, never()).sendText(any(), eq("q"))
    }

    @Test
    fun `uppercase key labels display correctly`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_UPPER)
        val row1 = container.getChildAt(0) as LinearLayout
        val qButton = findButton(row1.getChildAt(0))
        assertEquals("Q", qButton.text.toString())
    }

    @Test
    fun `symbol key labels display correctly`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_SYMBOLS)
        val row2 = container.getChildAt(1) as LinearLayout
        val firstKey = findButton(row2.getChildAt(0))
        assertEquals("*", firstKey.text.toString())
    }

    @Test
    fun `updatePackage with same package does not rebuild the grid`() {
        keyboard.updatePackage("com.example.app")
        val rowBefore = container.getChildAt(0)

        keyboard.updatePackage("com.example.app")
        val rowAfter = container.getChildAt(0)

        // A rebuild calls removeAllViews() and re-inflates, producing new view
        // instances. Unchanged package must reuse the existing grid.
        assertSame(rowBefore, rowAfter)
    }

    @Test
    fun `updatePackage with a different package rebuilds the grid`() {
        keyboard.updatePackage("com.example.app")
        val rowBefore = container.getChildAt(0)

        keyboard.updatePackage("com.other.app")
        val rowAfter = container.getChildAt(0)

        assertNotSame(rowBefore, rowAfter)
    }

    @Test
    fun `ctrlKeyCode maps lowercase letters to their keycodes`() {
        assertEquals(KeyEvent.KEYCODE_A, QwertyKeyboard.ctrlKeyCode('a'))
        assertEquals(KeyEvent.KEYCODE_Z, QwertyKeyboard.ctrlKeyCode('z'))
    }

    @Test
    fun `ctrlKeyCode maps uppercase letters to their keycodes`() {
        assertEquals(KeyEvent.KEYCODE_A, QwertyKeyboard.ctrlKeyCode('A'))
        assertEquals(KeyEvent.KEYCODE_Z, QwertyKeyboard.ctrlKeyCode('Z'))
    }

    @Test
    fun `ctrlKeyCode returns unknown for non-letters`() {
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, QwertyKeyboard.ctrlKeyCode('1'))
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, QwertyKeyboard.ctrlKeyCode('/'))
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, QwertyKeyboard.ctrlKeyCode('@'))
    }

    @Test
    fun `alt-key buttons have no background so the themed frame shows through`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val row2 = container.getChildAt(1) as LinearLayout
        // Row 2 starts with "a", which has accented alt characters and is wrapped
        // in a FrameLayout carrying the key background.
        val aView = row2.getChildAt(0)
        assertTrue("alt key should be wrapped in a FrameLayout", aView is FrameLayout)
        val frame = aView as FrameLayout
        assertNotNull("the wrapping frame carries the key background", frame.background)
        val innerButton = frame.getChildAt(0) as Button
        assertEquals("a", innerButton.text.toString())
        assertNull("the wrapped button must not paint its own background", innerButton.background)
    }

    @Test
    fun `autocorrect flag is cached and not re-read from prefs on every call`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("keyjawn_app_prefs", 0).edit().clear().commit()
        val prefs = AppPrefs(context)
        val kb = QwertyKeyboard(container, keySender, extraRowManager, { ic }, prefs)

        prefs.setAutocorrect("com.example.app", true)
        kb.updatePackage("com.example.app")
        assertTrue(kb.isAutocorrectOn())

        // Flip the pref directly, bypassing the keyboard's boundary events. The
        // cached flag must not observe the change on a plain read.
        prefs.setAutocorrect("com.example.app", false)
        assertTrue("autocorrect read must come from the cache, not prefs", kb.isAutocorrectOn())
    }

    @Test
    fun `updatePackage refreshes the cached autocorrect flag`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("keyjawn_app_prefs", 0).edit().clear().commit()
        val prefs = AppPrefs(context)
        prefs.setAutocorrect("com.app.on", true)
        val kb = QwertyKeyboard(container, keySender, extraRowManager, { ic }, prefs)

        kb.updatePackage("com.app.off")
        assertFalse(kb.isAutocorrectOn())

        kb.updatePackage("com.app.on")
        assertTrue(kb.isAutocorrectOn())
    }

    @Test
    fun `refreshAutocorrect picks up an external toggle for the current package`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("keyjawn_app_prefs", 0).edit().clear().commit()
        val prefs = AppPrefs(context)
        val kb = QwertyKeyboard(container, keySender, extraRowManager, { ic }, prefs)
        kb.updatePackage("com.example.app")
        assertFalse(kb.isAutocorrectOn())

        // The MenuPanel toggle writes the pref then signals the keyboard to
        // refresh through this single update point.
        prefs.toggleAutocorrect("com.example.app")
        kb.refreshAutocorrect()
        assertTrue(kb.isAutocorrectOn())
    }

    // ---- Render guard (#29 / #28 part 1) ----

    @Test
    fun `setLayer to the same layer does not rebuild the grid`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val rowBefore = container.getChildAt(0)

        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val rowAfter = container.getChildAt(0)

        // A rebuild calls removeAllViews() and re-inflates, producing a new row
        // instance. The same-layer guard must reuse the existing grid.
        assertSame(rowBefore, rowAfter)
    }

    @Test
    fun `refreshRender rebuilds even on the same layer`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val rowBefore = container.getChildAt(0)

        keyboard.refreshRender()
        val rowAfter = container.getChildAt(0)

        // The force path must tear down and rebuild so callers that change a
        // keycap without a layer change (spacebar, quick key) see the update.
        assertNotSame(rowBefore, rowAfter)
    }

    @Test
    fun `switching to a different layer rebuilds the grid`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val rowBefore = container.getChildAt(0)

        keyboard.setLayer(KeyboardLayouts.LAYER_SYMBOLS)
        val rowAfter = container.getChildAt(0)

        assertNotSame(rowBefore, rowAfter)
    }

    // ---- In-place shift relabel (#29 / #28 part 2) ----

    @Test
    fun `shift toggle relabels letter keys in place without tearing down the grid`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        val row1Before = container.getChildAt(0) as LinearLayout
        val qButtonBefore = charButtonAt(0, 0)
        val row3Before = container.getChildAt(2) as LinearLayout
        val shiftBefore = findButton(row3Before.getChildAt(0))
        assertEquals("q", qButtonBefore.text.toString())

        keyboard.setLayer(KeyboardLayouts.LAYER_UPPER)

        val row1After = container.getChildAt(0) as LinearLayout
        val qButtonAfter = charButtonAt(0, 0)
        val row3After = container.getChildAt(2) as LinearLayout
        val shiftAfter = findButton(row3After.getChildAt(0))

        // No teardown: every View instance survives the toggle.
        assertSame("row view must be reused", row1Before, row1After)
        assertSame("letter button must be reused", qButtonBefore, qButtonAfter)
        assertSame("shift button must be reused", shiftBefore, shiftAfter)
        // The reused button is relabeled to the upper case.
        assertEquals("Q", qButtonAfter.text.toString())

        // And back the other way, still in place.
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        assertSame(qButtonBefore, charButtonAt(0, 0))
        assertEquals("q", charButtonAt(0, 0).text.toString())
    }

    @Test
    fun `alt hint updates case in place on a shift toggle`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        // Row 2 index 0 is "a", which carries accented alt characters and is
        // wrapped in a FrameLayout with a hint TextView.
        val row2 = container.getChildAt(1) as LinearLayout
        val aFrame = row2.getChildAt(0) as FrameLayout
        val hint = aFrame.getChildAt(1) as TextView
        val lowerHint = hint.text.toString()

        keyboard.setLayer(KeyboardLayouts.LAYER_UPPER)

        // Same TextView instance, relabeled to the uppercase alt.
        val row2After = container.getChildAt(1) as LinearLayout
        val aFrameAfter = row2After.getChildAt(0) as FrameLayout
        assertSame("hint TextView must be reused", hint, aFrameAfter.getChildAt(1))
        assertEquals(lowerHint.uppercase(), hint.text.toString())
        assertNotEquals(lowerHint, hint.text.toString())
    }

    // ---- Emitted case correct after every shift transition ----

    @Test
    fun `one-shot shift emits uppercase then resets and emits lowercase`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        // OFF -> SINGLE
        val shiftButton = charButtonAt(2, 0)
        shiftButton.performClick()
        assertEquals(KeyboardLayouts.LAYER_UPPER, keyboard.currentLayer)

        // Type a character: emits uppercase synchronously.
        simulateTap(charButtonAt(0, 0))
        verify(keySender).sendChar(any(), eq("Q"), any())
        assertEquals(ShiftState.OFF, keyboard.shiftState)

        // The auto-reset is posted; drive the looper so the relabel runs.
        idleMainLooper()
        assertEquals(KeyboardLayouts.LAYER_LOWER, keyboard.currentLayer)
        assertEquals("q", charButtonAt(0, 0).text.toString())

        // The next tap emits lowercase -- label and output stay in sync.
        clearInvocations(keySender)
        simulateTap(charButtonAt(0, 0))
        verify(keySender).sendChar(any(), eq("q"), any())
    }

    @Test
    fun `caps lock keeps emitting uppercase across taps`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        // OFF -> SINGLE -> CAPS_LOCK (two quick taps on the reused shift button).
        charButtonAt(2, 0).performClick()
        charButtonAt(2, 0).performClick()
        assertEquals(ShiftState.CAPS_LOCK, keyboard.shiftState)
        assertEquals(KeyboardLayouts.LAYER_UPPER, keyboard.currentLayer)

        simulateTap(charButtonAt(0, 0))
        verify(keySender).sendChar(any(), eq("Q"), any())
        idleMainLooper()
        // Still uppercase: caps lock does not reset.
        assertEquals(ShiftState.CAPS_LOCK, keyboard.shiftState)
        assertEquals("Q", charButtonAt(0, 0).text.toString())

        clearInvocations(keySender)
        simulateTap(charButtonAt(0, 0))
        verify(keySender).sendChar(any(), eq("Q"), any())
    }

    @Test
    fun `caps lock to OFF returns to lowercase and emits lowercase`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        // OFF -> SINGLE -> CAPS_LOCK -> OFF (three taps).
        charButtonAt(2, 0).performClick()
        charButtonAt(2, 0).performClick()
        charButtonAt(2, 0).performClick()
        assertEquals(ShiftState.OFF, keyboard.shiftState)
        assertEquals(KeyboardLayouts.LAYER_LOWER, keyboard.currentLayer)

        simulateTap(charButtonAt(0, 0))
        verify(keySender).sendChar(any(), eq("q"), any())
    }

    @Test
    fun `label and output stay in sync across repeated shift toggles`() {
        keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
        repeat(5) {
            keyboard.setLayer(KeyboardLayouts.LAYER_UPPER)
            assertEquals("Q", charButtonAt(0, 0).text.toString())
            clearInvocations(keySender)
            simulateTap(charButtonAt(0, 0))
            verify(keySender).sendChar(any(), eq("Q"), any())
            idleMainLooper()

            keyboard.setLayer(KeyboardLayouts.LAYER_LOWER)
            assertEquals("q", charButtonAt(0, 0).text.toString())
            clearInvocations(keySender)
            simulateTap(charButtonAt(0, 0))
            verify(keySender).sendChar(any(), eq("q"), any())
            idleMainLooper()
        }
    }

    // ---- Auto-capitalize coverage (non-blocking gap from the prior attempt) ----

    @Test
    fun `auto-capitalize relabels to upper in place and the next key emits uppercase`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("keyjawn_app_prefs", 0).edit().clear().commit()
        val prefs = AppPrefs(context)
        prefs.setAutocorrect("com.example.app", true)
        val kb = QwertyKeyboard(container, keySender, extraRowManager, { ic }, prefs)
        kb.updatePackage("com.example.app")
        assertTrue(kb.isAutocorrectOn())
        assertEquals(KeyboardLayouts.LAYER_LOWER, kb.currentLayer)

        val qButton = charButtonAt(0, 0)

        // Sentence-ending punctuation before the cursor triggers auto-capitalize
        // on the next space (item 10 path in handleKeyPress).
        whenever(ic.getTextBeforeCursor(2, 0)).thenReturn(". ")
        val row4 = container.getChildAt(3) as LinearLayout
        val spaceButton = findButton(row4.getChildAt(2))
        spaceButton.performClick()

        assertEquals(ShiftState.SINGLE, kb.shiftState)

        // setLayer(LAYER_UPPER) is posted; drive the looper to apply the relabel.
        idleMainLooper()
        assertEquals(KeyboardLayouts.LAYER_UPPER, kb.currentLayer)
        // Relabeled in place -- same button instance, now uppercase.
        assertSame(qButton, charButtonAt(0, 0))
        assertEquals("Q", charButtonAt(0, 0).text.toString())

        // The next character is emitted uppercase.
        clearInvocations(keySender)
        simulateTap(charButtonAt(0, 0))
        verify(keySender).sendChar(any(), eq("Q"), any())
    }
}
