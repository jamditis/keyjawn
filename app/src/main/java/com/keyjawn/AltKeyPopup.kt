package com.keyjawn

import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow

class AltKeyPopup(
    private val keySender: KeySender,
    private val inputConnectionProvider: () -> InputConnection?
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
                setBackgroundResource(R.drawable.key_bg)
                setTextColor(context.getColor(R.color.key_text))
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
            setColor(0xFF2B2B30.toInt())
            cornerRadius = 8 * density
            setStroke((1 * density + 0.5f).toInt(), 0xFF38383E.toInt())
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

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }
}
