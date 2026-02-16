package com.keyjawn

import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ClipboardHistoryManagerTest {

    private lateinit var manager: ClipboardHistoryManager

    @Before
    fun setUp() {
        // Clear persisted pins between tests
        RuntimeEnvironment.getApplication()
            .getSharedPreferences("keyjawn_clipboard", 0)
            .edit().clear().commit()
        manager = ClipboardHistoryManager(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `starts with empty history`() {
        assertTrue(manager.getHistory().isEmpty())
        assertTrue(manager.getPinned().isEmpty())
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
    fun `limits to 30 items`() {
        for (i in 1..40) {
            manager.addToHistory("item$i")
        }
        val history = manager.getHistory()
        assertEquals(30, history.size)
        assertEquals("item40", history.first())
        assertEquals("item11", history.last())
    }

    @Test
    fun `paste commits most recent text`() {
        val ic = mock<android.view.inputmethod.InputConnection>()
        manager.addToHistory("paste me")
        manager.paste(ic)
        verify(ic).commitText("paste me", 1)
    }

    @Test
    fun `paste does nothing when empty`() {
        val ic = mock<android.view.inputmethod.InputConnection>()
        manager.paste(ic)
        verifyNoInteractions(ic)
    }

    @Test
    fun `pasteItem commits specific text`() {
        val ic = mock<android.view.inputmethod.InputConnection>()
        manager.pasteItem(ic, "specific text")
        verify(ic).commitText("specific text", 1)
    }

    @Test
    fun `getHistory returns a copy`() {
        manager.addToHistory("test")
        val history = manager.getHistory()
        manager.addToHistory("another")
        assertEquals(1, history.size)
        assertEquals(2, manager.getHistory().size)
    }

    // Pin tests

    @Test
    fun `pin moves item from history to pinned`() {
        manager.addToHistory("a")
        manager.addToHistory("b")
        manager.pin("a")
        assertEquals(listOf("a"), manager.getPinned())
        assertEquals(listOf("b"), manager.getHistory())
        assertTrue(manager.isPinned("a"))
        assertFalse(manager.isPinned("b"))
    }

    @Test
    fun `unpin moves item from pinned to top of history`() {
        manager.addToHistory("a")
        manager.addToHistory("b")
        manager.pin("a")
        manager.unpin("a")
        assertFalse(manager.isPinned("a"))
        assertEquals(emptyList<String>(), manager.getPinned())
        assertEquals("a", manager.getHistory().first())
    }

    @Test
    fun `addToHistory skips pinned items`() {
        manager.addToHistory("a")
        manager.pin("a")
        manager.addToHistory("a")
        assertEquals(listOf("a"), manager.getPinned())
        assertTrue(manager.getHistory().isEmpty())
    }

    @Test
    fun `pin returns false if already pinned`() {
        manager.addToHistory("a")
        assertTrue(manager.pin("a"))
        assertFalse(manager.pin("a"))
    }

    @Test
    fun `unpin returns false if not pinned`() {
        manager.addToHistory("a")
        assertFalse(manager.unpin("a"))
    }

    @Test
    fun `pin limited to 15 items`() {
        for (i in 1..20) {
            manager.addToHistory("item$i")
        }
        for (i in 1..20) {
            manager.pin("item$i")
        }
        assertEquals(15, manager.getPinned().size)
    }

    @Test
    fun `getMostRecent prefers pinned over history`() {
        manager.addToHistory("history item")
        manager.addToHistory("will pin")
        manager.pin("will pin")
        assertEquals("will pin", manager.getMostRecent())
    }

    @Test
    fun `pinned items persist across instances`() {
        manager.addToHistory("persist me")
        manager.pin("persist me")

        val manager2 = ClipboardHistoryManager(RuntimeEnvironment.getApplication())
        assertEquals(listOf("persist me"), manager2.getPinned())
        assertTrue(manager2.isPinned("persist me"))
        manager2.destroy()
    }
}
