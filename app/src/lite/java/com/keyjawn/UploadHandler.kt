package com.keyjawn

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.inputmethod.InputConnection

class UploadHandler(private val context: Context) {
    val isAvailable = false

    fun setHost(host: HostConfig) {}

    fun setInputConnectionProvider(provider: () -> InputConnection?) {}

    fun createPickerIntent(): Intent? = null

    fun handlePickerResult(uri: Uri?) {}

    fun destroy() {}
}
