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
}
