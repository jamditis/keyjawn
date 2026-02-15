package com.keyjawn

import android.inputmethodservice.InputMethodService
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout

class KeyJawnService : InputMethodService() {

    private val keySender = KeySender()
    private var extraRowManager: ExtraRowManager? = null
    private var qwertyKeyboard: QwertyKeyboard? = null

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        val erm = ExtraRowManager(view, keySender) { currentInputConnection }
        extraRowManager = erm

        val container = view.findViewById<LinearLayout>(R.id.qwerty_container)
        val qwerty = QwertyKeyboard(container, keySender, erm) { currentInputConnection }
        qwerty.setLayer(KeyboardLayouts.LAYER_LOWER)
        qwertyKeyboard = qwerty

        return view
    }
}
