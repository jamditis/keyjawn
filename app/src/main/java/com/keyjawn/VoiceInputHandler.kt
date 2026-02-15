package com.keyjawn

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.core.content.ContextCompat

class VoiceInputHandler(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var micButton: View? = null
    private var inputConnectionProvider: (() -> InputConnection?)? = null
    private var listening = false

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun setup(micButton: View, icProvider: () -> InputConnection?) {
        this.micButton = micButton
        this.inputConnectionProvider = icProvider
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(createListener())
    }

    fun startListening() {
        if (!isAvailable() || listening) return

        if (!hasRecordAudioPermission()) {
            Toast.makeText(context, "Mic permission required. Opening settings.", Toast.LENGTH_LONG).show()
            openAppSettings()
            return
        }

        listening = true
        updateMicVisual(true)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.startListening(intent)
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

    fun stopListening() {
        if (!listening) return
        listening = false
        updateMicVisual(false)
        speechRecognizer?.stopListening()
    }

    fun destroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    fun isListening(): Boolean = listening

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                listening = false
                updateMicVisual(false)
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: return
                inputConnectionProvider?.invoke()?.commitText(text, 1)
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onError(error: Int) {
                listening = false
                updateMicVisual(false)
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun updateMicVisual(active: Boolean) {
        micButton?.post {
            if (active) {
                micButton?.setBackgroundResource(R.drawable.key_bg_active)
            } else {
                micButton?.setBackgroundResource(R.drawable.key_bg)
            }
        }
    }
}
