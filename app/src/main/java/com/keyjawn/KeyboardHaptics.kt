package com.keyjawn

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Maps keyboard interactions to platform haptic semantics.
 *
 * The enabled callback is evaluated for every event so preference changes take
 * effect without rebuilding the input view.
 */
class KeyboardHaptics(
    private val view: View,
    private val enabled: () -> Boolean,
    private val sdkInt: Int = Build.VERSION.SDK_INT
) {

    fun key(output: KeyOutput) {
        val type = if (output is KeyOutput.Enter && sdkInt >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
        perform(type)
    }

    fun repeatPress() {
        perform(
            HapticFeedbackConstants.KEYBOARD_TAP,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
        )
    }

    fun confirm() {
        perform(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun perform(type: Int, flags: Int = 0) {
        if (!enabled()) return
        if (flags == 0) {
            view.performHapticFeedback(type)
        } else {
            view.performHapticFeedback(type, flags)
        }
    }
}
