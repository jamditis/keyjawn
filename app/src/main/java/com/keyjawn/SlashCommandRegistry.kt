package com.keyjawn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SlashCommandRegistry(context: Context) {
    private val builtInSets: Map<String, CommandSet>
    private val customSets: MutableMap<String, CommandSet> = mutableMapOf()
    private val enabledSets: MutableSet<String>
    private val mruCommands: MutableList<String> = mutableListOf()
    private val prefs = context.getSharedPreferences("keyjawn_commands", Context.MODE_PRIVATE)

    data class CommandSet(val label: String, val commands: List<String>)

    init {
        val json = context.assets.open("commands.json").bufferedReader().readText()
        val root = JSONObject(json)
        val sets = root.getJSONObject("command_sets")
        builtInSets = sets.keys().asSequence().associate { key ->
            val obj = sets.getJSONObject(key)
            key to CommandSet(
                label = obj.getString("label"),
                commands = (0 until obj.getJSONArray("commands").length()).map {
                    obj.getJSONArray("commands").getString(it)
                }
            )
        }
        val defaults = root.getJSONArray("default_enabled")
        enabledSets = loadEnabledSets()
            ?: (0 until defaults.length()).map { defaults.getString(it) }.toMutableSet()
        loadMru()
        loadCustomSets()
    }

    fun getCommands(): List<String> {
        val allSets = builtInSets + customSets
        val all = enabledSets.flatMap { allSets[it]?.commands ?: emptyList() }.distinct()
        val mru = mruCommands.filter { it in all }
        val rest = all.filter { it !in mruCommands }
        return mru + rest
    }

    fun recordUsage(command: String) {
        mruCommands.remove(command)
        mruCommands.add(0, command)
        if (mruCommands.size > 10) mruCommands.removeAt(mruCommands.size - 1)
        saveMru()
    }

    fun getAvailableSets(): Map<String, CommandSet> = builtInSets + customSets
    fun getEnabledSets(): Set<String> = enabledSets

    fun setEnabled(setId: String, enabled: Boolean) {
        if (enabled) enabledSets.add(setId) else enabledSets.remove(setId)
        saveEnabledSets()
    }

    fun isCustomSet(id: String): Boolean = id in customSets

    fun addCustomSet(id: String, label: String) {
        customSets[id] = CommandSet(label, emptyList())
        enabledSets.add(id)
        saveCustomSets()
        saveEnabledSets()
    }

    fun removeCustomSet(id: String) {
        customSets.remove(id)
        enabledSets.remove(id)
        saveCustomSets()
        saveEnabledSets()
    }

    fun addCommand(setId: String, command: String) {
        val set = customSets[setId] ?: return
        if (command in set.commands) return
        customSets[setId] = set.copy(commands = set.commands + command)
        saveCustomSets()
    }

    fun removeCommand(setId: String, command: String) {
        val set = customSets[setId] ?: return
        customSets[setId] = set.copy(commands = set.commands - command)
        saveCustomSets()
    }

    // -- Persistence --

    private fun loadMru() {
        val saved = prefs.getString("mru", null) ?: return
        mruCommands.addAll(saved.split(",").filter { it.isNotEmpty() })
    }

    private fun saveMru() {
        prefs.edit().putString("mru", mruCommands.joinToString(",")).apply()
    }

    private fun loadEnabledSets(): MutableSet<String>? {
        val saved = prefs.getString("enabled_sets", null) ?: return null
        return saved.split(",").filter { it.isNotEmpty() }.toMutableSet()
    }

    private fun saveEnabledSets() {
        prefs.edit().putString("enabled_sets", enabledSets.joinToString(",")).apply()
    }

    private fun loadCustomSets() {
        val json = prefs.getString("custom_sets", null) ?: return
        try {
            val root = JSONObject(json)
            for (key in root.keys()) {
                val obj = root.getJSONObject(key)
                val label = obj.getString("label")
                val cmds = obj.getJSONArray("commands")
                val commands = (0 until cmds.length()).map { cmds.getString(it) }
                customSets[key] = CommandSet(label, commands)
            }
        } catch (_: Exception) {
            // Corrupted prefs, ignore
        }
    }

    private fun saveCustomSets() {
        val root = JSONObject()
        for ((id, set) in customSets) {
            val obj = JSONObject()
            obj.put("label", set.label)
            obj.put("commands", JSONArray(set.commands))
            root.put(id, obj)
        }
        prefs.edit().putString("custom_sets", root.toString()).apply()
    }
}
