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
    fun `swatch does not read or write the persisted theme pref`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("keyjawn_theme", 0)
        themeManager.currentTheme = KeyboardTheme.LIGHT

        var writes = 0
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme") writes++
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        try {
            // Reading any other theme's palette must not change the live selection.
            for (theme in KeyboardTheme.entries) {
                themeManager.swatch(theme)
            }
        } finally {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }

        assertEquals("swatch must not write the theme pref", 0, writes)
        assertEquals(KeyboardTheme.LIGHT, themeManager.currentTheme)
    }

    @Test
    fun `swatch colors match the instance resolvers for the active theme`() {
        for (theme in KeyboardTheme.entries) {
            themeManager.currentTheme = theme
            val palette = themeManager.swatch(theme)
            assertEquals("keyboardBg mismatch for $theme", themeManager.keyboardBg(), palette.keyboardBg)
            assertEquals("keyBg mismatch for $theme", themeManager.keyBg(), palette.keyBg)
            assertEquals("keyText mismatch for $theme", themeManager.keyText(), palette.keyText)
        }
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

    @Test
    fun `color lookups do not re-read the theme pref after the theme is set`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("keyjawn_theme", 0)

        // Set the theme through the setter (the single refresh point).
        themeManager.currentTheme = KeyboardTheme.DARK
        val darkKeyBg = themeManager.keyBg()
        assertEquals(0xFF2B2B30.toInt(), darkKeyBg)

        // Mutate the persisted pref directly, bypassing the setter. A cached
        // palette must not observe this change on a plain color lookup.
        prefs.edit().putString("theme", KeyboardTheme.TERMINAL.name).commit()

        assertEquals(
            "color lookup must read the cached palette, not re-parse prefs",
            darkKeyBg,
            themeManager.keyBg()
        )
        assertEquals(KeyboardTheme.DARK, themeManager.currentTheme)
    }

    @Test
    fun `refresh re-resolves the theme and palette from prefs`() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("keyjawn_theme", 0)

        themeManager.currentTheme = KeyboardTheme.DARK
        // External write, then explicit refresh: the cache picks up the change.
        prefs.edit().putString("theme", KeyboardTheme.TERMINAL.name).commit()
        themeManager.refresh()

        assertEquals(KeyboardTheme.TERMINAL, themeManager.currentTheme)
        assertEquals(0xFF0F2B0F.toInt(), themeManager.keyBg())
    }

    @Test
    fun `themeForName resolves every enum name and falls back to DARK`() {
        for (theme in KeyboardTheme.entries) {
            assertEquals(theme, ThemeManager.themeForName(theme.name))
        }
        assertEquals(KeyboardTheme.DARK, ThemeManager.themeForName("NONEXISTENT"))
        assertEquals(KeyboardTheme.DARK, ThemeManager.themeForName(null))
    }

    @Test
    fun `cached palette matches per-theme colors across all resolvers`() {
        // Resolve every color for each theme and assert the cache stays in sync
        // with the theme after each set, with no stale carry-over.
        for (theme in KeyboardTheme.entries) {
            themeManager.currentTheme = theme
            val swatch = themeManager.swatch(theme)
            assertEquals(swatch.keyboardBg, themeManager.keyboardBg())
            assertEquals(swatch.keyBg, themeManager.keyBg())
            assertEquals(swatch.keyText, themeManager.keyText())
        }
    }
}
