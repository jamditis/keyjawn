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
}
