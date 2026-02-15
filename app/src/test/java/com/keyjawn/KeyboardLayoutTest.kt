package com.keyjawn

import org.junit.Test
import org.junit.Assert.*

class KeyboardLayoutTest {

    @Test
    fun `lowercase layer has 4 rows`() {
        assertEquals(4, KeyboardLayouts.lowercase.size)
    }

    @Test
    fun `uppercase layer has 4 rows`() {
        assertEquals(4, KeyboardLayouts.uppercase.size)
    }

    @Test
    fun `symbols layer has 4 rows`() {
        assertEquals(4, KeyboardLayouts.symbols.size)
    }

    @Test
    fun `lowercase row 1 has 10 keys`() {
        assertEquals(10, KeyboardLayouts.lowercase[0].size)
    }

    @Test
    fun `lowercase row 2 has 9 keys`() {
        assertEquals(9, KeyboardLayouts.lowercase[1].size)
    }

    @Test
    fun `lowercase row 3 has 9 keys`() {
        // shift + 7 letters + backspace = 9
        assertEquals(9, KeyboardLayouts.lowercase[2].size)
    }

    @Test
    fun `lowercase row 4 has 4 keys`() {
        assertEquals(4, KeyboardLayouts.lowercase[3].size)
    }

    @Test
    fun `uppercase row 1 has 10 keys`() {
        assertEquals(10, KeyboardLayouts.uppercase[0].size)
    }

    @Test
    fun `uppercase row 2 has 9 keys`() {
        assertEquals(9, KeyboardLayouts.uppercase[1].size)
    }

    @Test
    fun `uppercase row 3 has 9 keys`() {
        assertEquals(9, KeyboardLayouts.uppercase[2].size)
    }

    @Test
    fun `uppercase row 4 has 4 keys`() {
        assertEquals(4, KeyboardLayouts.uppercase[3].size)
    }

    @Test
    fun `symbols row 1 has 10 keys`() {
        assertEquals(10, KeyboardLayouts.symbols[0].size)
    }

    @Test
    fun `symbols row 2 has 9 keys`() {
        assertEquals(9, KeyboardLayouts.symbols[1].size)
    }

    @Test
    fun `symbols row 3 has 9 keys`() {
        assertEquals(9, KeyboardLayouts.symbols[2].size)
    }

    @Test
    fun `symbols row 4 has 4 keys`() {
        assertEquals(4, KeyboardLayouts.symbols[3].size)
    }

    @Test
    fun `lowercase row 1 contains qwerty sequence`() {
        val labels = KeyboardLayouts.lowercase[0].map { it.label }
        assertEquals(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"), labels)
    }

    @Test
    fun `uppercase row 1 contains QWERTY sequence`() {
        val labels = KeyboardLayouts.uppercase[0].map { it.label }
        assertEquals(listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P"), labels)
    }

    @Test
    fun `symbols row 1 contains digits`() {
        val labels = KeyboardLayouts.symbols[0].map { it.label }
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"), labels)
    }

    @Test
    fun `lowercase row 3 starts with shift and ends with backspace`() {
        val row = KeyboardLayouts.lowercase[2]
        assertTrue(row.first().output is KeyOutput.Shift)
        assertTrue(row.last().output is KeyOutput.Backspace)
    }

    @Test
    fun `uppercase row 3 starts with shift and ends with backspace`() {
        val row = KeyboardLayouts.uppercase[2]
        assertTrue(row.first().output is KeyOutput.Shift)
        assertTrue(row.last().output is KeyOutput.Backspace)
    }

    @Test
    fun `lowercase row 4 has sym, space, slash, enter`() {
        val row = KeyboardLayouts.lowercase[3]
        assertTrue(row[0].output is KeyOutput.SymSwitch)
        assertTrue(row[1].output is KeyOutput.Space)
        assertTrue(row[2].output is KeyOutput.Slash)
        assertTrue(row[3].output is KeyOutput.Enter)
    }

    @Test
    fun `symbols row 4 has abc, space, slash, enter`() {
        val row = KeyboardLayouts.symbols[3]
        assertTrue(row[0].output is KeyOutput.AbcSwitch)
        assertTrue(row[1].output is KeyOutput.Space)
        assertTrue(row[2].output is KeyOutput.Slash)
        assertTrue(row[3].output is KeyOutput.Enter)
    }

    @Test
    fun `shift and backspace have 1_5x weight`() {
        val row = KeyboardLayouts.lowercase[2]
        assertEquals(1.5f, row.first().weight)
        assertEquals(1.5f, row.last().weight)
    }

    @Test
    fun `space key has weight 5`() {
        val spaceKey = KeyboardLayouts.lowercase[3][1]
        assertTrue(spaceKey.output is KeyOutput.Space)
        assertEquals(5f, spaceKey.weight)
    }

    @Test
    fun `sym key has weight 1_5`() {
        val symKey = KeyboardLayouts.lowercase[3][0]
        assertTrue(symKey.output is KeyOutput.SymSwitch)
        assertEquals(1.5f, symKey.weight)
    }

    @Test
    fun `enter key has weight 1_5`() {
        val enterKey = KeyboardLayouts.lowercase[3][3]
        assertTrue(enterKey.output is KeyOutput.Enter)
        assertEquals(1.5f, enterKey.weight)
    }

    @Test
    fun `slash key has weight 1`() {
        val slashKey = KeyboardLayouts.lowercase[3][2]
        assertTrue(slashKey.output is KeyOutput.Slash)
        assertEquals(1f, slashKey.weight)
    }

    @Test
    fun `getLayer returns correct layers by index`() {
        assertSame(KeyboardLayouts.lowercase, KeyboardLayouts.getLayer(0))
        assertSame(KeyboardLayouts.uppercase, KeyboardLayouts.getLayer(1))
        assertSame(KeyboardLayouts.symbols, KeyboardLayouts.getLayer(2))
    }

    @Test
    fun `getLayer falls back to lowercase for invalid index`() {
        assertSame(KeyboardLayouts.lowercase, KeyboardLayouts.getLayer(99))
    }

    @Test
    fun `layer constants match expected values`() {
        assertEquals(0, KeyboardLayouts.LAYER_LOWER)
        assertEquals(1, KeyboardLayouts.LAYER_UPPER)
        assertEquals(2, KeyboardLayouts.LAYER_SYMBOLS)
    }

    @Test
    fun `all lowercase character keys output their label`() {
        for (row in KeyboardLayouts.lowercase) {
            for (key in row) {
                if (key.output is KeyOutput.Character) {
                    assertEquals(key.label, (key.output as KeyOutput.Character).char)
                }
            }
        }
    }

    @Test
    fun `all uppercase character keys output their label`() {
        for (row in KeyboardLayouts.uppercase) {
            for (key in row) {
                if (key.output is KeyOutput.Character) {
                    assertEquals(key.label, (key.output as KeyOutput.Character).char)
                }
            }
        }
    }

    @Test
    fun `lowercase and uppercase have same key count per row`() {
        for (i in KeyboardLayouts.lowercase.indices) {
            assertEquals(
                KeyboardLayouts.lowercase[i].size,
                KeyboardLayouts.uppercase[i].size
            )
        }
    }

    @Test
    fun `symbols row 2 contains expected punctuation`() {
        val labels = KeyboardLayouts.symbols[1].map { it.label }
        assertEquals(listOf("-", "_", "=", "+", ".", "\\", "|", "~", "`"), labels)
    }

    @Test
    fun `symbols row 3 contains expected symbols`() {
        val labels = KeyboardLayouts.symbols[2].map { it.label }
        assertEquals(listOf("!", "@", "#", "$", "%", "&", "*", "(", ")"), labels)
    }
}
