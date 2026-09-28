package com.example.fitnessdashboard.data

import android.app.Activity
import com.example.fitnessdashboard.domain.model.FitnessDashboardData
import com.example.fitnessdashboard.domain.model.FitnessDateRange
import com.example.fitnessdashboard.domain.model.FitnessDayData
import com.example.fitnessdashboard.domain.model.FitnessMetricStatus
import com.example.fitnessdashboard.domain.model.FitnessSummary
import com.example.fitnessdashboard.domain.model.MetricValue
import com.example.fitnessdashboard.domain.model.PermissionResult
import com.example.fitnessdashboard.domain.model.PermissionStatus
import com.example.fitnessdashboard.domain.model.ProviderAvailability
import com.example.fitnessdashboard.domain.model.SubscriptionResult
import com.example.fitnessdashboard.util.DateRangeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.math.roundToInt

class FakeFitnessDataProvider : FitnessDataProvider {

    override val name: String = "DemoProvider"

    var currentPermissionStatus: PermissionStatus = PermissionStatus.GRANTED
    var isRecordingSubscribed: Boolean = false

    override suspend fun checkAvailability(): ProviderAvailability = withContext(Dispatchers.IO) {
        ProviderAvailability.AVAILABLE
    }

    override suspend fun checkPermissions(): PermissionStatus = withContext(Dispatchers.IO) {
        currentPermissionStatus
    }

    override suspend fun requestPermissions(activity: Activity): PermissionResult = withContext(Dispatchers.Main) {
        currentPermissionStatus = PermissionStatus.GRANTED
        PermissionResult(isGranted = true)
    }

    override suspend fun readFitnessData(range: FitnessDateRange): FitnessDashboardData = withContext(Dispatchers.IO) {
        val dailyBounds = DateRangeUtils.getDailyBoundsForRange(range)
        val dayDataList = mutableListOf<FitnessDayData>()

        dailyBounds.forEachIndexed { index, (start, end) ->
            // Seed deterministic pseudo-random values based on day offset for reproducible tests
            val seed = (start.toLocalDate().toEpochDay() % 100).toInt()

            val steps = 7500 + (seed * 85) % 4500
            val calories = 480.0 + (seed * 7.5) % 300.0
            val distance = 5200.0 + (seed * 60.0) % 3500.0
            val heartPoints = 25.0 + (seed * 1.2) % 35.0
            val moveMinutes = 50 + (seed * 3) % 50
            val sleepMinutes = 390 + (seed * 5) % 110

            val dayData = FitnessDayData(
                date = start.toLocalDate(),
                startTime = DateRangeUtils.formatIsoDateTime(start),
                endTime = DateRangeUtils.formatIsoDateTime(end),
                steps = MetricValue(steps, FitnessMetricStatus.AVAILABLE),
                caloriesKcal = MetricValue((calories * 100).roundToInt() / 100.0, FitnessMetricStatus.AVAILABLE),
                distanceMeters = MetricValue((distance * 100).roundToInt() / 100.0, FitnessMetricStatus.AVAILABLE),
                heartPoints = MetricValue((heartPoints * 10).roundToInt() / 10.0, FitnessMetricStatus.AVAILABLE),
                moveMinutes = MetricValue(moveMinutes, FitnessMetricStatus.AVAILABLE),
                sleepDurationMinutes = MetricValue(sleepMinutes, FitnessMetricStatus.AVAILABLE)
            )
            dayDataList.add(dayData)
        }

        val summarySteps = dayDataList.sumOf { it.steps.value ?: 0 }
        val summaryCalories = (dayDataList.sumOf { it.caloriesKcal.value ?: 0.0 } * 100).roundToInt() / 100.0
        val summaryDistance = (dayDataList.sumOf { it.distanceMeters.value ?: 0.0 } * 100).roundToInt() / 100.0
        val summaryHeartPoints = (dayDataList.sumOf { it.heartPoints.value ?: 0.0 } * 10).roundToInt() / 10.0
        val summaryMoveMinutes = dayDataList.sumOf { it.moveMinutes.value ?: 0 }
        val summarySleepMinutes = dayDataList.sumOf { it.sleepDurationMinutes.value ?: 0 }

        FitnessDashboardData(
            range = range,
            summary = FitnessSummary(
                steps = summarySteps,
                caloriesKcal = summaryCalories,
                distanceMeters = summaryDistance,
                heartPoints = summaryHeartPoints,
                moveMinutes = summaryMoveMinutes,
                sleepDurationMinutes = summarySleepMinutes
            ),
            days = dayDataList,
            generatedAt = Instant.now(),
            providerName = name
        )
    }

    override suspend fun subscribeToRecording(): SubscriptionResult = withContext(Dispatchers.IO) {
        isRecordingSubscribed = true
        SubscriptionResult(
            isSubscribed = true,
            message = "Demo step count recording subscription active."
        )
    }

    override suspend fun unsubscribeFromRecording(): SubscriptionResult = withContext(Dispatchers.IO) {
        isRecordingSubscribed = false
        SubscriptionResult(
            isSubscribed = false,
            message = "Demo step count recording subscription removed."
        )
    }
}
