package com.keyjawn

import android.Manifest
import android.content.Intent
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
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

    @Test
    fun `recognition request prefers offline and permits a thinking pause`() {
        val intent = VoiceRecognitionRequest.build(Locale.US)

        assertTrue(intent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        assertEquals(
            VoiceRecognitionRequest.MINIMUM_INPUT_MS,
            intent.getLongExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, -1L)
        )
        assertEquals(
            VoiceRecognitionRequest.SILENCE_MS,
            intent.getLongExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                -1L
            )
        )
        assertTrue(VoiceRecognitionRequest.MINIMUM_INPUT_MS >= 5_000L)
        assertTrue(VoiceRecognitionRequest.SILENCE_MS >= 3_000L)
    }

    @Test
    fun `on-device recognizer is selected from Android 12 when available`() {
        assertTrue(shouldUseOnDeviceRecognizer(sdkInt = 31, onDeviceAvailable = true))
        assertTrue(shouldUseOnDeviceRecognizer(sdkInt = 33, onDeviceAvailable = true))
        assertFalse(shouldUseOnDeviceRecognizer(sdkInt = 30, onDeviceAvailable = true))
        assertFalse(shouldUseOnDeviceRecognizer(sdkInt = 33, onDeviceAvailable = false))
    }

    @Test
    fun `installed offline language starts recognition`() {
        val recognizer = FakeVoiceRecognizer(
            isOnDevice = true,
            support = VoiceRecognitionSupport(installed = listOf("en-US"))
        )
        val handler = handlerWith(recognizer)

        handler.startListening()

        assertNotNull(recognizer.startedIntent)
        assertNull(recognizer.downloadIntent)
        assertTrue(handler.isListening())
    }

    @Test
    fun `missing supported offline language starts download and reports next step`() {
        val recognizer = FakeVoiceRecognizer(
            isOnDevice = true,
            support = VoiceRecognitionSupport(supported = listOf("en-US"))
        )
        val handler = handlerWith(recognizer)
        val statuses = mutableListOf<String>()
        handler.listener = recordingListener(onStatus = statuses::add)

        handler.startListening()

        assertNotNull(recognizer.downloadIntent)
        assertNull(recognizer.startedIntent)
        assertFalse(handler.isSessionActive())
        assertTrue(statuses.single().contains("Downloading offline voice"))
    }

    @Test
    fun `pending offline language retriggers download instead of starting online`() {
        val recognizer = FakeVoiceRecognizer(
            isOnDevice = true,
            support = VoiceRecognitionSupport(pending = listOf("en-US"))
        )
        val handler = handlerWith(recognizer)

        handler.startListening()

        assertNotNull(recognizer.downloadIntent)
        assertNull(recognizer.startedIntent)
        assertFalse(handler.isSessionActive())
    }

    @Test
    fun `an in-flight support preflight never delays live capture`() {
        val recognizer = FakeVoiceRecognizer(
            isOnDevice = true,
            support = VoiceRecognitionSupport(installed = listOf("en-US")),
            deferSupport = true
        )
        val handler = handlerWith(recognizer)

        handler.startListening()

        assertNotNull(recognizer.startedIntent)
        assertTrue(handler.isListening())
    }

    @Test
    fun `script-specific model does not match another script`() {
        assertEquals(
            VoiceSupportDecision.Download,
            voiceSupportDecision(
                support = VoiceRecognitionSupport(
                    installed = listOf("zh-Hans"),
                    supported = listOf("zh-Hant-TW")
                ),
                requestedLanguage = "zh-Hant-TW",
                onDeviceRecognizer = true
            )
        )
    }

    @Test
    fun `support query failure degrades safely to dictation`() {
        val recognizer = FakeVoiceRecognizer(
            isOnDevice = true,
            supportError = SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT
        )
        val handler = handlerWith(recognizer)

        handler.startListening()

        assertNotNull(recognizer.startedIntent)
        assertTrue(handler.isListening())
    }

    @Test
    fun `unsupported on-device language reports an actionable setting`() {
        val recognizer = FakeVoiceRecognizer(
            isOnDevice = true,
            support = VoiceRecognitionSupport(installed = listOf("fr-FR"))
        )
        val handler = handlerWith(recognizer)
        val statuses = mutableListOf<String>()
        handler.listener = recordingListener(onStatus = statuses::add)

        handler.startListening()

        assertNull(recognizer.startedIntent)
        assertNull(recognizer.downloadIntent)
        assertFalse(handler.isSessionActive())
        assertTrue(statuses.single().contains("Speech Services settings"))
    }

    @Test
    fun `fallback recognizer starts when offline support is unavailable`() {
        val recognizer = FakeVoiceRecognizer(
            isOnDevice = false,
            support = VoiceRecognitionSupport()
        )
        val handler = handlerWith(recognizer)

        handler.startListening()

        assertNotNull(recognizer.startedIntent)
        assertTrue(handler.isListening())
    }

    private fun handlerWith(recognizer: FakeVoiceRecognizer): VoiceInputHandler {
        val context = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO)
        val handler = VoiceInputHandler(
            context,
            appPrefs = null,
            recognizerFactory = FakeVoiceRecognizerFactory(recognizer)
        )
        handler.setup(Button(context)) { null }
        return handler
    }

    private fun recordingListener(
        onStatus: (String) -> Unit = {}
    ): VoiceInputListener = object : VoiceInputListener {
        override fun onVoiceStart() {}
        override fun onVoiceStop() {}
        override fun onPartialResult(text: String) {}
        override fun onFinalResult(text: String) {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onError(error: Int) {}
        override fun onVoiceStatus(message: String) = onStatus(message)
    }

    private class FakeVoiceRecognizerFactory(
        private val recognizer: FakeVoiceRecognizer
    ) : VoiceRecognizerFactory {
        override fun isAvailable(): Boolean = true
        override fun create(): VoiceRecognizer = recognizer
    }

    private class FakeVoiceRecognizer(
        override val isOnDevice: Boolean,
        private val support: VoiceRecognitionSupport? = null,
        private val supportError: Int? = null,
        private val deferSupport: Boolean = false
    ) : VoiceRecognizer {
        var startedIntent: Intent? = null
        var downloadIntent: Intent? = null

        override fun setRecognitionListener(listener: RecognitionListener) {}

        override fun startListening(intent: Intent) {
            startedIntent = intent
        }

        override fun stopListening() {}
        override fun cancel() {}
        override fun destroy() {}

        override fun checkRecognitionSupport(
            intent: Intent,
            onSupport: (VoiceRecognitionSupport) -> Unit,
            onError: (Int) -> Unit
        ) {
            if (deferSupport) return
            support?.let(onSupport) ?: onError(
                supportError ?: SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT
            )
        }

        override fun triggerModelDownload(intent: Intent) {
            downloadIntent = intent
        }
    }
}
