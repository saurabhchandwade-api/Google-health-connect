package com.example.fitnessdashboard.domain.model

data class FitnessSummary(
    val steps: Int?,
    val caloriesKcal: Double?,
    val distanceMeters: Double?,
    val heartPoints: Double?,
    val moveMinutes: Int?,
    val sleepDurationMinutes: Int?
)
