package com.example.fitnessdashboard.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.fitnessdashboard.domain.model.PermissionStatus

class FitnessPermissionManager(private val context: Context) {

    fun checkActivityRecognitionPermission(): PermissionStatus {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val result = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACTIVITY_RECOGNITION
            )
            if (result == PackageManager.PERMISSION_GRANTED) {
                PermissionStatus.GRANTED
            } else {
                PermissionStatus.NOT_GRANTED
            }
        } else {
            PermissionStatus.GRANTED
        }
    }

    companion object {
        fun getRequiredPermissions(): Array<String> {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(Manifest.permission.ACTIVITY_RECOGNITION)
            } else {
                emptyArray()
            }
        }
    }
}
