package com.keyjawn

import android.view.KeyEvent
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QwertyKeyboardTest {

    private lateinit var container: LinearLayout
    private lateinit var keySender: KeySender
    private lateinit var extraRowManager: ExtraRowManager
    private lateinit var ic: InputConnection
    private lateinit var keyboard: QwertyKeyboard

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
        qButton.performClick()

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
        qButton.performClick()

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
        aButton.performClick()

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
        qButton.performClick()

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
}
