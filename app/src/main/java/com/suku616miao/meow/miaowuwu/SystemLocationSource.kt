package com.suku616miao.meow.miaowuwu

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import com.suku616miao.meow.miaowuwu.logging.AppLogger
import androidx.core.content.ContextCompat

/** Foreground real-location acquisition. Simulation positions belong to ServiceGo instead. */
class SystemLocationSource(context: Context, private val listener: Listener) {
    fun interface Listener {
        fun onLocation(location: Location)
    }

    private val appContext = context.applicationContext
    private val locations = appContext.getSystemService(LocationManager::class.java)
    private var active = false
    private lateinit var filter: RealLocationFilter
    private val platformListener = object : LocationListener {
        @Suppress("DEPRECATION")
        override fun onLocationChanged(location: Location) {
            if (!active || !filter.accept(location.isFromMockProvider, location.elapsedRealtimeNanos, location.accuracy)) return
            AppLogger.i(TAG, "Real fix: provider=${location.provider}, accuracy=${location.accuracy}m, mock=false")
            listener.onLocation(Location(location))
        }

        override fun onProviderEnabled(provider: String) {
            AppLogger.i(TAG, "Provider enabled: $provider")
        }

        override fun onProviderDisabled(provider: String) {
            AppLogger.i(TAG, "Provider disabled: $provider")
        }

        // Required on API 27–29, before LocationListener gained default implementations.
        @Deprecated("Provider status is unused")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    private val googleLocations = GoogleLocationSource(appContext, platformListener::onLocationChanged)

    fun hasPermission(): Boolean = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
        hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (active || !hasPermission()) return
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, "fused")
            .filter { it in locations.allProviders }
            .filter { it != LocationManager.GPS_PROVIDER || hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) }
        if (providers.isEmpty()) {
            AppLogger.w(TAG, "No system location provider available")
        }
        filter = RealLocationFilter(SystemClock.elapsedRealtimeNanos())
        active = true
        try {
            // Keep disabled providers registered, so enabling system location resumes updates.
            for (provider in providers) {
                locations.requestLocationUpdates(provider, 1000L, 0f, platformListener, Looper.getMainLooper())
            }
            googleLocations.start()
            AppLogger.i(TAG, "Started foreground providers: ${providers.joinToString()}")
        } catch (error: SecurityException) {
            // Permission can be revoked between the check and registration. Release partial registrations.
            stop()
            AppLogger.w(TAG, "Location permission changed during registration")
        }
    }

    fun stop() {
        if (!active) return
        active = false
        googleLocations.stop()
        locations.removeUpdates(platformListener)
        AppLogger.i(TAG, "Stopped foreground location")
    }

    private companion object {
        const val TAG = "GoSystemLocation"
    }
}

/** Mock and cached readings must never replace a fresh real fix, even across providers. */
internal class RealLocationFilter(private val startedAtNanos: Long) {
    private var lastFixNanos = Long.MIN_VALUE
    private var lastAccuracy = Float.POSITIVE_INFINITY

    fun accept(mock: Boolean, elapsedRealtimeNanos: Long, accuracy: Float): Boolean {
        if (mock || elapsedRealtimeNanos < startedAtNanos || elapsedRealtimeNanos < lastFixNanos) return false
        if (elapsedRealtimeNanos == lastFixNanos && accuracy >= lastAccuracy) return false
        lastFixNanos = elapsedRealtimeNanos
        lastAccuracy = accuracy
        return true
    }
}
