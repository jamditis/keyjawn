package com.keyjawn

import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
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
    private val currentPackageProvider: (() -> String)? = null
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

    private val handler = Handler(Looper.getMainLooper())
    private var tooltipDismissRunnable: Runnable? = null

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

        ctrlState.onStateChanged = { mode -> updateCtrlAppearance(mode) }
    }

    fun showTooltip(message: String, durationMs: Long = 1500L) {
        val bar = tooltipBar ?: return
        if (!AppPrefs(view.context).isTooltipsEnabled()) return
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
                    val ic = inputConnectionProvider() ?: return@setOnClickListener
                    keySender.sendText(ic, text)
                }
                if (isFullFlavor) {
                    button.setOnLongClickListener { showSlotPicker(slotIndex); true }
                }
            }
        }
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
                onBottomPaddingChanged = { onBottomPaddingChanged?.invoke() }
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
                if (voiceInputHandler.isListening()) {
                    voiceInputHandler.stopListening()
                } else {
                    voiceInputHandler.startListening()
                }
            }
        } else {
            micButton.setOnClickListener {
                showTooltip("Voice input not available")
            }
        }

        voiceStop?.setOnClickListener {
            voiceInputHandler?.stopListening()
        }

        voiceInputHandler?.onPermissionNeeded = { msg -> showTooltip(msg, 2500L) }

        voiceInputHandler?.listener = object : VoiceInputListener {
            override fun onVoiceStart() {
                // Dismiss tooltip if showing
                tooltipDismissRunnable?.let { handler.removeCallbacks(it) }
                tooltipBar?.visibility = View.GONE
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
            showTooltip(message)
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
