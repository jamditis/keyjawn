package com.keyjawn

import android.content.Context
import android.content.SharedPreferences

class AppPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("keyjawn_app_prefs", Context.MODE_PRIVATE)

    // Haptic feedback is read once per keystroke, per cursor step, and per
    // repeat tick -- the hottest pref in the app. Cache it in a field and keep
    // the cache honest with a change listener so a write from any AppPrefs
    // instance (the menu panel, the settings screen) is picked up without every
    // call site paying a SharedPreferences lookup on the input path.
    private var hapticCache: Boolean = prefs.getBoolean(KEY_HAPTIC, true)

    private val changeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
            if (key == KEY_HAPTIC) hapticCache = sp.getBoolean(KEY_HAPTIC, true)
        }

    init {
        prefs.registerOnSharedPreferenceChangeListener(changeListener)
    }

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

    fun isHapticEnabled(): Boolean = hapticCache

    fun setHapticEnabled(enabled: Boolean) {
        hapticCache = enabled
        prefs.edit().putBoolean(KEY_HAPTIC, enabled).apply()
    }

    /**
     * Emit a character on finger-down instead of finger-up. Removes the
     * press-to-release delay from every keystroke and lets one key fire while
     * the previous one is still held (rollover), which is what makes fast typing
     * feel connected. The cost is that sliding off a key no longer aborts it.
     */
    fun isFastKeyOutput(): Boolean = prefs.getBoolean("fast_key_output", true)

    fun setFastKeyOutput(enabled: Boolean) {
        prefs.edit().putBoolean("fast_key_output", enabled).apply()
    }

    /**
     * Keep the microphone armed between utterances so a whole prompt can be
     * dictated in natural sentences instead of one tap per phrase.
     */
    fun isVoiceContinuous(): Boolean = prefs.getBoolean("voice_continuous", true)

    fun setVoiceContinuous(enabled: Boolean) {
        prefs.edit().putBoolean("voice_continuous", enabled).apply()
    }

    /**
     * Show the in-flight transcription in the editor as composing text while the
     * user is still speaking, rather than only after the utterance lands.
     */
    fun isVoiceLivePreview(): Boolean = prefs.getBoolean("voice_live_preview", true)

    fun setVoiceLivePreview(enabled: Boolean) {
        prefs.edit().putBoolean("voice_live_preview", enabled).apply()
    }

    /**
     * Turn spoken words like "new line" and "comma" into the characters they
     * name. Off by default: dictating a prompt *about* adding a new line must
     * not silently become a line break.
     */
    fun isVoiceCommands(): Boolean = prefs.getBoolean("voice_commands", false)

    fun setVoiceCommands(enabled: Boolean) {
        prefs.edit().putBoolean("voice_commands", enabled).apply()
    }

    /** Bottom padding in dp (0-64). Default 0 — no extra padding. */
    fun getBottomPadding(): Int {
        return prefs.getInt("bottom_padding_dp", 0).coerceIn(0, 64)
    }

    fun setBottomPadding(dp: Int) {
        prefs.edit().putInt("bottom_padding_dp", dp.coerceIn(0, 64)).apply()
    }

    companion object {
        private const val KEY_HAPTIC = "haptic_enabled"

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
