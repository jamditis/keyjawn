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
    private val voiceInputHandler: VoiceInputHandler? = null
) {

    val ctrlState = CtrlState()

    private val ctrlButton: Button = view.findViewById(R.id.key_ctrl)

    init {
        wireEsc()
        wireTab()
        wireCtrl()
        wireArrow(R.id.key_left, KeyEvent.KEYCODE_DPAD_LEFT)
        wireArrow(R.id.key_down, KeyEvent.KEYCODE_DPAD_DOWN)
        wireArrow(R.id.key_up, KeyEvent.KEYCODE_DPAD_UP)
        wireArrow(R.id.key_right, KeyEvent.KEYCODE_DPAD_RIGHT)
        wirePlaceholder(R.id.key_upload, "Upload not yet configured")
        wireMic()

        ctrlState.onStateChanged = { mode -> updateCtrlAppearance(mode) }
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

    private fun wireMic() {
        val micButton = view.findViewById<Button>(R.id.key_mic)
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
            wirePlaceholder(R.id.key_mic, "Voice input not available")
        }
    }

    private fun wirePlaceholder(buttonId: Int, message: String) {
        view.findViewById<Button>(buttonId).setOnClickListener {
            Toast.makeText(view.context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateCtrlAppearance(mode: CtrlMode) {
        val bgRes = when (mode) {
            CtrlMode.OFF -> R.drawable.key_bg
            CtrlMode.ARMED -> R.drawable.key_bg_active
            CtrlMode.LOCKED -> R.drawable.key_bg_locked
        }
        ctrlButton.setBackgroundResource(bgRes)
    }
}
