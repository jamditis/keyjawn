package com.keyjawn

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.UnderlineSpan
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ExtraRowManager(
    private val view: View,
    private val keySender: KeySender,
    private val inputConnectionProvider: () -> InputConnection?,
    private val voiceInputHandler: VoiceInputHandler? = null,
    private val uploadHandler: UploadHandler? = null,
    private val onUploadTap: (() -> Unit)? = null,
    private val clipboardHistoryManager: ClipboardHistoryManager? = null,
    private val themeManager: ThemeManager? = null,
    private val isPaidUser: Boolean = false,
    private val clipboardPanelView: ScrollView? = null,
    private val clipboardListView: LinearLayout? = null,
    private val menuPanelView: ScrollView? = null,
    private val menuListView: LinearLayout? = null,
    private val appPrefs: AppPrefs? = null,
    private val isFullFlavor: Boolean = false,
    private val onOpenSettings: (() -> Unit)? = null,
    private val onThemeChanged: (() -> Unit)? = null,
    private val currentPackageProvider: (() -> String)? = null,
    private val onAutocorrectChanged: (() -> Unit)? = null,
    private val onTypingPrefsChanged: (() -> Unit)? = null
) {

    val ctrlState = CtrlState()

    private val ctrlButton: Button = view.findViewById(R.id.key_ctrl)

    private var clipboardPanel: ClipboardPanel? = null
    private var menuPanel: MenuPanel? = null

    private val extraRow: View = view.findViewById(R.id.extra_row)
    private val tooltipBar: TextView? = view.findViewById(R.id.tooltip_bar)
    private val voiceBar: View? = view.findViewById(R.id.voice_bar)
    private val voiceWaveform: VoiceWaveformView? = voiceBar?.findViewById(R.id.voice_waveform)
    private val voiceText: TextView? = voiceBar?.findViewById(R.id.voice_text)
    private val voiceStop: View? = voiceBar?.findViewById(R.id.voice_stop)
    private val voiceCancel: View? = voiceBar?.findViewById(R.id.voice_cancel)

    private val handler = Handler(Looper.getMainLooper())
    private var tooltipDismissRunnable: Runnable? = null

    /** Pending push-to-talk start, cancelled if the press turns out to be a tap. */
    private var micHoldRunnable: Runnable? = null

    /** The current physical press crossed the push-to-talk threshold. */
    private var micHoldTriggered = false

    /** The next mic click is the tail of a hold and must not re-open the mic. */
    private var suppressNextMicClick = false

    var onQuickKeyChanged: ((String) -> Unit)? = null
    var onBottomPaddingChanged: (() -> Unit)? = null

    init {
        wireSlot(0, R.id.key_esc)
        wireSlot(1, R.id.key_tab)
        wireClipboard()
        wireSlot(2, R.id.key_ctrl)
        wireArrow(R.id.key_left, KeyEvent.KEYCODE_DPAD_LEFT)
        wireArrow(R.id.key_down, KeyEvent.KEYCODE_DPAD_DOWN)
        wireArrow(R.id.key_up, KeyEvent.KEYCODE_DPAD_UP)
        wireArrow(R.id.key_right, KeyEvent.KEYCODE_DPAD_RIGHT)
        wireUpload()
        wireMic()

        applyThemeColors()

        ctrlState.onStateChanged = { mode ->
            updateCtrlAppearance(mode)
            // Brief transition feedback; OFF (fired on every armed-key consumption)
            // returns null so it doesn't pop a tooltip on every keystroke.
            ctrlTransitionMessage(mode)?.let { showTooltip(it, 900L) }
        }
    }

    fun showTooltip(message: String, durationMs: Long = 1500L, critical: Boolean = false) {
        val bar = tooltipBar ?: return
        // The tooltips preference governs transient hints only (long-press
        // discoverability, paste no-ops, state-transition feedback). Critical
        // messages -- upload results and speech-recognition errors/permission
        // prompts -- are the user's only feedback for an action they took, so
        // they bypass the gate. Reuse the injected long-lived AppPrefs instead
        // of constructing a new one (and a getSharedPreferences lookup) on every
        // tooltip. Wiring paths that pass no appPrefs keep the prior default-on
        // behavior.
        if (!critical && appPrefs?.isTooltipsEnabled() == false) return
        tooltipDismissRunnable?.let { handler.removeCallbacks(it) }
        bar.text = message
        extraRow.visibility = View.GONE
        bar.visibility = View.VISIBLE
        val dismiss = Runnable {
            bar.visibility = View.GONE
            extraRow.visibility = View.VISIBLE
        }
        tooltipDismissRunnable = dismiss
        handler.postDelayed(dismiss, durationMs)
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
        // Theme the tooltip bar
        tooltipBar?.setBackgroundColor(tm.extraRowBg())
        tooltipBar?.setTextColor(tm.keyText())
    }

    fun isCtrlActive(): Boolean = ctrlState.isActive()

    fun consumeCtrl(): Boolean = ctrlState.consume()

    fun wireSlot(slotIndex: Int, buttonId: Int) {
        val button = view.findViewById<Button>(buttonId)
        val defaults = arrayOf("keycode:KEYCODE_ESCAPE", "keycode:KEYCODE_TAB", "ctrl")
        val config = appPrefs?.getExtraSlot(slotIndex)
            ?: defaults.getOrElse(slotIndex) { return }

        // Clear existing listeners
        button.setOnClickListener(null)
        button.setOnLongClickListener(null)

        when {
            config == "ctrl" -> {
                button.text = "Ctrl"
                button.setOnClickListener { ctrlState.tap() }
                if (isFullFlavor) {
                    button.setOnLongClickListener { showSlotPicker(slotIndex); true }
                } else {
                    button.setOnLongClickListener { ctrlState.longPress(); true }
                }
            }
            config.startsWith("keycode:") -> {
                val keyCodeName = config.removePrefix("keycode:")
                val keyCode = try {
                    android.view.KeyEvent::class.java.getField(keyCodeName).getInt(null)
                } catch (_: Exception) {
                    android.view.KeyEvent.KEYCODE_ESCAPE
                }
                button.text = AppPrefs.getExtraSlotLabel(config)
                button.setOnClickListener {
                    performHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                    val ic = inputConnectionProvider() ?: return@setOnClickListener
                    keySender.sendKey(ic, keyCode)
                }
                if (isFullFlavor) {
                    button.setOnLongClickListener { showSlotPicker(slotIndex); true }
                }
            }
            config.startsWith("text:") -> {
                val text = config.removePrefix("text:")
                button.text = text
                button.setOnClickListener {
                    performHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                    val ic = inputConnectionProvider() ?: return@setOnClickListener
                    keySender.sendText(ic, text)
                }
                if (isFullFlavor) {
                    button.setOnLongClickListener { showSlotPicker(slotIndex); true }
                }
            }
        }
    }

    fun dismissPanels() {
        menuPanel?.hide()
        clipboardPanel?.hide()
    }

    fun rewireSlots() {
        wireSlot(0, R.id.key_esc)
        wireSlot(1, R.id.key_tab)
        wireSlot(2, R.id.key_ctrl)
        applyThemeColors()
    }

    fun showSlotPicker(slotIndex: Int) {
        menuPanel?.showSlotPicker(slotIndex) { value ->
            appPrefs?.setExtraSlot(slotIndex, value)
            rewireSlots()
        }
    }

    fun showQuickKeyPicker() {
        menuPanel?.showQuickKeyPicker { value ->
            appPrefs?.setQuickKey(value)
            onQuickKeyChanged?.invoke(value)
        }
    }

    private fun wireClipboard() {
        val clipButton = view.findViewById<View>(R.id.key_clipboard)
        if (clipboardHistoryManager != null && clipboardPanelView != null && clipboardListView != null) {
            val panel = ClipboardPanel(
                clipboardHistoryManager, isPaidUser,
                clipboardPanelView, clipboardListView,
                themeManager,
                onItemSelected = { text ->
                    val ic = inputConnectionProvider() ?: return@ClipboardPanel
                    clipboardHistoryManager.pasteItem(ic, text)
                },
                onShowTooltip = { msg -> showTooltip(msg) }
            )
            clipboardPanel = panel
            clipButton.setOnClickListener {
                if (panel.isShowing()) {
                    panel.hide()
                } else {
                    menuPanel?.hide()
                    panel.show()
                }
            }
            clipButton.setOnLongClickListener {
                val ic = inputConnectionProvider() ?: return@setOnLongClickListener true
                if (!clipboardHistoryManager.paste(ic)) {
                    showTooltip("Clipboard empty")
                }
                true
            }
        } else {
            // Fallback: simple system paste (no history tracking)
            val pasteFromSystem = {
                val ic = inputConnectionProvider()
                if (ic != null) {
                    val cm = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = cm.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val text = clip.getItemAt(0).coerceToText(view.context)
                        if (text.isNotEmpty()) {
                            ic.commitText(text, 1)
                        } else {
                            showTooltip("Clipboard empty")
                        }
                    } else {
                        showTooltip("Clipboard empty")
                    }
                }
            }
            clipButton.setOnClickListener { pasteFromSystem() }
            clipButton.setOnLongClickListener { pasteFromSystem(); true }
        }
    }

    private fun wireArrow(buttonId: Int, keyCode: Int) {
        val button = view.findViewById<Button>(buttonId)
        val listener = RepeatTouchListener(
            onPress = { performHaptic(HapticFeedbackConstants.KEYBOARD_TAP) }
        ) {
            val ic = inputConnectionProvider() ?: return@RepeatTouchListener
            val ctrl = ctrlState.isActive()
            keySender.sendKey(ic, keyCode, ctrl)
            if (ctrl) ctrlState.consume()
        }
        button.setOnTouchListener(listener)
    }

    private fun wireUpload() {
        val uploadButton = view.findViewById<View>(R.id.key_upload)
        // Route upload status to the in-keyboard tooltip bar instead of a Toast.
        // Upload results land after a picker round-trip, so use the longer duration
        // (the same the voice permission path uses) to give the user time to read.
        // Critical: an upload's success/failure is its only feedback, so it must
        // surface even when transient tooltips are disabled (was a Toast before).
        uploadHandler?.onShowStatus = { msg -> showTooltip(msg, 2500L, critical = true) }
        if (menuPanelView != null && menuListView != null && themeManager != null && appPrefs != null) {
            val mp = MenuPanel(
                panel = menuPanelView,
                list = menuListView,
                themeManager = themeManager,
                appPrefs = appPrefs,
                isFullFlavor = isFullFlavor,
                onUploadTap = onUploadTap,
                onOpenSettings = { onOpenSettings?.invoke() },
                onThemeChanged = { onThemeChanged?.invoke() },
                onShowTooltip = { msg -> showTooltip(msg) },
                currentPackageProvider = currentPackageProvider ?: { "unknown" },
                onBottomPaddingChanged = { onBottomPaddingChanged?.invoke() },
                onAutocorrectChanged = { onAutocorrectChanged?.invoke() },
                onTypingPrefsChanged = { onTypingPrefsChanged?.invoke() }
            )
            menuPanel = mp
            uploadButton.setOnClickListener {
                if (mp.isShowing()) {
                    mp.hide()
                } else {
                    clipboardPanel?.hide()
                    mp.show()
                }
            }
        } else if (uploadHandler != null && uploadHandler.isAvailable && onUploadTap != null) {
            uploadButton.setOnClickListener { onUploadTap.invoke() }
            uploadButton.setOnLongClickListener {
                showTooltip("Configure hosts in KeyJawn settings")
                true
            }
        } else {
            wirePlaceholder(R.id.key_upload, "Upload not yet configured")
        }
    }

    private fun wireMic() {
        val micButton = view.findViewById<View>(R.id.key_mic)
        if (voiceInputHandler != null) {
            voiceInputHandler.setup(micButton, inputConnectionProvider)
            micButton.setOnClickListener {
                // A release that ended a push-to-talk hold is not a tap.
                if (suppressNextMicClick) {
                    suppressNextMicClick = false
                    return@setOnClickListener
                }
                // A tap toggles a hands-free session; isSessionActive covers the
                // gap between utterances in continuous mode, where the mic is
                // momentarily not listening but dictation is still running.
                if (voiceInputHandler.isSessionActive()) {
                    voiceInputHandler.stopListening()
                } else {
                    voiceInputHandler.startListening()
                }
            }
            // Push-to-talk: hold the mic, speak, let go. Faster than tap-speak-tap
            // for the one-line corrections that make up most terminal dictation,
            // and it cannot leave the microphone armed by accident.
            //
            // The session starts on its own short timer rather than on the
            // platform long-click, which does not fire until 500ms. People start
            // speaking the instant they press, so waiting for the long-click
            // would clip the front of every short correction -- exactly the
            // phrases push-to-talk exists for.
            micButton.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        micHoldTriggered = false
                        val runnable = Runnable {
                            micHoldRunnable = null
                            if (!voiceInputHandler.isSessionActive()) {
                                // Set this before startListening(): model setup can
                                // finish the handler session synchronously.
                                micHoldTriggered = true
                                voiceInputHandler.startListening(holdToTalk = true)
                            }
                        }
                        micHoldRunnable = runnable
                        handler.postDelayed(runnable, MIC_HOLD_START_MS)
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        micHoldRunnable?.let { handler.removeCallbacks(it) }
                        micHoldRunnable = null
                        // Only a hold ends on release; a tap-started session
                        // keeps running until the user taps again.
                        if (micHoldTriggered) {
                            micHoldTriggered = false
                            if (voiceInputHandler.isHoldToTalk()) {
                                voiceInputHandler.stopListening()
                            }
                            if (event.actionMasked == android.view.MotionEvent.ACTION_UP) {
                                // The click still fires after this touch listener
                                // returns, even when model setup already closed
                                // the handler's hold session.
                                suppressNextMicClick = true
                            }
                        }
                    }
                }
                // Never consume: the click listener above still needs the event
                // to reach the default View handling.
                false
            }
        } else {
            micButton.setOnClickListener {
                // Critical: tapping the mic with no handler must explain why
                // nothing happened, even when transient tooltips are off.
                showTooltip("Voice input not available", critical = true)
            }
        }

        voiceStop?.setOnClickListener {
            voiceInputHandler?.stopListening()
        }

        // Stop keeps what was heard; cancel throws the utterance away, including
        // the live preview already sitting in the editor. A misfire needs an
        // undo that does not require deleting text by hand.
        voiceCancel?.setOnClickListener {
            voiceInputHandler?.cancel()
        }

        // Critical: a permission prompt is actionable feedback the user must see
        // to proceed, so it bypasses the transient-tooltips gate.
        voiceInputHandler?.onPermissionNeeded = { msg -> showTooltip(msg, 2500L, critical = true) }

        voiceInputHandler?.listener = object : VoiceInputListener {
            override fun onVoiceStart() {
                // Dismiss tooltip if showing
                tooltipDismissRunnable?.let { handler.removeCallbacks(it) }
                tooltipBar?.visibility = View.GONE
                extraRow.visibility = View.GONE
                voiceBar?.visibility = View.VISIBLE
                voiceText?.text = "Listening"
                voiceWaveform?.reset()
            }

            override fun onVoiceReady() {
                voiceText?.text = "Listening"
            }

            override fun onVoiceProcessing() {
                // Reached only when no partial arrived for this utterance, so
                // there is no transcription on screen to overwrite.
                voiceText?.text = "Transcribing"
            }

            override fun onVoiceStatus(message: String) {
                voiceText?.text = ""
                showTooltip(message, 4000L, critical = true)
            }

            override fun onVoiceContinue() {
                // The utterance was committed and the mic is re-arming. Clear the
                // bar so the next sentence starts from an empty line instead of
                // reading as though it is still being heard.
                voiceText?.text = "Listening"
                voiceWaveform?.reset()
            }

            override fun onVoiceStop() {
                voiceBar?.visibility = View.GONE
                // An error in onError() may have raised the tooltip bar in place
                // of the extra row; don't clobber it by re-showing the extra row.
                if (tooltipBar?.visibility != View.VISIBLE) {
                    extraRow.visibility = View.VISIBLE
                }
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

            override fun onError(error: Int) {
                voiceText?.text = ""
                // Critical: a speech-recognition failure is the only signal the
                // user gets that dictation stopped, so it must always surface.
                showTooltip(voiceErrorMessage(error), critical = true)
            }
        }
    }

    companion object {
        /**
         * How long the mic key must be held before the press counts as
         * push-to-talk. Well under the platform's 500ms long-click so the
         * recognizer is already warming up as the user starts speaking, and far
         * enough above a deliberate tap (typically under 150ms) to stay
         * distinguishable from one.
         */
        const val MIC_HOLD_START_MS = 250L
    }

    private fun wirePlaceholder(buttonId: Int, message: String) {
        view.findViewById<View>(buttonId).setOnClickListener {
            showTooltip(message)
        }
    }

    private fun performHaptic(type: Int) {
        if (appPrefs?.isHapticEnabled() != false) {
            view.performHapticFeedback(type)
        }
    }

    private fun updateCtrlAppearance(mode: CtrlMode) {
        // Gated like every other haptic: the toggle claims to govern feedback
        // for the whole keyboard, and Ctrl was the one key still ignoring it.
        performHaptic(HapticFeedbackConstants.CONTEXT_CLICK)
        applyCtrlLabel(mode)
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

    /**
     * Carries the locked distinction through shape, not color alone: LOCKED
     * underlines the label so it survives color-blindness and dim screens, while
     * OFF and ARMED keep the plain label.
     */
    private fun applyCtrlLabel(mode: CtrlMode) {
        if (mode == CtrlMode.LOCKED) {
            val locked = SpannableString("Ctrl")
            locked.setSpan(UnderlineSpan(), 0, locked.length, SpannableString.SPAN_INCLUSIVE_EXCLUSIVE)
            ctrlButton.text = locked
        } else {
            ctrlButton.text = "Ctrl"
        }
    }
}
