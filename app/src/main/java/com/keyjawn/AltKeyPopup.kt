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

    /**
     * Opens the candidate row for a slide-and-release gesture and returns a
     * [SlideSession] the host key's touch listener drives. The candidates carry
     * no click listeners (selection comes from the in-flight finger, not a second
     * tap) and the window is non-focusable so it does not steal the gesture the
     * host view already owns.
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
        // Lift the candidate row above the key. A bottom-anchored IME normally has
        // room above the pressed key, but a compact or landscape IME whose top row
        // sits near the screen top would not: showAsDropDown (clipping enabled) would
        // then flip the row below the anchor and leave our precomputed rects above it,
        // desyncing the hit-test the same way an unclamped left did before #38. So the
        // top is clamped to the visible frame below, and both the rects and the offset
        // derive from that clamped top (see clampPopupTop, mirroring clampPopupLeft).
        val requestedYOffset = -(anchor.height + popupHeight)
        val popupWidth = alts.size * (btnSize + 2 * margin) + (8 * density + 0.5f).toInt()

        // The popup top-left in screen space. showAsDropDown places the window at
        // the anchor's bottom-left plus the offset, so the same arithmetic gives
        // candidate bounds without waiting for a layout pass (see popupScreenTop
        // for the vertical anchoring, clampPopupLeft for the horizontal clamp).
        val anchorLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)
        val anchorScreenX = anchorLoc[0]
        val anchorScreenY = anchorLoc[1]

        // Centering a wide candidate row over a narrow edge key pushes the
        // requested left off screen. showAsDropDown (clipping enabled by default)
        // would slide the window back on screen but leave our precomputed rects at
        // the off-screen origin, desyncing hit-testing from the visible buttons.
        // Clamp horizontally ourselves to the anchor window's visible display frame
        // -- the same Rect showAsDropDown clips against -- then derive both the rects
        // AND the offset we pass to showAsDropDown from the clamped left, so the
        // framework finds the window already fits and leaves it where we put it. In
        // split-screen/freeform the frame's left can be > 0 and its width far narrower
        // than displayMetrics.widthPixels, so the raw screen width would let the
        // framework re-slide the window past our clamp.
        val displayFrame = Rect()
        anchor.getWindowVisibleDisplayFrame(displayFrame)
        val requestedLeft = anchorScreenX + (anchor.width - popupWidth) / 2
        val clampedLeft = clampPopupLeft(requestedLeft, popupWidth, displayFrame.left, displayFrame.right)
        val clampedXOffset = clampedLeft - anchorScreenX

        // Same clamp on the vertical axis: pin the requested top into the visible
        // frame so showAsDropDown finds the row already fits above the key and does not
        // flip it below, then derive both the rects and the offset from the clamped top
        // (dropDownYOffset inverts popupScreenTop to recover the anchor-bottom offset).
        val requestedTop = popupScreenTop(anchorScreenY, anchor.height, requestedYOffset)
        val clampedTop = clampPopupTop(requestedTop, popupHeight, displayFrame.top, displayFrame.bottom)
        val clampedYOffset = dropDownYOffset(clampedTop, anchorScreenY, anchor.height)

        // Each candidate sits at popup-internal x = pad + i*(btnSize + 2*margin) +
        // margin (the LinearLayout's left padding, the preceding buttons' cells,
        // and this button's own left margin), spanning btnSize x btnHeight at
        // top y = pad.
        val rects = ArrayList<Rect>(alts.size)
        for (i in alts.indices) {
            val left = clampedLeft + pad + i * (btnSize + 2 * margin) + margin
            val top = clampedTop + pad
            rects.add(Rect(left, top, left + btnSize, top + btnHeight))
        }

        window.showAsDropDown(anchor, clampedXOffset, clampedYOffset)
        popup = window

        return SlideSession(buttons, rects, themeManager) { dismiss() }
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }

    companion object {
        /**
         * The popup's actual top edge in screen space. showAsDropDown anchors the
         * window to the anchor's BOTTOM-left, so the real top is the anchor bottom
         * ([anchorScreenY] + [anchorHeight]) plus [yOffset] -- not [anchorScreenY] +
         * [yOffset]. The candidate hit-test rects derive from this value, so the
         * naive form shifts every Rect up by one key height and a slide onto the
         * visible candidate row hit-tests as outside, committing nothing. Pure
         * arithmetic so it is unit-testable without a rendered popup.
         */
        fun popupScreenTop(anchorScreenY: Int, anchorHeight: Int, yOffset: Int): Int =
            anchorScreenY + anchorHeight + yOffset

        /**
         * The showAsDropDown yOffset that lands the popup's top edge at [clampedTop].
         * showAsDropDown measures its offset from the anchor BOTTOM, so this is the
         * exact inverse of [popupScreenTop]: feeding this offset back through
         * popupScreenTop returns [clampedTop]. openForSlide derives the offset from the
         * clamped top (not the raw requested offset) so the shown window and the
         * precomputed candidate rects share one top edge; a naive offset that dropped
         * [anchorHeight] would misplace the window while every rect stayed put. Pure
         * arithmetic so it is unit-testable without a rendered popup.
         */
        fun dropDownYOffset(clampedTop: Int, anchorScreenY: Int, anchorHeight: Int): Int =
            clampedTop - (anchorScreenY + anchorHeight)

        /**
         * Clamp the requested popup left edge to the anchor window's visible display
         * frame -- the same frame showAsDropDown clips against -- so the precomputed
         * candidate rects stay aligned with where the window actually lands in any
         * window mode (full-screen, split-screen, or freeform). [frameLeft] and
         * [frameRight] are screen-space, matching getLocationOnScreen. A popup wider
         * than the frame pins to [frameLeft]; one that overflows left or right is slid
         * inward to the frame edge; one that already fits is unchanged. The horizontal
         * half of the pair -- [clampPopupTop] mirrors it on the Y axis. Pure arithmetic
         * so it is unit-testable without a rendered popup.
         */
        fun clampPopupLeft(requestedLeft: Int, popupWidth: Int, frameLeft: Int, frameRight: Int): Int {
            val maxLeft = frameRight - popupWidth
            return when {
                maxLeft <= frameLeft -> frameLeft
                requestedLeft < frameLeft -> frameLeft
                requestedLeft > maxLeft -> maxLeft
                else -> requestedLeft
            }
        }

        /**
         * Clamp the requested popup top edge to the anchor window's visible display
         * frame -- the same frame showAsDropDown clips and flips against -- so the
         * precomputed candidate rects stay aligned with where the window lands. The
         * vertical mirror of [clampPopupLeft]: [frameTop] and [frameBottom] are
         * screen-space. A popup taller than the frame pins to [frameTop]; a requested
         * top above the frame (where showAsDropDown would flip the row below the anchor
         * and desync the rects) is pushed down to [frameTop]; one that overflows the
         * bottom is slid up to the frame; one that already fits is unchanged. Pure
         * arithmetic so it is unit-testable without a rendered popup.
         */
        fun clampPopupTop(requestedTop: Int, popupHeight: Int, frameTop: Int, frameBottom: Int): Int {
            val maxTop = frameBottom - popupHeight
            return when {
                maxTop <= frameTop -> frameTop
                requestedTop < frameTop -> frameTop
                requestedTop > maxTop -> maxTop
                else -> requestedTop
            }
        }
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
