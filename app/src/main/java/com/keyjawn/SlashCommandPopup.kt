package com.keyjawn

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

class SlashCommandPopup(
    private val registry: SlashCommandRegistry,
    private val onCommandSelected: (String) -> Unit,
    private val onDismissedEmpty: () -> Unit,
    private val themeManager: ThemeManager? = null
) {

    private var popup: PopupWindow? = null

    fun show(anchor: View) {
        val context = anchor.context
        val inflater = LayoutInflater.from(context)
        val popupView = inflater.inflate(R.layout.slash_command_popup, null)
        val commandList = popupView.findViewById<LinearLayout>(R.id.command_list)

        val tm = themeManager
        val backgroundColor = tm?.keyboardBg() ?: DEFAULT_BACKGROUND
        val textColor = tm?.keyText() ?: DEFAULT_TEXT
        popupView.setBackgroundColor(backgroundColor)

        val commands = registry.getCommands()
        for (command in commands) {
            val item = inflater.inflate(R.layout.slash_command_item, commandList, false) as TextView
            item.text = command
            item.setTextColor(textColor)
            item.setOnClickListener {
                registry.recordUsage(command)
                onCommandSelected(command)
                dismiss()
            }
            commandList.addView(item)
        }

        val window = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(context, 200),
            true
        )
        window.setBackgroundDrawable(ColorDrawable(backgroundColor))
        window.isOutsideTouchable = true
        window.setOnDismissListener {
            onDismissedEmpty()
        }

        window.showAtLocation(anchor, Gravity.BOTTOM, 0, anchor.height)
        popup = window
    }

    fun dismiss() {
        val window = popup ?: return
        window.setOnDismissListener(null)
        window.dismiss()
        popup = null
    }

    fun isShowing(): Boolean = popup?.isShowing == true

    private fun dpToPx(context: Context, dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }

    companion object {
        // Fallback colors when no ThemeManager is supplied (lite flavor, which has
        // no themes). Matches the previous hardcoded dark popup appearance.
        private val DEFAULT_BACKGROUND = 0xFF1E1E1E.toInt()
        private val DEFAULT_TEXT = 0xFFFFFFFF.toInt()
    }
}
