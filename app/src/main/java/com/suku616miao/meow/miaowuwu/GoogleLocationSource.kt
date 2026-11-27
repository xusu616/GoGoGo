package com.suku616miao.meow.miaowuwu

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.suku616miao.meow.miaowuwu.logging.AppLogger
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/** Some ROMs expose Google fusion directly while their framework network provider has no fixes. */
internal class GoogleLocationSource(context: Context, private val onLocation: (Location) -> Unit) {
    private val appContext = context.applicationContext
    private val client by lazy { LocationServices.getFusedLocationProviderClient(appContext) }
    private var callback: LocationCallback? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (callback != null) return
        val availability = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(appContext)
        if (availability != ConnectionResult.SUCCESS) {
            // Devices without usable Play services continue using the framework providers.
            AppLogger.i(TAG, "Google fusion unavailable: $availability")
            return
        }
        val newCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (callback !== this) return
                result.locations.forEach(onLocation)
            }
        }
        callback = newCallback
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(1000L)
            .setMaxUpdateAgeMillis(0L)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            client.requestLocationUpdates(request, newCallback, Looper.getMainLooper())
                .addOnSuccessListener {
                    if (callback !== newCallback) client.removeLocationUpdates(newCallback)
                    else AppLogger.i(TAG, "Google fused location request registered")
                }
                .addOnFailureListener { error ->
                    if (callback === newCallback) callback = null
                    AppLogger.w(TAG, "Google fused request failed: ${error.javaClass.simpleName}")
                }
        } catch (error: SecurityException) {
            callback = null
            AppLogger.w(TAG, "Location permission changed during Google registration")
        }
    }

    fun stop() {
        val previous = callback ?: return
        callback = null
        client.removeLocationUpdates(previous)
            .addOnFailureListener { error -> AppLogger.w(TAG, "Google request removal failed: ${error.javaClass.simpleName}") }
    }

    private companion object {
        const val TAG = "GoGoogleLocation"
    }
}
