package com.keyjawn

import android.view.KeyEvent
import org.junit.Test
import org.junit.Assert.*
import org.mockito.kotlin.*

class KeySenderTest {

    @Test
    fun `sendKey sends down and up events`() {
        val ic = mock<android.view.inputmethod.InputConnection>()
        whenever(ic.sendKeyEvent(any())).thenReturn(true)

        val sender = KeySender()
        sender.sendKey(ic, KeyEvent.KEYCODE_TAB)

        val captor = argumentCaptor<KeyEvent>()
        verify(ic, times(2)).sendKeyEvent(captor.capture())
        assertEquals(KeyEvent.ACTION_DOWN, captor.firstValue.action)
        assertEquals(KeyEvent.ACTION_UP, captor.secondValue.action)
    }

    @Test
    fun `sendKey with ctrl sets meta state`() {
        val ic = mock<android.view.inputmethod.InputConnection>()
        whenever(ic.sendKeyEvent(any())).thenReturn(true)

        val sender = KeySender()
        sender.sendKey(ic, KeyEvent.KEYCODE_C, ctrl = true)

        verify(ic).sendKeyEvent(argThat {
            action == KeyEvent.ACTION_DOWN && metaState and KeyEvent.META_CTRL_ON != 0
        })
    }

    @Test
    fun `sendText commits text`() {
        val ic = mock<android.view.inputmethod.InputConnection>()
        whenever(ic.commitText(any(), any())).thenReturn(true)

        val sender = KeySender()
        sender.sendText(ic, "hello")

        verify(ic).commitText("hello", 1)
    }
}
