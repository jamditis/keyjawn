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
class ThemeManagerTest {

    private lateinit var themeManager: ThemeManager

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("keyjawn_theme", 0).edit().clear().commit()
        themeManager = ThemeManager(context)
    }

    @Test
    fun `default theme is DARK`() {
        assertEquals(KeyboardTheme.DARK, themeManager.currentTheme)
    }

    @Test
    fun `setting theme persists`() {
        themeManager.currentTheme = KeyboardTheme.TERMINAL
        assertEquals(KeyboardTheme.TERMINAL, themeManager.currentTheme)
    }

    @Test
    fun `setting theme persists across instances`() {
        themeManager.currentTheme = KeyboardTheme.OLED
        val second = ThemeManager(RuntimeEnvironment.getApplication())
        assertEquals(KeyboardTheme.OLED, second.currentTheme)
    }

    @Test
    fun `each theme has distinct keyboard background`() {
        val backgrounds = KeyboardTheme.entries.map { theme ->
            themeManager.currentTheme = theme
            themeManager.keyboardBg()
        }.toSet()
        assertEquals(KeyboardTheme.entries.size, backgrounds.size)
    }

    @Test
    fun `each theme has distinct key background`() {
        val colors = KeyboardTheme.entries.map { theme ->
            themeManager.currentTheme = theme
            themeManager.keyBg()
        }.toSet()
        assertEquals(KeyboardTheme.entries.size, colors.size)
    }

    @Test
    fun `quickKeyBg returns non-zero for all themes`() {
        for (theme in KeyboardTheme.entries) {
            themeManager.currentTheme = theme
            assertNotEquals(0, themeManager.quickKeyBg())
        }
    }

    @Test
    fun `OLED background is pure black`() {
        themeManager.currentTheme = KeyboardTheme.OLED
        assertEquals(0xFF000000.toInt(), themeManager.keyboardBg())
    }

    @Test
    fun `TERMINAL accent is green`() {
        themeManager.currentTheme = KeyboardTheme.TERMINAL
        assertEquals(0xFF33FF33.toInt(), themeManager.accent())
    }

    @Test
    fun `getAvailableThemes returns all four`() {
        val themes = themeManager.getAvailableThemes()
        assertEquals(4, themes.size)
        assertTrue(themes.contains(KeyboardTheme.DARK))
        assertTrue(themes.contains(KeyboardTheme.LIGHT))
        assertTrue(themes.contains(KeyboardTheme.OLED))
        assertTrue(themes.contains(KeyboardTheme.TERMINAL))
    }

    @Test
    fun `themeLabel returns correct labels`() {
        assertEquals("Dark", themeManager.themeLabel(KeyboardTheme.DARK))
        assertEquals("Light", themeManager.themeLabel(KeyboardTheme.LIGHT))
        assertEquals("OLED black", themeManager.themeLabel(KeyboardTheme.OLED))
        assertEquals("Terminal", themeManager.themeLabel(KeyboardTheme.TERMINAL))
    }

    @Test
    fun `invalid stored theme falls back to DARK`() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("keyjawn_theme", 0)
            .edit().putString("theme", "NONEXISTENT").commit()
        val tm = ThemeManager(context)
        assertEquals(KeyboardTheme.DARK, tm.currentTheme)
    }

    @Test
    fun `extra row background differs from keyboard background for non-OLED themes`() {
        for (theme in KeyboardTheme.entries) {
            if (theme == KeyboardTheme.OLED) continue // OLED uses pure black for both
            themeManager.currentTheme = theme
            assertNotEquals(
                "Extra row bg should differ from keyboard bg for $theme",
                themeManager.keyboardBg(),
                themeManager.extraRowBg()
            )
        }
    }
}
