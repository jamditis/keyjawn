package com.keyjawn

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.inputmethod.InputConnection
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.util.Locale
import java.util.concurrent.Executor

interface VoiceInputListener {
    fun onVoiceStart()
    fun onVoiceStop()
    fun onPartialResult(text: String)
    fun onFinalResult(text: String)
    fun onRmsChanged(rmsdB: Float)
    fun onError(error: Int)

    /** The recognizer is warmed up and audio is being captured. */
    fun onVoiceReady() {}

    /** An actionable recognizer setup status that should remain visible. */
    fun onVoiceStatus(message: String) {}

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

internal data class VoiceRecognitionSupport(
    val installed: List<String> = emptyList(),
    val pending: List<String> = emptyList(),
    val supported: List<String> = emptyList()
)

internal sealed class VoiceSupportDecision {
    object Start : VoiceSupportDecision()
    object Download : VoiceSupportDecision()
    object Unavailable : VoiceSupportDecision()
}

internal fun shouldUseOnDeviceRecognizer(
    sdkInt: Int,
    onDeviceAvailable: Boolean
): Boolean = sdkInt >= Build.VERSION_CODES.S && onDeviceAvailable

@SuppressLint("InlinedApi")
internal fun shouldFallbackFromOnDeviceRecognizer(
    sdkInt: Int,
    error: Int,
    onDeviceRecognizer: Boolean
): Boolean =
    sdkInt in Build.VERSION_CODES.S until Build.VERSION_CODES.TIRAMISU &&
        onDeviceRecognizer &&
        (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
            error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)

private fun languageMatches(candidate: String, requested: String): Boolean {
    val candidateLocale = Locale.forLanguageTag(candidate.replace('_', '-'))
    val requestedLocale = Locale.forLanguageTag(requested.replace('_', '-'))
    if (candidateLocale.language.isEmpty() || requestedLocale.language.isEmpty()) return false
    if (!candidateLocale.language.equals(requestedLocale.language, ignoreCase = true)) return false
    if (candidateLocale.script.isNotEmpty() &&
        requestedLocale.script.isNotEmpty() &&
        !candidateLocale.script.equals(requestedLocale.script, ignoreCase = true)
    ) {
        return false
    }
    if (candidateLocale.country.isNotEmpty() &&
        requestedLocale.country.isNotEmpty() &&
        !candidateLocale.country.equals(requestedLocale.country, ignoreCase = true)
    ) {
        return false
    }
    // Omitted script or region subtags are wildcards; explicit ones must agree.
    return true
}

internal fun voiceSupportDecision(
    support: VoiceRecognitionSupport,
    requestedLanguage: String,
    onDeviceRecognizer: Boolean
): VoiceSupportDecision = when {
    support.installed.any { languageMatches(it, requestedLanguage) } ->
        VoiceSupportDecision.Start
    support.pending.any { languageMatches(it, requestedLanguage) } ||
        support.supported.any { languageMatches(it, requestedLanguage) } ->
        VoiceSupportDecision.Download
    onDeviceRecognizer -> VoiceSupportDecision.Unavailable
    else -> VoiceSupportDecision.Start
}

internal object VoiceRecognitionRequest {
    /** Do not end a fresh request before the user has had time to form a prompt. */
    const val MINIMUM_INPUT_MS = 5_000L

    /** Best-effort endpointer window for a multi-second thinking pause. */
    const val SILENCE_MS = 3_000L

    fun build(
        locale: Locale = Locale.getDefault(),
        minimumInputMs: Long? = MINIMUM_INPUT_MS
    ): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            // Without an explicit language the recognizer falls back to the
            // service's own default, which can differ from the keyboard's locale.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.language)
            // These endpointer hints are recognizer-dependent, but services that
            // honor them no longer cut off a prompt at the first thinking pause.
            minimumInputMs?.let {
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, it)
            }
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, SILENCE_MS)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                SILENCE_MS
            )
        }
}

internal interface VoiceRecognizer {
    val isOnDevice: Boolean

    fun setRecognitionListener(listener: RecognitionListener)
    fun startListening(intent: Intent)
    fun stopListening()
    fun cancel()
    fun destroy()

    fun checkRecognitionSupport(
        intent: Intent,
        onSupport: (VoiceRecognitionSupport) -> Unit,
        onError: (Int) -> Unit
    )

    fun triggerModelDownload(intent: Intent)
}

internal interface VoiceRecognizerFactory {
    fun isAvailable(): Boolean
    fun create(): VoiceRecognizer?

    /** Creates the generic recognizer after an on-device language failure. */
    fun createFallback(): VoiceRecognizer? = create()
}

private class PlatformVoiceRecognizerFactory(
    private val context: Context
) : VoiceRecognizerFactory {
    override fun isAvailable(): Boolean {
        val onDeviceAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            false
        }
        return shouldUseOnDeviceRecognizer(Build.VERSION.SDK_INT, onDeviceAvailable) ||
            SpeechRecognizer.isRecognitionAvailable(context)
    }

    override fun create(): VoiceRecognizer {
        val onDeviceAvailable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            false
        }
        var onDevice = shouldUseOnDeviceRecognizer(Build.VERSION.SDK_INT, onDeviceAvailable)
        val recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && onDevice) {
            try {
                createOnDeviceRecognizer31()
            } catch (_: UnsupportedOperationException) {
                // Availability can change between the probe and factory call.
                onDevice = false
                SpeechRecognizer.createSpeechRecognizer(context)
            }
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        return PlatformVoiceRecognizer(
            recognizer,
            onDevice,
            ContextCompat.getMainExecutor(context)
        )
    }

    override fun createFallback(): VoiceRecognizer =
        PlatformVoiceRecognizer(
            SpeechRecognizer.createSpeechRecognizer(context),
            isOnDevice = false,
            callbackExecutor = ContextCompat.getMainExecutor(context)
        )

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createOnDeviceRecognizer31(): SpeechRecognizer =
        SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
}

private class PlatformVoiceRecognizer(
    private val recognizer: SpeechRecognizer,
    override val isOnDevice: Boolean,
    private val callbackExecutor: Executor
) : VoiceRecognizer {
    override fun setRecognitionListener(listener: RecognitionListener) {
        recognizer.setRecognitionListener(listener)
    }

    override fun startListening(intent: Intent) = recognizer.startListening(intent)
    override fun stopListening() = recognizer.stopListening()
    override fun cancel() = recognizer.cancel()
    override fun destroy() = recognizer.destroy()

    override fun checkRecognitionSupport(
        intent: Intent,
        onSupport: (VoiceRecognitionSupport) -> Unit,
        onError: (Int) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkRecognitionSupport33(intent, onSupport, onError)
        } else {
            onError(SpeechRecognizer.ERROR_CLIENT)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkRecognitionSupport33(
        intent: Intent,
        onSupport: (VoiceRecognitionSupport) -> Unit,
        onError: (Int) -> Unit
    ) {
        recognizer.checkRecognitionSupport(
            intent,
            callbackExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                    onSupport(
                        VoiceRecognitionSupport(
                            installed = recognitionSupport.installedOnDeviceLanguages,
                            pending = recognitionSupport.pendingOnDeviceLanguages,
                            supported = recognitionSupport.supportedOnDeviceLanguages
                        )
                    )
                }

                override fun onError(error: Int) = onError.invoke(error)
            }
        )
    }

    override fun triggerModelDownload(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            triggerModelDownload33(intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun triggerModelDownload33(intent: Intent) {
        recognizer.triggerModelDownload(intent)
    }
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
class VoiceInputHandler internal constructor(
    private val context: Context,
    private val appPrefs: AppPrefs?,
    private val recognizerFactory: VoiceRecognizerFactory
) {
    constructor(
        context: Context,
        appPrefs: AppPrefs? = null
    ) : this(context, appPrefs, PlatformVoiceRecognizerFactory(context))

    private var speechRecognizer: VoiceRecognizer? = null
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

    /** Last support result prepared before the user pressed the mic key. */
    private var cachedSupport: VoiceRecognitionSupport? = null
    private var cachedSupportRecognizer: VoiceRecognizer? = null
    private var cachedSupportLanguage: String? = null

    /** A prior blocking result requires a fresh check before audio capture. */
    private var supportCheckRequired = false

    /** Background probes are advisory; retries after a blocking result are gated. */
    private var supportProbeInFlight = false
    private var supportProbeGeneration = 0
    private var supportCheckWatchdog: Runnable? = null

    /** Keep using the generic recognizer after an Android 12 language failure. */
    private var useFallbackRecognizer = false

    var listener: VoiceInputListener? = null
    var onPermissionNeeded: ((String) -> Unit)? = null

    fun isAvailable(): Boolean {
        return recognizerFactory.isAvailable()
    }

    fun setup(micButton: View, icProvider: () -> InputConnection?) {
        this.micButton = micButton
        this.inputConnectionProvider = icProvider
        val recognizer = ensureRecognizer() ?: return
        preflightRecognitionSupport(recognizer, VoiceRecognitionRequest.build())
    }

    private fun ensureRecognizer(): VoiceRecognizer? {
        var recognizer = speechRecognizer
        if (recognizer == null) {
            recognizer = if (useFallbackRecognizer) {
                recognizerFactory.createFallback()
            } else {
                recognizerFactory.create()
            }
            recognizer?.let { it.setRecognitionListener(createListener(it)) }
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
        val recognizer = ensureRecognizer()
        if (recognizer == null) {
            finishSession()
            return
        }
        val intent = VoiceRecognitionRequest.build(
            minimumInputMs = if (holdToTalk) null else VoiceRecognitionRequest.MINIMUM_INPUT_MS
        )
        val requestedLanguage = requestedLanguage(intent)
        val support = if (
            cachedSupportRecognizer === recognizer &&
            cachedSupportLanguage == requestedLanguage
        ) {
            cachedSupport
        } else {
            null
        }
        if (support != null) {
            handleRecognitionSupport(recognizer, intent, support)
            return
        }
        if (supportCheckRequired && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkRequiredRecognitionSupport(recognizer, intent)
            return
        }

        // Setup normally finishes this probe before a mic interaction. If it is
        // still pending (or failed), capture starts now so leading words and
        // short push-to-talk holds are never lost to an advisory API call.
        startUtterance(recognizer, intent)
        preflightRecognitionSupport(recognizer, intent)
    }

    private fun requestedLanguage(intent: Intent): String =
        intent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)
            ?: Locale.getDefault().toLanguageTag()

    private fun preflightRecognitionSupport(
        recognizer: VoiceRecognizer,
        intent: Intent
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || supportProbeInFlight) return
        supportProbeInFlight = true
        val generation = ++supportProbeGeneration
        val requestedLanguage = requestedLanguage(intent)
        try {
            recognizer.checkRecognitionSupport(
                intent,
                onSupport = { support ->
                    if (generation != supportProbeGeneration) return@checkRecognitionSupport
                    supportProbeInFlight = false
                    if (speechRecognizer === recognizer) {
                        cachedSupport = support
                        cachedSupportRecognizer = recognizer
                        cachedSupportLanguage = requestedLanguage
                    }
                },
                onError = {
                    if (generation == supportProbeGeneration) {
                        supportProbeInFlight = false
                    }
                }
            )
        } catch (_: RuntimeException) {
            if (generation == supportProbeGeneration) {
                supportProbeInFlight = false
            }
        }
    }

    private fun checkRequiredRecognitionSupport(
        recognizer: VoiceRecognizer,
        intent: Intent
    ) {
        supportProbeInFlight = true
        val generation = ++supportProbeGeneration
        val requestedLanguage = requestedLanguage(intent)
        armSupportCheckWatchdog(recognizer, intent, generation)
        try {
            recognizer.checkRecognitionSupport(
                intent,
                onSupport = { support ->
                    if (generation != supportProbeGeneration) return@checkRecognitionSupport
                    cancelSupportCheckWatchdog()
                    supportProbeInFlight = false
                    if (speechRecognizer !== recognizer || !sessionActive) {
                        return@checkRecognitionSupport
                    }
                    supportCheckRequired = false
                    cachedSupport = support
                    cachedSupportRecognizer = recognizer
                    cachedSupportLanguage = requestedLanguage
                    handleRecognitionSupport(recognizer, intent, support)
                },
                onError = {
                    if (generation != supportProbeGeneration) return@checkRecognitionSupport
                    cancelSupportCheckWatchdog()
                    supportProbeInFlight = false
                    if (speechRecognizer === recognizer && sessionActive) {
                        supportCheckRequired = false
                        startUtterance(recognizer, intent)
                    }
                }
            )
        } catch (_: RuntimeException) {
            if (generation == supportProbeGeneration) {
                cancelSupportCheckWatchdog()
                supportProbeInFlight = false
                if (speechRecognizer === recognizer && sessionActive) {
                    supportCheckRequired = false
                    startUtterance(recognizer, intent)
                }
            }
        }
    }

    private fun armSupportCheckWatchdog(
        recognizer: VoiceRecognizer,
        intent: Intent,
        generation: Int
    ) {
        cancelSupportCheckWatchdog()
        val runnable = Runnable {
            if (generation != supportProbeGeneration) return@Runnable
            supportCheckWatchdog = null
            supportProbeGeneration++
            supportProbeInFlight = false
            if (speechRecognizer === recognizer && sessionActive) {
                supportCheckRequired = false
                startUtterance(recognizer, intent)
            }
        }
        supportCheckWatchdog = runnable
        handler.postDelayed(runnable, SUPPORT_CHECK_TIMEOUT_MS)
    }

    private fun cancelSupportCheckWatchdog() {
        supportCheckWatchdog?.let { handler.removeCallbacks(it) }
        supportCheckWatchdog = null
    }

    private fun clearRecognitionSupport() {
        cancelSupportCheckWatchdog()
        supportProbeGeneration++
        supportProbeInFlight = false
        supportCheckRequired = false
        cachedSupport = null
        cachedSupportRecognizer = null
        cachedSupportLanguage = null
    }

    private fun requireFreshRecognitionSupport() {
        clearRecognitionSupport()
        supportCheckRequired = true
    }

    private fun handleRecognitionSupport(
        recognizer: VoiceRecognizer,
        intent: Intent,
        support: VoiceRecognitionSupport
    ) {
        val requestedLanguage = requestedLanguage(intent)
        when (voiceSupportDecision(support, requestedLanguage, recognizer.isOnDevice)) {
            VoiceSupportDecision.Start -> startUtterance(recognizer, intent)
            VoiceSupportDecision.Download -> {
                val message = try {
                    recognizer.triggerModelDownload(intent)
                    "Downloading offline voice for $requestedLanguage. Try dictation again when it finishes."
                } catch (_: RuntimeException) {
                    "Offline voice download could not start. Check Speech Services settings."
                }
                // The next mic press checks again. Probing immediately would only
                // cache the still-pending download state.
                requireFreshRecognitionSupport()
                listener?.onVoiceStatus(message)
                finishSession()
            }
            VoiceSupportDecision.Unavailable -> {
                // The user can install this model without rebuilding the IME.
                // Recheck before the next attempt instead of opening the mic
                // against the same unsupported language.
                requireFreshRecognitionSupport()
                listener?.onVoiceStatus(
                    "Offline voice is not available for $requestedLanguage. " +
                        "Install the language in Speech Services settings."
                )
                finishSession()
            }
        }
    }

    private fun startUtterance(recognizer: VoiceRecognizer, intent: Intent) {
        listening = true
        sawPartial = false
        utteranceContext = inputConnectionProvider?.invoke()?.getTextBeforeCursor(CONTEXT_CHARS, 0)
        recognizer.startListening(intent)
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
        clearRecognitionSupport()
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

    private fun createListener(owner: VoiceRecognizer): RecognitionListener {
        return object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                if (speechRecognizer !== owner) return
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
                if (speechRecognizer !== owner) return
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
                if (speechRecognizer !== owner) return
                listening = false
                cancelStopWatchdog()
                clearComposing()

                // A deliberate cancel or teardown asked the recognizer to abort;
                // its complaint about being aborted is not news to the user.
                if (suppressErrors) {
                    if (sessionActive) finishSession()
                    return
                }

                if (shouldFallbackFromOnDeviceRecognizer(
                    Build.VERSION.SDK_INT,
                    error,
                    owner.isOnDevice
                )) {
                    useFallbackRecognizer = true
                    owner.destroy()
                    speechRecognizer = null
                    clearRecognitionSupport()
                    if (sessionActive) {
                        scheduleRestart()
                    } else {
                        // A push-to-talk release can precede this late language
                        // error. Keep the fallback choice for the next press.
                        finishSession()
                    }
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
                        clearRecognitionSupport()
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
                if (speechRecognizer !== owner) return
                listener?.onVoiceReady()
            }

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {
                if (speechRecognizer !== owner) return
                listener?.onRmsChanged(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                if (speechRecognizer !== owner) return
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

        /** Bound a required support recheck so a broken service cannot strand the UI. */
        const val SUPPORT_CHECK_TIMEOUT_MS = 750L

        /** How much preceding text to read for the spacing decision. */
        private const val CONTEXT_CHARS = 2
    }
}
