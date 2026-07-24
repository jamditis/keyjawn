package com.keyjawn

import android.speech.SpeechRecognizer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoiceSessionPolicyTest {

    // ---- After a transcribed utterance ----

    @Test
    fun `a continuous session re-arms for the next sentence`() {
        assertEquals(
            VoiceNextStep.Restart,
            VoiceSessionPolicy.afterUtterance(sessionActive = true, continuous = true, emptyRounds = 0)
        )
    }

    @Test
    fun `a one-shot session ends after its utterance`() {
        assertEquals(
            VoiceNextStep.Finish,
            VoiceSessionPolicy.afterUtterance(sessionActive = true, continuous = false, emptyRounds = 0)
        )
    }

    @Test
    fun `a stopped session does not re-arm`() {
        // The user tapped stop while the recognizer was still working; its final
        // result still commits, but nothing starts back up.
        assertEquals(
            VoiceNextStep.Finish,
            VoiceSessionPolicy.afterUtterance(sessionActive = false, continuous = true, emptyRounds = 0)
        )
    }

    @Test
    fun `an idle session closes itself at the empty-round bound`() {
        val bound = VoiceSessionPolicy.MAX_EMPTY_ROUNDS
        assertEquals(
            VoiceNextStep.Restart,
            VoiceSessionPolicy.afterUtterance(true, continuous = true, emptyRounds = bound - 1)
        )
        assertEquals(
            VoiceNextStep.Finish,
            VoiceSessionPolicy.afterUtterance(true, continuous = true, emptyRounds = bound)
        )
    }

    // ---- After an error ----

    @Test
    fun `silence is the pause between sentences, not a failure`() {
        assertTrue(VoiceSessionPolicy.isSilent(SpeechRecognizer.ERROR_NO_MATCH))
        assertTrue(VoiceSessionPolicy.isSilent(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertFalse(VoiceSessionPolicy.isSilent(SpeechRecognizer.ERROR_NETWORK))
        assertFalse(VoiceSessionPolicy.isSilent(SpeechRecognizer.ERROR_CLIENT))
    }

    @Test
    fun `a silent round re-arms quietly instead of reporting`() {
        assertEquals(
            VoiceNextStep.Restart,
            VoiceSessionPolicy.afterError(
                SpeechRecognizer.ERROR_NO_MATCH,
                sessionActive = true, continuous = true, emptyRounds = 1, canRecreate = true
            )
        )
    }

    @Test
    fun `repeated silence ends the session without an error message`() {
        // Walking away must close the microphone, and it is not a failure worth
        // putting on screen.
        assertEquals(
            VoiceNextStep.Finish,
            VoiceSessionPolicy.afterError(
                SpeechRecognizer.ERROR_NO_MATCH,
                sessionActive = true, continuous = true,
                emptyRounds = VoiceSessionPolicy.MAX_EMPTY_ROUNDS, canRecreate = true
            )
        )
    }

    @Test
    fun `silence outside a continuous session is reported`() {
        // One-shot dictation that heard nothing has no next round to hide the
        // failure in, so the user gets told.
        assertEquals(
            VoiceNextStep.Report(SpeechRecognizer.ERROR_NO_MATCH),
            VoiceSessionPolicy.afterError(
                SpeechRecognizer.ERROR_NO_MATCH,
                sessionActive = true, continuous = false, emptyRounds = 1, canRecreate = true
            )
        )
    }

    @Test
    fun `a busy recognizer is rebuilt once rather than reported`() {
        assertEquals(
            VoiceNextStep.RecreateAndRestart,
            VoiceSessionPolicy.afterError(
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                sessionActive = true, continuous = true, emptyRounds = 0, canRecreate = true
            )
        )
    }

    @Test
    fun `a recognizer that stays busy after a rebuild is reported`() {
        // Without this bound a permanently busy service would loop forever.
        assertEquals(
            VoiceNextStep.Report(SpeechRecognizer.ERROR_RECOGNIZER_BUSY),
            VoiceSessionPolicy.afterError(
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                sessionActive = true, continuous = true, emptyRounds = 0, canRecreate = false
            )
        )
    }

    @Test
    fun `a real failure is always reported`() {
        for (error in listOf(
            SpeechRecognizer.ERROR_NETWORK,
            SpeechRecognizer.ERROR_AUDIO,
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            SpeechRecognizer.ERROR_SERVER
        )) {
            assertEquals(
                "error $error should reach the user",
                VoiceNextStep.Report(error),
                VoiceSessionPolicy.afterError(
                    error, sessionActive = true, continuous = true, emptyRounds = 0, canRecreate = true
                )
            )
        }
    }

    @Test
    fun `an error after the session was stopped still reports rather than re-arming`() {
        assertEquals(
            VoiceNextStep.Report(SpeechRecognizer.ERROR_NETWORK),
            VoiceSessionPolicy.afterError(
                SpeechRecognizer.ERROR_NETWORK,
                sessionActive = false, continuous = true, emptyRounds = 0, canRecreate = true
            )
        )
    }
}
