package com.keyjawn

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

class ClipboardPopup(
    private val clipboardHistoryManager: ClipboardHistoryManager,
    private val onItemSelected: (String) -> Unit
) {

    private var popup: PopupWindow? = null

    fun show(anchor: View) {
        val context = anchor.context
        val inflater = LayoutInflater.from(context)
        val popupView = inflater.inflate(R.layout.clipboard_popup, null)
        val list = popupView.findViewById<LinearLayout>(R.id.clipboard_list)

        val items = clipboardHistoryManager.getHistory()
        if (items.isEmpty()) {
            val empty = inflater.inflate(R.layout.clipboard_item, list, false) as TextView
            empty.text = "Nothing copied yet"
            empty.setTextColor(0xFF888888.toInt())
            empty.gravity = Gravity.CENTER
            list.addView(empty)
        } else {
            for ((index, item) in items.withIndex()) {
                if (index > 0) {
                    // Divider between items
                    val divider = View(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1
                        ).apply {
                            marginStart = dpToPx(context, 14)
                            marginEnd = dpToPx(context, 14)
                        }
                        setBackgroundColor(0xFF333333.toInt())
                    }
                    list.addView(divider)
                }
                val row = inflater.inflate(R.layout.clipboard_item, list, false) as TextView
                row.text = item
                row.setOnClickListener {
                    onItemSelected(item)
                    dismiss()
                }
                list.addView(row)
            }
        }

        val window = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        window.setBackgroundDrawable(null)
        window.isOutsideTouchable = true

        window.showAtLocation(anchor, Gravity.BOTTOM, 0, anchor.height)
        popup = window
    }

    fun dismiss() {
        popup?.dismiss()
        popup = null
    }

    fun isShowing(): Boolean = popup?.isShowing == true

    private fun dpToPx(context: Context, dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}
