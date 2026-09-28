package com.example.fitnessdashboard.domain.model

data class SubscriptionResult(
    val isSubscribed: Boolean,
    val metric: String = "TYPE_STEP_COUNT_DELTA",
    val message: String
)
