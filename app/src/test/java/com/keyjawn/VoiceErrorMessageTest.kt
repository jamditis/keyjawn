package com.keyjawn

import android.speech.SpeechRecognizer
import org.junit.Test
import org.junit.Assert.*

class VoiceErrorMessageTest {

    @Test
    fun `no match maps to didn't catch that`() {
        assertEquals("Didn't catch that", voiceErrorMessage(SpeechRecognizer.ERROR_NO_MATCH))
    }

    @Test
    fun `speech timeout maps to didn't catch that`() {
        assertEquals("Didn't catch that", voiceErrorMessage(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
    }

    @Test
    fun `network maps to no network`() {
        assertEquals("No network", voiceErrorMessage(SpeechRecognizer.ERROR_NETWORK))
    }

    @Test
    fun `network timeout maps to no network`() {
        assertEquals("No network", voiceErrorMessage(SpeechRecognizer.ERROR_NETWORK_TIMEOUT))
    }

    @Test
    fun `recognizer busy maps to busy try again`() {
        assertEquals("Busy, try again", voiceErrorMessage(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
    }

    @Test
    fun `unknown code falls back to generic message`() {
        assertEquals("Voice input failed", voiceErrorMessage(SpeechRecognizer.ERROR_AUDIO))
    }
}
