package com.keyjawn

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QwertyKeyboardViewInstrumentedTest {

    @Test
    fun touchAtRenderedBoundsRoutesToTheMatchingVirtualKey() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var pressed = ""

        instrumentation.runOnMainSync {
            val surface = QwertyKeyboardView(instrumentation.targetContext)
            surface.layout(0, 0, 1080, surface.suggestedKeyboardHeight)
            surface.submit(
                KeyboardLayouts.lowercase.map { row ->
                    row.map { QwertyKeyboardView.RenderedKey(it, it.label) }
                }
            )
            surface.listener = object : QwertyKeyboardView.Listener {
                override fun onKeyPointer(
                    cell: QwertyKeyboardView.KeyCell,
                    sample: QwertyKeyboardView.PointerSample
                ): Boolean {
                    if (sample.action == MotionEvent.ACTION_DOWN) {
                        pressed = cell.key.label
                    }
                    return true
                }

                override fun onVirtualClick(cell: QwertyKeyboardView.KeyCell): Boolean = true
            }

            val q = requireNotNull(surface.keyBounds(0, 0))
            val now = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(
                now,
                now,
                MotionEvent.ACTION_DOWN,
                q.centerX(),
                q.centerY(),
                0
            )
            val up = MotionEvent.obtain(
                now,
                now + 10,
                MotionEvent.ACTION_UP,
                q.centerX(),
                q.centerY(),
                0
            )
            try {
                surface.dispatchTouchEvent(down)
                surface.dispatchTouchEvent(up)
            } finally {
                down.recycle()
                up.recycle()
            }
        }

        assertEquals("q", pressed)
    }
}
