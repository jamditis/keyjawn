package com.keyjawn

import android.app.Activity
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class QwertyKeyboardViewTest {

    @Test
    fun `the qwerty surface is one view with stable lower and upper geometry`() {
        val context = RuntimeEnvironment.getApplication()
        val host = FrameLayout(context)
        val surface = QwertyKeyboardView(context)
        host.addView(surface)
        surface.layout(0, 0, 1080, surface.suggestedKeyboardHeight)

        surface.submit(
            KeyboardLayouts.lowercase.map { row ->
                row.map { QwertyKeyboardView.RenderedKey(it, it.label) }
            }
        )
        val lowerBounds = surface.keyBounds(0, 0)
        val lowerGeneration = surface.geometryGeneration

        surface.submit(
            KeyboardLayouts.uppercase.map { row ->
                row.map { QwertyKeyboardView.RenderedKey(it, it.label) }
            }
        )

        assertEquals(1, host.childCount)
        assertSame(surface, host.getChildAt(0))
        assertEquals(lowerGeneration, surface.geometryGeneration)
        assertSame(lowerBounds, surface.keyBounds(0, 0))
    }

    @Test
    fun `geometry uses key weights and the same bounds for drawing and hit testing`() {
        val context = RuntimeEnvironment.getApplication()
        val surface = QwertyKeyboardView(context)
        surface.layout(0, 0, 1080, surface.suggestedKeyboardHeight)
        surface.submit(
            KeyboardLayouts.lowercase.map { row ->
                row.map { QwertyKeyboardView.RenderedKey(it, it.label) }
            }
        )

        val comma = surface.keyBounds(3, 1)
        val space = surface.keyBounds(3, 2)
        assertNotNull(comma)
        assertNotNull(space)
        comma!!
        space!!
        assertTrue(space.width() > comma.width() * 4)

        val hit = surface.keyAt(space.centerX(), space.centerY())
        assertEquals(3, hit?.rowIndex)
        assertEquals(2, hit?.colIndex)
        assertTrue(surface.isClickable)
    }

    @Test
    fun `one surface routes separate pointer downs to their own keys`() {
        val context = RuntimeEnvironment.getApplication()
        val surface = QwertyKeyboardView(context)
        surface.layout(0, 0, 1080, surface.suggestedKeyboardHeight)
        surface.submit(
            KeyboardLayouts.lowercase.map { row ->
                row.map { QwertyKeyboardView.RenderedKey(it, it.label) }
            }
        )
        val downs = mutableListOf<Pair<Int, String>>()
        surface.listener = object : QwertyKeyboardView.Listener {
            override fun onKeyPointer(
                cell: QwertyKeyboardView.KeyCell,
                sample: QwertyKeyboardView.PointerSample
            ): Boolean {
                if (sample.action == MotionEvent.ACTION_DOWN) {
                    downs += sample.pointerId to cell.key.label
                }
                return true
            }

            override fun onVirtualClick(cell: QwertyKeyboardView.KeyCell): Boolean = true
        }

        val q = requireNotNull(surface.keyBounds(0, 0))
        val w = requireNotNull(surface.keyBounds(0, 1))
        val now = SystemClock.uptimeMillis()
        val firstDown = MotionEvent.obtain(
            now,
            now,
            MotionEvent.ACTION_DOWN,
            q.centerX(),
            q.centerY(),
            0
        )
        surface.dispatchTouchEvent(firstDown)
        firstDown.recycle()

        val properties = arrayOf(
            MotionEvent.PointerProperties().apply { id = 0 },
            MotionEvent.PointerProperties().apply { id = 1 }
        )
        val coordinates = arrayOf(
            MotionEvent.PointerCoords().apply {
                x = q.centerX()
                y = q.centerY()
            },
            MotionEvent.PointerCoords().apply {
                x = w.centerX()
                y = w.centerY()
            }
        )
        val secondDown = MotionEvent.obtain(
            now,
            now + 10,
            MotionEvent.ACTION_POINTER_DOWN or
                (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
            2,
            properties,
            coordinates,
            0,
            0,
            1f,
            1f,
            0,
            0,
            0,
            0
        )
        surface.dispatchTouchEvent(secondDown)
        secondDown.recycle()

        assertEquals(listOf(0 to "q", 1 to "w"), downs)
    }

    @Test
    fun `a gap listener receives the full stream even when down is unclassified`() {
        val context = RuntimeEnvironment.getApplication()
        val surface = QwertyKeyboardView(context)
        surface.layout(0, 0, 1080, surface.suggestedKeyboardHeight)
        surface.submit(
            KeyboardLayouts.lowercase.map { row ->
                row.map { QwertyKeyboardView.RenderedKey(it, it.label) }
            }
        )
        val actions = mutableListOf<Int>()
        surface.listener = object : QwertyKeyboardView.Listener {
            override fun onKeyPointer(
                cell: QwertyKeyboardView.KeyCell,
                sample: QwertyKeyboardView.PointerSample
            ): Boolean = true

            override fun onGapTouch(
                view: QwertyKeyboardView,
                event: MotionEvent
            ): Boolean {
                actions += event.actionMasked
                return false
            }

            override fun onVirtualClick(cell: QwertyKeyboardView.KeyCell): Boolean = true
        }

        val q = requireNotNull(surface.keyBounds(0, 0))
        val w = requireNotNull(surface.keyBounds(0, 1))
        val gapX = (q.right + w.left) / 2f
        val y = q.centerY()
        assertEquals(null, surface.keyAt(gapX, y))
        val now = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, gapX, y, 0)
        val up = MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_UP, gapX, y, 0)
        try {
            assertTrue(surface.dispatchTouchEvent(down))
            assertTrue(surface.dispatchTouchEvent(up))
        } finally {
            down.recycle()
            up.recycle()
        }

        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), actions)
    }

    @Test
    fun `virtual accessibility keys expose button bounds and actions`() {
        val context = RuntimeEnvironment.getApplication()
        val surface = QwertyKeyboardView(context)
        val root = FrameLayout(context)
        root.addView(surface)
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        activityController.get().setContentView(root)
        surface.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(
                surface.suggestedKeyboardHeight,
                View.MeasureSpec.EXACTLY
            )
        )
        surface.layout(0, 0, 1080, surface.suggestedKeyboardHeight)
        surface.submit(
            KeyboardLayouts.lowercase.map { row ->
                row.map { QwertyKeyboardView.RenderedKey(it, it.label) }
            }
        )
        var clicked = ""
        surface.listener = object : QwertyKeyboardView.Listener {
            override fun onKeyPointer(
                cell: QwertyKeyboardView.KeyCell,
                sample: QwertyKeyboardView.PointerSample
            ): Boolean = true

            override fun onVirtualClick(cell: QwertyKeyboardView.KeyCell): Boolean {
                clicked = cell.key.label
                return true
            }
        }

        val node = requireNotNull(surface.accessibilityNode(0, 0))
        val bounds = Rect()
        node.getBoundsInParent(bounds)

        assertEquals("q", node.contentDescription)
        assertEquals(android.widget.Button::class.java.name, node.className)
        assertTrue(bounds.width() > 0)
        assertTrue(node.actionList.contains(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK))
        assertTrue(
            surface.performAccessibilityAction(
                0,
                0,
                AccessibilityNodeInfoCompat.ACTION_CLICK,
            )
        )
        assertEquals("q", clicked)
        activityController.pause().stop().destroy()
    }
}
