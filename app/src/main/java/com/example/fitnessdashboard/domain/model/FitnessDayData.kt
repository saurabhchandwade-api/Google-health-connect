package com.example.fitnessdashboard.domain.model

import java.time.LocalDate

data class FitnessDayData(
    val date: LocalDate,
    val startTime: String,
    val endTime: String,
    val steps: MetricValue<Int>,
    val caloriesKcal: MetricValue<Double>,
    val distanceMeters: MetricValue<Double>,
    val heartPoints: MetricValue<Double>,
    val moveMinutes: MetricValue<Int>,
    val sleepDurationMinutes: MetricValue<Int>
)
