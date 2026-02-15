package com.keyjawn

import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

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
    private val themeManager: ThemeManager? = null
) {

    private val altKeyPopup = AltKeyPopup(keySender, inputConnectionProvider)
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

    fun updatePackage(packageName: String) {
        currentPackage = packageName
        render()
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
                when (direction) {
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
            if (tm != null) {
                button.background = tm.createKeyDrawable(tm.keySpecialBg())
            } else {
                button.setBackgroundResource(R.drawable.key_bg_special)
            }
            button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        if (key.output is KeyOutput.Space && isAutocorrectOn()) {
            button.text = "SPACE"
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
            button.setOnLongClickListener {
                val enabled = appPrefs?.toggleAutocorrect(currentPackage) ?: false
                val state = if (enabled) "on" else "off"
                Toast.makeText(container.context, "Autocorrect $state", Toast.LENGTH_SHORT).show()
                render()
                true
            }
        }

        if (key.output is KeyOutput.QuickKey) {
            val currentQuickKey = appPrefs?.getQuickKey() ?: "/"
            button.text = currentQuickKey
            quickKeyButton = button
            button.setOnLongClickListener {
                val options = AppPrefs.QUICK_KEY_OPTIONS
                altKeyPopup.show(button, options, onSelect = { selected ->
                    appPrefs?.setQuickKey(selected)
                    button.text = selected
                })
                true
            }
        }

        // Alt key long-press for Character keys
        val alts = if (key.output is KeyOutput.Character) AltKeyMappings.getAlts(key.label) else null
        if (key.output is KeyOutput.Character && alts != null) {
            button.setOnLongClickListener {
                if (alts.size == 1) {
                    val ic = inputConnectionProvider() ?: return@setOnLongClickListener true
                    keySender.sendText(ic, alts[0])
                } else {
                    altKeyPopup.show(button, alts)
                }
                true
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

    private fun performHaptic() {
        if (appPrefs?.isHapticEnabled() != false) {
            container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    private fun handleKeyPress(key: Key) {
        performHaptic()
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
                keySender.sendKey(ic, KeyEvent.KEYCODE_ENTER)
            }
            is KeyOutput.Space -> {
                keySender.sendChar(ic, " ")
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
                val quickChar = appPrefs?.getQuickKey() ?: "/"
                keySender.sendChar(ic, quickChar)
            }
            is KeyOutput.KeyCode -> {
                keySender.sendKey(ic, key.output.code)
            }
            is KeyOutput.Shift -> { /* handled separately */ }
            is KeyOutput.Backspace -> { /* handled via RepeatTouchListener */ }
        }
    }

    private fun handleShiftTap() {
        performHaptic()
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
