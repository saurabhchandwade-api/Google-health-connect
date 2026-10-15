package com.example.fitnessdashboard.data

import android.content.Context
import com.example.fitnessdashboard.domain.model.FitnessDashboardData
import com.example.fitnessdashboard.domain.model.FitnessDateRange
import com.example.fitnessdashboard.domain.model.PermissionStatus
import com.example.fitnessdashboard.domain.model.ProviderAvailability

class FitnessRepository(
    private val context: Context,
    val healthConnectProvider: HealthConnectDataProvider = HealthConnectDataProvider(context)
) {

    val providerName: String
        get() = healthConnectProvider.name

    suspend fun checkAvailability(): ProviderAvailability {
        return healthConnectProvider.checkAvailability()
    }

    suspend fun checkPermissions(): PermissionStatus {
        return healthConnectProvider.checkPermissions()
    }

    suspend fun readFitnessData(range: FitnessDateRange): FitnessDashboardData {
        return healthConnectProvider.readFitnessData(range)
    }
}
