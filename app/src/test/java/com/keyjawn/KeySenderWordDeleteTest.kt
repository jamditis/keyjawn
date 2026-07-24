package com.keyjawn

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeySenderWordDeleteTest {

    private fun count(before: String): Int = KeySender.wordDeleteCount(before)

    @Test
    fun `deletes the word before the cursor`() {
        assertEquals(6, count("git status"))
    }

    @Test
    fun `trailing space is taken with the word before it`() {
        // A hold at the end of "git commit " should clear "commit ", not just
        // the space -- otherwise the first tick of a word-delete looks stuck.
        assertEquals(7, count("git commit "))
    }

    @Test
    fun `a path counts as one word`() {
        // Slashes and dots are word characters here: on a keyboard built for
        // shell prompts, a path is one thing the user meant to remove.
        assertEquals(15, count("cat src/main/App.kt"))
    }

    @Test
    fun `a flag counts as one word`() {
        assertEquals(11, count("git push --no-verify"))
    }

    @Test
    fun `symbols and words are separate steps`() {
        // "foo();" clears as "();" then "foo" -- predictable, not one greedy
        // swallow of the whole expression.
        assertEquals(3, count("foo();"))
    }

    @Test
    fun `deletion stops at the start of the line`() {
        assertEquals(2, count("one\nls"))
    }

    @Test
    fun `a trailing newline is its own step`() {
        assertEquals(1, count("one\n"))
    }

    @Test
    fun `nothing before the cursor deletes nothing`() {
        assertEquals(0, count(""))
    }

    @Test
    fun `only whitespace before the cursor is consumed`() {
        assertEquals(3, count("   "))
    }

    @Test
    fun `deleteWordBefore issues one delete for the whole word`() {
        val ic = mock<android.view.inputmethod.InputConnection>()
        whenever(ic.getTextBeforeCursor(any(), any())).thenReturn("git status")
        whenever(ic.deleteSurroundingText(any(), any())).thenReturn(true)

        assertTrue(KeySender().deleteWordBefore(ic))
        verify(ic).deleteSurroundingText(6, 0)
    }

    @Test
    fun `deleteWordBefore reports nothing to delete on an empty field`() {
        val ic = mock<android.view.inputmethod.InputConnection>()
        whenever(ic.getTextBeforeCursor(any(), any())).thenReturn("")

        assertFalse(KeySender().deleteWordBefore(ic))
        verify(ic, never()).deleteSurroundingText(any(), any())
    }

    @Test
    fun `deleteWordBefore reports nothing when the editor has no text to read`() {
        val ic = mock<android.view.inputmethod.InputConnection>()
        whenever(ic.getTextBeforeCursor(any(), any())).thenReturn(null)

        assertFalse(KeySender().deleteWordBefore(ic))
    }
}
