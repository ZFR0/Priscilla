package com.example.priscilla.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.app.Activity
import androidx.core.app.ActivityCompat

/**
 * A centralized manager to handle checking for permissions required by the app.
 */
class PermissionManager(private val context: Context) {

    /**
     * A list of all permissions required for the "Smart Priscilla" feature to be fully operational.
     */
    val smartPermissions: Array<String>
        get() {
            val permissions = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            // The POST_NOTIFICATIONS permission is only required on Android 13 (API 33) and above.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            return permissions.toTypedArray()
        }


    /**
     * Checks if all permissions required for "Smart Priscilla" have been granted.
     *
     * @return `true` if all necessary permissions are granted, `false` otherwise.
     */
    fun areAllSmartPermissionsGranted(): Boolean {
        // We use .all{} which is a concise way to check if every item in a list meets a condition.
        // It stops and returns false on the first permission that is not granted.
        return smartPermissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Checks if any of the smart permissions have been permanently denied by the user.
     * This is typically true when the user has denied the permission twice.
     *
     * @param activity The activity context is required to check the rationale.
     * @return `true` if at least one permission is in a "Don't ask again" state.
     */
    fun isAnyPermissionPermanentlyDenied(activity: Activity): Boolean {
        return smartPermissions.any { permission ->
            // A permission is permanently denied if:
            // 1. We don't currently have the permission.
            // 2. We are told we shouldn't show a rationale for it.
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) &&
                    ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
}