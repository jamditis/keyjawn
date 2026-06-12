package com.keyjawn

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.ScrollView

class KeyJawnService : InputMethodService() {

    private val keySender = KeySender()
    private lateinit var appPrefs: AppPrefs
    private lateinit var themeManager: ThemeManager
    private var extraRowManager: ExtraRowManager? = null
    private var qwertyKeyboard: QwertyKeyboard? = null
    private var voiceInputHandler: VoiceInputHandler? = null
    private var slashCommandRegistry: SlashCommandRegistry? = null
    private var uploadHandler: UploadHandler? = null
    // internal (module-scoped) so unit tests in the same module can assert the
    // manager instance is reused across input-view rebuilds. Not public.
    internal var clipboardHistoryManager: ClipboardHistoryManager? = null

    companion object {
        var pendingUploadHandler: UploadHandler? = null
    }

    override fun onCreate() {
        super.onCreate()
        appPrefs = AppPrefs(this)
        themeManager = ThemeManager(this)
        if (BuildConfig.FLAVOR != "full") {
            themeManager.currentTheme = KeyboardTheme.DARK
        }
        slashCommandRegistry = SlashCommandRegistry(this)
        // The clipboard manager holds the user's unpinned clip history in memory and
        // registers a system clipboard listener in its constructor. Build it once for
        // the service lifetime so a theme-change input-view rebuild reuses the same
        // instance instead of stranding the listener and resetting history to empty.
        // It keeps no reference to the input view, so reuse is safe; ExtraRowManager
        // re-wires the new view's clipboard panel to this instance on each rebuild.
        clipboardHistoryManager = ClipboardHistoryManager(this)
    }

    override fun onCreateInputView(): View {
        // A theme change rebuilds the input view via setInputView(onCreateInputView()).
        // Tear down the previous voice and upload handlers before constructing
        // replacements so their SpeechRecognizer and IO scope are released instead of
        // leaking until the process dies. destroy() on each is idempotent. The
        // clipboard manager is intentionally not torn down here: it is hoisted to
        // onCreate() and reused so the user's unpinned clip history survives a reskin.
        voiceInputHandler?.destroy()
        uploadHandler?.destroy()
        pendingUploadHandler = null

        val view = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        val tm = themeManager
        // The theme can be changed from SettingsActivity (a separate
        // ThemeManager instance writing the shared pref). The palette is cached,
        // so re-resolve it from prefs before applying colors to pick up a change
        // made while this service's view was torn down.
        tm.refresh()
        val isFullFlavor = BuildConfig.FLAVOR == "full"

        // Apply theme colors to layout backgrounds
        view.setBackgroundColor(tm.keyboardBg())
        view.findViewById<View>(R.id.extra_row)?.setBackgroundColor(tm.extraRowBg())
        view.findViewById<View>(R.id.number_row)?.setBackgroundColor(tm.extraRowBg())
        view.findViewById<LinearLayout>(R.id.qwerty_container)?.setBackgroundColor(tm.qwertyBg())

        val voice = VoiceInputHandler(this)
        voiceInputHandler = voice

        val upload = if (isFullFlavor) {
            UploadHandler(this).also {
                it.setInputConnectionProvider { currentInputConnection }
                pendingUploadHandler = it
            }
        } else null
        uploadHandler = upload

        // Reuse the hoisted clipboard manager (built once in onCreate) rather than
        // constructing a new one per rebuild, which would drop unpinned history.
        // onCreate() always runs before onCreateInputView(), so the manager must
        // already exist here. Fail loudly if a future change drops the onCreate()
        // construction instead of silently reconstructing per rebuild.
        val clipManager = requireNotNull(clipboardHistoryManager) {
            "clipboardHistoryManager must be created in onCreate() before onCreateInputView()"
        }

        val clipPanel = view.findViewById<ScrollView>(R.id.clipboard_panel)
        val clipList = view.findViewById<LinearLayout>(R.id.clipboard_list)
        clipPanel?.setBackgroundColor(tm.keyboardBg())

        val menuPanel = view.findViewById<ScrollView>(R.id.menu_panel)
        val menuList = view.findViewById<LinearLayout>(R.id.menu_list)
        menuPanel?.setBackgroundColor(tm.keyboardBg())

        val erm = ExtraRowManager(
            view, keySender, { currentInputConnection }, voice,
            uploadHandler = upload,
            onUploadTap = if (isFullFlavor) {{ launchPhotoPicker() }} else null,
            clipboardHistoryManager = clipManager,
            themeManager = tm,
            isPaidUser = isFullFlavor,
            clipboardPanelView = clipPanel,
            clipboardListView = clipList,
            menuPanelView = menuPanel,
            menuListView = menuList,
            appPrefs = appPrefs,
            isFullFlavor = isFullFlavor,
            onOpenSettings = {
                val intent = Intent(this, SettingsActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            },
            onThemeChanged = { setInputView(onCreateInputView()) },
            currentPackageProvider = { qwertyKeyboard?.currentPackage ?: "unknown" },
            onAutocorrectChanged = {
                // Refresh the cached flag and re-render so the spacebar keycap
                // ("space" vs "SPACE") reflects the new setting immediately.
                qwertyKeyboard?.refreshAutocorrect()
                qwertyKeyboard?.let { it.setLayer(it.currentLayer) }
            }
        )
        extraRowManager = erm

        NumberRowManager(view, keySender, { currentInputConnection }, tm)

        val keyboardFrame = view.findViewById<android.widget.FrameLayout>(R.id.keyboard_frame)
        val keyPreview = KeyPreview(keyboardFrame, tm)

        val container = view.findViewById<LinearLayout>(R.id.qwerty_container)
        val registry = slashCommandRegistry
        val slashPopup = if (registry != null) {
            SlashCommandPopup(
                registry = registry,
                onCommandSelected = { command ->
                    val ic = currentInputConnection ?: return@SlashCommandPopup
                    keySender.sendText(ic, command)
                },
                onDismissedEmpty = {
                    val ic = currentInputConnection ?: return@SlashCommandPopup
                    keySender.sendText(ic, "/")
                },
                themeManager = tm
            )
        } else null

        val qwerty = QwertyKeyboard(container, keySender, erm, { currentInputConnection }, appPrefs, slashPopup, tm, keyPreview)
        qwerty.setLayer(KeyboardLayouts.LAYER_LOWER)
        qwertyKeyboard = qwerty

        erm.onQuickKeyChanged = { _ ->
            qwerty.setLayer(qwerty.currentLayer)
        }

        // Bottom padding: user-configurable via menu slider (default 0)
        val density = resources.displayMetrics.density
        fun applyBottomPadding() {
            val extraPad = (appPrefs.getBottomPadding() * density + 0.5f).toInt()
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, extraPad)
        }
        applyBottomPadding()
        erm.onBottomPaddingChanged = { applyBottomPadding() }

        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)

        // If the theme was changed from settings while this view was cached,
        // rebuild the input view so the new theme applies on this open (the
        // settings screen tells the user it applies on the next keyboard open).
        if (themeManager.isThemeStale()) {
            setInputView(onCreateInputView())
        }

        // Pass EditorInfo to keyboard before updatePackage triggers render
        val imeAction = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        qwertyKeyboard?.updateImeAction(imeAction, info?.imeOptions ?: 0)
        qwertyKeyboard?.updateInputType(info?.inputType ?: 0)
        qwertyKeyboard?.resetTransientState()

        val packageName = info?.packageName ?: "unknown"
        qwertyKeyboard?.updatePackage(packageName)
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        // Close overlay panels when the keyboard is hidden by the system.
        // Terminal apps (Termius, Termux) aggressively hide the IME when focus
        // shifts to the picker overlay. Closing panels here ensures the keyboard
        // returns to a clean state when re-shown.
        extraRowManager?.dismissPanels()
    }

    override fun onDestroy() {
        voiceInputHandler?.destroy()
        uploadHandler?.destroy()
        clipboardHistoryManager?.destroy()
        pendingUploadHandler = null
        super.onDestroy()
    }

    private fun launchPhotoPicker() {
        try {
            val pickerClass = Class.forName("com.keyjawn.PickerActivity")
            val intent = Intent(this, pickerClass)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (_: ClassNotFoundException) {
            // lite flavor — PickerActivity not available
        }
    }
}
