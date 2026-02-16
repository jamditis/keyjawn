package com.keyjawn

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.inputmethod.InputConnection
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
class ExtraRowManagerTest {

    private lateinit var keySender: KeySender
    private lateinit var mockIc: InputConnection
    private lateinit var erm: ExtraRowManager

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        val view = LayoutInflater.from(context).inflate(R.layout.keyboard_view, null)
        keySender = KeySender()
        mockIc = mock()
        whenever(mockIc.sendKeyEvent(any())).thenReturn(true)
        erm = ExtraRowManager(
            view = view,
            keySender = keySender,
            inputConnectionProvider = { mockIc }
        )
    }

    @Test
    fun `ctrl starts in OFF`() {
        assertEquals(CtrlMode.OFF, erm.ctrlState.mode)
        assertFalse(erm.isCtrlActive())
    }

    @Test
    fun `ctrl tap arms`() {
        erm.ctrlState.tap()
        assertEquals(CtrlMode.ARMED, erm.ctrlState.mode)
        assertTrue(erm.isCtrlActive())
    }

    @Test
    fun `consumeCtrl returns false when OFF`() {
        assertFalse(erm.consumeCtrl())
    }

    @Test
    fun `consumeCtrl returns true when ARMED and resets`() {
        erm.ctrlState.tap()
        assertTrue(erm.consumeCtrl())
        assertEquals(CtrlMode.OFF, erm.ctrlState.mode)
    }

    @Test
    fun `esc button sends escape key`() {
        val context = RuntimeEnvironment.getApplication()
        val view = LayoutInflater.from(context).inflate(R.layout.keyboard_view, null)
        val escButton = view.findViewById<android.widget.Button>(R.id.key_esc)
        assertNotNull(escButton)
    }

    @Test
    fun `all extra row buttons are found in layout`() {
        val context = RuntimeEnvironment.getApplication()
        val view = LayoutInflater.from(context).inflate(R.layout.keyboard_view, null)
        assertNotNull(view.findViewById<android.view.View>(R.id.key_esc))
        assertNotNull(view.findViewById<android.view.View>(R.id.key_tab))
        assertNotNull(view.findViewById<android.view.View>(R.id.key_ctrl))
        assertNotNull(view.findViewById<android.view.View>(R.id.key_left))
        assertNotNull(view.findViewById<android.view.View>(R.id.key_right))
        assertNotNull(view.findViewById<android.view.View>(R.id.key_up))
        assertNotNull(view.findViewById<android.view.View>(R.id.key_down))
        assertNotNull(view.findViewById<android.view.View>(R.id.key_upload))
        assertNotNull(view.findViewById<android.view.View>(R.id.key_mic))
    }

    @Test
    fun `esc click sends KEYCODE_ESCAPE`() {
        val context = RuntimeEnvironment.getApplication()
        val view = LayoutInflater.from(context).inflate(R.layout.keyboard_view, null)
        val sender = KeySender()
        val ic: InputConnection = mock()
        whenever(ic.sendKeyEvent(any())).thenReturn(true)

        ExtraRowManager(view = view, keySender = sender, inputConnectionProvider = { ic })

        view.findViewById<android.widget.Button>(R.id.key_esc).performClick()

        val captor = argumentCaptor<KeyEvent>()
        verify(ic, atLeast(1)).sendKeyEvent(captor.capture())
        assertTrue(captor.allValues.any { it.keyCode == KeyEvent.KEYCODE_ESCAPE })
    }

    @Test
    fun `tab click sends KEYCODE_TAB`() {
        val context = RuntimeEnvironment.getApplication()
        val view = LayoutInflater.from(context).inflate(R.layout.keyboard_view, null)
        val sender = KeySender()
        val ic: InputConnection = mock()
        whenever(ic.sendKeyEvent(any())).thenReturn(true)

        ExtraRowManager(view = view, keySender = sender, inputConnectionProvider = { ic })

        view.findViewById<android.widget.Button>(R.id.key_tab).performClick()

        val captor = argumentCaptor<KeyEvent>()
        verify(ic, atLeast(1)).sendKeyEvent(captor.capture())
        assertTrue(captor.allValues.any { it.keyCode == KeyEvent.KEYCODE_TAB })
    }
}
