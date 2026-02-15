package com.keyjawn

enum class CtrlMode {
    OFF,
    ARMED,
    LOCKED
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
