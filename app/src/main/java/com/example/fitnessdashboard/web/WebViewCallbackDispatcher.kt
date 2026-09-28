package com.example.fitnessdashboard.web

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.example.fitnessdashboard.util.Logger

class WebViewCallbackDispatcher(private val webViewProvider: () -> WebView?) {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun dispatchDataReceived(dataJson: String) {
        evaluateJs("window.onFitnessDataReceived && window.onFitnessDataReceived($dataJson)")
    }

    fun dispatchError(errorJson: String) {
        evaluateJs("window.onFitnessError && window.onFitnessError($errorJson)")
    }

    fun dispatchPermissionStatus(permissionJson: String) {
        evaluateJs("window.onPermissionStatus && window.onPermissionStatus($permissionJson)")
    }

    fun dispatchAvailabilityStatus(availabilityJson: String) {
        evaluateJs("window.onAvailabilityStatus && window.onAvailabilityStatus($availabilityJson)")
    }

    fun dispatchSubscriptionStatus(subscriptionJson: String) {
        evaluateJs("window.onSubscriptionStatus && window.onSubscriptionStatus($subscriptionJson)")
    }

    private fun evaluateJs(script: String) {
        mainHandler.post {
            val webView = webViewProvider()
            if (webView != null) {
                Logger.d("Evaluating JS: ${script.take(80)}...")
                webView.evaluateJavascript(script, null)
            } else {
                Logger.w("Cannot evaluate JS: WebView is null")
            }
        }
    }
}
