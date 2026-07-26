package com.keyjawn

import android.view.inputmethod.EditorInfo
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeyJawnServiceTest {

    @Test
    fun `service class exists`() {
        val service = KeyJawnService()
        assertNotNull(service)
    }

    @Test
    fun `active package survives an input view rebuild`() {
        // The production code is shared by both flavors. Exercise the rebuild in
        // lite so the full-only encrypted upload setup does not make this state
        // test depend on a Robolectric keystore provider.
        assumeTrue(BuildConfig.FLAVOR == "lite")
        val service = Robolectric.buildService(KeyJawnService::class.java).create().get()
        val info = EditorInfo().apply { packageName = "com.submit.theme" }

        try {
            service.onStartInputView(info, false)
            service.onCreateInputView()

            assertEquals("com.submit.theme", service.activePackageName)
        } finally {
            service.onDestroy()
        }
    }
}
