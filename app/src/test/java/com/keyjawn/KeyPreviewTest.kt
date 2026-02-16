package com.keyjawn

import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class KeyPreviewTest {

    private lateinit var container: FrameLayout
    private lateinit var keyPreview: KeyPreview
    private lateinit var themeManager: ThemeManager

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        container = FrameLayout(context)
        container.layout(0, 0, 1080, 800)
        themeManager = ThemeManager(context)
        keyPreview = KeyPreview(container, themeManager)
    }

    @Test
    fun `preview starts hidden`() {
        assertEquals(View.GONE, keyPreview.previewView.visibility)
    }

    @Test
    fun `show makes preview visible`() {
        val anchor = Button(RuntimeEnvironment.getApplication())
        anchor.layout(100, 200, 200, 260)
        container.addView(anchor)
        keyPreview.show(anchor, "A")
        assertEquals(View.VISIBLE, keyPreview.previewView.visibility)
    }

    @Test
    fun `show sets correct text`() {
        val anchor = Button(RuntimeEnvironment.getApplication())
        anchor.layout(100, 200, 200, 260)
        container.addView(anchor)
        keyPreview.show(anchor, "Q")
        assertEquals("Q", (keyPreview.previewView as TextView).text.toString())
    }

    @Test
    fun `hide makes preview gone`() {
        val anchor = Button(RuntimeEnvironment.getApplication())
        anchor.layout(100, 200, 200, 260)
        container.addView(anchor)
        keyPreview.show(anchor, "A")
        keyPreview.hide()
        assertEquals(View.GONE, keyPreview.previewView.visibility)
    }

    @Test
    fun `multiple shows reuse same view`() {
        val anchor = Button(RuntimeEnvironment.getApplication())
        anchor.layout(100, 200, 200, 260)
        container.addView(anchor)
        keyPreview.show(anchor, "A")
        val view1 = keyPreview.previewView
        keyPreview.show(anchor, "B")
        val view2 = keyPreview.previewView
        assertSame(view1, view2)
        assertEquals("B", (view2 as TextView).text.toString())
    }
}
