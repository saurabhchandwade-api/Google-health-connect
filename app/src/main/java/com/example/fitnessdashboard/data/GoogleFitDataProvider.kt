package com.example.fitnessdashboard.data

import android.app.Activity
import android.content.Context
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.Bucket
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.concurrent.TimeUnit

class GoogleFitDataProvider(
    private val context: Context
) : FitnessDataProvider {

    override val name: String = "GoogleFit"

    private val fitnessOptions: FitnessOptions by lazy {
        FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_HEART_POINTS, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_HEART_POINTS, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_MOVE_MINUTES, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.AGGREGATE_MOVE_MINUTES, FitnessOptions.ACCESS_READ)
            .addDataType(DataType.TYPE_SLEEP_SEGMENT, FitnessOptions.ACCESS_READ)
            .build()
    }

    override suspend fun checkAvailability(): ProviderAvailability = withContext(Dispatchers.IO) {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
        if (resultCode == ConnectionResult.SUCCESS) {
            ProviderAvailability.AVAILABLE
        } else {
            ProviderAvailability.NOT_SUPPORTED
        }
    }

    override suspend fun checkPermissions(): PermissionStatus = withContext(Dispatchers.IO) {
        val account = getAccount()
        if (account == null) {
            PermissionStatus.NOT_GRANTED
        } else {
            if (GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                PermissionStatus.GRANTED
            } else {
                PermissionStatus.NOT_GRANTED
            }
        }
    }

    override suspend fun requestPermissions(activity: Activity): PermissionResult = withContext(Dispatchers.Main) {
        val account = getAccount()
        if (account != null && GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            PermissionResult(isGranted = true)
        } else {
            GoogleSignIn.requestPermissions(
                activity,
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                account ?: GoogleSignIn.getLastSignedInAccount(context),
                fitnessOptions
            )
            PermissionResult(
                isGranted = false,
                message = "Google Fit permissions requested via Google Account sign-in dialog."
            )
        }
    }

    override suspend fun readFitnessData(range: FitnessDateRange): FitnessDashboardData = withContext(Dispatchers.IO) {
        val account = getAccount()
        if (account == null || !GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            Logger.w("Google Fit permissions missing or account null")
            return@withContext createUnauthorisedDashboardData(range, "Google account authorization required.")
        }

        try {
            val readRequest = DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .aggregate(DataType.TYPE_CALORIES_EXPENDED, DataType.AGGREGATE_CALORIES_EXPENDED)
                .aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA)
                .aggregate(DataType.TYPE_HEART_POINTS, DataType.AGGREGATE_HEART_POINTS)
                .aggregate(DataType.TYPE_MOVE_MINUTES, DataType.AGGREGATE_MOVE_MINUTES)
                .read(DataType.TYPE_SLEEP_SEGMENT)
                .bucketByTime(1, TimeUnit.DAYS)
                .setTimeRange(range.start.toEpochMilli(), range.end.toEpochMilli(), TimeUnit.MILLISECONDS)
                .build()

            val response = Fitness.getHistoryClient(context, account)
                .readData(readRequest)
                .await()

            val dailyBounds = DateRangeUtils.getDailyBoundsForRange(range)
            val buckets = response.buckets
            val dayDataList = mutableListOf<FitnessDayData>()

            dailyBounds.forEachIndexed { index, (dayStart, dayEnd) ->
                val bucket = buckets.getOrNull(index)

                var steps: Int? = null
                var calories: Double? = null
                var distance: Double? = null
                var heartPoints: Double? = null
                var moveMinutes: Int? = null
                var sleepMinutes: Int? = null

                if (bucket != null) {
                    bucket.dataSets.forEach { dataSet ->
                        dataSet.dataPoints.forEach { dp ->
                            when (dp.dataType) {
                                DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA -> {
                                    val valInt = dp.getValue(Field.FIELD_STEPS).asInt()
                                    steps = (steps ?: 0) + valInt
                                }
                                DataType.TYPE_CALORIES_EXPENDED, DataType.AGGREGATE_CALORIES_EXPENDED -> {
                                    val valFloat = dp.getValue(Field.FIELD_CALORIES).asFloat().toDouble()
                                    calories = (calories ?: 0.0) + valFloat
                                }
                                DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA -> {
                                    val valFloat = dp.getValue(Field.FIELD_DISTANCE).asFloat().toDouble()
                                    distance = (distance ?: 0.0) + valFloat
                                }
                                DataType.TYPE_HEART_POINTS, DataType.AGGREGATE_HEART_POINTS -> {
                                    val valFloat = dp.getValue(Field.FIELD_INTENSITY).asFloat().toDouble()
                                    heartPoints = (heartPoints ?: 0.0) + valFloat
                                }
                                DataType.TYPE_MOVE_MINUTES, DataType.AGGREGATE_MOVE_MINUTES -> {
                                    val valInt = dp.getValue(Field.FIELD_DURATION).asInt()
                                    moveMinutes = (moveMinutes ?: 0) + valInt
                                }
                                DataType.TYPE_SLEEP_SEGMENT -> {
                                    val duration = dp.getEndTime(TimeUnit.MINUTES) - dp.getStartTime(TimeUnit.MINUTES)
                                    if (duration > 0) {
                                        sleepMinutes = (sleepMinutes ?: 0) + duration.toInt()
                                    }
                                }
                            }
                        }
                    }
                }

                val dayData = FitnessDayData(
                    date = dayStart.toLocalDate(),
                    startTime = DateRangeUtils.formatIsoDateTime(dayStart),
                    endTime = DateRangeUtils.formatIsoDateTime(dayEnd),
                    steps = createMetricValue(steps),
                    caloriesKcal = createMetricValue(calories),
                    distanceMeters = createMetricValue(distance),
                    heartPoints = createMetricValue(heartPoints),
                    moveMinutes = createMetricValue(moveMinutes),
                    sleepDurationMinutes = createMetricValue(sleepMinutes)
                )
                dayDataList.add(dayData)
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
            Logger.e("Error reading Google Fit data", e)
            createErrorDashboardData(range, "Google Fit API read failed: ${e.localizedMessage}")
        }
    }

    override suspend fun subscribeToRecording(): SubscriptionResult = withContext(Dispatchers.IO) {
        val account = getAccount()
        if (account == null || !GoogleSignIn.hasPermissions(account, fitnessOptions)) {
            return@withContext SubscriptionResult(
                isSubscribed = false,
                message = "Cannot subscribe: Google Account not signed in or permissions missing."
            )
        }
        try {
            Fitness.getRecordingClient(context, account)
                .subscribe(DataType.TYPE_STEP_COUNT_DELTA)
                .await()
            SubscriptionResult(
                isSubscribed = true,
                message = "Step count recording subscription active."
            )
        } catch (e: Exception) {
            Logger.e("Failed to subscribe to Google Fit recording", e)
            SubscriptionResult(
                isSubscribed = false,
                message = "Subscription failed: ${e.localizedMessage}"
            )
        }
    }

    override suspend fun unsubscribeFromRecording(): SubscriptionResult = withContext(Dispatchers.IO) {
        val account = getAccount()
        if (account == null) {
            return@withContext SubscriptionResult(
                isSubscribed = false,
                message = "Account not available."
            )
        }
        try {
            Fitness.getRecordingClient(context, account)
                .unsubscribe(DataType.TYPE_STEP_COUNT_DELTA)
                .await()
            SubscriptionResult(
                isSubscribed = false,
                message = "Step count recording subscription removed."
            )
        } catch (e: Exception) {
            SubscriptionResult(
                isSubscribed = false,
                message = "Unsubscribe failed: ${e.localizedMessage}"
            )
        }
    }

    private fun getAccount(): GoogleSignInAccount? {
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    private fun <T> createMetricValue(value: T?): MetricValue<T> {
        return if (value != null) {
            MetricValue(value, FitnessMetricStatus.AVAILABLE)
        } else {
            MetricValue(null, FitnessMetricStatus.NO_DATA)
        }
    }

    private fun createUnauthorisedDashboardData(range: FitnessDateRange, message: String): FitnessDashboardData {
        val dailyBounds = DateRangeUtils.getDailyBoundsForRange(range)
        val days = dailyBounds.map { (start, end) ->
            FitnessDayData(
                date = start.toLocalDate(),
                startTime = DateRangeUtils.formatIsoDateTime(start),
                endTime = DateRangeUtils.formatIsoDateTime(end),
                steps = MetricValue(null, FitnessMetricStatus.PERMISSION_DENIED, message),
                caloriesKcal = MetricValue(null, FitnessMetricStatus.PERMISSION_DENIED, message),
                distanceMeters = MetricValue(null, FitnessMetricStatus.PERMISSION_DENIED, message),
                heartPoints = MetricValue(null, FitnessMetricStatus.PERMISSION_DENIED, message),
                moveMinutes = MetricValue(null, FitnessMetricStatus.PERMISSION_DENIED, message),
                sleepDurationMinutes = MetricValue(null, FitnessMetricStatus.PERMISSION_DENIED, message)
            )
        }
        return FitnessDashboardData(
            range = range,
            summary = FitnessSummary(null, null, null, null, null, null),
            days = days,
            providerName = name
        )
    }

    private fun createErrorDashboardData(range: FitnessDateRange, message: String): FitnessDashboardData {
        val dailyBounds = DateRangeUtils.getDailyBoundsForRange(range)
        val days = dailyBounds.map { (start, end) ->
            FitnessDayData(
                date = start.toLocalDate(),
                startTime = DateRangeUtils.formatIsoDateTime(start),
                endTime = DateRangeUtils.formatIsoDateTime(end),
                steps = MetricValue(null, FitnessMetricStatus.ERROR, message),
                caloriesKcal = MetricValue(null, FitnessMetricStatus.ERROR, message),
                distanceMeters = MetricValue(null, FitnessMetricStatus.ERROR, message),
                heartPoints = MetricValue(null, FitnessMetricStatus.ERROR, message),
                moveMinutes = MetricValue(null, FitnessMetricStatus.ERROR, message),
                sleepDurationMinutes = MetricValue(null, FitnessMetricStatus.ERROR, message)
            )
        }
        return FitnessDashboardData(
            range = range,
            summary = FitnessSummary(null, null, null, null, null, null),
            days = days,
            providerName = name
        )
    }

    companion object {
        const val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1001
    }
}
