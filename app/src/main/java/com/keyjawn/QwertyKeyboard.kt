package com.keyjawn

import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout
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
    private val slashPopup: SlashCommandPopup? = null
) {

    private val altKeyPopup = AltKeyPopup(keySender, inputConnectionProvider)

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
                val button = createKeyButton(key)
                val params = LinearLayout.LayoutParams(0, dpToPx(48), key.weight)
                val margin = dpToPx(2)
                params.setMargins(margin, margin, margin, margin)
                button.layoutParams = params
                rowLayout.addView(button)
            }

            container.addView(rowLayout)
        }
    }

    private fun createKeyButton(key: Key): Button {
        val context = container.context
        val button = Button(context).apply {
            text = key.label
            isAllCaps = false
            setBackgroundResource(R.drawable.key_bg)
            setTextColor(context.getColor(R.color.key_text))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 0)
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            typeface = Typeface.MONOSPACE
        }

        if (key.output is KeyOutput.Space && isAutocorrectOn()) {
            button.text = "SPACE"
        }

        val textSize = when (key.output) {
            is KeyOutput.Character -> 18f
            is KeyOutput.Shift, is KeyOutput.Backspace,
            is KeyOutput.Enter, is KeyOutput.SymSwitch,
            is KeyOutput.AbcSwitch -> 14f
            is KeyOutput.Space -> 14f
            is KeyOutput.Slash -> 14f
            is KeyOutput.KeyCode -> 14f
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

        if (key.output is KeyOutput.Slash) {
            button.setOnLongClickListener {
                val longIc = inputConnectionProvider() ?: return@setOnLongClickListener true
                keySender.sendText(longIc, ".")
                true
            }
        }

        // Alt key long-press for Character keys
        if (key.output is KeyOutput.Character) {
            val alts = AltKeyMappings.getAlts(key.label)
            if (alts != null) {
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
        }

        return button
    }

    private fun handleKeyPress(key: Key) {
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
                } else if (isAutocorrectOn()) {
                    ic.setComposingText(key.output.char, 1)
                } else {
                    keySender.sendChar(ic, key.output.char)
                }
                if (shiftState == ShiftState.SINGLE) {
                    shiftState = ShiftState.OFF
                    setLayer(KeyboardLayouts.LAYER_LOWER)
                }
            }
            is KeyOutput.Enter -> {
                keySender.sendKey(ic, KeyEvent.KEYCODE_ENTER)
            }
            is KeyOutput.Space -> {
                if (isAutocorrectOn()) {
                    ic.finishComposingText()
                }
                keySender.sendChar(ic, " ")
            }
            is KeyOutput.SymSwitch -> {
                setLayer(KeyboardLayouts.LAYER_SYMBOLS)
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
            is KeyOutput.KeyCode -> {
                keySender.sendKey(ic, key.output.code)
            }
            is KeyOutput.Shift -> { /* handled separately */ }
            is KeyOutput.Backspace -> { /* handled via RepeatTouchListener */ }
        }
    }

    private fun handleShiftTap() {
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
        when (shiftState) {
            ShiftState.OFF -> button.setBackgroundResource(R.drawable.key_bg)
            ShiftState.SINGLE -> button.setBackgroundResource(R.drawable.key_bg_active)
            ShiftState.CAPS_LOCK -> button.setBackgroundResource(R.drawable.key_bg_locked)
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = container.context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}
