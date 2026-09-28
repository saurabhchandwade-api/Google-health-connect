package com.example.fitnessdashboard.util

import android.util.Log

object Logger {
    private const val TAG = "FitnessDashboard"
    var isDebug: Boolean = true

    fun d(message: String) {
        if (isDebug) {
            Log.d(TAG, message)
        }
    }

    fun i(message: String) {
        if (isDebug) {
            Log.i(TAG, message)
        }
    }

    fun w(message: String, throwable: Throwable? = null) {
        if (isDebug) {
            Log.w(TAG, message, throwable)
        }
    }

    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }

    /**
     * Privacy safeguard: Redacts sensitive health values in logs.
     */
    fun logSanitizedPayload(action: String, rangeType: String, status: String) {
        if (isDebug) {
            Log.d(TAG, "[$action] range=$rangeType, status=$status")
        }
    }
}
