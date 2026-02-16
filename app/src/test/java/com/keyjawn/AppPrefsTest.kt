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
class AppPrefsTest {

    private lateinit var appPrefs: AppPrefs

    @Before
    fun setUp() {
        appPrefs = AppPrefs(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `autocorrect defaults to off for any app`() {
        assertFalse(appPrefs.isAutocorrectEnabled("com.example.app"))
        assertFalse(appPrefs.isAutocorrectEnabled("com.another.app"))
        assertFalse(appPrefs.isAutocorrectEnabled("unknown"))
    }

    @Test
    fun `toggle flips off to on and returns new state`() {
        val result = appPrefs.toggleAutocorrect("com.example.app")
        assertTrue(result)
        assertTrue(appPrefs.isAutocorrectEnabled("com.example.app"))
    }

    @Test
    fun `toggle flips on back to off`() {
        appPrefs.setAutocorrect("com.example.app", true)
        val result = appPrefs.toggleAutocorrect("com.example.app")
        assertFalse(result)
        assertFalse(appPrefs.isAutocorrectEnabled("com.example.app"))
    }

    @Test
    fun `per-app isolation`() {
        appPrefs.toggleAutocorrect("com.app.a")
        assertTrue(appPrefs.isAutocorrectEnabled("com.app.a"))
        assertFalse(appPrefs.isAutocorrectEnabled("com.app.b"))
    }

    @Test
    fun `setAutocorrect explicitly sets value`() {
        appPrefs.setAutocorrect("com.example.app", true)
        assertTrue(appPrefs.isAutocorrectEnabled("com.example.app"))

        appPrefs.setAutocorrect("com.example.app", false)
        assertFalse(appPrefs.isAutocorrectEnabled("com.example.app"))
    }

    @Test
    fun `preference persists across reads`() {
        appPrefs.setAutocorrect("com.persist.test", true)
        assertTrue(appPrefs.isAutocorrectEnabled("com.persist.test"))
        assertTrue(appPrefs.isAutocorrectEnabled("com.persist.test"))
    }

    @Test
    fun `toggle twice returns to original state`() {
        appPrefs.toggleAutocorrect("com.example.app")
        appPrefs.toggleAutocorrect("com.example.app")
        assertFalse(appPrefs.isAutocorrectEnabled("com.example.app"))
    }

    @Test
    fun `sanitizeCustomText strips control characters`() {
        assertEquals("hello", AppPrefs.sanitizeCustomText("he\u0000llo"))
        assertEquals("ab", AppPrefs.sanitizeCustomText("a\u0001b"))
    }

    @Test
    fun `sanitizeCustomText preserves tab`() {
        assertEquals("a\tb", AppPrefs.sanitizeCustomText("a\tb"))
    }

    @Test
    fun `sanitizeCustomText truncates to 8 chars`() {
        assertEquals("12345678", AppPrefs.sanitizeCustomText("123456789"))
    }

    @Test
    fun `sanitizeCustomText returns empty for blank input`() {
        assertEquals("", AppPrefs.sanitizeCustomText(""))
        assertEquals("", AppPrefs.sanitizeCustomText("\u0000\u0001"))
    }

    @Test
    fun `sanitizeCustomText preserves normal text`() {
        assertEquals("hello", AppPrefs.sanitizeCustomText("hello"))
        assertEquals("|~`\\", AppPrefs.sanitizeCustomText("|~`\\"))
    }

    @Test
    fun `expanded quick key options include terminal chars`() {
        val options = AppPrefs.QUICK_KEY_OPTIONS
        assertTrue(options.contains("|"))
        assertTrue(options.contains("~"))
        assertTrue(options.contains("`"))
        assertTrue(options.contains("\\"))
        assertTrue(options.contains("@"))
        assertTrue(options.contains("#"))
    }

    @Test
    fun `extra slot defaults`() {
        assertEquals("keycode:KEYCODE_ESCAPE", appPrefs.getExtraSlot(0))
        assertEquals("keycode:KEYCODE_TAB", appPrefs.getExtraSlot(1))
        assertEquals("ctrl", appPrefs.getExtraSlot(2))
    }

    @Test
    fun `set and get extra slot`() {
        appPrefs.setExtraSlot(0, "keycode:KEYCODE_MOVE_HOME")
        assertEquals("keycode:KEYCODE_MOVE_HOME", appPrefs.getExtraSlot(0))
    }

    @Test
    fun `set extra slot with custom text sanitizes input`() {
        appPrefs.setExtraSlot(1, "text:he\u0000llo")
        assertEquals("text:hello", appPrefs.getExtraSlot(1))
    }

    @Test
    fun `set custom quick key sanitizes input`() {
        appPrefs.setQuickKey("text:\u0000|")
        assertEquals("text:|", appPrefs.getQuickKey())
    }

    @Test
    fun `bottom padding defaults to 0`() {
        assertEquals(0, appPrefs.getBottomPadding())
    }

    @Test
    fun `set and get bottom padding`() {
        appPrefs.setBottomPadding(24)
        assertEquals(24, appPrefs.getBottomPadding())
    }

    @Test
    fun `bottom padding clamps to max 64`() {
        appPrefs.setBottomPadding(100)
        assertEquals(64, appPrefs.getBottomPadding())
    }

    @Test
    fun `bottom padding clamps to min 0`() {
        appPrefs.setBottomPadding(-5)
        assertEquals(0, appPrefs.getBottomPadding())
    }

    @Test
    fun `bottom padding persists across reads`() {
        appPrefs.setBottomPadding(32)
        assertEquals(32, appPrefs.getBottomPadding())
        assertEquals(32, appPrefs.getBottomPadding())
    }

    @Test
    fun `getExtraSlotLabel returns readable name`() {
        assertEquals("ESC", AppPrefs.getExtraSlotLabel("keycode:KEYCODE_ESCAPE"))
        assertEquals("Tab", AppPrefs.getExtraSlotLabel("keycode:KEYCODE_TAB"))
        assertEquals("Ctrl", AppPrefs.getExtraSlotLabel("ctrl"))
        assertEquals("Home", AppPrefs.getExtraSlotLabel("keycode:KEYCODE_MOVE_HOME"))
        assertEquals("End", AppPrefs.getExtraSlotLabel("keycode:KEYCODE_MOVE_END"))
        assertEquals("|", AppPrefs.getExtraSlotLabel("text:|"))
        assertEquals("hello", AppPrefs.getExtraSlotLabel("text:hello"))
    }
}
