package com.keyjawn

import android.app.Activity
import android.content.Context

class BillingManager(context: Context) {
    val isFullVersion = false
    var onPurchaseStateChanged: (() -> Unit)? = null

    fun connect() {}
    fun launchPurchaseFlow(activity: Activity) {}
    fun destroy() {}
}
