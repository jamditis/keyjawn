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

    // Resolved once at construction and refreshed only through the setter or
    // refresh(): every color lookup reads these cached fields instead of
    // re-reading prefs and re-parsing the theme enum on the hot render path.
    private var _currentTheme: KeyboardTheme = readPersistedTheme()
    private var palette: Palette = paletteFor(_currentTheme)

    // Drawable prototypes, keyed by surface color, rebuilt only on a theme
    // change. Each call hands out a cheap copy via constantState.newDrawable()
    // (a Drawable can back only one view) instead of rebuilding the drawable
    // tree per key per render. The pressed ColorStateList is immutable, so a
    // single instance is shared across every key of the active theme.
    private var pressedColorStateList: ColorStateList = ColorStateList.valueOf(palette.keyBgPressed)
    private val keyDrawableCache = HashMap<Int, Drawable>()
    private val flatDrawableCache = HashMap<Int, Drawable>()
    private val extraRowDrawableCache = HashMap<Int, Drawable>()

    var currentTheme: KeyboardTheme
        get() = _currentTheme
        set(value) {
            prefs.edit().putString("theme", value.name).apply()
            _currentTheme = value
            applyPalette(paletteFor(value))
        }

    /**
     * Re-resolve the cached theme and palette from prefs. Needed only when the
     * persisted value can change without going through the setter on this
     * instance (the setter is the normal single refresh point).
     */
    fun refresh() {
        _currentTheme = readPersistedTheme()
        applyPalette(paletteFor(_currentTheme))
    }

    private fun applyPalette(newPalette: Palette) {
        palette = newPalette
        // The pressed ripple color is theme-specific, so the drawable
        // prototypes must be discarded even when a surface color repeats
        // across themes.
        pressedColorStateList = ColorStateList.valueOf(newPalette.keyBgPressed)
        keyDrawableCache.clear()
        flatDrawableCache.clear()
        extraRowDrawableCache.clear()
    }

    private fun readPersistedTheme(): KeyboardTheme =
        themeForName(prefs.getString("theme", KeyboardTheme.DARK.name))

    /**
     * True when the persisted theme differs from the cached one, i.e. another
     * instance (such as SettingsActivity) changed it. Lets the IME decide to
     * refresh and re-apply colors on the next keyboard open.
     */
    fun isThemeStale(): Boolean = readPersistedTheme() != _currentTheme

    // -- Core colors --

    fun keyboardBg(): Int = palette.keyboardBg

    fun keyBg(): Int = palette.keyBg

    fun keyText(): Int = palette.keyText

    fun keySpecialBg(): Int = palette.keySpecialBg

    fun quickKeyBg(): Int = palette.quickKeyBg

    fun accent(): Int = palette.accent

    fun accentLocked(): Int = palette.accentLocked

    fun extraRowBg(): Int = palette.extraRowBg

    fun qwertyBg(): Int = palette.qwertyBg

    fun keyHint(): Int = palette.keyHint

    fun keyBgPressed(): Int = palette.keyBgPressed

    fun divider(): Int = palette.divider

    // -- Extra row per-button colors --

    fun escBg(): Int = palette.escBg

    fun tabBg(): Int = palette.tabBg

    fun clipboardBg(): Int = palette.clipboardBg

    fun arrowBg(): Int = palette.arrowBg

    fun uploadBg(): Int = palette.uploadBg

    fun micBg(): Int = palette.micBg

    // -- Drawable builders --
    // The drawable tree for a given surface color is built once per theme and
    // cached as a prototype. Each create* call hands out a fresh, view-assignable
    // copy via constantState.newDrawable() (a Drawable can back only one view)
    // without reconstructing the GradientDrawable/LayerDrawable tree. Callers must
    // not tint the returned drawable in place (the surface color is already baked
    // in); a future per-instance tint would need a mutate() on the copy first.

    fun createKeyDrawable(bgColor: Int): Drawable =
        copyOf(keyDrawablePrototype(bgColor))

    fun createFlatDrawable(bgColor: Int): Drawable =
        copyOf(flatDrawablePrototype(bgColor))

    fun createExtraRowButtonDrawable(bgColor: Int): Drawable =
        copyOf(extraRowButtonDrawablePrototype(bgColor))

    // Cached prototypes. internal so tests can assert that the same prototype
    // instance is reused for a color until the theme changes (RippleDrawable's
    // newDrawable() does not preserve ConstantState identity, so prototype
    // identity is the observable signal that the tree was not rebuilt).
    internal fun keyDrawablePrototype(bgColor: Int): Drawable =
        keyDrawableCache.getOrPut(bgColor) { buildKeyDrawable(bgColor) }

    internal fun flatDrawablePrototype(bgColor: Int): Drawable =
        flatDrawableCache.getOrPut(bgColor) { buildFlatDrawable(bgColor) }

    internal fun extraRowButtonDrawablePrototype(bgColor: Int): Drawable =
        extraRowDrawableCache.getOrPut(bgColor) { buildExtraRowButtonDrawable(bgColor) }

    private fun copyOf(prototype: Drawable): Drawable =
        prototype.constantState?.newDrawable() ?: prototype

    private fun buildKeyDrawable(bgColor: Int): Drawable {
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

        return RippleDrawable(pressedColorStateList, layers, null)
    }

    private fun buildFlatDrawable(bgColor: Int): Drawable {
        val cornerRadius = 6f * density
        return GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            this.cornerRadius = cornerRadius
        }
    }

    private fun buildExtraRowButtonDrawable(bgColor: Int): Drawable {
        val cornerRadius = 6f * density
        val shape = GradientDrawable().apply {
            this.shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            this.cornerRadius = cornerRadius
        }
        return RippleDrawable(pressedColorStateList, shape, null)
    }

    // -- Pure palette --
    // Swatch colors for a given theme, with no prefs read or write and no
    // currentTheme mutation. Used to render theme previews without touching the
    // live selection.

    data class Swatch(val keyboardBg: Int, val keyBg: Int, val keyText: Int)

    fun swatch(theme: KeyboardTheme): Swatch = paletteFor(theme).let {
        Swatch(it.keyboardBg, it.keyBg, it.keyText)
    }

    fun getAvailableThemes(): List<KeyboardTheme> = KeyboardTheme.entries

    fun themeLabel(theme: KeyboardTheme): String = when (theme) {
        KeyboardTheme.DARK -> "Dark"
        KeyboardTheme.LIGHT -> "Light"
        KeyboardTheme.OLED -> "OLED black"
        KeyboardTheme.TERMINAL -> "Terminal"
    }

    /**
     * Every theme color resolved once into plain Int fields. Pure function of
     * the theme, so it carries no prefs access and can back both the live
     * cached palette and the preview swatches.
     */
    class Palette(
        val keyboardBg: Int,
        val keyBg: Int,
        val keyText: Int,
        val keySpecialBg: Int,
        val quickKeyBg: Int,
        val accent: Int,
        val accentLocked: Int,
        val extraRowBg: Int,
        val qwertyBg: Int,
        val keyHint: Int,
        val keyBgPressed: Int,
        val divider: Int,
        val escBg: Int,
        val tabBg: Int,
        val clipboardBg: Int,
        val arrowBg: Int,
        val uploadBg: Int,
        val micBg: Int
    )

    companion object {
        // Precomputed name -> enum lookup. Replaces KeyboardTheme.valueOf(),
        // which throws and catches on an unknown value, with a single map read.
        private val THEMES_BY_NAME: Map<String, KeyboardTheme> =
            KeyboardTheme.entries.associateBy { it.name }

        fun themeForName(name: String?): KeyboardTheme =
            THEMES_BY_NAME[name] ?: KeyboardTheme.DARK

        fun paletteFor(theme: KeyboardTheme): Palette = when (theme) {
            KeyboardTheme.DARK -> Palette(
                keyboardBg = 0xFF1B1B1F.toInt(),
                keyBg = 0xFF2B2B30.toInt(),
                keyText = 0xFFE8E8EC.toInt(),
                keySpecialBg = 0xFF333338.toInt(),
                quickKeyBg = 0xFF2A3A4A.toInt(),
                accent = 0xFF6C9BF2.toInt(),
                accentLocked = 0xFFE86C5A.toInt(),
                extraRowBg = 0xFF161619.toInt(),
                qwertyBg = 0xFF222226.toInt(),
                keyHint = 0xFF6E6E78.toInt(),
                keyBgPressed = 0xFF3A3A40.toInt(),
                divider = 0xFF38383E.toInt(),
                escBg = 0xFF1A1A1A.toInt(),
                tabBg = 0xFF2A4A35.toInt(),
                clipboardBg = 0xFF4A3D20.toInt(),
                arrowBg = 0xFF3A3A3F.toInt(),
                uploadBg = 0xFF253050.toInt(),
                micBg = 0xFF4A2525.toInt()
            )
            KeyboardTheme.LIGHT -> Palette(
                keyboardBg = 0xFFE8E8EC.toInt(),
                keyBg = 0xFFFFFFFF.toInt(),
                keyText = 0xFF1A1A1F.toInt(),
                keySpecialBg = 0xFFD8D8E0.toInt(),
                quickKeyBg = 0xFFC0D0E0.toInt(),
                accent = 0xFF2563EB.toInt(),
                accentLocked = 0xFFDC2626.toInt(),
                extraRowBg = 0xFFD0D0D8.toInt(),
                qwertyBg = 0xFFE0E0E6.toInt(),
                keyHint = 0xFF8888A0.toInt(),
                keyBgPressed = 0xFFD0D0D8.toInt(),
                divider = 0xFFC0C0CC.toInt(),
                escBg = 0xFFBBBBC8.toInt(),
                tabBg = 0xFFA8D4B8.toInt(),
                clipboardBg = 0xFFD4C898.toInt(),
                arrowBg = 0xFFC0C0CC.toInt(),
                uploadBg = 0xFF98B4D4.toInt(),
                micBg = 0xFFD4A0A0.toInt()
            )
            KeyboardTheme.OLED -> Palette(
                keyboardBg = 0xFF000000.toInt(),
                keyBg = 0xFF111114.toInt(),
                keyText = 0xFFE8E8EC.toInt(),
                keySpecialBg = 0xFF1A1A1E.toInt(),
                quickKeyBg = 0xFF1A2A3A.toInt(),
                accent = 0xFF6C9BF2.toInt(),
                accentLocked = 0xFFE86C5A.toInt(),
                extraRowBg = 0xFF000000.toInt(),
                qwertyBg = 0xFF080810.toInt(),
                keyHint = 0xFF555560.toInt(),
                keyBgPressed = 0xFF222228.toInt(),
                divider = 0xFF222228.toInt(),
                escBg = 0xFF0A0A0E.toInt(),
                tabBg = 0xFF1A3A25.toInt(),
                clipboardBg = 0xFF3A2D10.toInt(),
                arrowBg = 0xFF1A1A22.toInt(),
                uploadBg = 0xFF152040.toInt(),
                micBg = 0xFF3A1515.toInt()
            )
            KeyboardTheme.TERMINAL -> Palette(
                keyboardBg = 0xFF0A1A0A.toInt(),
                keyBg = 0xFF0F2B0F.toInt(),
                keyText = 0xFF33FF33.toInt(),
                keySpecialBg = 0xFF1A3A1A.toInt(),
                quickKeyBg = 0xFF1A3A2A.toInt(),
                accent = 0xFF33FF33.toInt(),
                accentLocked = 0xFFFF6633.toInt(),
                extraRowBg = 0xFF081808.toInt(),
                qwertyBg = 0xFF0D220D.toInt(),
                keyHint = 0xFF227722.toInt(),
                keyBgPressed = 0xFF1A4A1A.toInt(),
                divider = 0xFF1A3A1A.toInt(),
                escBg = 0xFF0A1A0A.toInt(),
                tabBg = 0xFF1A3A1A.toInt(),
                clipboardBg = 0xFF2A3A1A.toInt(),
                arrowBg = 0xFF1A2A1A.toInt(),
                uploadBg = 0xFF1A2A3A.toInt(),
                micBg = 0xFF3A1A1A.toInt()
            )
        }
    }
}
