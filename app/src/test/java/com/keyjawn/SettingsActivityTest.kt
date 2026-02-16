package com.keyjawn

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Tests the theme-aware settings layout by directly inflating activity_settings.xml
 * and calling the same styling patterns used by SettingsActivity.
 *
 * We avoid launching the full Activity because HostStorage uses AndroidX
 * EncryptedSharedPreferences which doesn't work in Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsActivityTest {

    private lateinit var themeManager: ThemeManager
    private lateinit var view: View

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("keyjawn_theme", 0).edit().clear().commit()
        context.getSharedPreferences("keyjawn_app_prefs", 0).edit().clear().commit()
        themeManager = ThemeManager(context)

        view = android.view.LayoutInflater.from(context)
            .inflate(R.layout.activity_settings, null)
    }

    @Test
    fun `layout inflates without crash`() {
        assertNotNull(view)
    }

    @Test
    fun `settings title exists`() {
        val title = view.findViewById<TextView>(R.id.settings_title)
        assertNotNull(title)
        assertEquals("KeyJawn settings", title.text.toString())
    }

    @Test
    fun `toggles header exists`() {
        val header = view.findViewById<TextView>(R.id.toggles_header)
        assertNotNull(header)
        assertEquals("Toggles", header.text.toString())
    }

    @Test
    fun `haptic toggle exists`() {
        val toggle = view.findViewById<CheckBox>(R.id.haptic_toggle)
        assertNotNull(toggle)
    }

    @Test
    fun `tooltip section starts hidden`() {
        val section = view.findViewById<LinearLayout>(R.id.tooltip_section)
        assertEquals(View.GONE, section.visibility)
    }

    @Test
    fun `theme section starts hidden`() {
        val section = view.findViewById<LinearLayout>(R.id.theme_section)
        assertEquals(View.GONE, section.visibility)
    }

    @Test
    fun `key mapping section starts hidden`() {
        val section = view.findViewById<LinearLayout>(R.id.key_mapping_section)
        assertEquals(View.GONE, section.visibility)
    }

    @Test
    fun `card containers exist`() {
        for (id in listOf(R.id.toggles_card, R.id.theme_card, R.id.key_mapping_card,
            R.id.host_card, R.id.commands_card)) {
            assertNotNull("Card container should exist", view.findViewById<View>(id))
        }
    }

    @Test
    fun `dividers exist`() {
        for (id in listOf(R.id.divider_1, R.id.divider_2, R.id.divider_3, R.id.divider_4)) {
            val divider = view.findViewById<View>(id)
            assertNotNull("Divider should exist", divider)
            assertEquals("Divider should be 1dp height", 1,
                (divider.layoutParams as? android.widget.LinearLayout.LayoutParams)?.height ?: -1)
        }
    }

    @Test
    fun `section headers exist for all sections`() {
        val headers = mapOf(
            R.id.toggles_header to "Toggles",
            R.id.theme_header to "Theme",
            R.id.key_mapping_header to "Key mapping",
            R.id.host_header to "SSH hosts",
            R.id.commands_header to "Slash command sets"
        )
        for ((id, expectedText) in headers) {
            val header = view.findViewById<TextView>(id)
            assertNotNull("Header for '$expectedText' should exist", header)
            assertEquals(expectedText, header.text.toString())
        }
    }

    @Test
    fun `upgrade button starts hidden`() {
        val btn = view.findViewById<View>(R.id.upgrade_btn)
        assertEquals(View.GONE, btn.visibility)
    }

    @Test
    fun `add command set button starts hidden`() {
        val btn = view.findViewById<View>(R.id.add_command_set_btn)
        assertEquals(View.GONE, btn.visibility)
    }

    @Test
    fun `version text exists`() {
        val versionText = view.findViewById<TextView>(R.id.version_text)
        assertNotNull(versionText)
    }

    @Test
    fun `card background can be applied programmatically`() {
        val card = view.findViewById<LinearLayout>(R.id.toggles_card)
        val density = RuntimeEnvironment.getApplication().resources.displayMetrics.density
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8 * density
            setColor(themeManager.keyBg())
        }
        card.background = bg
        assertTrue(card.background is GradientDrawable)
    }

    @Test
    fun `theme colors apply to section headers`() {
        val header = view.findViewById<TextView>(R.id.toggles_header)
        header.setTextColor(themeManager.keyHint())
        assertEquals(themeManager.keyHint(), header.currentTextColor)
    }

    @Test
    fun `theme colors apply to labels`() {
        val label = view.findViewById<TextView>(R.id.haptic_label)
        label.setTextColor(themeManager.keyText())
        assertEquals(themeManager.keyText(), label.currentTextColor)
    }

    @Test
    fun `all themes produce valid colors for settings elements`() {
        for (theme in KeyboardTheme.entries) {
            themeManager.currentTheme = theme
            // These should not throw
            val bg = themeManager.keyboardBg()
            val text = themeManager.keyText()
            val hint = themeManager.keyHint()
            val key = themeManager.keyBg()
            val divider = themeManager.divider()
            val accent = themeManager.accent()
            // Colors should be non-zero (fully transparent black would be suspicious)
            assertTrue("keyboardBg for $theme should have alpha",
                (bg ushr 24) and 0xFF > 0)
            assertTrue("keyText for $theme should have alpha",
                (text ushr 24) and 0xFF > 0)
        }
    }
}
