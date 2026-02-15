package com.keyjawn

import android.inputmethodservice.InputMethodService
import android.view.LayoutInflater
import android.view.View

class KeyJawnService : InputMethodService() {

    private val keySender = KeySender()
    private var extraRowManager: ExtraRowManager? = null

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        extraRowManager = ExtraRowManager(view, keySender) { currentInputConnection }
        return view
    }
}
