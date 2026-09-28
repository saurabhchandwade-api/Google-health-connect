package com.example.fitnessdashboard.data

import android.app.Activity
import android.content.Context
import com.example.fitnessdashboard.domain.model.FitnessDashboardData
import com.example.fitnessdashboard.domain.model.FitnessDateRange
import com.example.fitnessdashboard.domain.model.PermissionResult
import com.example.fitnessdashboard.domain.model.PermissionStatus
import com.example.fitnessdashboard.domain.model.ProviderAvailability
import com.example.fitnessdashboard.domain.model.SubscriptionResult
import com.example.fitnessdashboard.util.Logger

class FitnessRepository(
    private val context: Context,
    val googleFitProvider: GoogleFitDataProvider = GoogleFitDataProvider(context),
    val healthConnectProvider: HealthConnectDataProvider = HealthConnectDataProvider(context),
    val fakeProvider: FakeFitnessDataProvider = FakeFitnessDataProvider()
) {

    private var activeProviderName: String = "GoogleFit"

    fun getActiveProvider(): FitnessDataProvider {
        return when (activeProviderName.lowercase()) {
            "healthconnect", "health_connect" -> healthConnectProvider
            "demo", "demoprovider", "fake" -> fakeProvider
            else -> googleFitProvider
        }
    }

    fun selectProvider(providerName: String): String {
        activeProviderName = providerName
        Logger.i("Selected fitness data provider: $activeProviderName")
        return getActiveProvider().name
    }

    suspend fun checkAvailability(): ProviderAvailability {
        val provider = getActiveProvider()
        val availability = provider.checkAvailability()
        if (availability != ProviderAvailability.AVAILABLE && provider is GoogleFitDataProvider) {
            // Check if Health Connect is available as automatic fallback
            val hcAvailability = healthConnectProvider.checkAvailability()
            if (hcAvailability == ProviderAvailability.AVAILABLE) {
                Logger.i("Google Fit unavailable. Automatically falling back to Health Connect.")
                activeProviderName = "HealthConnect"
                return hcAvailability
            }
        }
        return availability
    }

    suspend fun checkPermissions(): PermissionStatus {
        return getActiveProvider().checkPermissions()
    }

    suspend fun requestPermissions(activity: Activity): PermissionResult {
        return getActiveProvider().requestPermissions(activity)
    }

    suspend fun readFitnessData(range: FitnessDateRange): FitnessDashboardData {
        return getActiveProvider().readFitnessData(range)
    }

    suspend fun subscribeToRecording(): SubscriptionResult {
        return getActiveProvider().subscribeToRecording()
    }

    suspend fun unsubscribeFromRecording(): SubscriptionResult {
        return getActiveProvider().unsubscribeFromRecording()
    }
}
