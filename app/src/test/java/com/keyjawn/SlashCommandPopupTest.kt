package com.keyjawn

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SlashCommandPopupTest {

    private lateinit var registry: SlashCommandRegistry
    private lateinit var themeManager: ThemeManager
    private lateinit var controller: ActivityController<Activity>
    private lateinit var anchor: Button

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("keyjawn_theme", 0).edit().clear().commit()
        registry = SlashCommandRegistry(context)
        themeManager = ThemeManager(context)

        // PopupWindow.showAtLocation needs an anchor with a window token, so host
        // the anchor in a started activity.
        controller = Robolectric.buildActivity(Activity::class.java).setup()
        anchor = Button(controller.get())
        controller.get().setContentView(anchor)
    }

    @After
    fun tearDown() {
        controller.pause().stop().destroy()
    }

    /**
     * The popup must color its background from keyboardBg() and item text from
     * keyText() for the active theme. Against the pre-#42 code, which hardcoded
     * 0xFF1E1E1E for the background and 0xFFFFFFFF for the item text, these
     * assertions fail for every theme (no theme's keyboardBg/keyText equals those
     * constants).
     */
    @Test
    fun `popup colors background and item text from the dark theme tokens`() {
        themeManager.currentTheme = KeyboardTheme.DARK
        assertPopupThemed(KeyboardTheme.DARK)
    }

    @Test
    fun `popup colors background and item text from the light theme tokens`() {
        themeManager.currentTheme = KeyboardTheme.LIGHT
        assertPopupThemed(KeyboardTheme.LIGHT)
    }

    private fun assertPopupThemed(theme: KeyboardTheme) {
        val expectedBg = themeManager.keyboardBg()
        val expectedText = themeManager.keyText()

        // Sanity: the theme tokens must differ from the old hardcoded values, or
        // this test could pass against the pre-fix code by accident.
        assertNotEquals(OLD_HARDCODED_BG, expectedBg)
        assertNotEquals(OLD_HARDCODED_TEXT, expectedText)

        val popup = SlashCommandPopup(
            registry = registry,
            onCommandSelected = {},
            onDismissedEmpty = {},
            themeManager = themeManager
        )
        popup.show(anchor)
        assertTrue("popup did not show for theme $theme", popup.isShowing())

        val window = popupWindowField(popup)
        val content = window.contentView as ScrollView

        // Background applied to both the content ScrollView and the window itself.
        assertEquals(expectedBg, (content.background as ColorDrawable).color)
        assertEquals(expectedBg, (window.background as ColorDrawable).color)

        val list = content.findViewById<LinearLayout>(R.id.command_list)
        assertTrue("popup produced no command items", list.childCount > 0)
        for (i in 0 until list.childCount) {
            val item = list.getChildAt(i) as TextView
            assertEquals(expectedText, item.currentTextColor)
        }

        popup.dismiss()
    }

    private fun popupWindowField(popup: SlashCommandPopup): PopupWindow {
        val field = SlashCommandPopup::class.java.getDeclaredField("popup")
        field.isAccessible = true
        return field.get(popup) as PopupWindow
    }

    companion object {
        private val OLD_HARDCODED_BG = 0xFF1E1E1E.toInt()
        private val OLD_HARDCODED_TEXT = 0xFFFFFFFF.toInt()
    }
}
