package com.keyjawn

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.TextView
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
    fun `slot 0 defaults to ESC behavior`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = AppPrefs(context)
        assertEquals("keycode:KEYCODE_ESCAPE", prefs.getExtraSlot(0))
    }

    @Test
    fun `slot 1 defaults to Tab behavior`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = AppPrefs(context)
        assertEquals("keycode:KEYCODE_TAB", prefs.getExtraSlot(1))
    }

    @Test
    fun `slot 2 defaults to Ctrl`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = AppPrefs(context)
        assertEquals("ctrl", prefs.getExtraSlot(2))
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

    // --- Critical tooltips bypass the tooltips-disabled preference (Codex 5.4 finding) ---
    // The tooltips toggle is meant to silence transient hints, not operation
    // results and errors. These gate that distinction.

    private fun managerWith(
        prefs: AppPrefs,
        voice: VoiceInputHandler? = null
    ): Pair<View, ExtraRowManager> {
        val context = RuntimeEnvironment.getApplication()
        val view = LayoutInflater.from(context).inflate(R.layout.keyboard_view, null)
        val ic: InputConnection = mock()
        whenever(ic.sendKeyEvent(any())).thenReturn(true)
        val erm = ExtraRowManager(
            view = view,
            keySender = KeySender(),
            inputConnectionProvider = { ic },
            voiceInputHandler = voice,
            appPrefs = prefs
        )
        return view to erm
    }

    @Test
    fun `critical tooltip shows even when tooltips disabled`() {
        val prefs = AppPrefs(RuntimeEnvironment.getApplication())
        prefs.setTooltipsEnabled(false)
        val (view, erm) = managerWith(prefs)

        erm.showTooltip("Uploaded photo.jpg", critical = true)

        val bar = view.findViewById<TextView>(R.id.tooltip_bar)
        assertEquals(View.VISIBLE, bar.visibility)
        assertEquals("Uploaded photo.jpg", bar.text.toString())
    }

    @Test
    fun `non-critical tooltip is suppressed when tooltips disabled`() {
        val prefs = AppPrefs(RuntimeEnvironment.getApplication())
        prefs.setTooltipsEnabled(false)
        val (view, erm) = managerWith(prefs)

        erm.showTooltip("press and hold for options")

        val bar = view.findViewById<TextView>(R.id.tooltip_bar)
        assertEquals(View.GONE, bar.visibility)
    }

    @Test
    fun `non-critical tooltip shows when tooltips enabled`() {
        val prefs = AppPrefs(RuntimeEnvironment.getApplication())
        prefs.setTooltipsEnabled(true)
        val (view, erm) = managerWith(prefs)

        erm.showTooltip("press and hold for options")

        val bar = view.findViewById<TextView>(R.id.tooltip_bar)
        assertEquals(View.VISIBLE, bar.visibility)
    }

    @Test
    fun `voice unavailable tap shows tooltip when tooltips disabled`() {
        val prefs = AppPrefs(RuntimeEnvironment.getApplication())
        prefs.setTooltipsEnabled(false)
        val (view, _) = managerWith(prefs, voice = null)

        view.findViewById<View>(R.id.key_mic).performClick()

        val bar = view.findViewById<TextView>(R.id.tooltip_bar)
        assertEquals(View.VISIBLE, bar.visibility)
        assertEquals("Voice input not available", bar.text.toString())
    }

    @Test
    fun `voice recognition error shows tooltip when tooltips disabled`() {
        val prefs = AppPrefs(RuntimeEnvironment.getApplication())
        prefs.setTooltipsEnabled(false)
        val voice: VoiceInputHandler = mock()
        val (view, _) = managerWith(prefs, voice = voice)

        // The manager registers an anonymous VoiceInputListener on the handler;
        // capture it and drive an error through the real onError path.
        val captor = argumentCaptor<VoiceInputListener>()
        verify(voice).listener = captor.capture()
        captor.lastValue.onError(2)

        val bar = view.findViewById<TextView>(R.id.tooltip_bar)
        assertEquals(View.VISIBLE, bar.visibility)
    }
}
