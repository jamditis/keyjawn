package com.keyjawn

import android.inputmethodservice.InputMethodService
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout

class KeyJawnService : InputMethodService() {

    private val keySender = KeySender()
    private lateinit var appPrefs: AppPrefs
    private var extraRowManager: ExtraRowManager? = null
    private var qwertyKeyboard: QwertyKeyboard? = null

    override fun onCreate() {
        super.onCreate()
        appPrefs = AppPrefs(this)
    }

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        val erm = ExtraRowManager(view, keySender) { currentInputConnection }
        extraRowManager = erm

        val container = view.findViewById<LinearLayout>(R.id.qwerty_container)
        val qwerty = QwertyKeyboard(container, keySender, erm, { currentInputConnection }, appPrefs)
        qwerty.setLayer(KeyboardLayouts.LAYER_LOWER)
        qwertyKeyboard = qwerty

        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val packageName = info?.packageName ?: "unknown"
        qwertyKeyboard?.updatePackage(packageName)
    }
}
