# UI/UX redesign, swipe gestures, voice input implementation plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Update KeyJawn's visual style to clean material dark, add directional swipe gestures, and replace the mic button toggle with an inline voice bar showing a waveform and streaming transcription.

**Architecture:** Three independent feature tracks that can be built and tested separately. Part 1 (visual) changes only resource files and key-building code. Part 2 (swipe) adds a gesture detector on the QWERTY container. Part 3 (voice) adds a custom View, new layout, and refactors VoiceInputHandler to use a callback interface.

**Tech stack:** Kotlin, Android XML layouts, custom View (Canvas), ValueAnimator, SpeechRecognizer partial results

---

## Task 1: Update color palette

**Files:**
- Modify: `app/src/main/res/values/colors.xml`

**Step 1: Replace colors.xml contents**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="keyboard_bg">#1B1B1F</color>
    <color name="key_bg">#2B2B30</color>
    <color name="key_bg_pressed">#3A3A40</color>
    <color name="key_text">#E8E8EC</color>
    <color name="extra_row_bg">#161619</color>
    <color name="qwerty_bg">#222226</color>
    <color name="key_special_bg">#333338</color>
    <color name="accent">#6C9BF2</color>
    <color name="accent_locked">#E86C5A</color>
    <color name="divider">#38383E</color>
</resources>
```

**Step 2: Build**

Run: `./gradlew assembleFullDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/res/values/colors.xml
git commit -m "style: update color palette to clean material dark"
```

---

## Task 2: Update key drawables

**Files:**
- Modify: `app/src/main/res/drawable/key_bg.xml`
- Modify: `app/src/main/res/drawable/key_bg_active.xml`
- Modify: `app/src/main/res/drawable/key_bg_locked.xml`
- Create: `app/src/main/res/drawable/key_bg_special.xml`
- Modify: `app/src/main/res/drawable/clipboard_popup_bg.xml`

**Step 1: Update key_bg.xml (6dp radius, shadow)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="@color/key_bg_pressed">
    <item>
        <layer-list>
            <item android:top="1dp">
                <shape android:shape="rectangle">
                    <solid android:color="#00000040" />
                    <corners android:radius="6dp" />
                </shape>
            </item>
            <item android:bottom="1dp">
                <shape android:shape="rectangle">
                    <solid android:color="@color/key_bg" />
                    <corners android:radius="6dp" />
                </shape>
            </item>
        </layer-list>
    </item>
</ripple>
```

**Step 2: Update key_bg_active.xml (accent color, 6dp radius)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/accent" />
    <corners android:radius="6dp" />
</shape>
```

**Step 3: Update key_bg_locked.xml (accent_locked, 6dp radius)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/accent_locked" />
    <corners android:radius="6dp" />
</shape>
```

**Step 4: Create key_bg_special.xml (special key background with shadow)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<ripple xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="@color/key_bg_pressed">
    <item>
        <layer-list>
            <item android:top="1dp">
                <shape android:shape="rectangle">
                    <solid android:color="#00000040" />
                    <corners android:radius="6dp" />
                </shape>
            </item>
            <item android:bottom="1dp">
                <shape android:shape="rectangle">
                    <solid android:color="@color/key_special_bg" />
                    <corners android:radius="6dp" />
                </shape>
            </item>
        </layer-list>
    </item>
</ripple>
```

**Step 5: Update clipboard_popup_bg.xml (border + updated color)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="@color/key_bg" />
    <stroke android:width="1dp" android:color="@color/divider" />
    <corners android:radius="8dp" />
</shape>
```

**Step 6: Build**

Run: `./gradlew assembleFullDebug`
Expected: BUILD SUCCESSFUL

**Step 7: Commit**

```bash
git add app/src/main/res/drawable/
git commit -m "style: update key drawables with 6dp radius, shadows, accent colors"
```

---

## Task 3: Update layout backgrounds and typography

**Files:**
- Modify: `app/src/main/res/layout/keyboard_view.xml`
- Modify: `app/src/main/res/layout/extra_row.xml`
- Modify: `app/src/main/res/layout/number_row.xml`
- Modify: `app/src/main/res/values/themes.xml`

**Step 1: Update keyboard_view.xml backgrounds**

Change the outer LinearLayout background from `#1E1E1E` to `@color/keyboard_bg`.
Change qwerty_container background from `#2D2D2D` to `@color/qwerty_bg`.

**Step 2: Update extra_row.xml text size**

Change all `android:textSize="12sp"` to `android:textSize="11sp"` on every Button in the extra row.

**Step 3: Update number_row.xml background**

Change `android:background="@color/extra_row_bg"` (already correct color name, just verify it references the updated value).

**Step 4: Update NumberKey style in themes.xml**

Change `android:textSize` from `14sp` to `15sp` in the NumberKey style.

**Step 5: Build**

Run: `./gradlew assembleFullDebug`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add app/src/main/res/layout/ app/src/main/res/values/themes.xml
git commit -m "style: update layout backgrounds and typography"
```

---

## Task 4: Apply special key styling in QwertyKeyboard

**Files:**
- Modify: `app/src/main/java/com/keyjawn/QwertyKeyboard.kt`

**Step 1: Update createKeyButton() to use special background and sans-serif for modifier keys**

In `createKeyButton()`, after the existing `setBackgroundResource(R.drawable.key_bg)` line inside the `Button(context).apply` block, the background is set uniformly. Instead, we need to conditionally set the background and typeface based on key type.

Replace this section in the `apply` block:

```kotlin
setBackgroundResource(R.drawable.key_bg)
```

With logic that checks after the block. After the `button` is created, add:

```kotlin
val isSpecialKey = key.output is KeyOutput.Shift ||
    key.output is KeyOutput.Backspace ||
    key.output is KeyOutput.Enter ||
    key.output is KeyOutput.SymSwitch ||
    key.output is KeyOutput.AbcSwitch ||
    key.output is KeyOutput.Space

if (isSpecialKey) {
    button.setBackgroundResource(R.drawable.key_bg_special)
    button.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
}
```

Also update the text sizes for special keys from 14f to 13f:

```kotlin
val textSize = when (key.output) {
    is KeyOutput.Character -> 18f
    is KeyOutput.Shift, is KeyOutput.Backspace,
    is KeyOutput.Enter, is KeyOutput.SymSwitch,
    is KeyOutput.AbcSwitch -> 13f
    is KeyOutput.Space -> 13f
    is KeyOutput.Slash -> 13f
    is KeyOutput.KeyCode -> 13f
}
```

**Step 2: Update updateShiftAppearance to use key_bg_special as the OFF state**

In `updateShiftAppearance()`, change the OFF case from `R.drawable.key_bg` to `R.drawable.key_bg_special` since Shift is a special key:

```kotlin
ShiftState.OFF -> button.setBackgroundResource(R.drawable.key_bg_special)
```

**Step 3: Build**

Run: `./gradlew assembleFullDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/keyjawn/QwertyKeyboard.kt
git commit -m "style: apply special key background and typography to modifier keys"
```

---

## Task 5: Update popup styling

**Files:**
- Modify: `app/src/main/java/com/keyjawn/AltKeyPopup.kt`

**Step 1: Update AltKeyPopup background**

In `AltKeyPopup.show()`, replace the `ColorDrawable` background with a `GradientDrawable` that has rounded corners and a border:

Replace:
```kotlin
window.setBackgroundDrawable(ColorDrawable(0xFF1A1A1A.toInt()))
```

With:
```kotlin
val bg = android.graphics.drawable.GradientDrawable().apply {
    setColor(0xFF2B2B30.toInt())
    cornerRadius = 8 * density
    setStroke((1 * density + 0.5f).toInt(), 0xFF38383E.toInt())
}
window.setBackgroundDrawable(bg)
window.elevation = 8 * density
```

**Step 2: Build**

Run: `./gradlew assembleFullDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/keyjawn/AltKeyPopup.kt
git commit -m "style: update alt key popup with rounded corners and border"
```

---

## Task 6: Create SwipeGestureDetector

**Files:**
- Create: `app/src/main/java/com/keyjawn/SwipeGestureDetector.kt`

**Step 1: Create the detector class**

```kotlin
package com.keyjawn

import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View

class SwipeGestureDetector(
    private val onSwipe: (SwipeDirection) -> Boolean
) : View.OnTouchListener {

    enum class SwipeDirection { LEFT, RIGHT, UP, DOWN }

    private var startX = 0f
    private var startY = 0f
    private var velocityTracker: VelocityTracker? = null
    private var tracking = false

    private val minDistanceDp = 60f
    private val minVelocityDp = 200f

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val density = view.context.resources.displayMetrics.density
        val minDistancePx = minDistanceDp * density
        val minVelocityPx = minVelocityDp * density

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                tracking = true
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                return false
            }
            MotionEvent.ACTION_UP -> {
                if (!tracking) return false
                tracking = false
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)

                val dx = event.x - startX
                val dy = event.y - startY
                val vx = velocityTracker?.xVelocity ?: 0f
                val vy = velocityTracker?.yVelocity ?: 0f
                velocityTracker?.recycle()
                velocityTracker = null

                val absDx = kotlin.math.abs(dx)
                val absDy = kotlin.math.abs(dy)

                val direction: SwipeDirection?
                if (absDx > absDy && absDx > minDistancePx && kotlin.math.abs(vx) > minVelocityPx) {
                    direction = if (dx < 0) SwipeDirection.LEFT else SwipeDirection.RIGHT
                } else if (absDy > absDx && absDy > minDistancePx && kotlin.math.abs(vy) > minVelocityPx) {
                    direction = if (dy < 0) SwipeDirection.UP else SwipeDirection.DOWN
                } else {
                    direction = null
                }

                if (direction != null) {
                    return onSwipe(direction)
                }
                return false
            }
            MotionEvent.ACTION_CANCEL -> {
                tracking = false
                velocityTracker?.recycle()
                velocityTracker = null
                return false
            }
        }
        return false
    }
}
```

**Step 2: Build**

Run: `./gradlew assembleFullDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/keyjawn/SwipeGestureDetector.kt
git commit -m "feat: add SwipeGestureDetector for directional swipe gestures"
```

---

## Task 7: Wire swipe gestures into QwertyKeyboard

**Files:**
- Modify: `app/src/main/java/com/keyjawn/QwertyKeyboard.kt`

**Step 1: Add swipe detector to the container in render()**

At the end of the `render()` method, after all rows are added, attach the swipe detector to the container:

```kotlin
container.setOnTouchListener(SwipeGestureDetector { direction ->
    val ic = inputConnectionProvider() ?: return@SwipeGestureDetector false
    when (direction) {
        SwipeGestureDetector.SwipeDirection.LEFT -> {
            // Delete word: send Ctrl+Backspace
            keySender.sendKey(ic, android.view.KeyEvent.KEYCODE_DEL, ctrl = true)
            true
        }
        SwipeGestureDetector.SwipeDirection.RIGHT -> {
            keySender.sendChar(ic, " ")
            true
        }
        SwipeGestureDetector.SwipeDirection.UP -> {
            if (currentLayer != KeyboardLayouts.LAYER_SYMBOLS) {
                setLayer(KeyboardLayouts.LAYER_SYMBOLS)
            }
            true
        }
        SwipeGestureDetector.SwipeDirection.DOWN -> {
            if (currentLayer == KeyboardLayouts.LAYER_SYMBOLS) {
                shiftState = ShiftState.OFF
                setLayer(KeyboardLayouts.LAYER_LOWER)
            }
            true
        }
    }
})
```

**Step 2: Build**

Run: `./gradlew assembleFullDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/keyjawn/QwertyKeyboard.kt
git commit -m "feat: wire swipe gestures for delete word, space, layer switching"
```

---

## Task 8: Create VoiceWaveformView

**Files:**
- Create: `app/src/main/java/com/keyjawn/VoiceWaveformView.kt`

**Step 1: Create the custom View**

```kotlin
package com.keyjawn

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator

class VoiceWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barCount = 5
    private val barHeights = FloatArray(barCount) { 0.2f }
    private val targetHeights = FloatArray(barCount) { 0.2f }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.accent)
        style = Paint.Style.FILL
    }
    private val barWidthDp = 3f
    private val barGapDp = 2f
    private val minBarFraction = 0.15f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 120
        interpolator = DecelerateInterpolator()
        addUpdateListener {
            val fraction = it.animatedFraction
            for (i in barHeights.indices) {
                barHeights[i] = barHeights[i] + (targetHeights[i] - barHeights[i]) * fraction
            }
            invalidate()
        }
    }

    fun updateRms(rmsdB: Float) {
        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        for (i in targetHeights.indices) {
            val offset = (1f - kotlin.math.abs(i - barCount / 2f) / (barCount / 2f)) * 0.3f
            targetHeights[i] = (normalized + offset).coerceIn(minBarFraction, 1f)
        }
        animator.cancel()
        animator.start()
    }

    fun reset() {
        for (i in targetHeights.indices) {
            targetHeights[i] = minBarFraction
        }
        animator.cancel()
        animator.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val barW = barWidthDp * density
        val gap = barGapDp * density
        val totalW = barCount * barW + (barCount - 1) * gap
        var x = (width - totalW) / 2f
        val maxH = height * 0.8f
        val cy = height / 2f

        for (i in 0 until barCount) {
            val h = maxH * barHeights[i]
            canvas.drawRoundRect(x, cy - h / 2, x + barW, cy + h / 2, barW / 2, barW / 2, paint)
            x += barW + gap
        }
    }
}
```

**Step 2: Build**

Run: `./gradlew assembleFullDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/java/com/keyjawn/VoiceWaveformView.kt
git commit -m "feat: add VoiceWaveformView custom View for voice input"
```

---

## Task 9: Create voice bar layout

**Files:**
- Create: `app/src/main/res/layout/voice_bar.xml`
- Create: `app/src/main/res/drawable/ic_stop.xml`

**Step 1: Create ic_stop.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#E8E8EC" />
    <size android:width="14dp" android:height="14dp" />
    <corners android:radius="2dp" />
</shape>
```

**Step 2: Create voice_bar.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/voice_bar"
    android:layout_width="match_parent"
    android:layout_height="42dp"
    android:orientation="horizontal"
    android:background="@color/extra_row_bg"
    android:gravity="center_vertical"
    android:paddingStart="8dp"
    android:paddingEnd="4dp"
    android:visibility="gone">

    <com.keyjawn.VoiceWaveformView
        android:id="@+id/voice_waveform"
        android:layout_width="48dp"
        android:layout_height="match_parent" />

    <HorizontalScrollView
        android:layout_width="0dp"
        android:layout_height="match_parent"
        android:layout_weight="1"
        android:scrollbars="none"
        android:fillViewport="true">

        <TextView
            android:id="@+id/voice_text"
            android:layout_width="wrap_content"
            android:layout_height="match_parent"
            android:gravity="center_vertical"
            android:paddingStart="8dp"
            android:paddingEnd="8dp"
            android:textColor="@color/key_text"
            android:textSize="14sp"
            android:maxLines="1" />
    </HorizontalScrollView>

    <ImageButton
        android:id="@+id/voice_stop"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:layout_marginEnd="2dp"
        android:background="@drawable/key_bg"
        android:src="@drawable/ic_stop"
        android:scaleType="center"
        android:contentDescription="Stop voice input" />
</LinearLayout>
```

**Step 3: Add voice bar include to keyboard_view.xml**

Add `<include layout="@layout/voice_bar" />` right after the `<include layout="@layout/extra_row" />` line.

**Step 4: Build**

Run: `./gradlew assembleFullDebug`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add app/src/main/res/drawable/ic_stop.xml app/src/main/res/layout/voice_bar.xml app/src/main/res/layout/keyboard_view.xml
git commit -m "feat: add voice bar layout with waveform, text, and stop button"
```

---

## Task 10: Refactor VoiceInputHandler with listener interface

**Files:**
- Modify: `app/src/main/java/com/keyjawn/VoiceInputHandler.kt`

**Step 1: Add VoiceInputListener interface and wire partial results + RMS**

Add the interface at the top of the file (outside the class):

```kotlin
interface VoiceInputListener {
    fun onVoiceStart()
    fun onVoiceStop()
    fun onPartialResult(text: String)
    fun onFinalResult(text: String)
    fun onRmsChanged(rmsdB: Float)
    fun onError()
}
```

Add a `listener` property to VoiceInputHandler:

```kotlin
var listener: VoiceInputListener? = null
```

**Step 2: Update createListener() to use the callbacks**

Replace `onResults`:
```kotlin
override fun onResults(results: Bundle?) {
    listening = false
    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    val text = matches?.firstOrNull() ?: ""
    if (text.isNotEmpty()) {
        listener?.onFinalResult(text)
        inputConnectionProvider?.invoke()?.commitText(text, 1)
    }
    listener?.onVoiceStop()
}
```

Replace `onPartialResults`:
```kotlin
override fun onPartialResults(partialResults: Bundle?) {
    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
    val text = matches?.firstOrNull() ?: return
    listener?.onPartialResult(text)
}
```

Replace `onRmsChanged`:
```kotlin
override fun onRmsChanged(rmsdB: Float) {
    listener?.onRmsChanged(rmsdB)
}
```

Replace `onError`:
```kotlin
override fun onError(error: Int) {
    listening = false
    listener?.onError()
    listener?.onVoiceStop()
}
```

**Step 3: Update startListening() and stopListening()**

In `startListening()`, after setting `listening = true`, add:
```kotlin
listener?.onVoiceStart()
```

Remove the `updateMicVisual(true)` call.

In `stopListening()`, remove the `updateMicVisual(false)` call. The listener handles visual state now.

**Step 4: Remove updateMicVisual method entirely**

Delete the `updateMicVisual()` private method. The listener interface replaces it.

**Step 5: Build**

Run: `./gradlew assembleFullDebug`
Expected: BUILD SUCCESSFUL

**Step 6: Commit**

```bash
git add app/src/main/java/com/keyjawn/VoiceInputHandler.kt
git commit -m "feat: add VoiceInputListener for streaming partial results and RMS"
```

---

## Task 11: Wire voice bar into ExtraRowManager

**Files:**
- Modify: `app/src/main/java/com/keyjawn/ExtraRowManager.kt`

**Step 1: Add voice bar references and implement VoiceInputListener**

Add fields after `clipboardPopup`:

```kotlin
private val extraRow: View = view.findViewById(R.id.extra_row)
private val voiceBar: View? = view.findViewById(R.id.voice_bar)
private val voiceWaveform: VoiceWaveformView? = voiceBar?.findViewById(R.id.voice_waveform)
private val voiceText: android.widget.TextView? = voiceBar?.findViewById(R.id.voice_text)
private val voiceStop: View? = voiceBar?.findViewById(R.id.voice_stop)
```

**Step 2: Wire the voice bar stop button and listener in wireMic()**

At the end of `wireMic()`, after the existing mic button setup, add:

```kotlin
voiceStop?.setOnClickListener {
    voiceInputHandler?.stopListening()
}

voiceInputHandler?.listener = object : VoiceInputListener {
    override fun onVoiceStart() {
        extraRow.visibility = View.GONE
        voiceBar?.visibility = View.VISIBLE
        voiceText?.text = ""
        voiceWaveform?.reset()
    }

    override fun onVoiceStop() {
        voiceBar?.visibility = View.GONE
        extraRow.visibility = View.VISIBLE
    }

    override fun onPartialResult(text: String) {
        voiceText?.text = text
        // Auto-scroll to end
        (voiceText?.parent as? android.widget.HorizontalScrollView)?.post {
            (voiceText.parent as? android.widget.HorizontalScrollView)?.fullScroll(View.FOCUS_RIGHT)
        }
    }

    override fun onFinalResult(text: String) {
        voiceText?.text = text
    }

    override fun onRmsChanged(rmsdB: Float) {
        voiceWaveform?.updateRms(rmsdB)
    }

    override fun onError() {
        voiceText?.text = ""
    }
}
```

**Step 3: Build**

Run: `./gradlew assembleFullDebug`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/com/keyjawn/ExtraRowManager.kt
git commit -m "feat: wire voice bar with waveform and streaming text into extra row"
```

---

## Task 12: Update mic icon

**Files:**
- Modify: `app/src/main/res/drawable/ic_mic.xml`

**Step 1: Replace with a proper mic path icon using the new text color**

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="18dp"
    android:height="18dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="@color/key_text"
        android:pathData="M12,14c1.66,0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6C9,12.66 10.34,14 12,14zM17,11c0,2.76 -2.24,5 -5,5s-5,-2.24 -5,-5H5c0,3.53 2.61,6.43 6,6.92V21h2v-3.08c3.39,-0.49 6,-3.39 6,-6.92H17z" />
</vector>
```

**Step 2: Build**

Run: `./gradlew assembleFullDebug`
Expected: BUILD SUCCESSFUL

**Step 3: Commit**

```bash
git add app/src/main/res/drawable/ic_mic.xml
git commit -m "style: update mic icon to vector drawable"
```

---

## Task 13: Final build and verification

**Step 1: Build both flavors**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL for both full and lite

**Step 2: Run existing tests**

Run: `./gradlew testFullDebugUnitTest --tests "com.keyjawn.ClipboardHistoryManagerTest"`
Expected: PASS

**Step 3: Verify on device (manual)**

- Keyboard colors updated to darker material palette
- Special keys (Shift, Del, Enter, ?123, spacebar) have distinct background
- Keys have subtle bottom shadow
- Swipe left on keys deletes a word
- Swipe right on keys inserts a space
- Swipe up switches to symbols, swipe down goes back to letters
- Tap mic: extra row hides, voice bar appears with waveform
- Speak: waveform animates, partial text streams in real time
- Tap stop: final text committed, voice bar hides, extra row returns
- Alt key popup has rounded corners and border

**Step 4: Commit any fixups, push**

```bash
git push
```
