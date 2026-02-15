package com.keyjawn

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONArray
import org.json.JSONObject

class HostStorage private constructor(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(
        EncryptedSharedPreferences.create(
            "keyjawn_hosts",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    )

    fun getHosts(): List<HostConfig> {
        val json = prefs.getString("hosts", "[]") ?: "[]"
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            HostConfig(
                name = obj.optString("name", ""),
                hostname = obj.getString("hostname"),
                port = obj.optInt("port", 22),
                username = obj.getString("username"),
                privateKeyPath = obj.optString("privateKeyPath", null),
                uploadDir = obj.optString("uploadDir", "/tmp/keyjawn/")
            )
        }
    }

    fun saveHosts(hosts: List<HostConfig>) {
        val array = JSONArray()
        hosts.forEach { host ->
            array.put(JSONObject().apply {
                put("name", host.name)
                put("hostname", host.hostname)
                put("port", host.port)
                put("username", host.username)
                put("privateKeyPath", host.privateKeyPath ?: "")
                put("uploadDir", host.uploadDir)
            })
        }
        prefs.edit().putString("hosts", array.toString()).apply()
    }

    fun addHost(host: HostConfig) {
        val hosts = getHosts().toMutableList()
        hosts.add(host)
        saveHosts(hosts)
    }

    fun removeHost(index: Int) {
        val hosts = getHosts().toMutableList()
        if (index in hosts.indices) {
            hosts.removeAt(index)
            saveHosts(hosts)
        }
    }

    fun getActiveHostIndex(): Int {
        return prefs.getInt("active_host", 0)
    }

    fun setActiveHostIndex(index: Int) {
        prefs.edit().putInt("active_host", index).apply()
    }

    fun getActiveHost(): HostConfig? {
        val hosts = getHosts()
        val index = getActiveHostIndex()
        return hosts.getOrNull(index)
    }

    companion object {
        fun createForTest(prefs: SharedPreferences): HostStorage {
            return HostStorage(prefs)
        }
    }
}
