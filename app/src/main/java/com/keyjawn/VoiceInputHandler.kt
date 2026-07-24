package com.keyjawn

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.inputmethod.InputConnection
import androidx.core.content.ContextCompat
import java.util.Locale

interface VoiceInputListener {
    fun onVoiceStart()
    fun onVoiceStop()
    fun onPartialResult(text: String)
    fun onFinalResult(text: String)
    fun onRmsChanged(rmsdB: Float)
    fun onError(error: Int)

    /** The recognizer is warmed up and audio is being captured. */
    fun onVoiceReady() {}

    /** Speech ended; the recognizer is turning the last utterance into text. */
    fun onVoiceProcessing() {}

    /**
     * A continuous session finished one utterance and is re-arming for the next.
     * Distinct from [onVoiceStop], which means dictation is over.
     */
    fun onVoiceContinue() {}
}

/** Maps a [SpeechRecognizer] error code to a short user-facing message. */
fun voiceErrorMessage(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_NO_MATCH,
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that"
    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "No network"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Busy, try again"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission required"
    else -> "Voice input failed"
}

/**
 * Speech-to-text against Android's [SpeechRecognizer], shaped for dictating a
 * whole prompt rather than a single search query.
 *
 * Three things make it feel continuous instead of transactional:
 *
 *  - A **session** outlives one utterance. The platform recognizer stops after
 *    every pause; in continuous mode this class re-arms it immediately, so the
 *    user speaks in natural sentences without re-tapping the mic between them.
 *  - **Live preview** puts the in-flight partial into the editor as composing
 *    text, so words appear while they are being spoken instead of landing in one
 *    block seconds later. The composing span is replaced by the final text, so
 *    the editor never keeps a half-heard guess.
 *  - **Context-aware commits** run every utterance through [VoiceTextFormatter],
 *    which supplies the space between utterances the recognizer knows nothing
 *    about and applies spoken punctuation commands when the user enables them.
 *
 * A session ends when the user stops it, on a hard error, or after
 * [MAX_EMPTY_ROUNDS] consecutive silent rounds, so an idle keyboard never holds
 * the microphone open indefinitely.
 */
class VoiceInputHandler(
    private val context: Context,
    private val appPrefs: AppPrefs? = null
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var micButton: View? = null
    private var inputConnectionProvider: (() -> InputConnection?)? = null

    /** The recognizer is currently capturing audio. */
    private var listening = false

    /**
     * The user wants dictation to keep going. Stays true across the gaps between
     * utterances in a continuous session, which is what separates a re-arm from
     * a real stop.
     */
    private var sessionActive = false

    /** True while the mic key is held down (push-to-talk); ends on release. */
    private var holdToTalk = false

    /** Consecutive utterances that produced no text. Bounds an idle session. */
    private var emptyRounds = 0

    /** The recognizer was recreated for this session after a busy error. */
    private var recreatedForBusy = false

    /** A composing span from live preview is currently in the editor. */
    private var composingActive = false

    /**
     * Text immediately before the cursor when the current utterance began,
     * captured before any composing span is placed so the spacing decision sees
     * the real editor content rather than our own preview.
     */
    private var utteranceContext: CharSequence? = null

    /**
     * The user cancelled deliberately, so the recognizer's own abort callback
     * must not surface as a failure. SpeechRecognizer.cancel() commonly answers
     * with ERROR_CLIENT, and reporting it would put "Voice input failed" on
     * screen immediately after the user asked for nothing to happen.
     */
    private var suppressErrors = false

    private val handler = Handler(Looper.getMainLooper())
    private var restartRunnable: Runnable? = null

    /**
     * Guards the explicit-stop path. Some recognizer implementations never
     * answer a stopListening() that arrived before any speech, which would
     * strand the voice bar over the keyboard with no way back.
     */
    private var stopWatchdog: Runnable? = null

    /** A partial result has arrived for the current utterance. */
    private var sawPartial = false

    var listener: VoiceInputListener? = null
    var onPermissionNeeded: ((String) -> Unit)? = null

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun setup(micButton: View, icProvider: () -> InputConnection?) {
        this.micButton = micButton
        this.inputConnectionProvider = icProvider
        ensureRecognizer()
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        var recognizer = speechRecognizer
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer?.setRecognitionListener(createListener())
            speechRecognizer = recognizer
        }
        return recognizer
    }

    /**
     * Begins a dictation session. [holdToTalk] runs a single push-to-talk
     * utterance that ends on release; otherwise the session follows the user's
     * continuous-dictation preference.
     */
    fun startListening(holdToTalk: Boolean = false) {
        // sessionActive covers the gap between utterances in continuous mode,
        // where listening is briefly false but dictation is still running --
        // starting again there would reset the session's own idle bound.
        if (!isAvailable() || listening || sessionActive) return

        if (!hasRecordAudioPermission()) {
            onPermissionNeeded?.invoke("Mic permission required. Opening settings.")
            openAppSettings()
            return
        }

        this.holdToTalk = holdToTalk
        sessionActive = true
        emptyRounds = 0
        recreatedForBusy = false
        suppressErrors = false
        listener?.onVoiceStart()
        beginUtterance()
    }

    /** Arms the recognizer for one utterance within the active session. */
    private fun beginUtterance() {
        val recognizer = ensureRecognizer() ?: return
        listening = true
        sawPartial = false
        utteranceContext = inputConnectionProvider?.invoke()?.getTextBeforeCursor(CONTEXT_CHARS, 0)
        recognizer.startListening(buildIntent())
    }

    private fun buildIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Without an explicit language the recognizer falls back to the
            // service's own default, which can differ from the keyboard's locale.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, Locale.getDefault().language)
            // Keep the mic open through natural pauses instead of cutting the
            // user off mid-thought; the session's own idle bound still applies.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                SILENCE_MS
            )
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /**
     * Ends the session, keeping whatever the recognizer has already heard. The
     * final callback still fires and commits, so a user who taps stop mid-word
     * does not lose the sentence.
     */
    fun stopListening() {
        if (!sessionActive && !listening) return
        sessionActive = false
        cancelPendingRestart()
        if (listening) {
            listening = false
            speechRecognizer?.stopListening()
            armStopWatchdog()
        } else {
            finishSession()
        }
    }

    /**
     * Ends the session and discards the current utterance, including any live
     * preview already showing in the editor. This is the escape hatch for a
     * misfire -- stop commits, cancel does not.
     */
    fun cancel() {
        sessionActive = false
        listening = false
        // Set before the abort so the ERROR_CLIENT that cancel() typically
        // answers with does not reach the user as a failure.
        suppressErrors = true
        cancelPendingRestart()
        clearComposing()
        speechRecognizer?.cancel()
        finishSession()
    }

    fun destroy() {
        sessionActive = false
        listening = false
        suppressErrors = true
        cancelPendingRestart()
        clearComposing()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    fun isListening(): Boolean = listening

    /** True while dictation is running, including the gap between utterances. */
    fun isSessionActive(): Boolean = sessionActive

    /** True while the current session was started by holding the mic key. */
    fun isHoldToTalk(): Boolean = holdToTalk

    private fun continuousEnabled(): Boolean =
        !holdToTalk && (appPrefs?.isVoiceContinuous() ?: true)

    private fun livePreviewEnabled(): Boolean = appPrefs?.isVoiceLivePreview() ?: true

    private fun commandsEnabled(): Boolean = appPrefs?.isVoiceCommands() ?: false

    private fun cancelPendingRestart() {
        restartRunnable?.let { handler.removeCallbacks(it) }
        restartRunnable = null
    }

    private fun cancelStopWatchdog() {
        stopWatchdog?.let { handler.removeCallbacks(it) }
        stopWatchdog = null
    }

    /**
     * Closes the session if the recognizer never answers an explicit stop. The
     * voice bar replaces the terminal key row while dictation runs, so a stop
     * that goes unanswered would leave the user staring at a bar with no keys.
     */
    private fun armStopWatchdog() {
        cancelStopWatchdog()
        val runnable = Runnable {
            stopWatchdog = null
            suppressErrors = true
            speechRecognizer?.cancel()
            finishSession()
        }
        stopWatchdog = runnable
        handler.postDelayed(runnable, STOP_TIMEOUT_MS)
    }

    private fun finishSession() {
        cancelStopWatchdog()
        sessionActive = false
        holdToTalk = false
        listener?.onVoiceStop()
    }

    /**
     * Re-arms for the next utterance after a short breath. The delay lets the
     * platform recognizer tear its previous session down; restarting inside the
     * callback reliably comes back as ERROR_RECOGNIZER_BUSY.
     */
    private fun scheduleRestart() {
        cancelPendingRestart()
        val runnable = Runnable {
            restartRunnable = null
            if (sessionActive && !listening) {
                listener?.onVoiceContinue()
                beginUtterance()
            }
        }
        restartRunnable = runnable
        handler.postDelayed(runnable, RESTART_DELAY_MS)
    }

    private fun clearComposing() {
        if (!composingActive) return
        composingActive = false
        val ic = inputConnectionProvider?.invoke() ?: return
        ic.setComposingText("", 1)
        ic.finishComposingText()
    }

    /**
     * Commits [raw] at the cursor, spaced against the text that was there when
     * the utterance began and with spoken commands applied. Returns false when
     * the utterance carried no text.
     */
    private fun commitUtterance(raw: String): Boolean {
        val formatted = VoiceTextFormatter.applyCommands(raw, commandsEnabled())
        if (formatted.isEmpty()) return false
        val ic = inputConnectionProvider?.invoke() ?: return false
        clearComposing()
        ic.commitText(VoiceTextFormatter.joinWithContext(utteranceContext, formatted), 1)
        return true
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                listening = false
                cancelStopWatchdog()
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotEmpty()) {
                    listener?.onFinalResult(text)
                    if (commitUtterance(text)) {
                        emptyRounds = 0
                    } else {
                        emptyRounds++
                    }
                } else {
                    clearComposing()
                    emptyRounds++
                }
                val step = VoiceSessionPolicy.afterUtterance(
                    sessionActive, continuousEnabled(), emptyRounds
                )
                if (step == VoiceNextStep.Restart) scheduleRestart() else finishSession()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return
                sawPartial = true
                listener?.onPartialResult(text)
                if (!livePreviewEnabled()) return
                val ic = inputConnectionProvider?.invoke() ?: return
                composingActive = true
                ic.setComposingText(VoiceTextFormatter.joinWithContext(utteranceContext, text), 1)
            }

            override fun onError(error: Int) {
                listening = false
                cancelStopWatchdog()
                clearComposing()

                // A deliberate cancel or teardown asked the recognizer to abort;
                // its complaint about being aborted is not news to the user.
                if (suppressErrors) {
                    if (sessionActive) finishSession()
                    return
                }

                // A silent round is the normal end of a sentence in continuous
                // mode, not a failure -- so it counts against the idle bound
                // rather than being reported.
                if (VoiceSessionPolicy.isSilent(error)) emptyRounds++

                when (
                    val step = VoiceSessionPolicy.afterError(
                        error, sessionActive, continuousEnabled(), emptyRounds, !recreatedForBusy
                    )
                ) {
                    VoiceNextStep.Restart -> scheduleRestart()
                    VoiceNextStep.RecreateAndRestart -> {
                        recreatedForBusy = true
                        speechRecognizer?.destroy()
                        speechRecognizer = null
                        scheduleRestart()
                    }
                    is VoiceNextStep.Report -> {
                        listener?.onError(step.error)
                        finishSession()
                    }
                    VoiceNextStep.Finish -> finishSession()
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {
                listener?.onVoiceReady()
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                listener?.onRmsChanged(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                // Only worth announcing when nothing was heard yet: with a
                // partial already on screen, replacing it with a status word
                // would take information away.
                if (!sawPartial) listener?.onVoiceProcessing()
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    companion object {
        /** Breath between utterances so the platform recognizer can re-arm. */
        const val RESTART_DELAY_MS = 180L

        /** How long an explicit stop waits for the recognizer's final answer. */
        const val STOP_TIMEOUT_MS = 3000L

        /** Pause tolerated inside one utterance before the recognizer cuts it. */
        private const val SILENCE_MS = 1500L

        /** How much preceding text to read for the spacing decision. */
        private const val CONTEXT_CHARS = 2
    }
}
