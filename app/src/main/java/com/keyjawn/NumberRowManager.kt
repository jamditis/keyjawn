package com.keyjawn

import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView

class NumberRowManager(
    private val view: View,
    private val keySender: KeySender,
    private val inputConnectionProvider: () -> InputConnection?,
    private val themeManager: ThemeManager? = null
) {

    init {
        wireNumber(R.id.num_1, "1")
        wireNumber(R.id.num_2, "2")
        wireNumber(R.id.num_3, "3")
        wireNumber(R.id.num_4, "4")
        wireNumber(R.id.num_5, "5")
        wireNumber(R.id.num_6, "6")
        wireNumber(R.id.num_7, "7")
        wireNumber(R.id.num_8, "8")
        wireNumber(R.id.num_9, "9")
        wireNumber(R.id.num_0, "0")
        applyThemeColors()
    }

    private fun wireNumber(buttonId: Int, digit: String) {
        val button = view.findViewById<Button>(buttonId)
        button.setOnClickListener {
            val ic = inputConnectionProvider() ?: return@setOnClickListener
            keySender.sendChar(ic, digit)
        }
        val alts = AltKeyMappings.getAlts(digit)
        if (alts != null && alts.size == 1) {
            button.setOnLongClickListener {
                val ic = inputConnectionProvider() ?: return@setOnLongClickListener true
                keySender.sendText(ic, alts[0])
                true
            }
        }
    }

    private fun applyThemeColors() {
        val tm = themeManager ?: return
        val numberRow = view.findViewById<View>(R.id.number_row) ?: return
        // Apply theme to each FrameLayout (number key container) in the number row
        val parent = numberRow as? android.widget.LinearLayout ?: return
        for (i in 0 until parent.childCount) {
            val frame = parent.getChildAt(i) as? FrameLayout ?: continue
            frame.background = tm.createKeyDrawable(tm.keyBg())
            // Update label and hint text colors
            for (j in 0 until frame.childCount) {
                val child = frame.getChildAt(j)
                if (child is TextView && child !is Button) {
                    // Distinguish label (center gravity, larger text) from hint (top|end, smaller)
                    val params = child.layoutParams as? FrameLayout.LayoutParams
                    if (params != null && (params.gravity and android.view.Gravity.TOP) != 0) {
                        child.setTextColor(tm.keyHint())
                    } else {
                        child.setTextColor(tm.keyText())
                    }
                }
            }
        }
    }
}
