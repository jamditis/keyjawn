package com.keyjawn

import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MenuPanelTest {

    private lateinit var panel: ScrollView
    private lateinit var list: LinearLayout
    private lateinit var themeManager: ThemeManager
    private lateinit var appPrefs: AppPrefs
    private lateinit var menuPanel: MenuPanel
    private var tooltipMessages = mutableListOf<String>()

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("keyjawn_theme", 0).edit().clear().commit()
        context.getSharedPreferences("keyjawn_prefs", 0).edit().clear().commit()

        panel = ScrollView(context)
        panel.visibility = View.GONE
        list = LinearLayout(context)
        panel.addView(list)
        themeManager = ThemeManager(context)
        appPrefs = AppPrefs(context)
        tooltipMessages.clear()

        menuPanel = MenuPanel(
            panel = panel,
            list = list,
            themeManager = themeManager,
            appPrefs = appPrefs,
            isFullFlavor = true,
            onUploadTap = null,
            onOpenSettings = {},
            onThemeChanged = {},
            onShowTooltip = { tooltipMessages.add(it) },
            currentPackageProvider = { "com.test.app" }
        )
    }

    @Test
    fun `starts hidden`() {
        assertFalse(menuPanel.isShowing())
    }

    @Test
    fun `show makes panel visible`() {
        menuPanel.show()
        assertTrue(menuPanel.isShowing())
        assertEquals(View.VISIBLE, panel.visibility)
    }

    @Test
    fun `hide makes panel gone`() {
        menuPanel.show()
        menuPanel.hide()
        assertFalse(menuPanel.isShowing())
        assertEquals(View.GONE, panel.visibility)
    }

    @Test
    fun `toggle shows when hidden`() {
        menuPanel.toggle()
        assertTrue(menuPanel.isShowing())
    }

    @Test
    fun `toggle hides when showing`() {
        menuPanel.show()
        menuPanel.toggle()
        assertFalse(menuPanel.isShowing())
    }

    @Test
    fun `show populates list with children`() {
        menuPanel.show()
        assertTrue(list.childCount > 0)
    }

    @Test
    fun `show clears and repopulates on each call`() {
        menuPanel.show()
        val firstCount = list.childCount
        menuPanel.show()
        assertEquals(firstCount, list.childCount)
    }

    @Test
    fun `lite flavor shows upgrade tooltip on full-only items`() {
        val context = RuntimeEnvironment.getApplication()
        val litePanel = MenuPanel(
            panel = panel,
            list = list,
            themeManager = themeManager,
            appPrefs = appPrefs,
            isFullFlavor = false,
            onUploadTap = null,
            onOpenSettings = {},
            onThemeChanged = {},
            onShowTooltip = { tooltipMessages.add(it) },
            currentPackageProvider = { "com.test.app" }
        )
        litePanel.show()

        // Find and click the SCP upload row (first action row, which is full-only)
        // The list has section headers and action rows
        // Click each clickable child to trigger the tooltip for disabled items
        var foundUpgradeTooltip = false
        for (i in 0 until list.childCount) {
            val child = list.getChildAt(i)
            if (child is LinearLayout) {
                child.performClick()
                if (tooltipMessages.any { it.contains("Upgrade") }) {
                    foundUpgradeTooltip = true
                    break
                }
            }
        }
        assertTrue("Should show upgrade tooltip for full-only features in lite", foundUpgradeTooltip)
    }
}
