package com.keyjawn

import android.content.Context
import org.json.JSONObject

class SlashCommandRegistry(context: Context) {
    private val commandSets: Map<String, CommandSet>
    private val enabledSets: MutableSet<String>
    private val mruCommands: MutableList<String> = mutableListOf()
    private val prefs = context.getSharedPreferences("keyjawn_commands", Context.MODE_PRIVATE)

    data class CommandSet(val label: String, val commands: List<String>)

    init {
        val json = context.assets.open("commands.json").bufferedReader().readText()
        val root = JSONObject(json)
        val sets = root.getJSONObject("command_sets")
        commandSets = sets.keys().asSequence().associate { key ->
            val obj = sets.getJSONObject(key)
            key to CommandSet(
                label = obj.getString("label"),
                commands = (0 until obj.getJSONArray("commands").length()).map {
                    obj.getJSONArray("commands").getString(it)
                }
            )
        }
        val defaults = root.getJSONArray("default_enabled")
        enabledSets = (0 until defaults.length()).map { defaults.getString(it) }.toMutableSet()
        loadMru()
    }

    fun getCommands(): List<String> {
        val all = enabledSets.flatMap { commandSets[it]?.commands ?: emptyList() }.distinct()
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

    fun getAvailableSets(): Map<String, CommandSet> = commandSets
    fun getEnabledSets(): Set<String> = enabledSets

    fun setEnabled(setId: String, enabled: Boolean) {
        if (enabled) enabledSets.add(setId) else enabledSets.remove(setId)
    }

    private fun loadMru() {
        val saved = prefs.getString("mru", null) ?: return
        mruCommands.addAll(saved.split(",").filter { it.isNotEmpty() })
    }

    private fun saveMru() {
        prefs.edit().putString("mru", mruCommands.joinToString(",")).apply()
    }
}
