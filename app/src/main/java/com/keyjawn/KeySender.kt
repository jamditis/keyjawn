package com.keyjawn

import android.os.SystemClock
import android.view.KeyEvent
import android.view.inputmethod.InputConnection

class KeySender {

    fun sendKey(
        ic: InputConnection,
        keyCode: Int,
        ctrl: Boolean = false,
        alt: Boolean = false
    ) {
        val now = SystemClock.uptimeMillis()
        var metaState = 0
        if (ctrl) {
            metaState = metaState or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        }
        if (alt) {
            metaState = metaState or KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        }
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, metaState))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, metaState))
    }

    fun sendChar(ic: InputConnection, char: String, shift: Boolean = false) {
        ic.finishComposingText()
        ic.commitText(char, 1)
    }

    fun sendText(ic: InputConnection, text: String) {
        ic.finishComposingText()
        ic.commitText(text, 1)
    }

    /**
     * Deletes the whole token before the cursor, plus any whitespace between it
     * and the cursor. Returns false when there was nothing to delete.
     *
     * Held backspace escalates to this once a per-character repeat has clearly
     * become a hold: deleting a mistyped path or flag one character at a time is
     * the slowest interaction on a phone keyboard.
     */
    fun deleteWordBefore(ic: InputConnection): Boolean {
        ic.finishComposingText()
        val before = ic.getTextBeforeCursor(WORD_LOOKBACK, 0) ?: return false
        val count = wordDeleteCount(before)
        if (count <= 0) return false
        // Report the editor's own answer, not just that we had something to
        // delete: an input connection can expose text and still refuse a
        // surrounding-text delete, and the caller's single-character fallback is
        // the difference between that reading as slow and reading as broken.
        return ic.deleteSurroundingText(count, 0)
    }

    companion object {
        /** Characters read back to find the start of the token being deleted. */
        const val WORD_LOOKBACK = 128

        /**
         * Part of a single "word" for deletion. Deliberately wider than letters
         * and digits: on a keyboard built for shell prompts, `src/main/App.kt`
         * and `--no-verify` are each one thing the user means to remove, not
         * five.
         */
        private fun isWordChar(c: Char): Boolean =
            c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' || c == '/' || c == '\\'

        /**
         * How many characters back from the end of [before] one word-delete
         * should remove.
         *
         * Trailing whitespace is consumed first so a hold at the end of "git
         * commit " removes "commit " rather than only the space. Then a run of
         * either word characters or symbols is consumed -- never a mix -- so
         * deleting `foo();` takes the punctuation and the identifier in two
         * predictable steps instead of one greedy swallow.
         *
         * A newline is never crossed: a hold on backspace stops at the start of
         * the current line, which keeps multi-line prompts recoverable.
         */
        fun wordDeleteCount(before: CharSequence): Int {
            var i = before.length
            if (i == 0) return 0

            // A trailing newline is its own step, so a hold pauses at the line
            // boundary instead of eating the line above.
            if (before[i - 1] == '\n') return 1

            while (i > 0 && before[i - 1].isWhitespace() && before[i - 1] != '\n') i--
            if (i > 0 && before[i - 1] != '\n') {
                val takeWords = isWordChar(before[i - 1])
                while (i > 0 && before[i - 1] != '\n' &&
                    !before[i - 1].isWhitespace() && isWordChar(before[i - 1]) == takeWords
                ) {
                    i--
                }
            }
            return before.length - i
        }
    }
}
