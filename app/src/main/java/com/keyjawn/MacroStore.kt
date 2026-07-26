package com.keyjawn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

sealed class MacroStep {
    data class KeyTap(val keyCode: Int, val ctrl: Boolean = false) : MacroStep()
    data class Text(val literal: String) : MacroStep()
    data class Delay(val ms: Int) : MacroStep()
}

data class Macro(val label: String, val steps: List<MacroStep>)

class MacroStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): Map<String, Macro> {
        val json = prefs.getString(PREFS_KEY, null) ?: return emptyMap()
        return try {
            deserialize(json)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun save(id: String, macro: Macro) {
        val macros = load().toMutableMap()
        macros[id] = macro
        prefs.edit().putString(PREFS_KEY, serialize(macros)).apply()
    }

    companion object {
        internal const val PREFS_NAME = "keyjawn_macros"
        internal const val PREFS_KEY = "macros"

        internal fun serialize(macros: Map<String, Macro>): String {
            val root = JSONObject()
            for ((id, macro) in macros) {
                root.put(
                    id,
                    JSONObject().apply {
                        put("label", macro.label)
                        put(
                            "steps",
                            JSONArray().apply {
                                macro.steps.forEach { put(serializeStep(it)) }
                            }
                        )
                    }
                )
            }
            return root.toString()
        }

        internal fun deserialize(json: String): Map<String, Macro> {
            val root = JSONObject(json)
            val macros = linkedMapOf<String, Macro>()
            for (id in root.keys()) {
                val obj = root.getJSONObject(id)
                val jsonSteps = obj.getJSONArray("steps")
                val steps = (0 until jsonSteps.length()).map { index ->
                    deserializeStep(jsonSteps.getJSONObject(index))
                }
                macros[id] = Macro(obj.getString("label"), steps)
            }
            return macros
        }

        private fun serializeStep(step: MacroStep): JSONObject =
            JSONObject().apply {
                when (step) {
                    is MacroStep.KeyTap -> {
                        put("type", "key")
                        put("keyCode", step.keyCode)
                        put("ctrl", step.ctrl)
                    }
                    is MacroStep.Text -> {
                        put("type", "text")
                        put("literal", step.literal)
                    }
                    is MacroStep.Delay -> {
                        put("type", "delay")
                        put("ms", step.ms)
                    }
                }
            }

        private fun deserializeStep(obj: JSONObject): MacroStep =
            when (obj.getString("type")) {
                "key" -> MacroStep.KeyTap(
                    keyCode = obj.getInt("keyCode"),
                    ctrl = obj.getBoolean("ctrl")
                )
                "text" -> MacroStep.Text(obj.getString("literal"))
                "delay" -> MacroStep.Delay(obj.getInt("ms"))
                else -> error("Unknown macro step type")
            }
    }
}
