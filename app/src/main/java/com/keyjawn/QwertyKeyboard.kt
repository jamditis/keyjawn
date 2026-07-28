package com.keyjawn

import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

enum class ShiftState {
    OFF,
    SINGLE,
    CAPS_LOCK
}

class QwertyKeyboard(
    private val container: QwertyKeyboardView,
    private val keySender: KeySender,
    private val extraRowManager: ExtraRowManager,
    private val inputConnectionProvider: () -> InputConnection?,
    private val appPrefs: AppPrefs? = null,
    private val slashPopup: SlashCommandPopup? = null,
    private val themeManager: ThemeManager? = null,
    private val keyPreview: KeyPreview? = null,
    private val haptics: KeyboardHaptics = KeyboardHaptics(
        container,
        enabled = { appPrefs?.isHapticEnabled() != false }
    )
) {

    private val altKeyPopup = AltKeyPopup(keySender, inputConnectionProvider, themeManager)
    private val density = container.context.resources.displayMetrics.density
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val activeTouches = HashMap<Int, ActiveTouch>()

    internal var currentSlideSession: AltKeyPopup.SlideSession? = null
        private set

    var currentLayer: Int = KeyboardLayouts.LAYER_LOWER
        private set

    var shiftState: ShiftState = ShiftState.OFF
        private set

    var currentPackage: String = "unknown"
        private set

    private var lastShiftTapTime: Long = 0
    private val doubleTapThresholdMs = 400L
    private var lastSpaceTime: Long = 0
    private var lastWasSpace: Boolean = false
    private var currentImeAction: Int = EditorInfo.IME_ACTION_UNSPECIFIED
    private var currentImeFlags: Int = 0
    private var inputTypeQuickKeyOverride: String? = null
    private var editorDisplayDirty = false
    private var autocorrectOn: Boolean =
        appPrefs?.isAutocorrectEnabled(currentPackage) ?: false
    private var fastKeyOutput: Boolean = appPrefs?.isFastKeyOutput() ?: true
    private var previewHideRunnable: Runnable? = null
    private var activeSpacePointerId = MotionEvent.INVALID_POINTER_ID

    private sealed interface ActiveTouch

    private class CharacterTouch(
        val key: Key,
        val pressedLabel: String,
        val bounds: RectF,
        var touchActive: Boolean = true,
        var emitted: Boolean = false,
        var emittedLength: Int = 0,
        var longPressRunnable: Runnable? = null,
        var slideSession: AltKeyPopup.SlideSession? = null,
        var surfaceScreenX: Int = 0,
        var surfaceScreenY: Int = 0
    ) : ActiveTouch

    private class GenericTouch(
        val key: Key,
        val bounds: RectF,
        var touchActive: Boolean = true,
        var longPressFired: Boolean = false,
        var longPressRunnable: Runnable? = null
    ) : ActiveTouch

    private data object BackspaceTouch : ActiveTouch
    private data object SpaceTouch : ActiveTouch
    private data object IgnoredTouch : ActiveTouch

    private val defaultKeyText = container.context.getColor(R.color.key_text)
    private val defaultKeyBg = container.context.getColor(R.color.key_bg)
    private val defaultSpecialBg = container.context.getColor(R.color.key_special_bg)
    private val defaultPressed = container.context.getColor(R.color.key_bg_pressed)
    private val defaultHint = container.context.getColor(R.color.key_hint)
    private val defaultAccent = container.context.getColor(R.color.accent)
    private val defaultAccentLocked = container.context.getColor(R.color.accent_locked)

    private val backspaceTouchListener = BackspaceTouchListener(
        onDeleteChar = {
            inputConnectionProvider()?.let { ic ->
                keySender.sendKey(ic, KeyEvent.KEYCODE_DEL)
            }
        },
        onDeleteWord = { deleteWordOrCharacter() },
        onHaptic = { haptics.repeatPress() }
    )

    private val spacebarCursorController = SpacebarCursorController(
        keySender = keySender,
        inputConnectionProvider = inputConnectionProvider,
        onTap = {
            KeyboardLayouts.getLayer(currentLayer)
                .flatten()
                .firstOrNull { it.output is KeyOutput.Space }
                ?.let(::handleKeyPress)
        },
        onLongPress = {
            if (activeSpacePointerId != MotionEvent.INVALID_POINTER_ID) {
                container.setPointerPressed(activeSpacePointerId, false)
            }
            toggleAutocorrect()
        },
        hapticView = container,
        appPrefs = appPrefs
    )

    private val rowSwipeDetector = SwipeGestureDetector { direction ->
        val ic = inputConnectionProvider() ?: return@SwipeGestureDetector false
        val handled = when (direction) {
            SwipeGestureDetector.SwipeDirection.LEFT -> {
                keySender.sendKey(ic, KeyEvent.KEYCODE_DEL, ctrl = true)
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
                if (currentLayer == KeyboardLayouts.LAYER_SYMBOLS ||
                    currentLayer == KeyboardLayouts.LAYER_SYMBOLS2
                ) {
                    shiftState = ShiftState.OFF
                    setLayer(KeyboardLayouts.LAYER_LOWER)
                }
                true
            }
        }
        if (handled) haptics.perform(HapticFeedbackConstants.LONG_PRESS)
        handled
    }

    init {
        container.listener = object : QwertyKeyboardView.Listener {
            override fun onKeyPointer(
                cell: QwertyKeyboardView.KeyCell,
                sample: QwertyKeyboardView.PointerSample
            ): Boolean {
                return handlePointer(cell, sample)
            }

            override fun onGapTouch(
                view: QwertyKeyboardView,
                event: MotionEvent
            ): Boolean = rowSwipeDetector.onTouch(view, event)

            override fun onVirtualClick(cell: QwertyKeyboardView.KeyCell): Boolean {
                performVirtualClick(cell.key)
                return true
            }

            override fun onVirtualLongClick(cell: QwertyKeyboardView.KeyCell): Boolean =
                performVirtualLongClick(cell.key)
        }
    }

    fun updatePackage(packageName: String) {
        val packageChanged = packageName != currentPackage
        if (packageChanged) {
            currentPackage = packageName
            refreshTypingPrefs()
        }
        if (packageChanged || editorDisplayDirty) render()
    }

    fun refreshAutocorrect() {
        autocorrectOn = appPrefs?.isAutocorrectEnabled(currentPackage) ?: false
    }

    fun refreshTypingPrefs() {
        refreshAutocorrect()
        fastKeyOutput = appPrefs?.isFastKeyOutput() ?: true
    }

    fun applyAutoCapitalize() {
        if (shiftState == ShiftState.CAPS_LOCK) return
        if (currentLayer != KeyboardLayouts.LAYER_LOWER &&
            currentLayer != KeyboardLayouts.LAYER_UPPER
        ) {
            return
        }
        val context = inputConnectionProvider()?.getTextBeforeCursor(2, 0)
        val wantsCapital = autocorrectOn && isSentenceStart(context)
        if (wantsCapital && shiftState == ShiftState.OFF) {
            shiftState = ShiftState.SINGLE
            setLayer(KeyboardLayouts.LAYER_UPPER)
        } else if (!wantsCapital && shiftState == ShiftState.SINGLE) {
            shiftState = ShiftState.OFF
            setLayer(KeyboardLayouts.LAYER_LOWER)
        }
    }

    fun resetTransientState() {
        lastWasSpace = false
        lastSpaceTime = 0
    }

    fun updateImeAction(action: Int, flags: Int) {
        if (action != currentImeAction || flags != currentImeFlags) {
            currentImeAction = action
            currentImeFlags = flags
            editorDisplayDirty = true
        }
    }

    fun updateInputType(inputType: Int) {
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        val override = when (variation) {
            InputType.TYPE_TEXT_VARIATION_URI -> "/"
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS -> "@"
            else -> null
        }
        if (override != inputTypeQuickKeyOverride) {
            inputTypeQuickKeyOverride = override
            editorDisplayDirty = true
        }
    }

    fun isAutocorrectOn(): Boolean = autocorrectOn

    fun setLayer(layer: Int) {
        currentLayer = layer
        render()
    }

    fun refreshRender() = render()

    private fun render() {
        val rows = KeyboardLayouts.getLayer(currentLayer).map { row ->
            row.map(::renderedKey)
        }
        container.submit(rows)
        editorDisplayDirty = false
    }

    private fun renderedKey(key: Key): QwertyKeyboardView.RenderedKey {
        val tm = themeManager
        var label = key.label
        var background = tm?.keyBg() ?: defaultKeyBg

        val special = key.output is KeyOutput.Shift ||
            key.output is KeyOutput.Backspace ||
            key.output is KeyOutput.Enter ||
            key.output is KeyOutput.SymSwitch ||
            key.output is KeyOutput.Sym2Switch ||
            key.output is KeyOutput.AbcSwitch ||
            key.output is KeyOutput.Space ||
            key.output is KeyOutput.QuickKey
        if (special) background = tm?.keySpecialBg() ?: defaultSpecialBg

        when (key.output) {
            is KeyOutput.Shift -> {
                background = when (shiftState) {
                    ShiftState.OFF -> tm?.keySpecialBg() ?: defaultSpecialBg
                    ShiftState.SINGLE -> tm?.accent() ?: defaultAccent
                    ShiftState.CAPS_LOCK -> tm?.accentLocked() ?: defaultAccentLocked
                }
            }
            is KeyOutput.Space -> {
                if (isAutocorrectOn()) label = "SPACE"
            }
            is KeyOutput.Enter -> {
                val noEnterAction =
                    currentImeFlags and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
                if (!noEnterAction) {
                    val actionLabel = when (currentImeAction) {
                        EditorInfo.IME_ACTION_GO -> "Go"
                        EditorInfo.IME_ACTION_SEND -> "Send"
                        EditorInfo.IME_ACTION_SEARCH -> "Search"
                        EditorInfo.IME_ACTION_NEXT -> "Next"
                        EditorInfo.IME_ACTION_DONE -> "Done"
                        else -> null
                    }
                    if (actionLabel != null) {
                        label = actionLabel
                        background =
                            tm?.accent() ?: defaultAccent
                    }
                }
            }
            is KeyOutput.QuickKey -> {
                val quickKey =
                    inputTypeQuickKeyOverride ?: appPrefs?.getQuickKey() ?: "/"
                label = quickKey.removePrefix("text:")
                background = tm?.quickKeyBg() ?: DEFAULT_QUICK_KEY_BG
            }
            else -> Unit
        }

        val textSize = when (key.output) {
            is KeyOutput.Character, is KeyOutput.QuickKey -> 18f
            else -> 13f
        }
        val hint = if (key.output is KeyOutput.Character) {
            AltKeyMappings.getAlts(key.label)?.firstOrNull()
        } else {
            null
        }
        return QwertyKeyboardView.RenderedKey(
            key = key,
            label = label,
            backgroundColor = background,
            textColor = tm?.keyText() ?: defaultKeyText,
            pressedColor = tm?.keyBgPressed() ?: defaultPressed,
            hint = hint,
            hintColor = tm?.keyHint() ?: defaultHint,
            textSizeSp = textSize
        )
    }

    private fun handlePointer(
        cell: QwertyKeyboardView.KeyCell,
        sample: QwertyKeyboardView.PointerSample
    ): Boolean {
        when (sample.action) {
            MotionEvent.ACTION_DOWN -> beginTouch(cell, sample)
            MotionEvent.ACTION_MOVE -> moveTouch(sample)
            MotionEvent.ACTION_UP -> endTouch(sample, cancelled = false)
            MotionEvent.ACTION_CANCEL -> endTouch(sample, cancelled = true)
        }
        return true
    }

    private fun beginTouch(
        cell: QwertyKeyboardView.KeyCell,
        sample: QwertyKeyboardView.PointerSample
    ) {
        if (currentSlideSession != null) {
            activeTouches[sample.pointerId] = IgnoredTouch
            container.setPointerPressed(sample.pointerId, false)
            return
        }
        when (cell.key.output) {
            is KeyOutput.Character -> beginCharacterTouch(cell, sample)
            is KeyOutput.Backspace -> {
                activeTouches[sample.pointerId] = BackspaceTouch
                dispatchToListener(backspaceTouchListener, sample)
            }
            is KeyOutput.Space -> {
                activeSpacePointerId = sample.pointerId
                activeTouches[sample.pointerId] = SpaceTouch
                dispatchToListener(spacebarCursorController, sample)
            }
            else -> beginGenericTouch(cell, sample)
        }
    }

    private fun beginCharacterTouch(
        cell: QwertyKeyboardView.KeyCell,
        sample: QwertyKeyboardView.PointerSample
    ) {
        previewHideRunnable?.let(longPressHandler::removeCallbacks)
        previewHideRunnable = null

        val session = CharacterTouch(
            key = cell.key,
            pressedLabel = cell.rendered.label,
            bounds = RectF(cell.bounds)
        )
        activeTouches[sample.pointerId] = session
        keyPreview?.show(container, session.bounds, session.pressedLabel)

        val ctrlWasActive = extraRowManager.isCtrlActive()
        if (fastKeyOutput) {
            handleKeyPress(session.key)
            session.emitted = true
            session.emittedLength =
                if (ctrlWasActive) 0 else (session.key.output as KeyOutput.Character).char.length
        }

        val alts = if (fastKeyOutput && ctrlWasActive) {
            null
        } else {
            AltKeyMappings.getAlts(session.pressedLabel)
        }
        if (alts != null) {
            val runnable = Runnable {
                if (!session.touchActive) return@Runnable
                if (currentSlideSession != null) return@Runnable
                session.touchActive = false
                container.setPointerPressed(sample.pointerId, false)
                keyPreview?.hide()
                if (session.emittedLength > 0) {
                    inputConnectionProvider()
                        ?.deleteSurroundingText(session.emittedLength, 0)
                    session.emittedLength = 0
                }
                if (alts.size == 1) {
                    inputConnectionProvider()?.let { ic ->
                        keySender.sendText(ic, alts[0])
                        haptics.confirm()
                    }
                } else {
                    val location = IntArray(2)
                    container.getLocationOnScreen(location)
                    session.surfaceScreenX = location[0]
                    session.surfaceScreenY = location[1]
                    val slide = altKeyPopup.openForSlide(container, session.bounds, alts)
                    session.slideSession = slide
                    currentSlideSession = slide
                }
            }
            session.longPressRunnable = runnable
            longPressHandler.postDelayed(runnable, LONG_PRESS_MS)
        }
    }

    private fun beginGenericTouch(
        cell: QwertyKeyboardView.KeyCell,
        sample: QwertyKeyboardView.PointerSample
    ) {
        val session = GenericTouch(cell.key, RectF(cell.bounds))
        activeTouches[sample.pointerId] = session
        if (cell.key.output is KeyOutput.QuickKey) {
            val runnable = Runnable {
                if (!session.touchActive) return@Runnable
                session.touchActive = false
                session.longPressFired = true
                container.setPointerPressed(sample.pointerId, false)
                extraRowManager.showQuickKeyPicker()
            }
            session.longPressRunnable = runnable
            longPressHandler.postDelayed(runnable, LONG_PRESS_MS)
        }
    }

    private fun moveTouch(sample: QwertyKeyboardView.PointerSample) {
        when (val session = activeTouches[sample.pointerId]) {
            is CharacterTouch -> moveCharacterTouch(sample, session)
            is GenericTouch -> {
                if (session.touchActive && !insideWithSlop(session.bounds, sample)) {
                    session.touchActive = false
                    session.longPressRunnable?.let(longPressHandler::removeCallbacks)
                    session.longPressRunnable = null
                    container.setPointerPressed(sample.pointerId, false)
                }
            }
            BackspaceTouch -> dispatchToListener(backspaceTouchListener, sample)
            SpaceTouch -> dispatchToListener(spacebarCursorController, sample)
            IgnoredTouch -> Unit
            null -> Unit
        }
    }

    private fun moveCharacterTouch(
        sample: QwertyKeyboardView.PointerSample,
        session: CharacterTouch
    ) {
        val slide = session.slideSession
        if (slide != null) {
            slide.onMove(
                session.surfaceScreenX + sample.x,
                session.surfaceScreenY + sample.y
            )
            return
        }
        if (session.touchActive && !insideCharacterSlop(session.bounds, sample)) {
            session.touchActive = false
            container.setPointerPressed(sample.pointerId, false)
            keyPreview?.hide()
            session.longPressRunnable?.let(longPressHandler::removeCallbacks)
            session.longPressRunnable = null
        }
    }

    private fun endTouch(
        sample: QwertyKeyboardView.PointerSample,
        cancelled: Boolean
    ) {
        when (val session = activeTouches.remove(sample.pointerId)) {
            is CharacterTouch -> endCharacterTouch(sample, session, cancelled)
            is GenericTouch -> {
                session.longPressRunnable?.let(longPressHandler::removeCallbacks)
                if (!cancelled && session.touchActive && !session.longPressFired) {
                    if (session.key.output is KeyOutput.Shift) {
                        handleShiftTap()
                    } else {
                        handleKeyPress(session.key)
                    }
                }
            }
            BackspaceTouch -> dispatchToListener(backspaceTouchListener, sample)
            SpaceTouch -> {
                dispatchToListener(spacebarCursorController, sample)
                if (activeSpacePointerId == sample.pointerId) {
                    activeSpacePointerId = MotionEvent.INVALID_POINTER_ID
                }
            }
            IgnoredTouch -> Unit
            null -> Unit
        }
    }

    private fun endCharacterTouch(
        sample: QwertyKeyboardView.PointerSample,
        session: CharacterTouch,
        cancelled: Boolean
    ) {
        schedulePreviewHide()
        session.longPressRunnable?.let(longPressHandler::removeCallbacks)
        session.longPressRunnable = null

        val slide = session.slideSession
        if (slide != null) {
            if (cancelled) {
                cancelSlide(slide)
            } else {
                val selected = slide.onRelease(
                    session.surfaceScreenX + sample.x,
                    session.surfaceScreenY + sample.y
                )
                if (selected != null) {
                    inputConnectionProvider()?.let { ic ->
                        keySender.sendText(ic, selected)
                        haptics.confirm()
                    }
                }
                slide.dismiss()
                if (currentSlideSession === slide) currentSlideSession = null
            }
        } else if (!cancelled && !session.emitted && session.touchActive) {
            handleKeyPress(session.key)
        }
        session.touchActive = false
    }

    private fun dispatchToListener(
        listener: android.view.View.OnTouchListener,
        sample: QwertyKeyboardView.PointerSample
    ) {
        val event = MotionEvent.obtain(
            sample.downTime,
            sample.eventTime,
            sample.action,
            sample.x,
            sample.y,
            0
        )
        try {
            listener.onTouch(container, event)
        } finally {
            event.recycle()
        }
    }

    private fun insideWithSlop(
        bounds: RectF,
        sample: QwertyKeyboardView.PointerSample
    ): Boolean {
        val slop = dp(8f)
        return sample.x >= bounds.left - slop &&
            sample.x <= bounds.right + slop &&
            sample.y >= bounds.top - slop &&
            sample.y <= bounds.bottom + slop
    }

    private fun insideCharacterSlop(
        bounds: RectF,
        sample: QwertyKeyboardView.PointerSample
    ): Boolean {
        val horizontalSlop = dp(4f)
        return sample.x >= bounds.left - horizontalSlop &&
            sample.x <= bounds.right + horizontalSlop &&
            sample.y >= bounds.top - bounds.height() &&
            sample.y <= bounds.bottom + bounds.height()
    }

    private fun schedulePreviewHide() {
        val runnable = Runnable { keyPreview?.hide() }
        previewHideRunnable = runnable
        longPressHandler.postDelayed(runnable, PREVIEW_HIDE_MS)
    }

    private fun cancelSlide(session: AltKeyPopup.SlideSession) {
        session.dismiss()
        if (currentSlideSession === session) currentSlideSession = null
    }

    private fun performVirtualClick(key: Key) {
        when (key.output) {
            is KeyOutput.Backspace -> {
                haptics.repeatPress()
                inputConnectionProvider()?.let { ic ->
                    keySender.sendKey(ic, KeyEvent.KEYCODE_DEL)
                }
            }
            is KeyOutput.Shift -> handleShiftTap()
            else -> handleKeyPress(key)
        }
    }

    private fun performVirtualLongClick(key: Key): Boolean {
        return when (key.output) {
            is KeyOutput.Character -> {
                val alt = AltKeyMappings.getAlts(key.label)?.firstOrNull() ?: return false
                inputConnectionProvider()?.let { ic ->
                    keySender.sendText(ic, alt)
                    haptics.confirm()
                }
                true
            }
            is KeyOutput.Backspace -> {
                haptics.perform(HapticFeedbackConstants.LONG_PRESS)
                deleteWordOrCharacter()
                true
            }
            is KeyOutput.Space -> {
                toggleAutocorrect()
                true
            }
            is KeyOutput.QuickKey -> {
                extraRowManager.showQuickKeyPicker()
                true
            }
            else -> false
        }
    }

    private fun toggleAutocorrect() {
        val enabled = appPrefs?.toggleAutocorrect(currentPackage) ?: false
        autocorrectOn = enabled
        extraRowManager.showTooltip("Autocorrect ${if (enabled) "on" else "off"}")
        render()
    }

    private fun deleteWordOrCharacter() {
        inputConnectionProvider()?.let { ic ->
            if (!keySender.deleteWordBefore(ic)) {
                keySender.sendKey(ic, KeyEvent.KEYCODE_DEL)
            }
        }
    }

    private fun handleKeyPress(key: Key) {
        haptics.key(key.output)
        if (key.output !is KeyOutput.Space) lastWasSpace = false

        val ic = inputConnectionProvider() ?: return
        when (key.output) {
            is KeyOutput.Character -> {
                val ctrlActive = extraRowManager.isCtrlActive()
                if (ctrlActive) {
                    val keyCode = ctrlKeyCode(key.output.char.firstOrNull() ?: ' ')
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
                    setLayer(KeyboardLayouts.LAYER_LOWER)
                }
            }
            is KeyOutput.Enter -> {
                val noEnterAction =
                    currentImeFlags and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
                val hasSpecificAction =
                    !noEnterAction &&
                        currentImeAction != EditorInfo.IME_ACTION_UNSPECIFIED &&
                        currentImeAction != EditorInfo.IME_ACTION_NONE
                if (hasSpecificAction) {
                    ic.performEditorAction(currentImeAction)
                } else {
                    keySender.sendKey(ic, KeyEvent.KEYCODE_ENTER)
                }
            }
            is KeyOutput.Space -> {
                val now = System.currentTimeMillis()
                if (isAutocorrectOn() && lastWasSpace && now - lastSpaceTime < 350L) {
                    ic.deleteSurroundingText(1, 0)
                    keySender.sendText(ic, ". ")
                    lastWasSpace = false
                    if (shiftState == ShiftState.OFF &&
                        (currentLayer == KeyboardLayouts.LAYER_LOWER ||
                            currentLayer == KeyboardLayouts.LAYER_UPPER)
                    ) {
                        shiftState = ShiftState.SINGLE
                        setLayer(KeyboardLayouts.LAYER_UPPER)
                    }
                } else {
                    keySender.sendChar(ic, " ")
                    lastWasSpace = true
                    lastSpaceTime = now
                    if (isAutocorrectOn() && shiftState == ShiftState.OFF &&
                        (currentLayer == KeyboardLayouts.LAYER_LOWER ||
                            currentLayer == KeyboardLayouts.LAYER_UPPER) &&
                        isSentenceStart(ic.getTextBeforeCursor(2, 0))
                    ) {
                        shiftState = ShiftState.SINGLE
                        setLayer(KeyboardLayouts.LAYER_UPPER)
                    }
                }
            }
            is KeyOutput.SymSwitch -> setLayer(KeyboardLayouts.LAYER_SYMBOLS)
            is KeyOutput.Sym2Switch -> setLayer(KeyboardLayouts.LAYER_SYMBOLS2)
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
                val quickKey =
                    inputTypeQuickKeyOverride ?: appPrefs?.getQuickKey() ?: "/"
                keySender.sendChar(ic, quickKey.removePrefix("text:"))
            }
            is KeyOutput.KeyCode -> keySender.sendKey(ic, key.output.code)
            is KeyOutput.Shift, is KeyOutput.Backspace -> Unit
        }
    }

    private fun handleShiftTap() {
        haptics.perform(HapticFeedbackConstants.CLOCK_TICK)
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
                    render()
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

    private fun dp(value: Float): Float = value * density

    companion object {
        private const val LONG_PRESS_MS = 500L
        private const val PREVIEW_HIDE_MS = 300L
        private const val DEFAULT_QUICK_KEY_BG = 0xFF2A3A4A.toInt()

        fun isSentenceStart(before: CharSequence?): Boolean {
            if (before.isNullOrEmpty()) return true
            if (before.last() == '\n') return true
            if (before.length < 2) return false
            return before.last() == ' ' && before[before.length - 2] in ".?!"
        }

        fun ctrlKeyCode(c: Char): Int {
            val lower = c.lowercaseChar()
            return if (lower in 'a'..'z') {
                KeyEvent.KEYCODE_A + (lower - 'a')
            } else {
                KeyEvent.KEYCODE_UNKNOWN
            }
        }
    }
}
