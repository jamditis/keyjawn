package com.keyjawn

import android.graphics.Rect
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow

class AltKeyPopup(
    private val keySender: KeySender,
    private val inputConnectionProvider: () -> InputConnection?,
    private val themeManager: ThemeManager? = null
) {

    private var popup: PopupWindow? = null

    fun show(anchor: android.view.View, alts: List<String>, onSelect: ((String) -> Unit)? = null) {
        dismiss()
        val context = anchor.context
        val density = context.resources.displayMetrics.density

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val pad = (4 * density + 0.5f).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val btnSize = (40 * density + 0.5f).toInt()
        val btnHeight = (44 * density + 0.5f).toInt()
        val margin = (2 * density + 0.5f).toInt()

        for (alt in alts) {
            val btn = Button(context).apply {
                text = alt
                isAllCaps = false
                val tm = themeManager
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
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setOnClickListener {
                    if (onSelect != null) {
                        onSelect(alt)
                    } else {
                        val ic = inputConnectionProvider() ?: return@setOnClickListener
                        keySender.sendText(ic, alt)
                    }
                    dismiss()
                }
            }
            val params = LinearLayout.LayoutParams(btnSize, btnHeight)
            params.setMargins(margin, 0, margin, 0)
            btn.layoutParams = params
            row.addView(btn)
        }

        val window = PopupWindow(
            row,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        )
        val bg = android.graphics.drawable.GradientDrawable().apply {
            setColor(themeManager?.keyboardBg() ?: 0xFF2B2B30.toInt())
            cornerRadius = 8 * density
            setStroke((1 * density + 0.5f).toInt(), themeManager?.divider() ?: 0xFF38383E.toInt())
        }
        window.setBackgroundDrawable(bg)
        window.elevation = 8 * density
        window.isOutsideTouchable = true
        window.isTouchable = true

        // Position above the anchor key
        val anchorLoc = IntArray(2)
        anchor.getLocationInWindow(anchorLoc)
        val popupHeight = btnHeight + (8 * density + 0.5f).toInt()
        val yOffset = -(anchor.height + popupHeight)

        // Center the popup horizontally on the anchor
        val popupWidth = alts.size * (btnSize + 2 * margin) + (8 * density + 0.5f).toInt()
        val xOffset = (anchor.width - popupWidth) / 2

        window.showAsDropDown(anchor, xOffset, yOffset)
        popup = window
    }

    /**
     * Opens the candidate row for a slide-and-release gesture and returns a
     * [SlideSession] the host key's touch listener drives. Unlike [show], the
     * candidates carry no click listeners (selection comes from the in-flight
     * finger, not a second tap) and the window is non-focusable so it does not
     * steal the gesture the host view already owns.
     *
     * Candidate screen-space [Rect]s are precomputed from the same sizing
     * constants the button row is built with, so hit-testing never depends on
     * the popup window being measured or laid out.
     */
    fun openForSlide(anchor: android.view.View, alts: List<String>): SlideSession {
        dismiss()
        val context = anchor.context
        val density = context.resources.displayMetrics.density
        val tm = themeManager

        val pad = (4 * density + 0.5f).toInt()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(pad, pad, pad, pad)
        }

        val btnSize = (40 * density + 0.5f).toInt()
        val btnHeight = (44 * density + 0.5f).toInt()
        val margin = (2 * density + 0.5f).toInt()

        val buttons = ArrayList<Button>(alts.size)
        for (alt in alts) {
            val btn = Button(context).apply {
                text = alt
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
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            }
            val params = LinearLayout.LayoutParams(btnSize, btnHeight)
            params.setMargins(margin, 0, margin, 0)
            btn.layoutParams = params
            row.addView(btn)
            buttons.add(btn)
        }

        val window = PopupWindow(
            row,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            false
        )
        val bg = android.graphics.drawable.GradientDrawable().apply {
            setColor(tm?.keyboardBg() ?: 0xFF2B2B30.toInt())
            cornerRadius = 8 * density
            setStroke((1 * density + 0.5f).toInt(), tm?.divider() ?: 0xFF38383E.toInt())
        }
        window.setBackgroundDrawable(bg)
        window.elevation = 8 * density
        window.isOutsideTouchable = true
        window.isTouchable = false

        val popupHeight = btnHeight + (8 * density + 0.5f).toInt()
        val yOffset = -(anchor.height + popupHeight)
        val popupWidth = alts.size * (btnSize + 2 * margin) + (8 * density + 0.5f).toInt()
        val xOffset = (anchor.width - popupWidth) / 2

        // The popup top-left in screen space. showAsDropDown places the window at
        // (anchorScreen + offset), so the same arithmetic gives candidate bounds
        // without waiting for a layout pass.
        val anchorLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)
        val anchorScreenX = anchorLoc[0]
        val anchorScreenY = anchorLoc[1]
        val popupLeft = anchorScreenX + xOffset
        val popupTop = anchorScreenY + yOffset

        // Each candidate sits at popup-internal x = pad + i*(btnSize + 2*margin) +
        // margin (the LinearLayout's left padding, the preceding buttons' cells,
        // and this button's own left margin), spanning btnSize x btnHeight at
        // top y = pad.
        val rects = ArrayList<Rect>(alts.size)
        for (i in alts.indices) {
            val left = popupLeft + pad + i * (btnSize + 2 * margin) + margin
            val top = popupTop + pad
            rects.add(Rect(left, top, left + btnSize, top + btnHeight))
        }

        window.showAsDropDown(anchor, xOffset, yOffset)
        popup = window

        return SlideSession(buttons, rects, themeManager) { dismiss() }
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }

    /**
     * Tracks the slide gesture over an open candidate row. The host key's touch
     * listener feeds it screen-space pointer coordinates; it owns the hover
     * highlight and resolves the selected alt on release. Hit-testing is pure
     * arithmetic over precomputed [candidateRects], so it is unit-testable
     * without a rendered popup window.
     */
    class SlideSession internal constructor(
        private val buttons: List<Button>,
        private val candidateRects: List<Rect>,
        private val themeManager: ThemeManager?,
        private val onDismiss: () -> Unit
    ) {
        val alts: List<String> = buttons.map { it.text.toString() }

        var hoveredIndex: Int = -1
            private set

        private var dismissed = false

        /** Candidate screen-space bounds, exposed so a test can drive synthetic points. */
        fun candidateRectsForTest(): List<Rect> = candidateRects

        /** Index of the candidate at the given screen point, or -1 if outside all. */
        fun indexAt(screenX: Float, screenY: Float): Int {
            val x = screenX.toInt()
            val y = screenY.toInt()
            for (i in candidateRects.indices) {
                if (candidateRects[i].contains(x, y)) return i
            }
            return -1
        }

        /** Update the hovered candidate from a screen-space pointer position. */
        fun onMove(screenX: Float, screenY: Float) {
            if (dismissed) return
            val newIndex = indexAt(screenX, screenY)
            if (newIndex != hoveredIndex) {
                hoveredIndex = newIndex
                updateHighlight()
            }
        }

        /**
         * Resolve the release point. Returns the selected alt when the finger
         * lifts over a candidate, or null when it lifts outside all of them.
         * Does not dismiss; the caller dismisses after committing.
         */
        fun onRelease(screenX: Float, screenY: Float): String? {
            if (dismissed) return null
            val index = indexAt(screenX, screenY)
            hoveredIndex = index
            return if (index >= 0) alts[index] else null
        }

        /** Repaint candidate backgrounds so only [hoveredIndex] reads as active. */
        fun updateHighlight() {
            val tm = themeManager ?: return
            for (i in buttons.indices) {
                buttons[i].background = if (i == hoveredIndex) {
                    tm.createFlatDrawable(tm.accent())
                } else {
                    tm.createKeyDrawable(tm.keyBg())
                }
            }
        }

        fun isShowing(): Boolean = !dismissed

        fun dismiss() {
            if (dismissed) return
            dismissed = true
            hoveredIndex = -1
            onDismiss()
        }
    }
}
