package com.keyjawn

import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.InputConnection

class KeySender {

    fun sendKey(ic: InputConnection, keyCode: Int, ctrl: Boolean = false) {
        val now = SystemClock.uptimeMillis()
        var metaState = 0
        if (ctrl) {
            metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        }
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState))
    }

    fun sendText(ic: InputConnection, text: String) {
        ic.commitText(text, 1)
    }
}
