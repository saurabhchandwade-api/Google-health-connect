package com.example.fitnessdashboard.domain.model

data class PermissionResult(
    val isGranted: Boolean,
    val deniedPermissions: List<String> = emptyList(),
    val message: String? = null
)
