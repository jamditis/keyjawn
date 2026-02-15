package com.keyjawn

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class VoiceInputHandlerTest {

    @Test
    fun `handler can be created`() {
        val handler = VoiceInputHandler(RuntimeEnvironment.getApplication())
        assertNotNull(handler)
    }

    @Test
    fun `isAvailable returns boolean`() {
        val handler = VoiceInputHandler(RuntimeEnvironment.getApplication())
        val result = handler.isAvailable()
        assertNotNull(result)
    }

    @Test
    fun `isListening defaults to false`() {
        val handler = VoiceInputHandler(RuntimeEnvironment.getApplication())
        assertFalse(handler.isListening())
    }

    @Test
    fun `destroy does not throw`() {
        val handler = VoiceInputHandler(RuntimeEnvironment.getApplication())
        handler.destroy()
    }
}
