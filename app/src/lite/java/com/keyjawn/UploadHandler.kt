package com.keyjawn

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.inputmethod.InputConnection

class UploadHandler(private val context: Context) {
    val isAvailable = false

    // No-op in the lite flavor; present for source parity with the full flavor
    // so shared code can set it unconditionally.
    var onShowStatus: ((String) -> Unit)? = null

    fun setHost(host: HostConfig) {}

    fun setInputConnectionProvider(provider: () -> InputConnection?) {}

    fun createPickerIntent(): Intent? = null

    fun handlePickerResult(uri: Uri?) {}

    fun destroy() {}
}
