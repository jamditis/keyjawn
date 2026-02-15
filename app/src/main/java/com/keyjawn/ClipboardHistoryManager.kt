package com.keyjawn

import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.InputConnection

class ClipboardHistoryManager(context: Context) {

    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val history = mutableListOf<String>()
    private val maxSize = 10

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val clip = clipboard.primaryClip ?: return@OnPrimaryClipChangedListener
        if (clip.itemCount == 0) return@OnPrimaryClipChangedListener
        val text = clip.getItemAt(0).text?.toString() ?: return@OnPrimaryClipChangedListener
        if (text.isBlank()) return@OnPrimaryClipChangedListener
        addToHistory(text)
    }

    init {
        // Seed history with whatever is currently on the clipboard
        val current = clipboard.primaryClip
        if (current != null && current.itemCount > 0) {
            val text = current.getItemAt(0).text?.toString()
            if (!text.isNullOrBlank()) {
                history.add(text)
            }
        }
        clipboard.addPrimaryClipChangedListener(clipListener)
    }

    fun addToHistory(text: String) {
        history.remove(text)
        history.add(0, text)
        if (history.size > maxSize) {
            history.removeAt(history.lastIndex)
        }
    }

    fun getHistory(): List<String> = history.toList()

    fun getMostRecent(): String? = history.firstOrNull()

    fun paste(ic: InputConnection): Boolean {
        val text = getMostRecent() ?: return false
        ic.commitText(text, 1)
        return true
    }

    fun pasteItem(ic: InputConnection, text: String) {
        ic.commitText(text, 1)
    }

    fun destroy() {
        clipboard.removePrimaryClipChangedListener(clipListener)
    }
}
