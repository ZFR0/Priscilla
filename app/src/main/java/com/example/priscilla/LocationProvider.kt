package com.example.priscilla

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.priscilla.data.InformationProvider
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * A dedicated class to handle fetching the device's GPS location.
 * This encapsulates the complexity of using Android's FusedLocationProviderClient.
 */
class LocationProvider(private val context: Context) {

    private val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    /**
     * Asynchronously gets the current device location.
     * Returns null if permission is not granted or if the location cannot be found.
     */
    suspend fun getUserLocation(): InformationProvider.Coordinates? = withContext(Dispatchers.IO) {
        // Check for EITHER fine or coarse location permission.
        val hasFinePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasCoarsePermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // If we have neither permission, we cannot proceed.
        if (!hasFinePermission && !hasCoarsePermission) {
            Log.w("LocationProvider", "Location permission not granted.")
            return@withContext null
        }

        try {
            // Choose accuracy based on the best permission we have.
            // This respects the user's choice to only grant approximate location.
            val priority = if (hasFinePermission) {
                Priority.PRIORITY_HIGH_ACCURACY
            } else {
                Priority.PRIORITY_BALANCED_POWER_ACCURACY
            }

            // Use the modern coroutine-friendly await() function from the library we added.
            val location = fusedLocationProviderClient.getCurrentLocation(
                priority,
                CancellationTokenSource().token
            ).await() // This suspends until the location is available.

            if (location != null) {
                InformationProvider.Coordinates(lat = location.latitude, lon = location.longitude)
            } else {
                Log.w("LocationProvider", "FusedLocationProviderClient returned null location.")
                null
            }
        } catch (e: SecurityException) {
            // This can happen in rare edge cases even with the permission check.
            Log.e("LocationProvider", "Location permission check failed.", e)
            null
        }
        catch (e: Exception) {
            // This can happen if GPS is turned off or if there's another issue.
            Log.e("LocationProvider", "Failed to get location.", e)
            null
        }
    }
}