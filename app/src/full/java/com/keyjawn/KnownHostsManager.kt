package com.keyjawn

import android.content.Context
import android.content.SharedPreferences
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.JSch

class KnownHostsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("keyjawn_known_hosts", Context.MODE_PRIVATE)

    fun checkAndStore(hostname: String, port: Int, key: HostKey, jsch: JSch): Boolean {
        val prefKey = "hostkey_$hostname:$port"
        val fingerprint = key.getFingerPrint(jsch)
        val stored = prefs.getString(prefKey, null)

        if (stored == null) {
            prefs.edit().putString(prefKey, fingerprint).apply()
            return true
        }

        return stored == fingerprint
    }

    fun getStoredFingerprint(hostname: String, port: Int): String? {
        return prefs.getString("hostkey_$hostname:$port", null)
    }

    fun clearHost(hostname: String, port: Int) {
        prefs.edit().remove("hostkey_$hostname:$port").apply()
    }
}
