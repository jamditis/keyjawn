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
    private lateinit var billingManager: BillingManager
    private lateinit var themeManager: ThemeManager
    private var extraRowManager: ExtraRowManager? = null
    private var qwertyKeyboard: QwertyKeyboard? = null
    private var voiceInputHandler: VoiceInputHandler? = null
    private var slashCommandRegistry: SlashCommandRegistry? = null
    private var uploadHandler: UploadHandler? = null
    private var clipboardHistoryManager: ClipboardHistoryManager? = null

    companion object {
        var pendingUploadHandler: UploadHandler? = null
    }

    override fun onCreate() {
        super.onCreate()
        appPrefs = AppPrefs(this)
        billingManager = BillingManager(this)
        billingManager.connect()
        themeManager = ThemeManager(this)
        if (BuildConfig.FLAVOR != "full") {
            themeManager.currentTheme = KeyboardTheme.DARK
        }
        slashCommandRegistry = SlashCommandRegistry(this)
    }

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        val tm = themeManager
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

        val clipManager = ClipboardHistoryManager(this)
        clipboardHistoryManager = clipManager

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
            currentPackageProvider = { qwertyKeyboard?.currentPackage ?: "unknown" }
        )
        extraRowManager = erm

        NumberRowManager(view, keySender, { currentInputConnection }, tm)

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
                }
            )
        } else null

        val qwerty = QwertyKeyboard(container, keySender, erm, { currentInputConnection }, appPrefs, slashPopup, tm)
        qwerty.setLayer(KeyboardLayouts.LAYER_LOWER)
        qwertyKeyboard = qwerty

        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val packageName = info?.packageName ?: "unknown"
        qwertyKeyboard?.updatePackage(packageName)
    }

    override fun onDestroy() {
        voiceInputHandler?.destroy()
        uploadHandler?.destroy()
        clipboardHistoryManager?.destroy()
        billingManager.destroy()
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
