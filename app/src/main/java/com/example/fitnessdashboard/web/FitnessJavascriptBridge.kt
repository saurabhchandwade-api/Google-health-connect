package com.example.fitnessdashboard.web

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.webkit.JavascriptInterface
import com.example.fitnessdashboard.data.FitnessRepository
import com.example.fitnessdashboard.domain.model.FitnessError
import com.example.fitnessdashboard.domain.model.FitnessErrorCode
import com.example.fitnessdashboard.domain.model.PermissionResult
import com.example.fitnessdashboard.domain.model.PermissionStatus
import com.example.fitnessdashboard.util.DateRangeUtils
import com.example.fitnessdashboard.util.JsonUtils
import com.example.fitnessdashboard.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FitnessJavascriptBridge(
    private val activity: Activity,
    private val repository: FitnessRepository,
    private val dispatcher: WebViewCallbackDispatcher,
    private val coroutineScope: CoroutineScope
) {

    @JavascriptInterface
    fun checkProviderAvailability() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val availability = repository.checkAvailability()
                val json = JsonUtils.toJson(availability, repository.getActiveProvider().name)
                dispatcher.dispatchAvailabilityStatus(json)
            } catch (e: Exception) {
                Logger.e("Error checking provider availability", e)
                val error = FitnessError(
                    code = FitnessErrorCode.PROVIDER_UNAVAILABLE,
                    message = "Failed to check provider availability: ${e.localizedMessage}"
                )
                dispatcher.dispatchError(JsonUtils.toJson(error))
            }
        }
    }

    @JavascriptInterface
    fun checkFitnessPermissions() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val status = repository.checkPermissions()
                val isGranted = status == PermissionStatus.GRANTED
                val resultJson = JsonUtils.toJson(
                    PermissionResult(
                        isGranted = isGranted,
                        message = if (isGranted) "Permissions granted." else "Permissions required."
                    )
                )
                dispatcher.dispatchPermissionStatus(resultJson)
            } catch (e: Exception) {
                Logger.e("Error checking fitness permissions", e)
                val error = FitnessError(
                    code = FitnessErrorCode.PERMISSION_DENIED,
                    message = "Error checking permission status: ${e.localizedMessage}"
                )
                dispatcher.dispatchError(JsonUtils.toJson(error))
            }
        }
    }

    @JavascriptInterface
    fun requestFitnessPermissions() {
        coroutineScope.launch(Dispatchers.Main) {
            try {
                val result = repository.requestPermissions(activity)
                dispatcher.dispatchPermissionStatus(JsonUtils.toJson(result))
            } catch (e: Exception) {
                Logger.e("Error requesting fitness permissions", e)
                val error = FitnessError(
                    code = FitnessErrorCode.PERMISSION_DENIED,
                    message = "Error requesting permissions: ${e.localizedMessage}"
                )
                dispatcher.dispatchError(JsonUtils.toJson(error))
            }
        }
    }

    @JavascriptInterface
    fun getFitnessData(requestJson: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val rangeType = JsonUtils.parseRangeType(requestJson)
                val requestedProvider = JsonUtils.parseProviderName(requestJson)
                if (requestedProvider != null) {
                    repository.selectProvider(requestedProvider)
                }

                val dateRange = DateRangeUtils.calculateDateRange(rangeType)
                val data = repository.readFitnessData(dateRange)
                val json = JsonUtils.toJson(data)
                dispatcher.dispatchDataReceived(json)
            } catch (e: Exception) {
                Logger.e("Error getting fitness data", e)
                val error = FitnessError(
                    code = FitnessErrorCode.DATA_READ_FAILED,
                    message = "Failed to read fitness data: ${e.localizedMessage}"
                )
                dispatcher.dispatchError(JsonUtils.toJson(error))
            }
        }
    }

    @JavascriptInterface
    fun subscribeToFitnessRecording() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val result = repository.subscribeToRecording()
                dispatcher.dispatchSubscriptionStatus(JsonUtils.toJson(result))
            } catch (e: Exception) {
                Logger.e("Error subscribing to recording", e)
                val error = FitnessError(
                    code = FitnessErrorCode.RECORDING_SUBSCRIPTION_FAILED,
                    message = "Failed to subscribe to recording: ${e.localizedMessage}"
                )
                dispatcher.dispatchError(JsonUtils.toJson(error))
            }
        }
    }

    @JavascriptInterface
    fun unsubscribeFromFitnessRecording() {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val result = repository.unsubscribeFromRecording()
                dispatcher.dispatchSubscriptionStatus(JsonUtils.toJson(result))
            } catch (e: Exception) {
                Logger.e("Error unsubscribing from recording", e)
                val error = FitnessError(
                    code = FitnessErrorCode.RECORDING_SUBSCRIPTION_FAILED,
                    message = "Failed to unsubscribe: ${e.localizedMessage}"
                )
                dispatcher.dispatchError(JsonUtils.toJson(error))
            }
        }
    }

    @JavascriptInterface
    fun selectProvider(providerName: String) {
        coroutineScope.launch(Dispatchers.IO) {
            val selectedName = repository.selectProvider(providerName)
            checkProviderAvailability()
        }
    }

    @JavascriptInterface
    fun openApplicationSettings() {
        coroutineScope.launch(Dispatchers.Main) {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", activity.packageName, null)
                }
                activity.startActivity(intent)
            } catch (e: Exception) {
                Logger.e("Error opening app settings", e)
            }
        }
    }

    companion object {
        const val BRIDGE_NAME = "AndroidFitnessBridge"
    }
}
