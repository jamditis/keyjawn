package com.keyjawn

import org.junit.Test
import org.junit.Assert.*

class CtrlTransitionMessageTest {

    @Test
    fun `armed maps to ctrl armed`() {
        assertEquals("Ctrl armed", ctrlTransitionMessage(CtrlMode.ARMED))
    }

    @Test
    fun `locked maps to ctrl locked`() {
        assertEquals("Ctrl locked", ctrlTransitionMessage(CtrlMode.LOCKED))
    }

    @Test
    fun `off has no transition tooltip`() {
        // OFF fires on every armed-key consumption, so it must not pop a tooltip.
        assertNull(ctrlTransitionMessage(CtrlMode.OFF))
    }
}
