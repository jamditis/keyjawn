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

    @Test
    fun `destroy is idempotent`() {
        // A rebuild may destroy an already destroyed handler.
        val handler = VoiceInputHandler(RuntimeEnvironment.getApplication())
        handler.destroy()
        handler.destroy()
    }

    @Test
    fun `no session is active before dictation starts`() {
        val handler = VoiceInputHandler(RuntimeEnvironment.getApplication())
        assertFalse(handler.isSessionActive())
        assertFalse(handler.isHoldToTalk())
    }

    @Test
    fun `stopListening on an idle handler does nothing`() {
        // The mic key toggles on session state, so a stop can arrive with no
        // session running -- it must not fire a stop callback into the UI.
        val handler = VoiceInputHandler(RuntimeEnvironment.getApplication())
        var stops = 0
        handler.listener = object : VoiceInputListener {
            override fun onVoiceStart() {}
            override fun onVoiceStop() { stops++ }
            override fun onPartialResult(text: String) {}
            override fun onFinalResult(text: String) {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onError(error: Int) {}
        }

        handler.stopListening()
        assertEquals(0, stops)
        assertFalse(handler.isSessionActive())
    }

    @Test
    fun `cancel on an idle handler is safe`() {
        val handler = VoiceInputHandler(RuntimeEnvironment.getApplication())
        handler.cancel()
        assertFalse(handler.isSessionActive())
        assertFalse(handler.isListening())
    }

    @Test
    fun `starting without mic permission does not open a session`() {
        // Robolectric grants no runtime permissions by default, so this is the
        // first-run path: the handler must route to the permission prompt rather
        // than leave a half-open session behind.
        val handler = VoiceInputHandler(RuntimeEnvironment.getApplication())
        var prompted = false
        handler.onPermissionNeeded = { prompted = true }

        handler.startListening()

        if (handler.isAvailable()) {
            assertTrue(prompted)
            assertFalse(handler.isSessionActive())
        }
    }
}
