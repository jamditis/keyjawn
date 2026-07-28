package com.keyjawn

import android.content.Context
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MacroStoreTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(MacroStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `save then fresh load round trips every step in order`() {
        val macro = Macro(
            label = "Agent prompt",
            steps = listOf(
                MacroStep.Text("Review this"),
                MacroStep.Delay(250),
                MacroStep.KeyTap(keyCode = 66, ctrl = true)
            )
        )

        MacroStore(context).save("agent_prompt", macro)

        assertEquals(macro, MacroStore(context).load()["agent_prompt"])
    }

    @Test
    fun `every step type serializes with its discriminator and deserializes equally`() {
        val macro = Macro(
            label = "All steps",
            steps = listOf(
                MacroStep.KeyTap(keyCode = 61),
                MacroStep.Text("literal"),
                MacroStep.Delay(500)
            )
        )

        val json = MacroStore.serialize(mapOf("all" to macro))
        val steps = JSONObject(json).getJSONObject("all").getJSONArray("steps")

        assertEquals("key", steps.getJSONObject(0).getString("type"))
        assertEquals("text", steps.getJSONObject(1).getString("type"))
        assertEquals("delay", steps.getJSONObject(2).getString("type"))
        assertEquals(mapOf("all" to macro), MacroStore.deserialize(json))
    }

    @Test
    fun `missing macros load as empty`() {
        assertTrue(MacroStore(context).load().isEmpty())
    }

    @Test
    fun `corrupt macros load as empty without throwing`() {
        context.getSharedPreferences(MacroStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(MacroStore.PREFS_KEY, "{not json")
            .commit()

        assertTrue(MacroStore(context).load().isEmpty())
    }

    @Test
    fun `non-string macros load as empty without throwing`() {
        context.getSharedPreferences(MacroStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(MacroStore.PREFS_KEY, 42)
            .commit()

        assertTrue(MacroStore(context).load().isEmpty())
    }
}
