package com.keyjawn

import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.Toast

class ExtraRowManager(
    private val view: View,
    private val keySender: KeySender,
    private val inputConnectionProvider: () -> InputConnection?,
    private val voiceInputHandler: VoiceInputHandler? = null,
    private val uploadHandler: UploadHandler? = null,
    private val onUploadTap: (() -> Unit)? = null,
    private val clipboardHistoryManager: ClipboardHistoryManager? = null,
    private val themeManager: ThemeManager? = null
) {

    val ctrlState = CtrlState()

    private val ctrlButton: Button = view.findViewById(R.id.key_ctrl)

    private var clipboardPopup: ClipboardPopup? = null

    private val extraRow: View = view.findViewById(R.id.extra_row)
    private val voiceBar: View? = view.findViewById(R.id.voice_bar)
    private val voiceWaveform: VoiceWaveformView? = voiceBar?.findViewById(R.id.voice_waveform)
    private val voiceText: android.widget.TextView? = voiceBar?.findViewById(R.id.voice_text)
    private val voiceStop: View? = voiceBar?.findViewById(R.id.voice_stop)

    init {
        wireEsc()
        wireTab()
        wireClipboard()
        wireCtrl()
        wireArrow(R.id.key_left, KeyEvent.KEYCODE_DPAD_LEFT)
        wireArrow(R.id.key_down, KeyEvent.KEYCODE_DPAD_DOWN)
        wireArrow(R.id.key_up, KeyEvent.KEYCODE_DPAD_UP)
        wireArrow(R.id.key_right, KeyEvent.KEYCODE_DPAD_RIGHT)
        wireUpload()
        wireMic()

        applyThemeColors()

        ctrlState.onStateChanged = { mode -> updateCtrlAppearance(mode) }
    }

    private fun applyThemeColors() {
        val tm = themeManager ?: return
        view.findViewById<View>(R.id.key_esc)?.background = tm.createExtraRowButtonDrawable(tm.escBg())
        view.findViewById<View>(R.id.key_tab)?.background = tm.createExtraRowButtonDrawable(tm.tabBg())
        view.findViewById<View>(R.id.key_clipboard)?.background = tm.createExtraRowButtonDrawable(tm.clipboardBg())
        ctrlButton.background = tm.createExtraRowButtonDrawable(tm.keyBg())
        ctrlButton.setTextColor(tm.keyText())
        view.findViewById<View>(R.id.key_left)?.background = tm.createExtraRowButtonDrawable(tm.arrowBg())
        view.findViewById<View>(R.id.key_down)?.background = tm.createExtraRowButtonDrawable(tm.arrowBg())
        view.findViewById<View>(R.id.key_up)?.background = tm.createExtraRowButtonDrawable(tm.arrowBg())
        view.findViewById<View>(R.id.key_right)?.background = tm.createExtraRowButtonDrawable(tm.arrowBg())
        view.findViewById<View>(R.id.key_upload)?.background = tm.createExtraRowButtonDrawable(tm.uploadBg())
        view.findViewById<View>(R.id.key_mic)?.background = tm.createExtraRowButtonDrawable(tm.micBg())
        // Set text color on text buttons
        for (id in listOf(R.id.key_esc, R.id.key_tab, R.id.key_left, R.id.key_down, R.id.key_up, R.id.key_right)) {
            (view.findViewById<View>(id) as? Button)?.setTextColor(tm.keyText())
        }
    }

    fun isCtrlActive(): Boolean = ctrlState.isActive()

    fun consumeCtrl(): Boolean = ctrlState.consume()

    private fun wireEsc() {
        view.findViewById<Button>(R.id.key_esc).setOnClickListener {
            val ic = inputConnectionProvider() ?: return@setOnClickListener
            keySender.sendKey(ic, KeyEvent.KEYCODE_ESCAPE)
        }
    }

    private fun wireTab() {
        view.findViewById<Button>(R.id.key_tab).setOnClickListener {
            val ic = inputConnectionProvider() ?: return@setOnClickListener
            keySender.sendKey(ic, KeyEvent.KEYCODE_TAB)
        }
    }

    private fun wireClipboard() {
        val clipButton = view.findViewById<View>(R.id.key_clipboard)
        if (clipboardHistoryManager != null) {
            clipButton.setOnClickListener {
                val popup = ClipboardPopup(clipboardHistoryManager) { text ->
                    val ic = inputConnectionProvider() ?: return@ClipboardPopup
                    clipboardHistoryManager.pasteItem(ic, text)
                }
                clipboardPopup = popup
                popup.show(view)
            }
            clipButton.setOnLongClickListener {
                val ic = inputConnectionProvider() ?: return@setOnLongClickListener true
                if (!clipboardHistoryManager.paste(ic)) {
                    Toast.makeText(view.context, "Clipboard empty", Toast.LENGTH_SHORT).show()
                }
                true
            }
        } else {
            wirePlaceholder(R.id.key_clipboard, "Clipboard not available")
        }
    }

    private fun wireCtrl() {
        ctrlButton.setOnClickListener {
            ctrlState.tap()
        }
        ctrlButton.setOnLongClickListener {
            ctrlState.longPress()
            true
        }
    }

    private fun wireArrow(buttonId: Int, keyCode: Int) {
        val button = view.findViewById<Button>(buttonId)
        val listener = RepeatTouchListener {
            val ic = inputConnectionProvider() ?: return@RepeatTouchListener
            val ctrl = ctrlState.isActive()
            keySender.sendKey(ic, keyCode, ctrl)
            if (ctrl) ctrlState.consume()
        }
        button.setOnTouchListener(listener)
    }

    private fun wireUpload() {
        val uploadButton = view.findViewById<View>(R.id.key_upload)
        if (uploadHandler != null && uploadHandler.isAvailable && onUploadTap != null) {
            uploadButton.setOnClickListener { onUploadTap.invoke() }
            uploadButton.setOnLongClickListener {
                Toast.makeText(view.context, "Configure hosts in KeyJawn settings", Toast.LENGTH_SHORT).show()
                true
            }
        } else if (uploadHandler != null && !uploadHandler.isAvailable) {
            wirePlaceholder(R.id.key_upload, "SCP upload available in full version")
        } else {
            wirePlaceholder(R.id.key_upload, "Upload not yet configured")
        }
    }

    private fun wireMic() {
        val micButton = view.findViewById<View>(R.id.key_mic)
        if (voiceInputHandler != null) {
            voiceInputHandler.setup(micButton, inputConnectionProvider)
            micButton.setOnClickListener {
                if (voiceInputHandler.isListening()) {
                    voiceInputHandler.stopListening()
                } else {
                    voiceInputHandler.startListening()
                }
            }
        } else {
            micButton.setOnClickListener {
                Toast.makeText(view.context, "Voice input not available", Toast.LENGTH_SHORT).show()
            }
        }

        voiceStop?.setOnClickListener {
            voiceInputHandler?.stopListening()
        }

        voiceInputHandler?.listener = object : VoiceInputListener {
            override fun onVoiceStart() {
                extraRow.visibility = View.GONE
                voiceBar?.visibility = View.VISIBLE
                voiceText?.text = ""
                voiceWaveform?.reset()
            }

            override fun onVoiceStop() {
                voiceBar?.visibility = View.GONE
                extraRow.visibility = View.VISIBLE
            }

            override fun onPartialResult(text: String) {
                voiceText?.text = text
                (voiceText?.parent as? android.widget.HorizontalScrollView)?.post {
                    (voiceText.parent as? android.widget.HorizontalScrollView)?.fullScroll(View.FOCUS_RIGHT)
                }
            }

            override fun onFinalResult(text: String) {
                voiceText?.text = text
            }

            override fun onRmsChanged(rmsdB: Float) {
                voiceWaveform?.updateRms(rmsdB)
            }

            override fun onError() {
                voiceText?.text = ""
            }
        }
    }

    private fun wirePlaceholder(buttonId: Int, message: String) {
        view.findViewById<View>(buttonId).setOnClickListener {
            Toast.makeText(view.context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCtrlAppearance(mode: CtrlMode) {
        val tm = themeManager
        if (tm != null) {
            when (mode) {
                CtrlMode.OFF -> ctrlButton.background = tm.createExtraRowButtonDrawable(tm.keyBg())
                CtrlMode.ARMED -> ctrlButton.background = tm.createFlatDrawable(tm.accent())
                CtrlMode.LOCKED -> ctrlButton.background = tm.createFlatDrawable(tm.accentLocked())
            }
        } else {
            val bgRes = when (mode) {
                CtrlMode.OFF -> R.drawable.key_bg
                CtrlMode.ARMED -> R.drawable.key_bg_active
                CtrlMode.LOCKED -> R.drawable.key_bg_locked
            }
            ctrlButton.setBackgroundResource(bgRes)
        }
    }
}
