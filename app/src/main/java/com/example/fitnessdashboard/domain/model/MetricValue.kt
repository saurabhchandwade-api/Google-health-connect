package com.example.fitnessdashboard.domain.model

data class MetricValue<T>(
    val value: T?,
    val status: FitnessMetricStatus,
    val errorMessage: String? = null
)
