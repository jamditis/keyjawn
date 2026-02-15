package com.keyjawn

import android.content.ClipboardManager
import android.content.Context
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ClipboardHistoryManagerTest {

    private lateinit var manager: ClipboardHistoryManager

    @Before
    fun setUp() {
        manager = ClipboardHistoryManager(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `starts with empty history`() {
        assertTrue(manager.getHistory().isEmpty())
        assertNull(manager.getMostRecent())
    }

    @Test
    fun `addToHistory adds item`() {
        manager.addToHistory("hello")
        assertEquals(listOf("hello"), manager.getHistory())
        assertEquals("hello", manager.getMostRecent())
    }

    @Test
    fun `most recent item is first`() {
        manager.addToHistory("first")
        manager.addToHistory("second")
        assertEquals("second", manager.getMostRecent())
        assertEquals(listOf("second", "first"), manager.getHistory())
    }

    @Test
    fun `deduplicates and moves to front`() {
        manager.addToHistory("a")
        manager.addToHistory("b")
        manager.addToHistory("a")
        assertEquals(listOf("a", "b"), manager.getHistory())
    }

    @Test
    fun `limits to 10 items`() {
        for (i in 1..15) {
            manager.addToHistory("item$i")
        }
        val history = manager.getHistory()
        assertEquals(10, history.size)
        assertEquals("item15", history.first())
        assertEquals("item6", history.last())
    }

    @Test
    fun `paste commits most recent text`() {
        val ic = mock<android.view.inputmethod.InputConnection>()
        manager.addToHistory("paste me")
        manager.paste(ic)
        org.mockito.kotlin.verify(ic).commitText("paste me", 1)
    }

    @Test
    fun `paste does nothing when empty`() {
        val ic = mock<android.view.inputmethod.InputConnection>()
        manager.paste(ic)
        org.mockito.kotlin.verifyNoInteractions(ic)
    }

    @Test
    fun `pasteItem commits specific text`() {
        val ic = mock<android.view.inputmethod.InputConnection>()
        manager.pasteItem(ic, "specific text")
        org.mockito.kotlin.verify(ic).commitText("specific text", 1)
    }

    @Test
    fun `getHistory returns a copy`() {
        manager.addToHistory("test")
        val history = manager.getHistory()
        manager.addToHistory("another")
        assertEquals(1, history.size)
        assertEquals(2, manager.getHistory().size)
    }
}
