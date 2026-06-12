package com.keyjawn

enum class CtrlMode {
    OFF,
    ARMED,
    LOCKED
}

/**
 * Short transition tooltip for a [CtrlMode], or null when none should show.
 * OFF returns null because it fires on every armed-key consumption, which would
 * pop a tooltip on every keystroke.
 */
fun ctrlTransitionMessage(mode: CtrlMode): String? = when (mode) {
    CtrlMode.ARMED -> "Ctrl armed"
    CtrlMode.LOCKED -> "Ctrl locked"
    CtrlMode.OFF -> null
}

class CtrlState {

    var mode: CtrlMode = CtrlMode.OFF
        private set

    var onStateChanged: ((CtrlMode) -> Unit)? = null

    fun tap() {
        mode = when (mode) {
            CtrlMode.OFF -> CtrlMode.ARMED
            CtrlMode.ARMED -> CtrlMode.OFF
            CtrlMode.LOCKED -> CtrlMode.OFF
        }
        onStateChanged?.invoke(mode)
    }

    fun longPress() {
        mode = when (mode) {
            CtrlMode.OFF -> CtrlMode.LOCKED
            CtrlMode.ARMED -> CtrlMode.LOCKED
            CtrlMode.LOCKED -> CtrlMode.OFF
        }
        onStateChanged?.invoke(mode)
    }

    fun isActive(): Boolean = mode != CtrlMode.OFF

    fun consume(): Boolean {
        if (mode == CtrlMode.ARMED) {
            mode = CtrlMode.OFF
            onStateChanged?.invoke(mode)
            return true
        }
        return mode == CtrlMode.LOCKED
    }
}
