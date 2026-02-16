package com.keyjawn

import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.InputConnection
import org.json.JSONArray

class ClipboardHistoryManager(context: Context) {

    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val prefs = context.getSharedPreferences("keyjawn_clipboard", Context.MODE_PRIVATE)
    private val history = mutableListOf<String>()
    private val pinned = mutableListOf<String>()
    private val maxHistory = 30
    private val maxPinned = 15

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        val clip = clipboard.primaryClip ?: return@OnPrimaryClipChangedListener
        if (clip.itemCount == 0) return@OnPrimaryClipChangedListener
        val text = clip.getItemAt(0).text?.toString() ?: return@OnPrimaryClipChangedListener
        if (text.isBlank()) return@OnPrimaryClipChangedListener
        addToHistory(text)
    }

    init {
        loadPinned()
        val current = clipboard.primaryClip
        if (current != null && current.itemCount > 0) {
            val text = current.getItemAt(0).text?.toString()
            if (!text.isNullOrBlank() && !isPinned(text)) {
                history.add(text)
            }
        }
        clipboard.addPrimaryClipChangedListener(clipListener)
    }

    fun addToHistory(text: String) {
        if (isPinned(text)) return
        history.remove(text)
        history.add(0, text)
        if (history.size > maxHistory) {
            history.removeAt(history.lastIndex)
        }
    }

    fun getHistory(): List<String> = history.toList()

    fun getPinned(): List<String> = pinned.toList()

    fun isPinned(text: String): Boolean = pinned.contains(text)

    fun pin(text: String): Boolean {
        if (isPinned(text)) return false
        if (pinned.size >= maxPinned) return false
        history.remove(text)
        pinned.add(0, text)
        savePinned()
        return true
    }

    fun unpin(text: String): Boolean {
        if (!isPinned(text)) return false
        pinned.remove(text)
        savePinned()
        history.add(0, text)
        if (history.size > maxHistory) {
            history.removeAt(history.lastIndex)
        }
        return true
    }

    fun getMostRecent(): String? = pinned.firstOrNull() ?: history.firstOrNull()

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

    private fun savePinned() {
        val arr = JSONArray()
        for (item in pinned) arr.put(item)
        prefs.edit().putString("pinned_items", arr.toString()).apply()
    }

    private fun loadPinned() {
        val json = prefs.getString("pinned_items", null) ?: return
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val text = arr.getString(i)
                if (text.isNotBlank() && pinned.size < maxPinned) {
                    pinned.add(text)
                }
            }
        } catch (_: Exception) {
            // Corrupted prefs, start fresh
        }
    }
}
