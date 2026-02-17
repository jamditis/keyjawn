package com.keyjawn

import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

enum class ShiftState {
    OFF,
    SINGLE,
    CAPS_LOCK
}

class QwertyKeyboard(
    private val container: LinearLayout,
    private val keySender: KeySender,
    private val extraRowManager: ExtraRowManager,
    private val inputConnectionProvider: () -> InputConnection?,
    private val appPrefs: AppPrefs? = null,
    private val slashPopup: SlashCommandPopup? = null,
    private val themeManager: ThemeManager? = null,
    private val keyPreview: KeyPreview? = null
) {

    private val altKeyPopup = AltKeyPopup(keySender, inputConnectionProvider, themeManager)
    private var quickKeyButton: Button? = null

    var currentLayer: Int = KeyboardLayouts.LAYER_LOWER
        private set

    var shiftState: ShiftState = ShiftState.OFF
        private set

    var currentPackage: String = "unknown"
        private set

    private var shiftButton: Button? = null
    private var lastShiftTapTime: Long = 0
    private val doubleTapThresholdMs = 400L

    // Double-tap space to period (item 3)
    private var lastSpaceTime: Long = 0
    private var lastWasSpace: Boolean = false

    // Adaptive enter key (item 7)
    private var currentImeAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED
    private var currentImeFlags: Int = 0

    // EditorInfo inputType hints (item 11)
    private var inputTypeQuickKeyOverride: String? = null

    // Touch drag-off long-press handler
    private val longPressHandler = Handler(Looper.getMainLooper())

    fun updatePackage(packageName: String) {
        currentPackage = packageName
        render()
    }

    fun resetTransientState() {
        lastWasSpace = false
        lastSpaceTime = 0
    }

    fun updateImeAction(action: Int, flags: Int) {
        currentImeAction = action
        currentImeFlags = flags
    }

    fun updateInputType(inputType: Int) {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        inputTypeQuickKeyOverride = when (variation) {
            InputType.TYPE_TEXT_VARIATION_URI -> "/"
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> "@"
            else -> null
        }
    }

    fun isAutocorrectOn(): Boolean {
        return appPrefs?.isAutocorrectEnabled(currentPackage) ?: false
    }

    fun setLayer(layer: Int) {
        currentLayer = layer
        render()
    }

    private fun render() {
        container.removeAllViews()
        val layout = KeyboardLayouts.getLayer(currentLayer)
        val context = container.context

        for (row in layout) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val hPad = dpToPx(2)
                val vPad = dpToPx(2)
                setPadding(hPad, vPad, hPad, vPad)
            }

            for (key in row) {
                val keyView = createKeyView(key)
                val params = LinearLayout.LayoutParams(0, dpToPx(48), key.weight)
                val margin = dpToPx(2)
                params.setMargins(margin, margin, margin, margin)
                keyView.layoutParams = params
                rowLayout.addView(keyView)
            }

            container.addView(rowLayout)
        }

        // Swipe gestures on each row's padding area (not on the container,
        // which can interfere with child button touch dispatch)
        for (i in 0 until container.childCount) {
            val rowLayout = container.getChildAt(i) as? LinearLayout ?: continue
            rowLayout.setOnTouchListener(SwipeGestureDetector { direction ->
                val ic = inputConnectionProvider() ?: return@SwipeGestureDetector false
                val handled = when (direction) {
                    SwipeGestureDetector.SwipeDirection.LEFT -> {
                        keySender.sendKey(ic, android.view.KeyEvent.KEYCODE_DEL, ctrl = true)
                        true
                    }
                    SwipeGestureDetector.SwipeDirection.RIGHT -> {
                        keySender.sendChar(ic, " ")
                        true
                    }
                    SwipeGestureDetector.SwipeDirection.UP -> {
                        if (currentLayer != KeyboardLayouts.LAYER_SYMBOLS) {
                            setLayer(KeyboardLayouts.LAYER_SYMBOLS)
                        }
                        true
                    }
                    SwipeGestureDetector.SwipeDirection.DOWN -> {
                        if (currentLayer == KeyboardLayouts.LAYER_SYMBOLS || currentLayer == KeyboardLayouts.LAYER_SYMBOLS2) {
                            shiftState = ShiftState.OFF
                            setLayer(KeyboardLayouts.LAYER_LOWER)
                        }
                        true
                    }
                }
                if (handled) performHaptic(HapticFeedbackConstants.LONG_PRESS)
                handled
            })
        }
    }

    private fun createKeyView(key: Key): View {
        val context = container.context
        val tm = themeManager
        val button = Button(context).apply {
            text = key.label
            isAllCaps = false
            if (tm != null) {
                background = tm.createKeyDrawable(tm.keyBg())
                setTextColor(tm.keyText())
            } else {
                setBackgroundResource(R.drawable.key_bg)
                setTextColor(context.getColor(R.color.key_text))
            }
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            typeface = Typeface.MONOSPACE
        }

        val isSpecialKey = key.output is KeyOutput.Shift ||
            key.output is KeyOutput.Backspace ||
            key.output is KeyOutput.Enter ||
            key.output is KeyOutput.SymSwitch ||
            key.output is KeyOutput.Sym2Switch ||
            key.output is KeyOutput.AbcSwitch ||
            key.output is KeyOutput.Space ||
            key.output is KeyOutput.QuickKey

        if (isSpecialKey) {
            val bg = if (key.output is KeyOutput.QuickKey) {
                tm?.quickKeyBg() ?: tm?.keySpecialBg()
            } else null
            if (tm != null) {
                button.background = tm.createKeyDrawable(bg ?: tm.keySpecialBg())
            } else {
                button.setBackgroundResource(R.drawable.key_bg_special)
            }
            button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        if (key.output is KeyOutput.Space && isAutocorrectOn()) {
            button.text = "SPACE"
        }

        // Adaptive enter key label (item 7)
        if (key.output is KeyOutput.Enter) {
            val noEnterAction = currentImeFlags and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
            if (!noEnterAction) {
                val label = when (currentImeAction) {
                    EditorInfo.IME_ACTION_GO -> "Go"
                    EditorInfo.IME_ACTION_SEND -> "Send"
                    EditorInfo.IME_ACTION_SEARCH -> "Search"
                    EditorInfo.IME_ACTION_NEXT -> "Next"
                    EditorInfo.IME_ACTION_DONE -> "Done"
                    else -> null
                }
                if (label != null) {
                    button.text = label
                    if (tm != null) {
                        button.background = tm.createKeyDrawable(tm.accent())
                    }
                }
            }
        }

        val textSize = when (key.output) {
            is KeyOutput.Character -> 18f
            is KeyOutput.Shift, is KeyOutput.Backspace,
            is KeyOutput.Enter, is KeyOutput.SymSwitch,
            is KeyOutput.Sym2Switch, is KeyOutput.AbcSwitch -> 13f
            is KeyOutput.Space -> 13f
            is KeyOutput.Slash -> 13f
            is KeyOutput.QuickKey -> 18f
            is KeyOutput.KeyCode -> 13f
        }
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSize)

        when (key.output) {
            is KeyOutput.Shift -> {
                shiftButton = button
                updateShiftAppearance(button)
                button.setOnClickListener { handleShiftTap() }
            }
            is KeyOutput.Backspace -> {
                val listener = RepeatTouchListener {
                    performHaptic()
                    val ic = inputConnectionProvider() ?: return@RepeatTouchListener
                    keySender.sendKey(ic, KeyEvent.KEYCODE_DEL)
                }
                button.setOnTouchListener(listener)
            }
            else -> {
                button.setOnClickListener { handleKeyPress(key) }
            }
        }

        if (key.output is KeyOutput.Space) {
            val cursorController = SpacebarCursorController(
                keySender = keySender,
                inputConnectionProvider = inputConnectionProvider,
                onTap = { handleKeyPress(key) },
                onLongPress = {
                    val enabled = appPrefs?.toggleAutocorrect(currentPackage) ?: false
                    val state = if (enabled) "on" else "off"
                    extraRowManager.showTooltip("Autocorrect $state")
                    render()
                },
                hapticView = container,
                appPrefs = appPrefs
            )
            button.setOnTouchListener(cursorController)
        }

        if (key.output is KeyOutput.QuickKey) {
            val override = inputTypeQuickKeyOverride
            val currentQuickKey = override ?: appPrefs?.getQuickKey() ?: "/"
            val displayKey = if (currentQuickKey.startsWith("text:")) currentQuickKey.removePrefix("text:") else currentQuickKey
            button.text = displayKey
            quickKeyButton = button
            button.setOnLongClickListener {
                extraRowManager.showQuickKeyPicker()
                true
            }
        }

        // Character key touch handling with drag-off cancellation (item 4)
        val alts = if (key.output is KeyOutput.Character) AltKeyMappings.getAlts(key.label) else null
        if (key.output is KeyOutput.Character) {
            val previewLabel = key.label
            var touchStarted = false
            var longPressRunnable: Runnable? = null

            button.setOnClickListener(null) // Remove default click -- handled by touch
            button.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStarted = true
                        v.isPressed = true
                        keyPreview?.show(v, previewLabel)

                        // Schedule long-press for alt keys
                        if (alts != null) {
                            val runnable = Runnable {
                                touchStarted = false
                                keyPreview?.hide()
                                if (alts.size == 1) {
                                    val ic = inputConnectionProvider() ?: return@Runnable
                                    keySender.sendText(ic, alts[0])
                                } else {
                                    altKeyPopup.show(v, alts)
                                }
                            }
                            longPressRunnable = runnable
                            longPressHandler.postDelayed(runnable, 500L)
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val inBounds = event.x >= 0 && event.x <= v.width &&
                            event.y >= -v.height && event.y <= v.height * 2
                        if (!inBounds && touchStarted) {
                            touchStarted = false
                            v.isPressed = false
                            keyPreview?.hide()
                            longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                            longPressRunnable = null
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        keyPreview?.hide()
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        longPressRunnable = null
                        if (touchStarted && v.isPressed) {
                            handleKeyPress(key)
                        }
                        touchStarted = false
                        v.isPressed = false
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        keyPreview?.hide()
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        longPressRunnable = null
                        touchStarted = false
                        v.isPressed = false
                        true
                    }
                    else -> false
                }
            }
        }

        // Wrap character keys that have alts in a FrameLayout with a hint label
        if (key.output is KeyOutput.Character && alts != null) {
            val hintChar = if (alts.size == 1) alts[0] else alts[0]
            button.background = null
            val frame = FrameLayout(context).apply {
                if (tm != null) {
                    background = tm.createKeyDrawable(tm.keyBg())
                } else {
                    setBackgroundResource(R.drawable.key_bg)
                }
            }
            button.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            frame.addView(button)
            val hint = TextView(context).apply {
                text = hintChar
                setTextColor(if (tm != null) tm.keyHint() else context.getColor(R.color.key_hint))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END
                ).apply {
                    topMargin = dpToPx(1)
                    marginEnd = dpToPx(2)
                }
            }
            frame.addView(hint)
            return frame
        }

        return button
    }

    private fun performHaptic(type: Int = HapticFeedbackConstants.KEYBOARD_TAP) {
        if (appPrefs?.isHapticEnabled() != false) {
            container.performHapticFeedback(type)
        }
    }

    private fun handleKeyPress(key: Key) {
        // Determine haptic type based on key output (item 6)
        val hapticType = when (key.output) {
            is KeyOutput.Enter -> if (Build.VERSION.SDK_INT >= 27)
                HapticFeedbackConstants.KEYBOARD_PRESS else HapticFeedbackConstants.KEYBOARD_TAP
            else -> HapticFeedbackConstants.KEYBOARD_TAP
        }
        performHaptic(hapticType)

        // Reset double-tap space tracking for any non-space key (item 3)
        if (key.output !is KeyOutput.Space) {
            lastWasSpace = false
        }

        val ic = inputConnectionProvider() ?: return

        when (key.output) {
            is KeyOutput.Character -> {
                val ctrlActive = extraRowManager.isCtrlActive()
                if (ctrlActive) {
                    val charCode = key.output.char.lowercase()[0]
                    val keyCode = KeyEvent.keyCodeFromString("KEYCODE_${charCode.uppercaseChar()}")
                    if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
                        keySender.sendText(ic, key.output.char)
                    } else {
                        keySender.sendKey(ic, keyCode, ctrl = true)
                    }
                    extraRowManager.consumeCtrl()
                } else {
                    keySender.sendChar(ic, key.output.char)
                }
                if (shiftState == ShiftState.SINGLE) {
                    shiftState = ShiftState.OFF
                    container.post { setLayer(KeyboardLayouts.LAYER_LOWER) }
                }
            }
            is KeyOutput.Enter -> {
                // Adaptive enter: use performEditorAction for specific IME actions (item 7)
                val noEnterAction = currentImeFlags and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
                val hasSpecificAction = !noEnterAction && currentImeAction != EditorInfo.IME_ACTION_UNSPECIFIED
                    && currentImeAction != EditorInfo.IME_ACTION_NONE
                if (hasSpecificAction) {
                    ic.performEditorAction(currentImeAction)
                } else {
                    keySender.sendKey(ic, KeyEvent.KEYCODE_ENTER)
                }
            }
            is KeyOutput.Space -> {
                val now = System.currentTimeMillis()
                if (isAutocorrectOn() && lastWasSpace && now - lastSpaceTime < 350L) {
                    // Double-tap space: replace trailing space with period+space (item 3)
                    ic.deleteSurroundingText(1, 0)
                    keySender.sendText(ic, ". ")
                    lastWasSpace = false
                    // Auto-capitalize after sentence-ending punctuation (item 10)
                    if (shiftState == ShiftState.OFF &&
                        (currentLayer == KeyboardLayouts.LAYER_LOWER || currentLayer == KeyboardLayouts.LAYER_UPPER)) {
                        shiftState = ShiftState.SINGLE
                        container.post { setLayer(KeyboardLayouts.LAYER_UPPER) }
                    }
                } else {
                    keySender.sendChar(ic, " ")
                    lastWasSpace = true
                    lastSpaceTime = now
                    // Auto-capitalize after sentence-ending punctuation (item 10)
                    if (isAutocorrectOn() && shiftState == ShiftState.OFF &&
                        (currentLayer == KeyboardLayouts.LAYER_LOWER || currentLayer == KeyboardLayouts.LAYER_UPPER)) {
                        val before = ic.getTextBeforeCursor(2, 0)?.toString()
                        if (before == ". " || before == "? " || before == "! ") {
                            shiftState = ShiftState.SINGLE
                            container.post { setLayer(KeyboardLayouts.LAYER_UPPER) }
                        }
                    }
                }
            }
            is KeyOutput.SymSwitch -> {
                setLayer(KeyboardLayouts.LAYER_SYMBOLS)
            }
            is KeyOutput.Sym2Switch -> {
                setLayer(KeyboardLayouts.LAYER_SYMBOLS2)
            }
            is KeyOutput.AbcSwitch -> {
                shiftState = ShiftState.OFF
                setLayer(KeyboardLayouts.LAYER_LOWER)
            }
            is KeyOutput.Slash -> {
                if (slashPopup != null) {
                    slashPopup.show(container)
                } else {
                    keySender.sendText(ic, "/")
                }
            }
            is KeyOutput.QuickKey -> {
                val override = inputTypeQuickKeyOverride
                val quickChar = override ?: appPrefs?.getQuickKey() ?: "/"
                val text = if (quickChar.startsWith("text:")) quickChar.removePrefix("text:") else quickChar
                keySender.sendChar(ic, text)
            }
            is KeyOutput.KeyCode -> {
                keySender.sendKey(ic, key.output.code)
            }
            is KeyOutput.Shift -> { /* handled separately */ }
            is KeyOutput.Backspace -> { /* handled via RepeatTouchListener */ }
        }
    }

    private fun handleShiftTap() {
        performHaptic(HapticFeedbackConstants.CLOCK_TICK)
        val now = System.currentTimeMillis()
        val timeSinceLastTap = now - lastShiftTapTime
        lastShiftTapTime = now

        when (shiftState) {
            ShiftState.OFF -> {
                shiftState = ShiftState.SINGLE
                setLayer(KeyboardLayouts.LAYER_UPPER)
            }
            ShiftState.SINGLE -> {
                if (timeSinceLastTap < doubleTapThresholdMs) {
                    shiftState = ShiftState.CAPS_LOCK
                    updateShiftAppearance(shiftButton)
                } else {
                    shiftState = ShiftState.OFF
                    setLayer(KeyboardLayouts.LAYER_LOWER)
                }
            }
            ShiftState.CAPS_LOCK -> {
                shiftState = ShiftState.OFF
                setLayer(KeyboardLayouts.LAYER_LOWER)
            }
        }
    }

    private fun updateShiftAppearance(button: Button?) {
        button ?: return
        val tm = themeManager
        if (tm != null) {
            when (shiftState) {
                ShiftState.OFF -> button.background = tm.createKeyDrawable(tm.keySpecialBg())
                ShiftState.SINGLE -> button.background = tm.createFlatDrawable(tm.accent())
                ShiftState.CAPS_LOCK -> button.background = tm.createFlatDrawable(tm.accentLocked())
            }
        } else {
            when (shiftState) {
                ShiftState.OFF -> button.setBackgroundResource(R.drawable.key_bg_special)
                ShiftState.SINGLE -> button.setBackgroundResource(R.drawable.key_bg_active)
                ShiftState.CAPS_LOCK -> button.setBackgroundResource(R.drawable.key_bg_locked)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = container.context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}
