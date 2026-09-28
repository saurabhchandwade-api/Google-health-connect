package com.example.fitnessdashboard.domain.model

import java.time.Instant

data class FitnessDashboardData(
    val range: FitnessDateRange,
    val summary: FitnessSummary,
    val days: List<FitnessDayData>,
    val generatedAt: Instant = Instant.now(),
    val providerName: String = "GoogleFit"
)
