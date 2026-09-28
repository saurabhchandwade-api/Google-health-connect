package com.example.fitnessdashboard.domain.model

data class FitnessError(
    val code: FitnessErrorCode,
    val message: String,
    val recoverable: Boolean = true,
    val suggestedAction: String? = null
)
