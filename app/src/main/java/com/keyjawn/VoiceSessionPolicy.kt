package com.keyjawn

import android.speech.SpeechRecognizer

/** What a dictation session should do after a recognizer callback. */
sealed class VoiceNextStep {
    /** Re-arm the recognizer for another utterance in the same session. */
    object Restart : VoiceNextStep()

    /** End the session. */
    object Finish : VoiceNextStep()

    /** Rebuild the recognizer, then re-arm. */
    object RecreateAndRestart : VoiceNextStep()

    /** Surface the failure to the user, then end the session. */
    data class Report(val error: Int) : VoiceNextStep()
}

/**
 * The decision table behind continuous dictation, kept separate from
 * [VoiceInputHandler] so it can be tested without a speech service.
 *
 * Every branch here is a judgement call about whether an interruption means
 * "the user paused" or "dictation is over", and getting one wrong is either a
 * microphone that will not close or a session that quits mid-sentence. That is
 * worth being able to assert on directly.
 */
object VoiceSessionPolicy {

    /** Consecutive silent utterances after which an idle session gives up. */
    const val MAX_EMPTY_ROUNDS = 3

    /**
     * Whether [error] means "heard nothing" rather than "something broke". In
     * continuous mode this is the normal end of a sentence, not a failure.
     */
    fun isSilent(error: Int): Boolean =
        error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT

    /**
     * After an utterance was transcribed. [emptyRounds] is the running count of
     * consecutive rounds that produced no text, already updated for this one.
     */
    fun afterUtterance(
        sessionActive: Boolean,
        continuous: Boolean,
        emptyRounds: Int
    ): VoiceNextStep =
        if (sessionActive && continuous && emptyRounds < MAX_EMPTY_ROUNDS) {
            VoiceNextStep.Restart
        } else {
            VoiceNextStep.Finish
        }

    /**
     * After the recognizer reported [error]. [emptyRounds] is already updated
     * for this round when the error was a silent one; [canRecreate] is false
     * once this session has already rebuilt the recognizer, so a permanently
     * busy service cannot loop.
     */
    fun afterError(
        error: Int,
        sessionActive: Boolean,
        continuous: Boolean,
        emptyRounds: Int,
        canRecreate: Boolean
    ): VoiceNextStep {
        if (isSilent(error) && sessionActive && continuous) {
            return if (emptyRounds < MAX_EMPTY_ROUNDS) {
                VoiceNextStep.Restart
            } else {
                // Silence all the way to the bound: the user walked away, and
                // holding the microphone open is the wrong default.
                VoiceNextStep.Finish
            }
        }
        // The platform recognizer can hold its previous session past our restart
        // delay. Rebuilding once beats reporting a failure the user cannot act on.
        if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && sessionActive && canRecreate) {
            return VoiceNextStep.RecreateAndRestart
        }
        return VoiceNextStep.Report(error)
    }
}
