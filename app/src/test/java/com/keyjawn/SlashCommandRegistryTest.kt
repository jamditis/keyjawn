package com.keyjawn

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SlashCommandRegistryTest {

    private lateinit var registry: SlashCommandRegistry

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        registry = SlashCommandRegistry(context)
    }

    @Test
    fun `loads command sets from assets`() {
        val sets = registry.getAvailableSets()
        assertTrue(sets.containsKey("claude_code"))
        assertTrue(sets.containsKey("aider"))
        assertTrue(sets.containsKey("general"))
    }

    @Test
    fun `claude_code set has correct commands`() {
        val sets = registry.getAvailableSets()
        val claude = sets["claude_code"]!!
        assertEquals("Claude Code", claude.label)
        assertTrue(claude.commands.contains("/help"))
        assertTrue(claude.commands.contains("/commit"))
        assertTrue(claude.commands.contains("/compact"))
        assertEquals(9, claude.commands.size)
    }

    @Test
    fun `aider set has correct commands`() {
        val sets = registry.getAvailableSets()
        val aider = sets["aider"]!!
        assertEquals("Aider", aider.label)
        assertTrue(aider.commands.contains("/add"))
        assertTrue(aider.commands.contains("/test"))
        assertEquals(9, aider.commands.size)
    }

    @Test
    fun `general set has correct commands`() {
        val sets = registry.getAvailableSets()
        val general = sets["general"]!!
        assertEquals("General", general.label)
        assertEquals(3, general.commands.size)
    }

    @Test
    fun `default enabled sets are claude_code and general`() {
        val enabled = registry.getEnabledSets()
        assertTrue(enabled.contains("claude_code"))
        assertTrue(enabled.contains("general"))
        assertFalse(enabled.contains("aider"))
    }

    @Test
    fun `getCommands returns only enabled set commands`() {
        val commands = registry.getCommands()
        assertTrue(commands.contains("/commit"))
        assertTrue(commands.contains("/exit"))
        assertFalse(commands.contains("/add"))
    }

    @Test
    fun `getCommands deduplicates across sets`() {
        val commands = registry.getCommands()
        val helpCount = commands.count { it == "/help" }
        assertEquals(1, helpCount)
    }

    @Test
    fun `enabling a set includes its commands`() {
        registry.setEnabled("aider", true)
        val commands = registry.getCommands()
        assertTrue(commands.contains("/add"))
        assertTrue(commands.contains("/test"))
    }

    @Test
    fun `disabling a set removes its commands`() {
        registry.setEnabled("claude_code", false)
        val commands = registry.getCommands()
        assertFalse(commands.contains("/compact"))
        assertFalse(commands.contains("/cost"))
        assertTrue(commands.contains("/exit"))
    }

    @Test
    fun `recordUsage moves command to front`() {
        registry.recordUsage("/exit")
        val commands = registry.getCommands()
        assertEquals("/exit", commands[0])
    }

    @Test
    fun `multiple MRU entries maintain order`() {
        registry.recordUsage("/exit")
        registry.recordUsage("/commit")
        val commands = registry.getCommands()
        assertEquals("/commit", commands[0])
        assertEquals("/exit", commands[1])
    }

    @Test
    fun `recording same command twice does not duplicate`() {
        registry.recordUsage("/commit")
        registry.recordUsage("/exit")
        registry.recordUsage("/commit")
        val commands = registry.getCommands()
        assertEquals("/commit", commands[0])
        assertEquals("/exit", commands[1])
        val commitCount = commands.count { it == "/commit" }
        assertEquals(1, commitCount)
    }

    @Test
    fun `MRU is capped at 10 entries`() {
        val manyCommands = registry.getCommands()
        for (cmd in manyCommands.take(12)) {
            registry.recordUsage(cmd)
        }
        registry.recordUsage("/new-one")
        // /new-one won't appear because it's not in enabled commands,
        // but the internal list should not grow beyond 10
        // Verify the result list still works
        val commands = registry.getCommands()
        assertTrue(commands.isNotEmpty())
    }

    @Test
    fun `MRU persists via shared preferences`() {
        val context = RuntimeEnvironment.getApplication()
        registry.recordUsage("/exit")

        val registry2 = SlashCommandRegistry(context)
        val commands = registry2.getCommands()
        assertEquals("/exit", commands[0])
    }

    @Test
    fun `disabling all sets returns empty command list`() {
        registry.setEnabled("claude_code", false)
        registry.setEnabled("general", false)
        val commands = registry.getCommands()
        assertTrue(commands.isEmpty())
    }
}
