package com.keyjawn

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
class NumberRowManagerTest {

    private lateinit var mockIc: InputConnection

    @Before
    fun setUp() {
        mockIc = mock()
        whenever(mockIc.commitText(any<CharSequence>(), any())).thenReturn(true)
    }

    private fun createManager(): android.view.View {
        val context = RuntimeEnvironment.getApplication()
        val view = LayoutInflater.from(context).inflate(R.layout.keyboard_view, null)
        NumberRowManager(view, KeySender(), { mockIc })
        return view
    }

    @Test
    fun `all number buttons exist in layout`() {
        val context = RuntimeEnvironment.getApplication()
        val view = LayoutInflater.from(context).inflate(R.layout.keyboard_view, null)
        assertNotNull(view.findViewById<android.view.View>(R.id.num_0))
        assertNotNull(view.findViewById<android.view.View>(R.id.num_1))
        assertNotNull(view.findViewById<android.view.View>(R.id.num_2))
        assertNotNull(view.findViewById<android.view.View>(R.id.num_3))
        assertNotNull(view.findViewById<android.view.View>(R.id.num_4))
        assertNotNull(view.findViewById<android.view.View>(R.id.num_5))
        assertNotNull(view.findViewById<android.view.View>(R.id.num_6))
        assertNotNull(view.findViewById<android.view.View>(R.id.num_7))
        assertNotNull(view.findViewById<android.view.View>(R.id.num_8))
        assertNotNull(view.findViewById<android.view.View>(R.id.num_9))
    }

    @Test
    fun `clicking 1 sends digit 1`() {
        val view = createManager()
        view.findViewById<android.widget.Button>(R.id.num_1).performClick()
        verify(mockIc).commitText("1", 1)
    }

    @Test
    fun `clicking 5 sends digit 5`() {
        val view = createManager()
        view.findViewById<android.widget.Button>(R.id.num_5).performClick()
        verify(mockIc).commitText("5", 1)
    }

    @Test
    fun `clicking 0 sends digit 0`() {
        val view = createManager()
        view.findViewById<android.widget.Button>(R.id.num_0).performClick()
        verify(mockIc).commitText("0", 1)
    }

    @Test
    fun `clicking each digit sends correct character`() {
        val ids = listOf(
            R.id.num_1 to "1", R.id.num_2 to "2", R.id.num_3 to "3",
            R.id.num_4 to "4", R.id.num_5 to "5", R.id.num_6 to "6",
            R.id.num_7 to "7", R.id.num_8 to "8", R.id.num_9 to "9",
            R.id.num_0 to "0"
        )
        for ((id, digit) in ids) {
            val ic: InputConnection = mock()
            whenever(ic.commitText(any<CharSequence>(), any())).thenReturn(true)
            val context = RuntimeEnvironment.getApplication()
            val view = LayoutInflater.from(context).inflate(R.layout.keyboard_view, null)
            NumberRowManager(view, KeySender(), { ic })
            view.findViewById<android.widget.Button>(id).performClick()
            verify(ic).commitText(digit, 1)
        }
    }

    @Test
    fun `long press on 1 sends exclamation mark`() {
        val view = createManager()
        val button = view.findViewById<android.widget.Button>(R.id.num_1)
        val result = button.performLongClick()
        assertTrue("Long click handler should be set", result)
        verify(mockIc).commitText("!", 1)
    }
}
