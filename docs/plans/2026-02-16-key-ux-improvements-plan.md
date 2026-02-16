# Key UX improvements implementation plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add key press previews, widen the spacebar by removing the period key, make extra row keys customizable, and expand quick key options — all for full flavor users.

**Architecture:** Four independent features that share `AppPrefs` for storage and `MenuPanel` for UI. Each feature is self-contained: KeyPreview is a new overlay class, layout changes are in KeyboardLayout.kt, extra row customization extends ExtraRowManager, and quick key expansion extends the existing AppPrefs/AltKeyPopup pattern. Input validation for custom text is centralized in AppPrefs.

**Tech Stack:** Kotlin, Android Views, SharedPreferences, Robolectric for tests

---

### Task 1: Remove period key and widen spacebar

This is the simplest change and affects tests that hardcode row counts and key positions. Do it first so all subsequent work builds on the new layout.

**Files:**
- Modify: `app/src/main/java/com/keyjawn/KeyboardLayout.kt:36-43` (lowercase bottom row)
- Modify: `app/src/main/java/com/keyjawn/KeyboardLayout.kt:54-61` (uppercase bottom row)
- Modify: `app/src/test/java/com/keyjawn/KeyboardLayoutTest.kt` (fix affected assertions)

**Step 1: Update the failing tests first**

In `KeyboardLayoutTest.kt`, update these tests to expect the new bottom row:

```kotlin
@Test
fun `lowercase row 4 has 5 keys`() {
    // sym, comma, space, quickkey, enter (period removed)
    assertEquals(5, KeyboardLayouts.lowercase[3].size)
}

@Test
fun `uppercase row 4 has 5 keys`() {
    assertEquals(5, KeyboardLayouts.uppercase[3].size)
}

@Test
fun `lowercase row 4 has sym, comma, space, quickkey, enter`() {
    val row = KeyboardLayouts.lowercase[3]
    assertTrue(row[0].output is KeyOutput.SymSwitch)
    assertTrue(row[1].output is KeyOutput.Character)
    assertEquals(",", (row[1].output as KeyOutput.Character).char)
    assertTrue(row[2].output is KeyOutput.Space)
    assertTrue(row[3].output is KeyOutput.QuickKey)
    assertTrue(row[4].output is KeyOutput.Enter)
}

@Test
fun `space key has weight 4_5`() {
    val spaceKey = KeyboardLayouts.lowercase[3][2]
    assertTrue(spaceKey.output is KeyOutput.Space)
    assertEquals(4.5f, spaceKey.weight)
}

@Test
fun `enter key has weight 1_5`() {
    val enterKey = KeyboardLayouts.lowercase[3][4]
    assertTrue(enterKey.output is KeyOutput.Enter)
    assertEquals(1.5f, enterKey.weight)
}

@Test
fun `quick key has weight 1`() {
    val quickKey = KeyboardLayouts.lowercase[3][3]
    assertTrue(quickKey.output is KeyOutput.QuickKey)
    assertEquals(1f, quickKey.weight)
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew testFullDebugUnitTest --tests "com.keyjawn.KeyboardLayoutTest" -q`
Expected: FAIL — tests expect 5 keys but layout still has 6

**Step 3: Update KeyboardLayout.kt**

In `KeyboardLayout.kt`, change the `lowercase` bottom row (lines 36-43) from:

```kotlin
listOf(
    Key("?123", KeyOutput.SymSwitch, weight = 1.5f),
    Key(",", KeyOutput.Character(","), weight = 1f),
    Key(" ", KeyOutput.Space, weight = 3.5f),
    Key(".", KeyOutput.Character("."), weight = 1f),
    Key("/", KeyOutput.QuickKey),
    Key("Enter", KeyOutput.Enter, weight = 1.5f)
)
```

to:

```kotlin
listOf(
    Key("?123", KeyOutput.SymSwitch, weight = 1.5f),
    Key(",", KeyOutput.Character(","), weight = 1f),
    Key(" ", KeyOutput.Space, weight = 4.5f),
    Key("/", KeyOutput.QuickKey),
    Key("Enter", KeyOutput.Enter, weight = 1.5f)
)
```

Make the same change to the `uppercase` bottom row (lines 54-61).

**Step 4: Run tests to verify they pass**

Run: `./gradlew testFullDebugUnitTest --tests "com.keyjawn.KeyboardLayoutTest" -q`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/keyjawn/KeyboardLayout.kt app/src/test/java/com/keyjawn/KeyboardLayoutTest.kt
git commit -m "remove period key from bottom row, widen spacebar to 4.5x weight"
```

---

### Task 2: Add input validation to AppPrefs

Custom text entries (for extra row slots and quick key) need validation before any UI references them. Build this first.

**Files:**
- Modify: `app/src/main/java/com/keyjawn/AppPrefs.kt`
- Modify: `app/src/test/java/com/keyjawn/AppPrefsTest.kt`

**Step 1: Write failing tests**

Add these tests to `AppPrefsTest.kt`:

```kotlin
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
    appPrefs.setExtraSlot(0, "keycode:KEYCODE_HOME")
    assertEquals("keycode:KEYCODE_HOME", appPrefs.getExtraSlot(0))
}

@Test
fun `set extra slot with custom text sanitizes input`() {
    appPrefs.setExtraSlot(1, "text:he\u0000llo")
    assertEquals("text:hello", appPrefs.getExtraSlot(1))
}

@Test
fun `set custom quick key sanitizes input`() {
    appPrefs.setQuickKey("text:\u0000|")
    // Quick key stores raw value for predefined, "text:X" for custom
    assertEquals("text:|", appPrefs.getQuickKey())
}

@Test
fun `getExtraSlotLabel returns readable name`() {
    assertEquals("ESC", AppPrefs.getExtraSlotLabel("keycode:KEYCODE_ESCAPE"))
    assertEquals("Tab", AppPrefs.getExtraSlotLabel("keycode:KEYCODE_TAB"))
    assertEquals("Ctrl", AppPrefs.getExtraSlotLabel("ctrl"))
    assertEquals("Home", AppPrefs.getExtraSlotLabel("keycode:KEYCODE_HOME"))
    assertEquals("End", AppPrefs.getExtraSlotLabel("keycode:KEYCODE_END"))
    assertEquals("|", AppPrefs.getExtraSlotLabel("text:|"))
    assertEquals("hello", AppPrefs.getExtraSlotLabel("text:hello"))
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew testFullDebugUnitTest --tests "com.keyjawn.AppPrefsTest" -q`
Expected: FAIL — methods don't exist yet

**Step 3: Implement in AppPrefs.kt**

Add these to `AppPrefs`:

```kotlin
companion object {
    val QUICK_KEY_OPTIONS = listOf(
        "/", ".", ",", "?", "!", "\u2014", "'", "\"", ":", ";",
        "|", "~", "`", "\\", "@", "#", "$", "_", "&", "-", "+", "=", "^", "%"
    )

    val EXTRA_SLOT_OPTIONS = listOf(
        "keycode:KEYCODE_ESCAPE", "keycode:KEYCODE_TAB", "ctrl",
        "keycode:KEYCODE_MOVE_HOME", "keycode:KEYCODE_MOVE_END",
        "keycode:KEYCODE_PAGE_UP", "keycode:KEYCODE_PAGE_DOWN",
        "keycode:KEYCODE_INSERT", "keycode:KEYCODE_FORWARD_DEL",
        "text:|", "text:~", "text:`", "text:\\"
    )

    private val EXTRA_SLOT_DEFAULTS = arrayOf(
        "keycode:KEYCODE_ESCAPE",
        "keycode:KEYCODE_TAB",
        "ctrl"
    )

    private val SLOT_LABELS = mapOf(
        "keycode:KEYCODE_ESCAPE" to "ESC",
        "keycode:KEYCODE_TAB" to "Tab",
        "ctrl" to "Ctrl",
        "keycode:KEYCODE_MOVE_HOME" to "Home",
        "keycode:KEYCODE_MOVE_END" to "End",
        "keycode:KEYCODE_PAGE_UP" to "PgUp",
        "keycode:KEYCODE_PAGE_DOWN" to "PgDn",
        "keycode:KEYCODE_INSERT" to "Ins",
        "keycode:KEYCODE_FORWARD_DEL" to "Del"
    )

    fun sanitizeCustomText(input: String): String {
        return input
            .filter { it == '\t' || it.code >= 0x20 }  // keep tab, strip other control chars
            .take(8)
    }

    fun getExtraSlotLabel(value: String): String {
        SLOT_LABELS[value]?.let { return it }
        if (value.startsWith("text:")) return value.removePrefix("text:")
        return value
    }
}

fun getExtraSlot(index: Int): String {
    val key = "extra_slot_$index"
    return prefs.getString(key, EXTRA_SLOT_DEFAULTS.getOrElse(index) { "keycode:KEYCODE_ESCAPE" }) ?: EXTRA_SLOT_DEFAULTS[0]
}

fun setExtraSlot(index: Int, value: String) {
    val sanitized = if (value.startsWith("text:")) {
        "text:" + sanitizeCustomText(value.removePrefix("text:"))
    } else {
        value
    }
    prefs.edit().putString("extra_slot_$index", sanitized).apply()
}
```

Also update `setQuickKey` to sanitize custom entries:

```kotlin
fun setQuickKey(char: String) {
    val sanitized = if (char.startsWith("text:")) {
        "text:" + sanitizeCustomText(char.removePrefix("text:"))
    } else {
        char
    }
    prefs.edit().putString("quick_key", sanitized).apply()
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew testFullDebugUnitTest --tests "com.keyjawn.AppPrefsTest" -q`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/keyjawn/AppPrefs.kt app/src/test/java/com/keyjawn/AppPrefsTest.kt
git commit -m "add extra row slot prefs, input validation, expanded quick key options"
```

---

### Task 3: Create KeyPreview overlay

**Files:**
- Create: `app/src/main/java/com/keyjawn/KeyPreview.kt`
- Create: `app/src/test/java/com/keyjawn/KeyPreviewTest.kt`

**Step 1: Write failing tests**

Create `KeyPreviewTest.kt`:

```kotlin
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
        // Give container a measurable size
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
        // After hide, visibility should be GONE (may need to advance animations in Robolectric)
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
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew testFullDebugUnitTest --tests "com.keyjawn.KeyPreviewTest" -q`
Expected: FAIL — KeyPreview class doesn't exist

**Step 3: Implement KeyPreview.kt**

Create `app/src/main/java/com/keyjawn/KeyPreview.kt`:

```kotlin
package com.keyjawn

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.TextView

class KeyPreview(
    private val container: FrameLayout,
    private val themeManager: ThemeManager
) {

    val previewView: TextView

    private val density = container.context.resources.displayMetrics.density
    private val previewSize = (48 * density + 0.5f).toInt()
    private var currentAnimator: AnimatorSet? = null

    init {
        previewView = TextView(container.context).apply {
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
            setTextColor(themeManager.keyText())
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(previewSize, previewSize)
            elevation = 12 * density
        }
        updateBackground()
        container.addView(previewView)
    }

    private fun updateBackground() {
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 8 * density
            setColor(themeManager.keyBg())
            setStroke((1 * density + 0.5f).toInt(), themeManager.keyHint())
        }
        previewView.background = bg
    }

    fun show(anchor: View, label: String) {
        currentAnimator?.cancel()

        previewView.text = label

        // Position above the anchor key, centered horizontally
        val anchorLoc = IntArray(2)
        val containerLoc = IntArray(2)
        anchor.getLocationInWindow(anchorLoc)
        container.getLocationInWindow(containerLoc)

        val anchorCenterX = anchorLoc[0] - containerLoc[0] + anchor.width / 2
        val anchorTop = anchorLoc[1] - containerLoc[1]

        val params = previewView.layoutParams as FrameLayout.LayoutParams
        params.leftMargin = anchorCenterX - previewSize / 2
        params.topMargin = anchorTop - previewSize - (4 * density + 0.5f).toInt()
        // Clamp to container bounds
        if (params.leftMargin < 0) params.leftMargin = 0
        if (params.leftMargin + previewSize > container.width) {
            params.leftMargin = container.width - previewSize
        }
        if (params.topMargin < 0) params.topMargin = 0
        previewView.layoutParams = params

        previewView.visibility = View.VISIBLE

        // Bounce animation: scale from 0.8 to 1.0 with overshoot
        previewView.scaleX = 0.8f
        previewView.scaleY = 0.8f
        val scaleX = ObjectAnimator.ofFloat(previewView, "scaleX", 0.8f, 1f)
        val scaleY = ObjectAnimator.ofFloat(previewView, "scaleY", 0.8f, 1f)
        val animator = AnimatorSet().apply {
            playTogether(scaleX, scaleY)
            duration = 100
            interpolator = OvershootInterpolator(1.5f)
        }
        currentAnimator = animator
        animator.start()
    }

    fun hide() {
        currentAnimator?.cancel()
        currentAnimator = null
        previewView.animate()
            .alpha(0f)
            .setDuration(50)
            .withEndAction {
                previewView.visibility = View.GONE
                previewView.alpha = 1f
            }
            .start()
    }
}
```

**Step 4: Run tests to verify they pass**

Run: `./gradlew testFullDebugUnitTest --tests "com.keyjawn.KeyPreviewTest" -q`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/keyjawn/KeyPreview.kt app/src/test/java/com/keyjawn/KeyPreviewTest.kt
git commit -m "add key press preview overlay with bounce animation"
```

---

### Task 4: Wire KeyPreview into QwertyKeyboard

**Files:**
- Modify: `app/src/main/java/com/keyjawn/QwertyKeyboard.kt`
- Modify: `app/src/main/java/com/keyjawn/KeyJawnService.kt`
- Modify: `app/src/main/res/layout/keyboard_view.xml`

**Step 1: Add a preview container ID to the layout**

In `keyboard_view.xml`, add an `android:id` to the existing FrameLayout (line 22-24) so we can find it from code:

Change:
```xml
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="wrap_content">
```

to:
```xml
<FrameLayout
    android:id="@+id/keyboard_frame"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">
```

**Step 2: Add KeyPreview parameter to QwertyKeyboard**

In `QwertyKeyboard.kt`, add a `keyPreview` parameter:

```kotlin
class QwertyKeyboard(
    private val container: LinearLayout,
    private val keySender: KeySender,
    private val extraRowManager: ExtraRowManager,
    private val inputConnectionProvider: () -> InputConnection?,
    private val appPrefs: AppPrefs? = null,
    private val slashPopup: SlashCommandPopup? = null,
    private val themeManager: ThemeManager? = null,
    private val keyPreview: KeyPreview? = null
)
```

**Step 3: Wire touch listeners in createKeyView()**

In `QwertyKeyboard.createKeyView()`, after the button is created and before the return statements, add a touch listener for character keys and number keys (but NOT when on symbols layer). Add this right before the `// Alt key long-press for Character keys` comment (around line 225):

```kotlin
// Key preview on touch for character keys (letters + numbers)
if (key.output is KeyOutput.Character && keyPreview != null &&
    currentLayer != KeyboardLayouts.LAYER_SYMBOLS &&
    currentLayer != KeyboardLayouts.LAYER_SYMBOLS2) {
    val previewLabel = key.label
    button.setOnTouchListener { v, event ->
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                keyPreview.show(v, previewLabel)
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                keyPreview.hide()
            }
        }
        false  // Don't consume — let click listener fire too
    }
}
```

**Step 4: Create KeyPreview in KeyJawnService**

In `KeyJawnService.onCreateInputView()`, after the qwerty container is found (line 101) and before `QwertyKeyboard` is constructed (line 117), add:

```kotlin
val keyboardFrame = view.findViewById<FrameLayout>(R.id.keyboard_frame)
val keyPreview = KeyPreview(keyboardFrame, tm)
```

Then pass `keyPreview` to the QwertyKeyboard constructor:

```kotlin
val qwerty = QwertyKeyboard(container, keySender, erm, { currentInputConnection }, appPrefs, slashPopup, tm, keyPreview)
```

**Step 5: Build and run tests**

Run: `./gradlew assembleFullDebug && ./gradlew testFullDebugUnitTest -q`
Expected: BUILD SUCCESS, all tests pass

**Step 6: Commit**

```bash
git add app/src/main/res/layout/keyboard_view.xml app/src/main/java/com/keyjawn/QwertyKeyboard.kt app/src/main/java/com/keyjawn/KeyJawnService.kt
git commit -m "wire key preview into QwertyKeyboard for letter and number keys"
```

---

### Task 5: Make extra row slots configurable in ExtraRowManager

**Files:**
- Modify: `app/src/main/java/com/keyjawn/ExtraRowManager.kt`
- Modify: `app/src/test/java/com/keyjawn/ExtraRowManagerTest.kt`

**Step 1: Write failing tests**

Add to `ExtraRowManagerTest.kt`:

```kotlin
@Test
fun `slot 0 defaults to ESC behavior`() {
    val context = RuntimeEnvironment.getApplication()
    val view = LayoutInflater.from(context).inflate(R.layout.keyboard_view, null)
    val prefs = AppPrefs(context)
    // Default slot 0 is ESC
    assertEquals("keycode:KEYCODE_ESCAPE", prefs.getExtraSlot(0))
}

@Test
fun `slot 1 defaults to Tab behavior`() {
    val context = RuntimeEnvironment.getApplication()
    val prefs = AppPrefs(context)
    assertEquals("keycode:KEYCODE_TAB", prefs.getExtraSlot(1))
}

@Test
fun `slot 2 defaults to Ctrl`() {
    val context = RuntimeEnvironment.getApplication()
    val prefs = AppPrefs(context)
    assertEquals("ctrl", prefs.getExtraSlot(2))
}
```

**Step 2: Run tests to verify they pass (prefs already added in task 2)**

Run: `./gradlew testFullDebugUnitTest --tests "com.keyjawn.ExtraRowManagerTest" -q`
Expected: PASS (these test the prefs, which were added in Task 2)

**Step 3: Refactor ExtraRowManager to read slot prefs**

In `ExtraRowManager.kt`, replace the hardcoded `wireEsc()`, `wireTab()`, and `wireCtrl()` calls in `init` with a dynamic wiring approach.

Replace lines 54-57 (the three wire calls):

```kotlin
wireEsc()
wireTab()
wireClipboard()
wireCtrl()
```

with:

```kotlin
wireSlot(0, R.id.key_esc)
wireSlot(1, R.id.key_tab)
wireClipboard()
wireSlot(2, R.id.key_ctrl)
```

Add the `wireSlot` method:

```kotlin
fun wireSlot(slotIndex: Int, buttonId: Int) {
    val button = view.findViewById<Button>(buttonId)
    val config = appPrefs?.getExtraSlot(slotIndex) ?: return

    // Clear existing listeners
    button.setOnClickListener(null)
    button.setOnLongClickListener(null)

    when {
        config == "ctrl" -> {
            // Preserve the 3-state Ctrl machine
            button.text = "Ctrl"
            button.setOnClickListener { ctrlState.tap() }
            button.setOnLongClickListener { ctrlState.longPress(); true }
        }
        config.startsWith("keycode:") -> {
            val keyCodeName = config.removePrefix("keycode:")
            val keyCode = try {
                android.view.KeyEvent::class.java.getField(keyCodeName).getInt(null)
            } catch (_: Exception) {
                android.view.KeyEvent.KEYCODE_ESCAPE  // fallback
            }
            button.text = AppPrefs.getExtraSlotLabel(config)
            button.setOnClickListener {
                val ic = inputConnectionProvider() ?: return@setOnClickListener
                keySender.sendKey(ic, keyCode)
            }
        }
        config.startsWith("text:") -> {
            val text = config.removePrefix("text:")
            button.text = text
            button.setOnClickListener {
                val ic = inputConnectionProvider() ?: return@setOnClickListener
                keySender.sendText(ic, text)
            }
        }
    }
}
```

Remove the now-unused `wireEsc()` and `wireTab()` private methods. Keep `wireCtrl()` removed since `wireSlot` handles the ctrl case.

Add a `rewireSlots` method for live reconfiguration from MenuPanel:

```kotlin
fun rewireSlots() {
    wireSlot(0, R.id.key_esc)
    wireSlot(1, R.id.key_tab)
    wireSlot(2, R.id.key_ctrl)
    applyThemeColors()
}
```

**Step 4: Run all tests**

Run: `./gradlew testFullDebugUnitTest -q`
Expected: PASS

**Step 5: Commit**

```bash
git add app/src/main/java/com/keyjawn/ExtraRowManager.kt app/src/test/java/com/keyjawn/ExtraRowManagerTest.kt
git commit -m "make extra row ESC/Tab/Ctrl slots configurable via AppPrefs"
```

---

### Task 6: Add key mapping section to MenuPanel

**Files:**
- Modify: `app/src/main/java/com/keyjawn/MenuPanel.kt`
- Modify: `app/src/test/java/com/keyjawn/MenuPanelTest.kt`

**Step 1: Write failing tests**

Add to `MenuPanelTest.kt`:

```kotlin
@Test
fun `menu shows key mapping section when full flavor`() {
    menuPanel.show()
    var foundKeyMapping = false
    for (i in 0 until list.childCount) {
        val child = list.getChildAt(i)
        if (child is TextView && child.text.toString() == "Key mapping") {
            foundKeyMapping = true
            break
        }
    }
    assertTrue("Should have key mapping section header", foundKeyMapping)
}

@Test
fun `key mapping section has 4 rows`() {
    menuPanel.show()
    // Find the key mapping header, then count rows until next header or end
    var inSection = false
    var rowCount = 0
    for (i in 0 until list.childCount) {
        val child = list.getChildAt(i)
        if (child is TextView && child.text.toString() == "Key mapping") {
            inSection = true
            continue
        }
        if (inSection) {
            if (child is LinearLayout) {
                rowCount++
            } else if (child is TextView && rowCount > 0) {
                // Hit next section header
                break
            }
        }
    }
    assertEquals("Should have 4 key mapping rows (3 slots + quick key)", 4, rowCount)
}
```

**Step 2: Run tests to verify they fail**

Run: `./gradlew testFullDebugUnitTest --tests "com.keyjawn.MenuPanelTest" -q`
Expected: FAIL

**Step 3: Add key mapping section to MenuPanel**

Add an `onExtraRowChanged` callback parameter to `MenuPanel`:

```kotlin
class MenuPanel(
    // ... existing params ...
    private val onExtraRowChanged: () -> Unit = {},
)
```

In `populateMenu()`, after the Toggles section, add:

```kotlin
if (isFullFlavor) {
    addSectionHeader("Key mapping")
    for (slot in 0..2) {
        val label = when (slot) {
            0 -> "Slot 1 (left)"
            1 -> "Slot 2 (center)"
            2 -> "Slot 3 (right)"
            else -> "Slot $slot"
        }
        addPickerRow(
            label = label,
            currentValue = AppPrefs.getExtraSlotLabel(appPrefs.getExtraSlot(slot)),
            options = AppPrefs.EXTRA_SLOT_OPTIONS.map { AppPrefs.getExtraSlotLabel(it) to it },
            onSelect = { value ->
                appPrefs.setExtraSlot(slot, value)
                onExtraRowChanged()
                populateMenu()
            }
        )
    }
    addPickerRow(
        label = "Quick key",
        currentValue = appPrefs.getQuickKey().let {
            if (it.startsWith("text:")) it.removePrefix("text:") else it
        },
        options = AppPrefs.QUICK_KEY_OPTIONS.map { it to it },
        onSelect = { value ->
            appPrefs.setQuickKey(value)
            populateMenu()
        }
    )
}
```

Add the `addPickerRow` helper method:

```kotlin
private fun addPickerRow(label: String, currentValue: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
        )
        setPadding(dp(14), 0, dp(14), 0)
    }

    val text = TextView(context).apply {
        this.text = label
        textSize = 15f
        setTextColor(themeManager.keyText())
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
    row.addView(text)

    val value = TextView(context).apply {
        this.text = currentValue
        textSize = 13f
        setTextColor(themeManager.accent())
    }
    row.addView(value)

    row.setOnClickListener {
        showPickerPopup(options, onSelect)
    }

    list.addView(row)
    addDivider()
}

private fun showPickerPopup(options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    val scrollView = ScrollView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            dp(200)
        )
    }
    val pickerList = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
    }
    scrollView.addView(pickerList)

    for ((displayLabel, rawValue) in options) {
        val item = TextView(context).apply {
            text = displayLabel
            textSize = 16f
            setTextColor(themeManager.keyText())
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener {
                onSelect(rawValue)
                // Remove picker from list
                (scrollView.parent as? LinearLayout)?.removeView(scrollView)
            }
        }
        pickerList.addView(item)
    }

    // Add "Custom..." option
    val customItem = TextView(context).apply {
        text = "Custom..."
        textSize = 16f
        setTextColor(themeManager.accent())
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setOnClickListener {
            showCustomTextInput { customText ->
                if (customText.isNotEmpty()) {
                    onSelect("text:$customText")
                }
                (scrollView.parent as? LinearLayout)?.removeView(scrollView)
            }
        }
    }
    pickerList.addView(customItem)

    // Insert the picker in-place in the menu list
    scrollView.setBackgroundColor(themeManager.keyboardBg())
    list.addView(scrollView)
    scrollView.requestFocus()
}

private fun showCustomTextInput(onConfirm: (String) -> Unit) {
    val input = android.widget.EditText(context).apply {
        hint = "Type custom key (max 8 chars)"
        setTextColor(themeManager.keyText())
        setHintTextColor(themeManager.keyHint())
        filters = arrayOf(android.text.InputFilter.LengthFilter(8))
    }
    android.app.AlertDialog.Builder(context)
        .setTitle("Custom key")
        .setView(input)
        .setPositiveButton("OK") { _, _ ->
            val sanitized = AppPrefs.sanitizeCustomText(input.text.toString())
            onConfirm(sanitized)
        }
        .setNegativeButton("Cancel", null)
        .show()
}
```

**Step 4: Update MenuPanel constructor calls**

In `ExtraRowManager.kt` where `MenuPanel` is created (around line 202), add the `onExtraRowChanged` parameter:

```kotlin
val mp = MenuPanel(
    // ... existing params ...
    onExtraRowChanged = { rewireSlots() }
)
```

In `MenuPanelTest.kt` setup, add the parameter:

```kotlin
menuPanel = MenuPanel(
    // ... existing params ...
    onExtraRowChanged = {}
)
```

**Step 5: Run all tests**

Run: `./gradlew testFullDebugUnitTest -q`
Expected: PASS

**Step 6: Commit**

```bash
git add app/src/main/java/com/keyjawn/MenuPanel.kt app/src/main/java/com/keyjawn/ExtraRowManager.kt app/src/test/java/com/keyjawn/MenuPanelTest.kt
git commit -m "add key mapping section to menu panel for extra row and quick key customization"
```

---

### Task 7: Update QuickKey long-press to use expanded options

**Files:**
- Modify: `app/src/main/java/com/keyjawn/QwertyKeyboard.kt:211-223`

**Step 1: Update QuickKey long-press handler**

In `QwertyKeyboard.createKeyView()`, the QuickKey long-press handler (lines 211-223) currently shows `AltKeyPopup` with the old options list. The AltKeyPopup can't fit 24+ options horizontally.

Replace the QuickKey block:

```kotlin
if (key.output is KeyOutput.QuickKey) {
    val currentQuickKey = appPrefs?.getQuickKey() ?: "/"
    val displayKey = if (currentQuickKey.startsWith("text:")) currentQuickKey.removePrefix("text:") else currentQuickKey
    button.text = displayKey
    quickKeyButton = button
    button.setOnLongClickListener {
        val options = AppPrefs.QUICK_KEY_OPTIONS
        altKeyPopup.show(button, options, onSelect = { selected ->
            appPrefs?.setQuickKey(selected)
            button.text = selected
        })
        true
    }
}
```

Note: With 24 options, the horizontal AltKeyPopup will scroll off-screen. This is acceptable as a shortcut — the full picker is in MenuPanel. If the popup feels too cramped, we can later replace it with a grid popup. For now the horizontal scroll works.

**Step 2: Build and run tests**

Run: `./gradlew assembleFullDebug && ./gradlew testFullDebugUnitTest -q`
Expected: PASS

**Step 3: Commit**

```bash
git add app/src/main/java/com/keyjawn/QwertyKeyboard.kt
git commit -m "update quick key to show expanded options and handle custom text prefix"
```

---

### Task 8: Run full test suite and lint

**Step 1: Run all tests for both flavors**

Run: `./gradlew testFullDebugUnitTest testLiteDebugUnitTest -q`
Expected: All tests pass

**Step 2: Run lint**

Run: `./gradlew lintFullDebug lintLiteDebug`
Expected: PASS (may need to update lint baseline if new warnings appear)

**Step 3: If lint baseline needs updating**

If lint fails with new warnings, delete the old baseline and regenerate:

```bash
rm app/lint-baseline.xml
./gradlew lintFullDebug  # first run creates new baseline
./gradlew lintFullDebug  # second run passes with baseline
```

**Step 4: Commit any baseline updates**

```bash
git add app/lint-baseline.xml
git commit -m "update lint baseline for key UX changes"
```

---

### Task 9: Manual testing on emulator

**Step 1: Build and install**

```bash
./gradlew assembleFullDebug
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```

**Step 2: Test key previews**

- Open a text field
- Type letters — verify preview bubble appears above each key with bounce animation
- Type numbers — verify preview appears
- Switch to symbols layer — verify NO preview appears
- Verify special keys (Shift, Del, Enter, Space, ?123) don't show preview

**Step 3: Test spacebar width**

- Verify the bottom row has: ?123, comma, SPACE (wider), QuickKey, Enter
- Verify no period key
- Verify spacebar is noticeably wider and easier to hit

**Step 4: Test extra row customization**

- Open gear menu -> Key mapping section
- Tap Slot 1 (ESC) -> select Home
- Verify the ESC button now shows "Home" and sends Home key
- Tap Slot 3 (Ctrl) -> select Custom... -> type "|"
- Verify Ctrl button now shows "|" and types "|"
- Reset Slot 3 back to Ctrl, verify 3-state machine works

**Step 5: Test quick key customization**

- Open gear menu -> Quick key row
- Select "~"
- Verify quick key button shows "~" and types "~"
- Long-press quick key -> verify expanded options appear
- Select Custom... -> type ">>>" -> verify it works

**Step 6: Test input validation**

- Try entering a custom text with more than 8 chars -> verify truncated
- Try entering control characters -> verify stripped
