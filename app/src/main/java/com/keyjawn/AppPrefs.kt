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
        prefs.edit().putString("quick_key", char).apply()
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

    companion object {
        val QUICK_KEY_OPTIONS = listOf("/", ".", ",", "?", "!", "\u2014", "'", "\"", ":", ";")
    }
}
