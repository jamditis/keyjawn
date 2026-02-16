package com.keyjawn

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class ClipboardPanel(
    private val clipboardHistoryManager: ClipboardHistoryManager,
    private val isPaidUser: Boolean,
    private val panel: ScrollView,
    private val list: LinearLayout,
    private val themeManager: ThemeManager?,
    private val onItemSelected: (String) -> Unit,
    private val onShowTooltip: (String) -> Unit
) {

    fun toggle() {
        if (isShowing()) {
            hide()
        } else {
            show()
        }
    }

    fun show() {
        populateList()
        panel.visibility = View.VISIBLE
    }

    fun hide() {
        panel.visibility = View.GONE
    }

    fun isShowing(): Boolean = panel.visibility == View.VISIBLE

    private fun populateList() {
        list.removeAllViews()
        val context = panel.context
        val inflater = LayoutInflater.from(context)

        val pinnedItems = clipboardHistoryManager.getPinned()
        val historyItems = clipboardHistoryManager.getHistory()

        // Spacer pushes content to the bottom of the panel
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        list.addView(spacer)

        if (pinnedItems.isEmpty() && historyItems.isEmpty()) {
            val empty = TextView(context).apply {
                text = "Nothing copied yet"
                setTextColor(mutedText())
                textSize = 14f
                setPadding(dpToPx(context, 14), dpToPx(context, 16), dpToPx(context, 14), dpToPx(context, 16))
            }
            list.addView(empty)
            return
        }

        if (pinnedItems.isNotEmpty()) {
            addSectionHeader(context, "Pinned")
            for ((index, item) in pinnedItems.withIndex()) {
                if (index > 0) addDivider(context)
                addRow(inflater, item, isPinned = true)
            }

            if (historyItems.isNotEmpty()) {
                addSectionHeader(context, "Recent")
            }
        }

        for ((index, item) in historyItems.withIndex()) {
            if (index > 0) addDivider(context)
            addRow(inflater, item, isPinned = false)
        }
    }

    private fun addRow(inflater: LayoutInflater, text: String, isPinned: Boolean) {
        val row = inflater.inflate(R.layout.clipboard_row, list, false)
        val textView = row.findViewById<TextView>(R.id.clipboard_row_text)
        val pinButton = row.findViewById<TextView>(R.id.clipboard_row_pin)

        textView.text = text
        if (isPinned) {
            textView.setTextColor(accentColor())
        } else {
            textView.setTextColor(normalText())
        }

        row.setOnClickListener {
            onItemSelected(text)
            hide()
        }

        if (isPinned) {
            pinButton.text = "x"
        }

        if (isPaidUser) {
            pinButton.setTextColor(if (isPinned) accentColor() else normalText())
            pinButton.setOnClickListener {
                if (isPinned) {
                    clipboardHistoryManager.unpin(text)
                } else {
                    clipboardHistoryManager.pin(text)
                }
                populateList()
            }
        } else {
            pinButton.setTextColor(disabledText())
            pinButton.setOnClickListener {
                onShowTooltip("Upgrade to full version for pinning")
            }
        }

        list.addView(row)
    }

    private fun addSectionHeader(context: Context, title: String) {
        val header = TextView(context).apply {
            text = title
            setTextColor(mutedText())
            textSize = 12f
            setPadding(dpToPx(context, 14), dpToPx(context, 10), dpToPx(context, 14), dpToPx(context, 6))
        }
        list.addView(header)
    }

    private fun addDivider(context: Context) {
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                marginStart = dpToPx(context, 14)
                marginEnd = dpToPx(context, 14)
            }
            setBackgroundColor(themeManager?.divider() ?: 0xFF333333.toInt())
        }
        list.addView(divider)
    }

    private fun normalText(): Int = themeManager?.keyText() ?: 0xFFCCCCCC.toInt()

    private fun mutedText(): Int = themeManager?.keyHint() ?: 0xFF888888.toInt()

    private fun accentColor(): Int = themeManager?.accent() ?: 0xFF66BBFF.toInt()

    private fun disabledText(): Int = themeManager?.keyHint() ?: 0xFF555555.toInt()

    private fun dpToPx(context: Context, dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}
