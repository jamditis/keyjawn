package com.keyjawn

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UploadHandler(private val context: Context) {
    val isAvailable = true

    private val scpUploader = ScpUploader()
    private var activeHost: HostConfig? = null
    private var inputConnectionProvider: (() -> InputConnection?)? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun setHost(host: HostConfig) {
        activeHost = host
    }

    fun setInputConnectionProvider(provider: () -> InputConnection?) {
        inputConnectionProvider = provider
    }

    fun hasMediaPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun hasHostConfigured(): Boolean = activeHost != null

    fun createPickerIntent(): Intent? {
        if (!hasMediaPermission()) {
            showToast("Image permission required. Opening settings.")
            openAppSettings()
            return null
        }
        if (!hasHostConfigured()) {
            showToast("No host configured. Open KeyJawn settings to add one.")
            return null
        }
        return Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
            type = "image/*"
        }
    }

    fun handlePickerResult(uri: Uri?) {
        if (uri == null) return
        val host = activeHost
        if (host == null) {
            showToast("No host configured. Open KeyJawn settings to add one.")
            return
        }

        val localFile = copyUriToTemp(uri) ?: return

        scope.launch {
            val result = scpUploader.upload(host, localFile)
            withContext(Dispatchers.Main) {
                if (result.success) {
                    showToast("Uploaded -> ${result.remotePath}")
                    inputConnectionProvider?.invoke()?.commitText(result.remotePath, 1)
                } else {
                    showToast("Upload failed: ${result.error}")
                }
                localFile.delete()
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }

    private fun copyUriToTemp(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "upload_temp.png")
            tempFile.outputStream().use { out -> inputStream.copyTo(out) }
            inputStream.close()
            tempFile
        } catch (e: Exception) {
            showToast("Failed to read image")
            null
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
