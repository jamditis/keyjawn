package com.keyjawn

import android.inputmethodservice.InputMethodService
import android.view.LayoutInflater
import android.view.View

class KeyJawnService : InputMethodService() {

    override fun onCreateInputView(): View {
        return LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
    }
}
