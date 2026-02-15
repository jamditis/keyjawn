package com.keyjawn

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout

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
        if (!billingManager.isFullVersion) {
            themeManager.currentTheme = KeyboardTheme.DARK
        }
        slashCommandRegistry = SlashCommandRegistry(this)
    }

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        val paid = billingManager.isFullVersion
        val tm = themeManager

        // Apply theme colors to layout backgrounds
        view.setBackgroundColor(tm.keyboardBg())
        view.findViewById<View>(R.id.extra_row)?.setBackgroundColor(tm.extraRowBg())
        view.findViewById<View>(R.id.number_row)?.setBackgroundColor(tm.extraRowBg())
        view.findViewById<LinearLayout>(R.id.qwerty_container)?.setBackgroundColor(tm.qwertyBg())

        val voice = if (paid) VoiceInputHandler(this) else null
        voiceInputHandler = voice

        val upload = if (paid) {
            UploadHandler(this).also {
                it.setInputConnectionProvider { currentInputConnection }
                pendingUploadHandler = it
            }
        } else null
        uploadHandler = upload

        val clipManager = if (paid) ClipboardHistoryManager(this) else null
        clipboardHistoryManager = clipManager

        val erm = ExtraRowManager(
            view, keySender, { currentInputConnection }, voice,
            uploadHandler = upload,
            onUploadTap = if (paid) {{ launchPhotoPicker() }} else null,
            clipboardHistoryManager = clipManager,
            themeManager = tm
        )
        extraRowManager = erm

        NumberRowManager(view, keySender, { currentInputConnection }, tm)

        val container = view.findViewById<LinearLayout>(R.id.qwerty_container)
        val registry = slashCommandRegistry
        val slashPopup = if (paid && registry != null) {
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
