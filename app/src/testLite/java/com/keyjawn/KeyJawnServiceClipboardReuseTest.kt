package com.keyjawn

import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Gates issue #37: the clipboard manager is hoisted to onCreate() and reused
 * across input-view rebuilds so a theme change does not drop the user's unpinned
 * clip history. This drives the real service onCreateInputView() (lite flavor,
 * whose UploadHandler is a no-op stub that avoids the Android Keystore the full
 * flavor's encrypted prefs need) and asserts the manager field is never replaced.
 *
 * Against a regression that reverts onCreateInputView() to construct a fresh
 * ClipboardHistoryManager per call, the second-rebuild assertion fails because
 * the instance changes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeyJawnServiceClipboardReuseTest {

    @Test
    fun `onCreateInputView reuses the clipboard manager built in onCreate`() {
        val service = Robolectric.buildService(KeyJawnService::class.java).create().get()

        // onCreate() must have built the manager.
        val afterCreate = service.clipboardHistoryManager
        assertNotNull("manager should be built in onCreate", afterCreate)

        // First input-view build must reuse the same instance.
        val firstView: View = service.onCreateInputView()
        assertNotNull(firstView)
        assertSame(
            "first onCreateInputView must reuse the onCreate manager",
            afterCreate,
            service.clipboardHistoryManager
        )

        // A second build (as a theme change triggers) must also reuse it.
        val secondView: View = service.onCreateInputView()
        assertNotNull(secondView)
        assertSame(
            "second onCreateInputView must not replace the manager",
            afterCreate,
            service.clipboardHistoryManager
        )
    }
}
