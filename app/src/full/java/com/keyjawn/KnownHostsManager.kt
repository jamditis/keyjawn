package com.keyjawn

import android.content.Context
import android.content.SharedPreferences
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.UserInfo

class KnownHostsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("keyjawn_known_hosts", Context.MODE_PRIVATE)

    /**
     * Check if a host key matches a stored key.
     * Returns: MATCH if known and matches, MISMATCH if known but changed, NEW if never seen.
     */
    fun check(hostname: String, port: Int, key: HostKey, jsch: JSch): KeyStatus {
        val prefKey = "hostkey_$hostname:$port"
        val fingerprint = key.getFingerPrint(jsch)
        val stored = prefs.getString(prefKey, null)

        return when {
            stored == null -> KeyStatus.NEW
            stored == fingerprint -> KeyStatus.MATCH
            else -> KeyStatus.MISMATCH
        }
    }

    fun store(hostname: String, port: Int, key: HostKey, jsch: JSch) {
        val prefKey = "hostkey_$hostname:$port"
        val fingerprint = key.getFingerPrint(jsch)
        prefs.edit().putString(prefKey, fingerprint).apply()
    }

    fun getStoredFingerprint(hostname: String, port: Int): String? {
        return prefs.getString("hostkey_$hostname:$port", null)
    }

    fun clearHost(hostname: String, port: Int) {
        prefs.edit().remove("hostkey_$hostname:$port").apply()
    }

    enum class KeyStatus {
        MATCH,
        MISMATCH,
        NEW
    }
}
