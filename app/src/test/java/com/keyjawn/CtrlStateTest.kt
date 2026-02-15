package com.keyjawn

import org.junit.Test
import org.junit.Assert.*

class CtrlStateTest {

    @Test
    fun `starts in OFF`() {
        val state = CtrlState()
        assertEquals(CtrlMode.OFF, state.mode)
        assertFalse(state.isActive())
    }

    @Test
    fun `tap from OFF arms`() {
        val state = CtrlState()
        state.tap()
        assertEquals(CtrlMode.ARMED, state.mode)
        assertTrue(state.isActive())
    }

    @Test
    fun `tap from ARMED returns to OFF`() {
        val state = CtrlState()
        state.tap()
        state.tap()
        assertEquals(CtrlMode.OFF, state.mode)
    }

    @Test
    fun `long press from OFF locks`() {
        val state = CtrlState()
        state.longPress()
        assertEquals(CtrlMode.LOCKED, state.mode)
        assertTrue(state.isActive())
    }

    @Test
    fun `tap from LOCKED returns to OFF`() {
        val state = CtrlState()
        state.longPress()
        assertEquals(CtrlMode.LOCKED, state.mode)
        state.tap()
        assertEquals(CtrlMode.OFF, state.mode)
    }

    @Test
    fun `long press from ARMED locks`() {
        val state = CtrlState()
        state.tap()
        state.longPress()
        assertEquals(CtrlMode.LOCKED, state.mode)
    }

    @Test
    fun `long press from LOCKED returns to OFF`() {
        val state = CtrlState()
        state.longPress()
        state.longPress()
        assertEquals(CtrlMode.OFF, state.mode)
    }

    @Test
    fun `consume when ARMED returns true and resets to OFF`() {
        val state = CtrlState()
        state.tap()
        assertTrue(state.consume())
        assertEquals(CtrlMode.OFF, state.mode)
    }

    @Test
    fun `consume when LOCKED returns true and stays LOCKED`() {
        val state = CtrlState()
        state.longPress()
        assertTrue(state.consume())
        assertEquals(CtrlMode.LOCKED, state.mode)
    }

    @Test
    fun `consume when OFF returns false`() {
        val state = CtrlState()
        assertFalse(state.consume())
        assertEquals(CtrlMode.OFF, state.mode)
    }

    @Test
    fun `callback fires on tap`() {
        val state = CtrlState()
        var received: CtrlMode? = null
        state.onStateChanged = { received = it }
        state.tap()
        assertEquals(CtrlMode.ARMED, received)
    }

    @Test
    fun `callback fires on long press`() {
        val state = CtrlState()
        var received: CtrlMode? = null
        state.onStateChanged = { received = it }
        state.longPress()
        assertEquals(CtrlMode.LOCKED, received)
    }

    @Test
    fun `callback fires on consume from ARMED`() {
        val state = CtrlState()
        state.tap()
        var received: CtrlMode? = null
        state.onStateChanged = { received = it }
        state.consume()
        assertEquals(CtrlMode.OFF, received)
    }

    @Test
    fun `callback does not fire on consume from OFF`() {
        val state = CtrlState()
        var callCount = 0
        state.onStateChanged = { callCount++ }
        state.consume()
        assertEquals(0, callCount)
    }

    @Test
    fun `ARMED then consume then tap cycle works`() {
        val state = CtrlState()
        state.tap()
        assertEquals(CtrlMode.ARMED, state.mode)
        state.consume()
        assertEquals(CtrlMode.OFF, state.mode)
        state.tap()
        assertEquals(CtrlMode.ARMED, state.mode)
    }
}
