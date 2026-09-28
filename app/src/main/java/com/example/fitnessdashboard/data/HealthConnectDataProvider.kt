package com.example.fitnessdashboard.data

import android.app.Activity
import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
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
import com.example.fitnessdashboard.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

class HealthConnectDataProvider(
    private val context: Context
) : FitnessDataProvider {

    override val name: String = "HealthConnect"

    private val healthConnectClient by lazy {
        if (HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE) {
            HealthConnectClient.getOrCreate(context)
        } else {
            null
        }
    }

    val requiredPermissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class)
    )

    override suspend fun checkAvailability(): ProviderAvailability = withContext(Dispatchers.IO) {
        val status = HealthConnectClient.getSdkStatus(context)
        when (status) {
            HealthConnectClient.SDK_AVAILABLE -> ProviderAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> ProviderAvailability.NOT_INSTALLED
            else -> ProviderAvailability.NOT_SUPPORTED
        }
    }

    override suspend fun checkPermissions(): PermissionStatus = withContext(Dispatchers.IO) {
        val client = healthConnectClient ?: return@withContext PermissionStatus.NOT_GRANTED
        try {
            val granted = client.permissionController.getGrantedPermissions()
            if (granted.containsAll(requiredPermissions)) {
                PermissionStatus.GRANTED
            } else {
                PermissionStatus.NOT_GRANTED
            }
        } catch (e: Exception) {
            Logger.e("Error checking Health Connect permissions", e)
            PermissionStatus.NOT_GRANTED
        }
    }

    override suspend fun requestPermissions(activity: Activity): PermissionResult = withContext(Dispatchers.Main) {
        PermissionResult(
            isGranted = false,
            message = "Health Connect permissions must be requested using HealthConnect Permission Contract."
        )
    }

    override suspend fun readFitnessData(range: FitnessDateRange): FitnessDashboardData = withContext(Dispatchers.IO) {
        val client = healthConnectClient
        if (client == null) {
            return@withContext createUnavailabilityData(range, "Health Connect SDK unavailable on this device.")
        }

        try {
            val dailyBounds = DateRangeUtils.getDailyBoundsForRange(range)
            val dayDataList = mutableListOf<FitnessDayData>()

            for ((dayStart, dayEnd) in dailyBounds) {
                val filter = TimeRangeFilter.between(dayStart.toInstant(), dayEnd.toInstant())

                // 1. Steps
                val stepsResult = try {
                    val records = client.readRecords(ReadRecordsRequest(StepsRecord::class, filter)).records
                    val total = records.sumOf { it.count }
                    MetricValue(total.toInt(), FitnessMetricStatus.AVAILABLE)
                } catch (e: Exception) {
                    MetricValue<Int>(null, FitnessMetricStatus.ERROR, e.localizedMessage)
                }

                // 2. Calories
                val caloriesResult = try {
                    val records = client.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, filter)).records
                    val total = records.sumOf { it.energy.inKilocalories }
                    MetricValue(total, FitnessMetricStatus.AVAILABLE)
                } catch (e: Exception) {
                    MetricValue<Double>(null, FitnessMetricStatus.ERROR, e.localizedMessage)
                }

                // 3. Distance
                val distanceResult = try {
                    val records = client.readRecords(ReadRecordsRequest(DistanceRecord::class, filter)).records
                    val total = records.sumOf { it.distance.inMeters }
                    MetricValue(total, FitnessMetricStatus.AVAILABLE)
                } catch (e: Exception) {
                    MetricValue<Double>(null, FitnessMetricStatus.ERROR, e.localizedMessage)
                }

                // 4. Move minutes (Exercise session duration)
                val moveMinutesResult = try {
                    val records = client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, filter)).records
                    val totalMinutes = records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                    MetricValue(totalMinutes.toInt(), FitnessMetricStatus.AVAILABLE)
                } catch (e: Exception) {
                    MetricValue<Int>(null, FitnessMetricStatus.UNSUPPORTED, "Move minutes/exercise read error")
                }

                // 5. Sleep duration
                val sleepResult = try {
                    val records = client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, filter)).records
                    val totalSleepMinutes = records.sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                    MetricValue(totalSleepMinutes.toInt(), FitnessMetricStatus.AVAILABLE)
                } catch (e: Exception) {
                    MetricValue<Int>(null, FitnessMetricStatus.ERROR, e.localizedMessage)
                }

                // 6. Heart points / Heart rate (Heart Points is Google Fit specific; in Health Connect return average HR or UNSUPPORTED)
                val heartPointsResult = MetricValue<Double>(
                    null,
                    FitnessMetricStatus.UNSUPPORTED,
                    "Heart Points metric is specific to Google Fit."
                )

                dayDataList.add(
                    FitnessDayData(
                        date = dayStart.toLocalDate(),
                        startTime = DateRangeUtils.formatIsoDateTime(dayStart),
                        endTime = DateRangeUtils.formatIsoDateTime(dayEnd),
                        steps = stepsResult,
                        caloriesKcal = caloriesResult,
                        distanceMeters = distanceResult,
                        heartPoints = heartPointsResult,
                        moveMinutes = moveMinutesResult,
                        sleepDurationMinutes = sleepResult
                    )
                )
            }

            val summarySteps = dayDataList.mapNotNull { it.steps.value }.takeIf { it.isNotEmpty() }?.sum()
            val summaryCalories = dayDataList.mapNotNull { it.caloriesKcal.value }.takeIf { it.isNotEmpty() }?.sum()
            val summaryDistance = dayDataList.mapNotNull { it.distanceMeters.value }.takeIf { it.isNotEmpty() }?.sum()
            val summaryHeartPoints = dayDataList.mapNotNull { it.heartPoints.value }.takeIf { it.isNotEmpty() }?.sum()
            val summaryMoveMinutes = dayDataList.mapNotNull { it.moveMinutes.value }.takeIf { it.isNotEmpty() }?.sum()
            val summarySleepMinutes = dayDataList.mapNotNull { it.sleepDurationMinutes.value }.takeIf { it.isNotEmpty() }?.sum()

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
        } catch (e: Exception) {
            Logger.e("Error reading Health Connect data", e)
            createUnavailabilityData(range, "Health Connect error: ${e.localizedMessage}")
        }
    }

    override suspend fun subscribeToRecording(): SubscriptionResult = withContext(Dispatchers.IO) {
        SubscriptionResult(
            isSubscribed = false,
            message = "Background recording subscriptions are managed automatically by Health Connect system sync."
        )
    }

    override suspend fun unsubscribeFromRecording(): SubscriptionResult = withContext(Dispatchers.IO) {
        SubscriptionResult(
            isSubscribed = false,
            message = "Background recording subscriptions are managed automatically by Health Connect system sync."
        )
    }

    private fun createUnavailabilityData(range: FitnessDateRange, message: String): FitnessDashboardData {
        val dailyBounds = DateRangeUtils.getDailyBoundsForRange(range)
        val days = dailyBounds.map { (start, end) ->
            FitnessDayData(
                date = start.toLocalDate(),
                startTime = DateRangeUtils.formatIsoDateTime(start),
                endTime = DateRangeUtils.formatIsoDateTime(end),
                steps = MetricValue(null, FitnessMetricStatus.PROVIDER_UNAVAILABLE, message),
                caloriesKcal = MetricValue(null, FitnessMetricStatus.PROVIDER_UNAVAILABLE, message),
                distanceMeters = MetricValue(null, FitnessMetricStatus.PROVIDER_UNAVAILABLE, message),
                heartPoints = MetricValue(null, FitnessMetricStatus.UNSUPPORTED, message),
                moveMinutes = MetricValue(null, FitnessMetricStatus.PROVIDER_UNAVAILABLE, message),
                sleepDurationMinutes = MetricValue(null, FitnessMetricStatus.PROVIDER_UNAVAILABLE, message)
            )
        }
        return FitnessDashboardData(
            range = range,
            summary = FitnessSummary(null, null, null, null, null, null),
            days = days,
            providerName = name
        )
    }
}
