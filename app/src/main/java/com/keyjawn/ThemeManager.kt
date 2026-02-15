package com.keyjawn

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable

enum class KeyboardTheme {
    DARK, LIGHT, OLED, TERMINAL
}

class ThemeManager(context: Context) {

    private val prefs = context.getSharedPreferences("keyjawn_theme", Context.MODE_PRIVATE)
    private val density = context.resources.displayMetrics.density

    var currentTheme: KeyboardTheme
        get() {
            val name = prefs.getString("theme", KeyboardTheme.DARK.name) ?: KeyboardTheme.DARK.name
            return try {
                KeyboardTheme.valueOf(name)
            } catch (_: IllegalArgumentException) {
                KeyboardTheme.DARK
            }
        }
        set(value) {
            prefs.edit().putString("theme", value.name).apply()
        }

    // -- Core colors --

    fun keyboardBg(): Int = when (currentTheme) {
        KeyboardTheme.DARK -> 0xFF1B1B1F.toInt()
        KeyboardTheme.LIGHT -> 0xFFE8E8EC.toInt()
        KeyboardTheme.OLED -> 0xFF000000.toInt()
        KeyboardTheme.TERMINAL -> 0xFF0A1A0A.toInt()
    }

    fun keyBg(): Int = when (currentTheme) {
        KeyboardTheme.DARK -> 0xFF2B2B30.toInt()
        KeyboardTheme.LIGHT -> 0xFFFFFFFF.toInt()
        KeyboardTheme.OLED -> 0xFF111114.toInt()
        KeyboardTheme.TERMINAL -> 0xFF0F2B0F.toInt()
    }

    fun keyText(): Int = when (currentTheme) {
        KeyboardTheme.DARK -> 0xFFE8E8EC.toInt()
        KeyboardTheme.LIGHT -> 0xFF1A1A1F.toInt()
        KeyboardTheme.OLED -> 0xFFE8E8EC.toInt()
        KeyboardTheme.TERMINAL -> 0xFF33FF33.toInt()
    }

    fun keySpecialBg(): Int = when (currentTheme) {
        KeyboardTheme.DARK -> 0xFF333338.toInt()
        KeyboardTheme.LIGHT -> 0xFFD8D8E0.toInt()
        KeyboardTheme.OLED -> 0xFF1A1A1E.toInt()
        KeyboardTheme.TERMINAL -> 0xFF1A3A1A.toInt()
    }

    fun accent(): Int = when (currentTheme) {
        KeyboardTheme.DARK -> 0xFF6C9BF2.toInt()
        KeyboardTheme.LIGHT -> 0xFF2563EB.toInt()
        KeyboardTheme.OLED -> 0xFF6C9BF2.toInt()
        KeyboardTheme.TERMINAL -> 0xFF33FF33.toInt()
    }

    fun accentLocked(): Int = when (currentTheme) {
        KeyboardTheme.DARK -> 0xFFE86C5A.toInt()
        KeyboardTheme.LIGHT -> 0xFFDC2626.toInt()
        KeyboardTheme.OLED -> 0xFFE86C5A.toInt()
        KeyboardTheme.TERMINAL -> 0xFFFF6633.toInt()
    }

    fun extraRowBg(): Int = when (currentTheme) {
        KeyboardTheme.DARK -> 0xFF161619.toInt()
        KeyboardTheme.LIGHT -> 0xFFD0D0D8.toInt()
        KeyboardTheme.OLED -> 0xFF000000.toInt()
        KeyboardTheme.TERMINAL -> 0xFF081808.toInt()
    }

    fun qwertyBg(): Int = when (currentTheme) {
        KeyboardTheme.DARK -> 0xFF222226.toInt()
        KeyboardTheme.LIGHT -> 0xFFE0E0E6.toInt()
        KeyboardTheme.OLED -> 0xFF080810.toInt()
        KeyboardTheme.TERMINAL -> 0xFF0D220D.toInt()
    }

    fun keyHint(): Int = when (currentTheme) {
        KeyboardTheme.DARK -> 0xFF6E6E78.toInt()
        KeyboardTheme.LIGHT -> 0xFF8888A0.toInt()
        KeyboardTheme.OLED -> 0xFF555560.toInt()
        KeyboardTheme.TERMINAL -> 0xFF227722.toInt()
    }

    fun keyBgPressed(): Int = when (currentTheme) {
        KeyboardTheme.DARK -> 0xFF3A3A40.toInt()
        KeyboardTheme.LIGHT -> 0xFFD0D0D8.toInt()
        KeyboardTheme.OLED -> 0xFF222228.toInt()
        KeyboardTheme.TERMINAL -> 0xFF1A4A1A.toInt()
    }

    fun divider(): Int = when (currentTheme) {
        KeyboardTheme.DARK -> 0xFF38383E.toInt()
        KeyboardTheme.LIGHT -> 0xFFC0C0CC.toInt()
        KeyboardTheme.OLED -> 0xFF222228.toInt()
        KeyboardTheme.TERMINAL -> 0xFF1A3A1A.toInt()
    }

    // -- Extra row per-button colors --

    fun escBg(): Int = when (currentTheme) {
        KeyboardTheme.DARK -> 0xFF1A1A1A.toInt()
        KeyboardTheme.LIGHT -> 0xFFBBBBC8.toInt()
        KeyboardTheme.OLED -> 0xFF0A0A0E.toInt()
        KeyboardTheme.TERMINAL -> 0xFF0A1A0A.toInt()
    }

    fun tabBg(): Int = when (currentTheme) {
        KeyboardTheme.DARK -> 0xFF2A4A35.toInt()
        KeyboardTheme.LIGHT -> 0xFFA8D4B8.toInt()
        KeyboardTheme.OLED -> 0xFF1A3A25.toInt()
        KeyboardTheme.TERMINAL -> 0xFF1A3A1A.toInt()
    }

    fun clipboardBg(): Int = when (currentTheme) {
        KeyboardTheme.DARK -> 0xFF4A3D20.toInt()
        KeyboardTheme.LIGHT -> 0xFFD4C898.toInt()
        KeyboardTheme.OLED -> 0xFF3A2D10.toInt()
        KeyboardTheme.TERMINAL -> 0xFF2A3A1A.toInt()
    }

    fun arrowBg(): Int = when (currentTheme) {
        KeyboardTheme.DARK -> 0xFF3A3A3F.toInt()
        KeyboardTheme.LIGHT -> 0xFFC0C0CC.toInt()
        KeyboardTheme.OLED -> 0xFF1A1A22.toInt()
        KeyboardTheme.TERMINAL -> 0xFF1A2A1A.toInt()
    }

    fun uploadBg(): Int = when (currentTheme) {
        KeyboardTheme.DARK -> 0xFF253050.toInt()
        KeyboardTheme.LIGHT -> 0xFF98B4D4.toInt()
        KeyboardTheme.OLED -> 0xFF152040.toInt()
        KeyboardTheme.TERMINAL -> 0xFF1A2A3A.toInt()
    }

    fun micBg(): Int = when (currentTheme) {
        KeyboardTheme.DARK -> 0xFF4A2525.toInt()
        KeyboardTheme.LIGHT -> 0xFFD4A0A0.toInt()
        KeyboardTheme.OLED -> 0xFF3A1515.toInt()
        KeyboardTheme.TERMINAL -> 0xFF3A1A1A.toInt()
    }

    // -- Drawable builders --

    fun createKeyDrawable(bgColor: Int): Drawable {
        val cornerRadius = 6f * density

        // Shadow layer (darker, offset down 1dp)
        val shadow = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(0x40000000)
            this.cornerRadius = cornerRadius
        }

        // Main key surface
        val surface = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            this.cornerRadius = cornerRadius
        }

        val layers = LayerDrawable(arrayOf(shadow, surface))
        val insetPx = (1f * density + 0.5f).toInt()
        layers.setLayerInset(0, 0, insetPx, 0, 0) // shadow offset down
        layers.setLayerInset(1, 0, 0, 0, insetPx) // surface offset up

        val rippleColor = ColorStateList.valueOf(keyBgPressed())
        return RippleDrawable(rippleColor, layers, null)
    }

    fun createFlatDrawable(bgColor: Int): Drawable {
        val cornerRadius = 6f * density
        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            this.cornerRadius = cornerRadius
        }
        return shape
    }

    fun createExtraRowButtonDrawable(bgColor: Int): Drawable {
        val cornerRadius = 6f * density
        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            this.cornerRadius = cornerRadius
        }
        val rippleColor = ColorStateList.valueOf(keyBgPressed())
        return RippleDrawable(rippleColor, shape, null)
    }

    fun getAvailableThemes(): List<KeyboardTheme> = KeyboardTheme.entries

    fun themeLabel(theme: KeyboardTheme): String = when (theme) {
        KeyboardTheme.DARK -> "Dark"
        KeyboardTheme.LIGHT -> "Light"
        KeyboardTheme.OLED -> "OLED black"
        KeyboardTheme.TERMINAL -> "Terminal"
    }
}
