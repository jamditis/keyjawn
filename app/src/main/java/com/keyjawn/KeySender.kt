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

    /**
     * Send a single character as a key event if it has an Android keycode,
     * otherwise fall back to commitText. Key events work reliably in
     * web-based terminals (xterm.js / Cockpit) where commitText replaces
     * instead of appending.
     */
    fun sendChar(ic: InputConnection, char: String, shift: Boolean = false) {
        if (char.length == 1) {
            val c = char[0]
            val keyCode = charToKeyCode(c)
            if (keyCode != null) {
                val now = SystemClock.uptimeMillis()
                var metaState = 0
                if (shift || c.isUpperCase()) {
                    metaState = metaState or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
                }
                ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
                ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState))
                return
            }
        }
        // Fallback for multi-char strings or chars without keycodes (accented, etc.)
        ic.commitText(char, 1)
    }

    fun sendText(ic: InputConnection, text: String) {
        ic.commitText(text, 1)
    }

    private fun charToKeyCode(c: Char): Int? {
        return when (c.lowercaseChar()) {
            in 'a'..'z' -> KeyEvent.KEYCODE_A + (c.lowercaseChar() - 'a')
            in '0'..'9' -> KeyEvent.KEYCODE_0 + (c - '0')
            ' ' -> KeyEvent.KEYCODE_SPACE
            '.' -> KeyEvent.KEYCODE_PERIOD
            ',' -> KeyEvent.KEYCODE_COMMA
            ';' -> KeyEvent.KEYCODE_SEMICOLON
            '\'' -> KeyEvent.KEYCODE_APOSTROPHE
            '/' -> KeyEvent.KEYCODE_SLASH
            '\\' -> KeyEvent.KEYCODE_BACKSLASH
            '-' -> KeyEvent.KEYCODE_MINUS
            '=' -> KeyEvent.KEYCODE_EQUALS
            '[' -> KeyEvent.KEYCODE_LEFT_BRACKET
            ']' -> KeyEvent.KEYCODE_RIGHT_BRACKET
            '`' -> KeyEvent.KEYCODE_GRAVE
            '*' -> KeyEvent.KEYCODE_STAR
            '#' -> KeyEvent.KEYCODE_POUND
            '+' -> KeyEvent.KEYCODE_PLUS
            '@' -> KeyEvent.KEYCODE_AT
            '\t' -> KeyEvent.KEYCODE_TAB
            else -> null
        }
    }
}
