package com.keyjawn

import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.Button

class NumberRowManager(
    private val view: View,
    private val keySender: KeySender,
    private val inputConnectionProvider: () -> InputConnection?
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
}
