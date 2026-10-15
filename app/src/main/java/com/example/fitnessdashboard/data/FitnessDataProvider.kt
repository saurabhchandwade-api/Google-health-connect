package com.example.fitnessdashboard.data

import com.example.fitnessdashboard.domain.model.FitnessDashboardData
import com.example.fitnessdashboard.domain.model.FitnessDateRange
import com.example.fitnessdashboard.domain.model.PermissionStatus
import com.example.fitnessdashboard.domain.model.ProviderAvailability

interface FitnessDataProvider {
    val name: String
    suspend fun checkAvailability(): ProviderAvailability
    suspend fun checkPermissions(): PermissionStatus
    suspend fun readFitnessData(range: FitnessDateRange): FitnessDashboardData
}
