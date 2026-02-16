package com.keyjawn

import org.junit.Test
import org.junit.Assert.*

class AltKeyMappingsTest {

    @Test
    fun `lowercase a has accented variants`() {
        val alts = AltKeyMappings.getAlts("a")
        assertNotNull(alts)
        assertTrue(alts!!.contains("\u00e1")) // a-acute
        assertTrue(alts.contains("\u00e0")) // a-grave
        assertTrue(alts.contains("\u00e4")) // a-umlaut
        assertEquals(6, alts.size)
    }

    @Test
    fun `lowercase e has accented variants`() {
        val alts = AltKeyMappings.getAlts("e")
        assertNotNull(alts)
        assertTrue(alts!!.contains("\u00e9")) // e-acute
        assertEquals(4, alts.size)
    }

    @Test
    fun `lowercase i has accented variants`() {
        val alts = AltKeyMappings.getAlts("i")
        assertNotNull(alts)
        assertEquals(4, alts!!.size)
    }

    @Test
    fun `lowercase o has accented variants`() {
        val alts = AltKeyMappings.getAlts("o")
        assertNotNull(alts)
        assertEquals(5, alts!!.size)
    }

    @Test
    fun `lowercase u has accented variants`() {
        val alts = AltKeyMappings.getAlts("u")
        assertNotNull(alts)
        assertEquals(4, alts!!.size)
    }

    @Test
    fun `n maps to n-tilde`() {
        val alts = AltKeyMappings.getAlts("n")
        assertNotNull(alts)
        assertEquals(listOf("\u00f1"), alts)
    }

    @Test
    fun `c maps to c-cedilla`() {
        val alts = AltKeyMappings.getAlts("c")
        assertNotNull(alts)
        assertEquals(listOf("\u00e7"), alts)
    }

    @Test
    fun `s has eszett and dollar`() {
        val alts = AltKeyMappings.getAlts("s")
        assertNotNull(alts)
        assertTrue(alts!!.contains("\u00df"))
        assertTrue(alts.contains("$"))
        assertEquals(2, alts.size)
    }

    @Test
    fun `y maps to y-umlaut`() {
        val alts = AltKeyMappings.getAlts("y")
        assertNotNull(alts)
        assertEquals(listOf("\u00ff"), alts)
    }

    @Test
    fun `uppercase A returns uppercased accented variants`() {
        val alts = AltKeyMappings.getAlts("A")
        assertNotNull(alts)
        assertTrue(alts!!.contains("\u00c1")) // A-acute (uppercase of a-acute)
        assertTrue(alts.contains("\u00c0")) // A-grave
        assertEquals(6, alts.size)
    }

    @Test
    fun `uppercase N returns uppercase n-tilde`() {
        val alts = AltKeyMappings.getAlts("N")
        assertNotNull(alts)
        assertEquals(listOf("\u00d1"), alts)
    }

    @Test
    fun `number keys map to shifted symbols`() {
        assertEquals(listOf("!"), AltKeyMappings.getAlts("1"))
        assertEquals(listOf("@"), AltKeyMappings.getAlts("2"))
        assertEquals(listOf("#"), AltKeyMappings.getAlts("3"))
        assertEquals(listOf("$"), AltKeyMappings.getAlts("4"))
        assertEquals(listOf("%"), AltKeyMappings.getAlts("5"))
        assertEquals(listOf("^"), AltKeyMappings.getAlts("6"))
        assertEquals(listOf("&"), AltKeyMappings.getAlts("7"))
        assertEquals(listOf("*"), AltKeyMappings.getAlts("8"))
        assertEquals(listOf("("), AltKeyMappings.getAlts("9"))
        assertEquals(listOf(")"), AltKeyMappings.getAlts("0"))
    }

    @Test
    fun `period has ellipsis and punctuation`() {
        val alts = AltKeyMappings.getAlts(".")
        assertNotNull(alts)
        assertTrue(alts!!.contains("\u2026")) // ellipsis
        assertTrue(alts.contains("?"))
        assertTrue(alts.contains("!"))
        assertEquals(3, alts.size)
    }

    @Test
    fun `hyphen has em-dash and en-dash`() {
        val alts = AltKeyMappings.getAlts("-")
        assertNotNull(alts)
        assertTrue(alts!!.contains("\u2014")) // em-dash
        assertTrue(alts.contains("\u2013")) // en-dash
        assertEquals(2, alts.size)
    }

    @Test
    fun `unmapped key returns null`() {
        assertNull(AltKeyMappings.getAlts("z"))
        assertNull(AltKeyMappings.getAlts("q"))
        assertNull(AltKeyMappings.getAlts("x"))
    }

    @Test
    fun `uppercase unmapped key returns null`() {
        assertNull(AltKeyMappings.getAlts("Z"))
        assertNull(AltKeyMappings.getAlts("Q"))
    }
}
