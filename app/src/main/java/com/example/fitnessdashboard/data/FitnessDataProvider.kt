package com.example.fitnessdashboard.data

import android.app.Activity
import com.example.fitnessdashboard.domain.model.FitnessDashboardData
import com.example.fitnessdashboard.domain.model.FitnessDateRange
import com.example.fitnessdashboard.domain.model.PermissionResult
import com.example.fitnessdashboard.domain.model.PermissionStatus
import com.example.fitnessdashboard.domain.model.ProviderAvailability
import com.example.fitnessdashboard.domain.model.SubscriptionResult

interface FitnessDataProvider {
    val name: String
    suspend fun checkAvailability(): ProviderAvailability
    suspend fun checkPermissions(): PermissionStatus
    suspend fun requestPermissions(activity: Activity): PermissionResult
    suspend fun readFitnessData(range: FitnessDateRange): FitnessDashboardData
    suspend fun subscribeToRecording(): SubscriptionResult
    suspend fun unsubscribeFromRecording(): SubscriptionResult
}
