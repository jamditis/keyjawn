package com.keyjawn

import android.content.Context
import android.content.SharedPreferences

class AppPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("keyjawn_app_prefs", Context.MODE_PRIVATE)

    fun isAutocorrectEnabled(packageName: String): Boolean {
        return prefs.getBoolean("ac_$packageName", false)
    }

    fun toggleAutocorrect(packageName: String): Boolean {
        val newValue = !isAutocorrectEnabled(packageName)
        prefs.edit().putBoolean("ac_$packageName", newValue).apply()
        return newValue
    }

    fun setAutocorrect(packageName: String, enabled: Boolean) {
        prefs.edit().putBoolean("ac_$packageName", enabled).apply()
    }

    fun getQuickKey(): String {
        return prefs.getString("quick_key", "/") ?: "/"
    }

    fun setQuickKey(char: String) {
        val sanitized = if (char.startsWith("text:")) {
            "text:" + sanitizeCustomText(char.removePrefix("text:"))
        } else {
            char
        }
        prefs.edit().putString("quick_key", sanitized).apply()
    }

    fun getExtraSlot(index: Int): String {
        val key = "extra_slot_$index"
        return prefs.getString(key, EXTRA_SLOT_DEFAULTS.getOrElse(index) { "keycode:KEYCODE_ESCAPE" })
            ?: EXTRA_SLOT_DEFAULTS[0]
    }

    fun setExtraSlot(index: Int, value: String) {
        val sanitized = if (value.startsWith("text:")) {
            "text:" + sanitizeCustomText(value.removePrefix("text:"))
        } else {
            value
        }
        prefs.edit().putString("extra_slot_$index", sanitized).apply()
    }

    fun isTooltipsEnabled(): Boolean {
        return prefs.getBoolean("tooltips_enabled", true)
    }

    fun setTooltipsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("tooltips_enabled", enabled).apply()
    }

    fun isHapticEnabled(): Boolean {
        return prefs.getBoolean("haptic_enabled", true)
    }

    fun setHapticEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("haptic_enabled", enabled).apply()
    }

    /** Bottom padding in dp (0-64). Default 0 — no extra padding. */
    fun getBottomPadding(): Int {
        return prefs.getInt("bottom_padding_dp", 0).coerceIn(0, 64)
    }

    fun setBottomPadding(dp: Int) {
        prefs.edit().putInt("bottom_padding_dp", dp.coerceIn(0, 64)).apply()
    }

    companion object {
        val QUICK_KEY_OPTIONS = listOf(
            "/", ".", ",", "?", "!", "\u2014", "'", "\"", ":", ";",
            "|", "~", "`", "\\", "@", "#", "$", "_", "&", "-", "+", "=", "^", "%"
        )

        val EXTRA_SLOT_OPTIONS = listOf(
            "keycode:KEYCODE_ESCAPE", "keycode:KEYCODE_TAB", "ctrl",
            "keycode:KEYCODE_MOVE_HOME", "keycode:KEYCODE_MOVE_END",
            "keycode:KEYCODE_PAGE_UP", "keycode:KEYCODE_PAGE_DOWN",
            "keycode:KEYCODE_INSERT", "keycode:KEYCODE_FORWARD_DEL",
            "text:|", "text:~", "text:`", "text:\\"
        )

        private val EXTRA_SLOT_DEFAULTS = arrayOf(
            "keycode:KEYCODE_ESCAPE",
            "keycode:KEYCODE_TAB",
            "ctrl"
        )

        private val SLOT_LABELS = mapOf(
            "keycode:KEYCODE_ESCAPE" to "ESC",
            "keycode:KEYCODE_TAB" to "Tab",
            "ctrl" to "Ctrl",
            "keycode:KEYCODE_MOVE_HOME" to "Home",
            "keycode:KEYCODE_MOVE_END" to "End",
            "keycode:KEYCODE_PAGE_UP" to "PgUp",
            "keycode:KEYCODE_PAGE_DOWN" to "PgDn",
            "keycode:KEYCODE_INSERT" to "Ins",
            "keycode:KEYCODE_FORWARD_DEL" to "Del"
        )

        fun sanitizeCustomText(input: String): String {
            return input
                .filter { it == '\t' || it.code >= 0x20 }
                .take(8)
        }

        fun getExtraSlotLabel(value: String): String {
            SLOT_LABELS[value]?.let { return it }
            if (value.startsWith("text:")) return value.removePrefix("text:")
            return value
        }
    }
}
