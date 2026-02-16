package com.keyjawn

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MenuPanel(
    private val panel: ScrollView,
    private val list: LinearLayout,
    private val themeManager: ThemeManager,
    private val appPrefs: AppPrefs,
    private val isFullFlavor: Boolean,
    private val onUploadTap: (() -> Unit)?,
    private val onOpenSettings: () -> Unit,
    private val onThemeChanged: () -> Unit,
    private val onShowTooltip: (String) -> Unit,
    private val currentPackageProvider: () -> String,
    private val onExtraRowChanged: () -> Unit = {}
) {

    private val context: Context get() = panel.context
    private val density: Float = context.resources.displayMetrics.density

    fun toggle() {
        if (isShowing()) hide() else show()
    }

    fun show() {
        populateMenu()
        panel.visibility = View.VISIBLE
    }

    fun hide() {
        panel.visibility = View.GONE
    }

    fun isShowing(): Boolean = panel.visibility == View.VISIBLE

    private fun populateMenu() {
        list.removeAllViews()

        addSectionHeader("Actions")
        addActionRow("SCP upload", fullOnly = true) {
            onUploadTap?.invoke()
            hide()
        }
        addActionRow("Open settings", fullOnly = false) {
            onOpenSettings()
            hide()
        }

        addSectionHeader("Appearance")
        addThemeRow()

        addSectionHeader("Toggles")
        addToggleRow("Haptic feedback", fullOnly = false,
            isOn = { appPrefs.isHapticEnabled() },
            onToggle = { appPrefs.setHapticEnabled(!appPrefs.isHapticEnabled()); populateMenu() }
        )
        addToggleRow("Autocorrect", fullOnly = false,
            isOn = { appPrefs.isAutocorrectEnabled(currentPackageProvider()) },
            onToggle = { appPrefs.toggleAutocorrect(currentPackageProvider()); populateMenu() }
        )
        addToggleRow("Tooltips", fullOnly = true,
            isOn = { appPrefs.isTooltipsEnabled() },
            onToggle = { appPrefs.setTooltipsEnabled(!appPrefs.isTooltipsEnabled()); populateMenu() }
        )

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
    }

    private fun addSectionHeader(title: String) {
        val header = TextView(context).apply {
            text = title
            setTextColor(themeManager.keyHint())
            textSize = 12f
            setPadding(dp(14), dp(10), dp(14), dp(6))
        }
        list.addView(header)
    }

    private fun addActionRow(label: String, fullOnly: Boolean, action: () -> Unit) {
        val disabled = fullOnly && !isFullFlavor
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
            setTextColor(if (disabled) themeManager.keyHint() else themeManager.keyText())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(text)

        if (disabled) {
            val badge = TextView(context).apply {
                this.text = "full"
                textSize = 11f
                setTextColor(themeManager.keyHint())
            }
            row.addView(badge)
        }

        row.setOnClickListener {
            if (disabled) {
                onShowTooltip("Upgrade to full version")
            } else {
                action()
            }
        }

        list.addView(row)
        addDivider()
    }

    private fun addToggleRow(label: String, fullOnly: Boolean, isOn: () -> Boolean, onToggle: () -> Unit) {
        val disabled = fullOnly && !isFullFlavor
        val on = if (disabled) false else isOn()

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
            setTextColor(if (disabled) themeManager.keyHint() else themeManager.keyText())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(text)

        val indicator = TextView(context).apply {
            this.text = if (on) "ON" else "OFF"
            textSize = 13f
            setTextColor(
                if (disabled) themeManager.keyHint()
                else if (on) themeManager.accent()
                else themeManager.keyHint()
            )
        }
        row.addView(indicator)

        if (disabled) {
            val badge = TextView(context).apply {
                this.text = "  full"
                textSize = 11f
                setTextColor(themeManager.keyHint())
            }
            row.addView(badge)
        }

        row.setOnClickListener {
            if (disabled) {
                onShowTooltip("Upgrade to full version")
            } else {
                onToggle()
            }
        }

        list.addView(row)
        addDivider()
    }

    private fun addThemeRow() {
        val disabled = !isFullFlavor

        val label = TextView(context).apply {
            text = "Theme"
            textSize = 15f
            setTextColor(if (disabled) themeManager.keyHint() else themeManager.keyText())
            setPadding(dp(14), 0, dp(14), dp(4))
        }
        list.addView(label)

        val strip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(dp(10), dp(4), dp(10), dp(8))
        }

        val selected = themeManager.currentTheme

        for (theme in themeManager.getAvailableThemes()) {
            val preview = ThemeManager(context)
            preview.currentTheme = theme
            val bgColor = preview.keyboardBg()
            val keyColor = preview.keyBg()
            val textColor = preview.keyText()

            val swatch = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val size = dp(56)
                layoutParams = LinearLayout.LayoutParams(0, size, 1f).apply {
                    val margin = dp(4)
                    setMargins(margin, 0, margin, 0)
                }
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 8 * density
                    setColor(if (disabled) darken(bgColor) else bgColor)
                    if (theme == selected && !disabled) {
                        setStroke(dp(2), themeManager.accent())
                    }
                }
                background = bg
                isClickable = true
                isFocusable = true
            }

            val keyPreview = View(context).apply {
                val w = dp(20)
                val h = dp(10)
                layoutParams = LinearLayout.LayoutParams(w, h).apply {
                    bottomMargin = dp(3)
                }
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 3 * density
                    setColor(if (disabled) darken(keyColor) else keyColor)
                }
                background = bg
            }
            swatch.addView(keyPreview)

            val swatchLabel = TextView(context).apply {
                text = themeManager.themeLabel(theme)
                setTextColor(if (disabled) darken(textColor) else textColor)
                textSize = 9f
                gravity = Gravity.CENTER
            }
            swatch.addView(swatchLabel)

            swatch.setOnClickListener {
                if (disabled) {
                    onShowTooltip("Upgrade to full version")
                } else {
                    themeManager.currentTheme = theme
                    hide()
                    onThemeChanged()
                }
            }

            strip.addView(swatch)
        }

        // Restore real theme after creating previews
        themeManager.currentTheme = selected

        list.addView(strip)
        addDivider()
    }

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
                    (scrollView.parent as? LinearLayout)?.removeView(scrollView)
                }
            }
            pickerList.addView(item)
        }

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

    private fun addDivider() {
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply {
                marginStart = dp(14)
                marginEnd = dp(14)
            }
            setBackgroundColor(themeManager.divider())
        }
        list.addView(divider)
    }

    private fun darken(color: Int): Int {
        val a = (color ushr 24) and 0xFF
        val r = ((color ushr 16) and 0xFF) * 5 / 10
        val g = ((color ushr 8) and 0xFF) * 5 / 10
        val b = (color and 0xFF) * 5 / 10
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun dp(value: Int): Int = (value * density + 0.5f).toInt()
}
