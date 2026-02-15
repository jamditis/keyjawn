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
        slashCommandRegistry = SlashCommandRegistry(this)
    }

    override fun onCreateInputView(): View {
        val view = LayoutInflater.from(this).inflate(R.layout.keyboard_view, null)
        val voice = VoiceInputHandler(this)
        voiceInputHandler = voice

        val upload = UploadHandler(this)
        upload.setInputConnectionProvider { currentInputConnection }
        uploadHandler = upload
        pendingUploadHandler = upload

        val clipManager = ClipboardHistoryManager(this)
        clipboardHistoryManager = clipManager

        val erm = ExtraRowManager(
            view, keySender, { currentInputConnection }, voice,
            uploadHandler = upload,
            onUploadTap = { launchPhotoPicker() },
            clipboardHistoryManager = clipManager
        )
        extraRowManager = erm

        NumberRowManager(view, keySender, { currentInputConnection })

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

        val qwerty = QwertyKeyboard(container, keySender, erm, { currentInputConnection }, appPrefs, slashPopup)
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
        pendingUploadHandler = null
        super.onDestroy()
    }

    private fun launchPhotoPicker() {
        val handler = uploadHandler ?: return
        val intent = handler.createPickerIntent() ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
