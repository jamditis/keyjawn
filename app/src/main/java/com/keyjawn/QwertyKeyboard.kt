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

    // The slide-and-release session for the currently open alt-key popup, or null
    // when no multi-alt popup is up. The character key's touch listener routes the
    // tracked finger's MOVE/UP into it. Exposed for tests to drive the gesture.
    internal var currentSlideSession: AltKeyPopup.SlideSession? = null
        private set

    // The layer the current view tree was last built for. render() compares it
    // against currentLayer and bails when nothing structural changed, so the most
    // common interactions (same-layer re-renders) do not tear the grid down.
    // -1 means "nothing built yet" so the first render always runs.
    private var renderedLayer: Int = -1

    // One holder per Character key in the built grid, in row/col order. The holder
    // is the single source of truth for what a character key currently shows and
    // emits: its touch listener dereferences holder.key at touch time (never a
    // build-time capture), so the visible label and the sent character cannot
    // diverge. applyShiftCase() mutates holder.key in place on a shift toggle.
    private class CharKeyHolder(
        val rowIndex: Int,
        val colIndex: Int,
        val button: Button,
        var key: Key
    ) {
        var hint: TextView? = null
    }

    private val charHolders = mutableListOf<CharKeyHolder>()

    // Display density never changes for the keyboard view's lifetime (the view
    // is rebuilt on a configuration change), so cache it once instead of reading
    // resources.displayMetrics on every dpToPx call across every key per render.
    private val density: Float = container.context.resources.displayMetrics.density

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

    // Set by updateImeAction/updateInputType when the editor-derived display state
    // (adaptive Enter label, input-type quick-key override) actually changes.
    // updatePackage consumes it to force one render on a same-package focus switch
    // -- e.g. moving from a text field to a search or URL field in the same app --
    // which the #29 same-package early-return would otherwise skip, leaving a stale
    // Enter label or quick key. Change-detection in the setters keeps a refocus
    // with identical editor state from forcing a needless rebuild. Cleared by
    // render() once a rebuild consumes it.
    private var editorDisplayDirty = false

    // Touch drag-off long-press handler
    private val longPressHandler = Handler(Looper.getMainLooper())

    // Cached per-package autocorrect flag, read from prefs only at the boundary
    // events that can change it (package change and the autocorrect toggle)
    // instead of on every render and Space-key handler call. refreshAutocorrect()
    // is the single update point so every write site invalidates the same field.
    private var autocorrectOn: Boolean = appPrefs?.isAutocorrectEnabled(currentPackage) ?: false

    // Read on every ACTION_DOWN, so it is cached alongside autocorrect rather
    // than hitting SharedPreferences on the touch path.
    private var fastKeyOutput: Boolean = appPrefs?.isFastKeyOutput() ?: true

    // Delayed key preview hide — cancelled when next key is pressed
    private var previewHideRunnable: Runnable? = null

    fun updatePackage(packageName: String) {
        val packageChanged = packageName != currentPackage
        if (packageChanged) {
            currentPackage = packageName
            refreshTypingPrefs()
        }
        // This is the single render point for a focus change: onStartInputView
        // calls updateImeAction/updateInputType first (recording any editor-state
        // change), then this. Render once when the package changed (per-package
        // autocorrect can flip the spacebar "space"/"SPACE" keycap) OR the editor
        // display state changed (adaptive Enter label, input-type quick-key
        // override). A same-package refocus with no change keeps the existing
        // grid (issue #29). render() clears editorDisplayDirty.
        if (packageChanged || editorDisplayDirty) {
            refreshRender()
        }
    }

    /** Re-read the autocorrect flag for the current package into the cache. */
    fun refreshAutocorrect() {
        autocorrectOn = appPrefs?.isAutocorrectEnabled(currentPackage) ?: false
    }

    /**
     * Re-read every pref the touch path reads per keystroke. Call this at the
     * boundaries that can change them (focus change, settings toggle) so the
     * hot path never touches SharedPreferences.
     */
    fun refreshTypingPrefs() {
        refreshAutocorrect()
        fastKeyOutput = appPrefs?.isFastKeyOutput() ?: true
    }

    /**
     * Arms one-shot shift when the cursor sits at the start of a sentence, so
     * the first letter typed into an empty field or after a full stop is capital
     * without a shift tap. Gated on autocorrect, the per-app switch that already
     * governs the other assistive text behaviours.
     */
    fun applyAutoCapitalize() {
        if (!autocorrectOn) return
        if (shiftState != ShiftState.OFF) return
        if (currentLayer != KeyboardLayouts.LAYER_LOWER) return
        val ic = inputConnectionProvider() ?: return
        if (!isSentenceStart(ic.getTextBeforeCursor(2, 0))) return
        shiftState = ShiftState.SINGLE
        setLayer(KeyboardLayouts.LAYER_UPPER)
    }

    fun resetTransientState() {
        lastWasSpace = false
        lastSpaceTime = 0
    }

    fun updateImeAction(action: Int, flags: Int) {
        // Mark the display dirty only on a real change so a refocus with the same
        // action does not force a rebuild (preserves the #29 same-package skip).
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
        val previous = currentLayer
        currentLayer = layer
        // A shift toggle (lower<->upper) swaps only the per-key labels; the grid
        // structure is position-identical between the two layers. When the other
        // case is already built, relabel the existing letter buttons in place
        // instead of tearing the grid down. Any other transition (to/from the
        // symbol layers) swaps the whole key SET and key COUNT, so it must do a
        // full rebuild.
        val isShiftToggle =
            (previous == KeyboardLayouts.LAYER_LOWER && layer == KeyboardLayouts.LAYER_UPPER) ||
            (previous == KeyboardLayouts.LAYER_UPPER && layer == KeyboardLayouts.LAYER_LOWER)
        if (isShiftToggle && charHolders.isNotEmpty()) {
            if (!applyShiftCase(layer)) render(force = true)
        } else {
            render()
        }
    }

    /**
     * Applies a layer change requested from inside a touch handler.
     *
     * A lower<->upper toggle only relabels existing buttons, which is safe with
     * a finger down and saves the frame that posting it would cost -- and on the
     * one-shot shift path that frame lands on every capitalized letter typed.
     * Anything that would restructure the grid still goes through post(),
     * because tearing down the very view currently dispatching the touch is not.
     */
    private fun setLayerDuringTouch(layer: Int) {
        val previous = currentLayer
        val isShiftToggle =
            (previous == KeyboardLayouts.LAYER_LOWER && layer == KeyboardLayouts.LAYER_UPPER) ||
            (previous == KeyboardLayouts.LAYER_UPPER && layer == KeyboardLayouts.LAYER_LOWER)
        if (isShiftToggle && charHolders.isNotEmpty()) {
            currentLayer = layer
            if (!applyShiftCase(layer)) container.post { render(force = true) }
        } else {
            container.post { setLayer(layer) }
        }
    }

    /**
     * Force a rebuild of the grid even when the layer is unchanged. Use this from
     * any caller that changes what a key DISPLAYS without changing the layer (the
     * spacebar "space"/"SPACE" keycap, the quick-key label). A plain setLayer to
     * the current layer is a no-op under render()'s same-layer guard, so those
     * refreshers must come through here.
     */
    fun refreshRender() = render(force = true)

    /**
     * Rebuilds the QWERTY grid from scratch for [currentLayer].
     *
     * Guarded so a same-layer call is a no-op: re-rendering the layer that is
     * already on screen would tear down and re-inflate ~34 views for nothing.
     * Any caller that needs to refresh displayed content WITHOUT a layer change
     * (spacebar keycap, quick-key label) must use [refreshRender] / force = true,
     * not a setLayer(currentLayer) that this guard would swallow.
     */
    private fun render(force: Boolean = false) {
        if (!force && currentLayer == renderedLayer) return
        container.removeAllViews()
        charHolders.clear()
        val layout = KeyboardLayouts.getLayer(currentLayer)
        val context = container.context

        for ((rowIndex, row) in layout.withIndex()) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val hPad = dpToPx(1)
                val vPad = dpToPx(1)
                setPadding(hPad, vPad, hPad, vPad)
            }

            for ((colIndex, key) in row.withIndex()) {
                val keyView = createKeyView(key, rowIndex, colIndex)
                val params = LinearLayout.LayoutParams(0, dpToPx(48), key.weight)
                val margin = dpToPx(1)
                params.setMargins(margin, margin, margin, margin)
                keyView.layoutParams = params
                rowLayout.addView(keyView)
            }

            container.addView(rowLayout)
        }
        renderedLayer = currentLayer
        // This rebuild reflects the current editor state (Enter label, quick-key
        // override), so any pending editor-display change is now consumed.
        editorDisplayDirty = false

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

    private fun createKeyView(key: Key, rowIndex: Int, colIndex: Int): View {
        val context = container.context
        val tm = themeManager
        // Character keys with alt characters render their key background on the
        // wrapping FrameLayout, so the button itself stays transparent. Resolve
        // alts up front and skip building a button background that would only be
        // discarded for those keys; the button's platform-default background is
        // cleared instead so the themed frame shows through.
        val alts = if (key.output is KeyOutput.Character) AltKeyMappings.getAlts(key.label) else null
        val buttonHasOwnBackground = alts == null
        val button = Button(context).apply {
            text = key.label
            isAllCaps = false
            if (tm != null) {
                if (buttonHasOwnBackground) {
                    background = tm.createKeyDrawable(tm.keyBg())
                } else {
                    background = null
                }
                setTextColor(tm.keyText())
            } else {
                if (buttonHasOwnBackground) {
                    setBackgroundResource(R.drawable.key_bg)
                } else {
                    background = null
                }
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
                button.setOnTouchListener(
                    BackspaceTouchListener(
                        onDeleteChar = {
                            inputConnectionProvider()?.let { ic ->
                                keySender.sendKey(ic, KeyEvent.KEYCODE_DEL)
                            }
                        },
                        onDeleteWord = {
                            inputConnectionProvider()?.let { ic ->
                                // Nothing to take as a word (an empty field, a
                                // lone newline) still deletes one character, so
                                // a hold never stalls with the finger down.
                                if (!keySender.deleteWordBefore(ic)) {
                                    keySender.sendKey(ic, KeyEvent.KEYCODE_DEL)
                                }
                            }
                        },
                        onHaptic = { performHaptic() }
                    )
                )
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
                    autocorrectOn = enabled
                    val state = if (enabled) "on" else "off"
                    extraRowManager.showTooltip("Autocorrect $state")
                    // The spacebar keycap ("space" vs "SPACE") tracks autocorrect
                    // but the layer is unchanged, so force the rebuild past the
                    // same-layer guard.
                    refreshRender()
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
        // (alts was resolved at the top of this method.)
        if (key.output is KeyOutput.Character) {
            // Register this character key so a shift toggle can relabel it in
            // place. holder.key is the single source of truth: the listener below
            // reads holder.key at touch time, never the build-time `key`, so the
            // visible label and the emitted character stay in sync after a
            // lower<->upper relabel.
            val holder = CharKeyHolder(rowIndex, colIndex, button, key)
            charHolders.add(holder)
            var touchStarted = false
            var longPressRunnable: Runnable? = null
            // Emit-on-press bookkeeping. `emitted` says the key already produced
            // its output at ACTION_DOWN so ACTION_UP must not repeat it;
            // `emittedLength` is how many characters a following long-press has
            // to take back before the alt lands (0 for a Ctrl combo, which sent
            // a key event rather than text).
            var emitted = false
            var emittedLength = 0
            // Slide-and-release state. slideSession is non-null once a multi-alt
            // popup is open for this key; while it is, the tracked finger's
            // MOVE/UP route into the popup instead of typing the base char.
            var slideSession: AltKeyPopup.SlideSession? = null
            var activePointerId = MotionEvent.INVALID_POINTER_ID
            var anchorScreenX = 0
            var anchorScreenY = 0

            button.setOnClickListener(null) // Remove default click -- handled by touch
            button.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStarted = true
                        activePointerId = event.getPointerId(0)
                        v.isPressed = true
                        previewHideRunnable?.let { longPressHandler.removeCallbacks(it) }
                        previewHideRunnable = null
                        keyPreview?.show(v, holder.key.label)

                        // Emit on press rather than on release: the character
                        // lands as the finger touches down, so the press-to-lift
                        // delay is off every keystroke and the next key can fire
                        // while this one is still held (rollover). A long-press
                        // that follows takes the character back below.
                        emitted = false
                        emittedLength = 0
                        if (fastKeyOutput) {
                            val ctrlWasActive = extraRowManager.isCtrlActive()
                            val output = holder.key.output
                            handleKeyPress(holder.key)
                            emitted = true
                            emittedLength = if (ctrlWasActive || output !is KeyOutput.Character) {
                                0
                            } else {
                                output.char.length
                            }
                        }

                        // Schedule long-press for alt keys. Resolve alts from the
                        // CURRENT label so the case matches what is on screen --
                        // and resolve them again when the timer fires, because a
                        // one-shot shift may have relabelled the key in between.
                        val currentAlts = AltKeyMappings.getAlts(holder.key.label)
                        if (currentAlts != null) {
                            val runnable = Runnable {
                                touchStarted = false
                                keyPreview?.hide()
                                val alts = AltKeyMappings.getAlts(holder.key.label)
                                    ?: return@Runnable
                                // Emit-on-press already typed the base
                                // character; take it back so a long-press never
                                // leaves both it and the alt.
                                if (emittedLength > 0) {
                                    inputConnectionProvider()
                                        ?.deleteSurroundingText(emittedLength, 0)
                                    emittedLength = 0
                                }
                                if (alts.size == 1) {
                                    val ic = inputConnectionProvider() ?: return@Runnable
                                    keySender.sendText(ic, alts[0])
                                } else {
                                    // Open the slide popup and capture the anchor's
                                    // screen position so MOVE can convert key-local
                                    // pointer coords into the popup's screen space.
                                    val loc = IntArray(2)
                                    v.getLocationOnScreen(loc)
                                    anchorScreenX = loc[0]
                                    anchorScreenY = loc[1]
                                    val session = altKeyPopup.openForSlide(v, alts)
                                    slideSession = session
                                    currentSlideSession = session
                                }
                            }
                            longPressRunnable = runnable
                            longPressHandler.postDelayed(runnable, 500L)
                        }
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val session = slideSession
                        if (session != null) {
                            // The popup owns the gesture. Route only the tracked
                            // finger; a second pointer must not move the highlight.
                            val idx = event.findPointerIndex(activePointerId)
                            if (idx < 0) {
                                cancelSlide(session)
                                slideSession = null
                            } else {
                                session.onMove(
                                    anchorScreenX + event.getX(idx),
                                    anchorScreenY + event.getY(idx)
                                )
                            }
                        } else {
                            // 4dp horizontal slop so a fast diagonal swipe does not
                            // prematurely cancel the tap (issue #22).
                            val slop = dpToPx(4)
                            val inBounds = event.x >= -slop && event.x <= v.width + slop &&
                                event.y >= -v.height && event.y <= v.height * 2
                            if (!inBounds && touchStarted) {
                                touchStarted = false
                                v.isPressed = false
                                keyPreview?.hide()
                                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                                longPressRunnable = null
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        // If the tracked finger is the one lifting, end the gesture
                        // here; a non-tracked finger lifting is ignored.
                        if (event.getPointerId(event.actionIndex) == activePointerId) {
                            val session = slideSession
                            if (session != null) {
                                commitSlide(session, anchorScreenX, anchorScreenY, event, activePointerId)
                                slideSession = null
                            } else {
                                keyPreview?.hide()
                                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                                longPressRunnable = null
                                if (!emitted && touchStarted && v.isPressed) {
                                    handleKeyPress(holder.key)
                                }
                            }
                            emitted = false
                            touchStarted = false
                            v.isPressed = false
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val hideRunnable = Runnable { keyPreview?.hide() }
                        previewHideRunnable = hideRunnable
                        longPressHandler.postDelayed(hideRunnable, 300L)
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        longPressRunnable = null
                        val session = slideSession
                        if (session != null) {
                            commitSlide(session, anchorScreenX, anchorScreenY, event, activePointerId)
                            slideSession = null
                        } else if (!emitted && touchStarted && v.isPressed) {
                            handleKeyPress(holder.key)
                        }
                        emitted = false
                        touchStarted = false
                        v.isPressed = false
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                        true
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        val hideRunnable = Runnable { keyPreview?.hide() }
                        previewHideRunnable = hideRunnable
                        longPressHandler.postDelayed(hideRunnable, 300L)
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                        longPressRunnable = null
                        slideSession?.let { cancelSlide(it) }
                        slideSession = null
                        emitted = false
                        touchStarted = false
                        v.isPressed = false
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                        true
                    }
                    else -> false
                }
            }

            // Wrap character keys that have alts in a FrameLayout with a hint
            // label. The button background was never built for these keys (see top
            // of createKeyView); the key surface lives on the wrapping frame. The
            // hint TextView is captured on the holder so applyShiftCase() can
            // update it to the case-correct alt in place.
            if (alts != null) {
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
                    text = alts[0]
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
                holder.hint = hint
                return frame
            }
        }

        return button
    }

    /**
     * Resolve the slide release at the tracked finger's position. Commits the
     * hovered alt (if any) and dismisses the popup. Lifting outside all
     * candidates sends nothing.
     */
    private fun commitSlide(
        session: AltKeyPopup.SlideSession,
        anchorScreenX: Int,
        anchorScreenY: Int,
        event: MotionEvent,
        activePointerId: Int
    ) {
        val idx = event.findPointerIndex(activePointerId)
        val selected = if (idx >= 0) {
            session.onRelease(anchorScreenX + event.getX(idx), anchorScreenY + event.getY(idx))
        } else {
            null
        }
        if (selected != null) {
            inputConnectionProvider()?.let { ic -> keySender.sendText(ic, selected) }
        }
        session.dismiss()
        if (currentSlideSession === session) currentSlideSession = null
    }

    /** Dismiss the slide popup without committing (cancel, lost pointer). */
    private fun cancelSlide(session: AltKeyPopup.SlideSession) {
        session.dismiss()
        if (currentSlideSession === session) currentSlideSession = null
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
                    setLayerDuringTouch(KeyboardLayouts.LAYER_LOWER)
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
                        setLayerDuringTouch(KeyboardLayouts.LAYER_UPPER)
                    }
                } else {
                    keySender.sendChar(ic, " ")
                    lastWasSpace = true
                    lastSpaceTime = now
                    // Auto-capitalize after sentence-ending punctuation (item 10)
                    if (isAutocorrectOn() && shiftState == ShiftState.OFF &&
                        (currentLayer == KeyboardLayouts.LAYER_LOWER || currentLayer == KeyboardLayouts.LAYER_UPPER)) {
                        if (isSentenceStart(ic.getTextBeforeCursor(2, 0))) {
                            shiftState = ShiftState.SINGLE
                            setLayerDuringTouch(KeyboardLayouts.LAYER_UPPER)
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

    /**
     * Relabels the existing character buttons in place for a lower<->upper shift
     * toggle, instead of tearing the grid down and rebuilding it. The lower and
     * upper layers are position-identical, so every holder's (rowIndex, colIndex)
     * maps to a Character key in the target layer and only its label, alt hint,
     * and the holder's key binding change. No view is added or removed, so no
     * relayout is needed.
     *
     * Safety net: if any holder's position falls outside the target layer or the
     * target key at that position is not a Character (a structural divergence
     * between the two layers that does not exist today but a future layout edit
     * could introduce), returns false so the caller can fall back to a full
     * rebuild -- and can choose to defer that rebuild when a touch is in flight.
     * This keeps the core typing path correct over a fragile fast path.
     *
     * Returns true when every holder was relabelled in place.
     */
    private fun applyShiftCase(targetLayer: Int): Boolean {
        val layout = KeyboardLayouts.getLayer(targetLayer)
        for (holder in charHolders) {
            val row = layout.getOrNull(holder.rowIndex)
            val targetKey = row?.getOrNull(holder.colIndex)
            if (targetKey == null || targetKey.output !is KeyOutput.Character) {
                return false
            }
            holder.key = targetKey
            holder.button.text = targetKey.label
            holder.hint?.let { hint ->
                AltKeyMappings.getAlts(targetKey.label)?.let { alts ->
                    hint.text = alts[0]
                }
            }
        }
        renderedLayer = targetLayer
        updateShiftAppearance(shiftButton)
        return true
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * density + 0.5f).toInt()
    }

    companion object {
        /**
         * Whether the cursor sits where a new sentence begins, given the text
         * immediately before it. An empty context (a fresh, empty field) counts,
         * as does the start of a new line and the space after a full stop,
         * question mark, or exclamation mark.
         */
        fun isSentenceStart(before: CharSequence?): Boolean {
            if (before.isNullOrEmpty()) return true
            if (before.last() == '\n') return true
            if (before.length < 2) return false
            return before.last() == ' ' && before[before.length - 2] in ".?!"
        }

        /**
         * Maps a character to its Ctrl-combo key code without a reflective
         * KeyEvent.keyCodeFromString parse. Letters map directly via the
         * contiguous KEYCODE_A..KEYCODE_Z range; any non-letter returns
         * KEYCODE_UNKNOWN so the caller falls back to sending plain text.
         */
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
