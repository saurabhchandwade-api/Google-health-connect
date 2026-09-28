package com.example.fitnessdashboard.domain.model

import java.time.Instant
import java.time.ZoneId

enum class RangeType {
    TODAY,
    WEEK,
    MONTH,
    CUSTOM
}

data class FitnessDateRange(
    val start: Instant,
    val end: Instant,
    val type: RangeType,
    val zoneId: ZoneId = ZoneId.systemDefault()
)
