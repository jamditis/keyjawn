package com.keyjawn

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class PickerActivity : Activity() {

    companion object {
        private const val REQUEST_PICK_IMAGE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val pickerIntent = KeyJawnService.pendingUploadHandler?.createPickerIntent()
        if (pickerIntent == null) {
            finish()
            return
        }

        @Suppress("DEPRECATION")
        startActivityForResult(pickerIntent, REQUEST_PICK_IMAGE)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK) {
            val uri = data?.data
            KeyJawnService.pendingUploadHandler?.handlePickerResult(uri)
        }
        finish()
    }
}
