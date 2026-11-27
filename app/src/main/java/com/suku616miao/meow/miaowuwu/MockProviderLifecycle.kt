package com.suku616miao.meow.miaowuwu

import android.app.AppOpsManager
import android.content.Context
import android.location.LocationManager
import android.os.Process
import com.suku616miao.meow.miaowuwu.logging.AppLogger

/** Android test providers can survive abrupt process death; restore them on the next cold start. */
object MockProviderLifecycle {
    @Volatile private var serviceRunning = false

    @JvmStatic fun markServiceRunning(running: Boolean) { serviceRunning = running }

    @JvmStatic fun restoreAfterProcessDeath(context: Context) {
        if (serviceRunning) return
        val appOps = context.getSystemService(AppOpsManager::class.java)
        if (appOps.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), context.packageName) != AppOpsManager.MODE_ALLOWED) return
        val locations = context.getSystemService(LocationManager::class.java)
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                locations.removeTestProvider(provider)
                AppLogger.i("GoProviderLifecycle", "Restored system provider: $provider")
            } catch (error: IllegalArgumentException) {
                // A cold start without previous simulation has no test provider to remove.
                AppLogger.d("GoProviderLifecycle", "No stale test provider: $provider")
            } catch (error: SecurityException) {
                // The user can revoke mock-location access while this OS call is pending.
                AppLogger.w("GoProviderLifecycle", "Mock-provider cleanup permission changed")
            }
        }
    }
}
