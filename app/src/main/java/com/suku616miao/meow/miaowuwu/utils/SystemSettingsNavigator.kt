package com.suku616miao.meow.miaowuwu.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.suku616miao.meow.miaowuwu.logging.AppLogger
import android.widget.Toast
import com.suku616miao.meow.miaowuwu.R

/** Public settings actions let each ROM choose its own settings Activity. */
object SystemSettingsNavigator {
    @JvmStatic fun openDeveloperOptions(context: Context) {
        val openedDeveloperPage = open(context, listOf(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        ), R.string.settings_developer_manual)
        // This flag is useful for guidance, but must not gate a ROM's actual settings entry.
        if (openedDeveloperPage && !GoUtils.isDeveloperOptionsEnabled(context)) {
            showHint(context, R.string.settings_developer_enable)
        }
    }

    @JvmStatic fun openLocation(context: Context) {
        open(context, listOf(
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        ), R.string.settings_location_manual)
    }

    @JvmStatic fun openOverlayPermission(context: Context) {
        val candidates = mutableListOf<Intent>()
        // The per-app data URI is supported by the public contract only before Android 11.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            candidates.add(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context)))
        }
        candidates.add(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        candidates.add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)))
        candidates.add(Intent(Settings.ACTION_SETTINGS))
        val openedOverlayPage = open(context, candidates, R.string.settings_overlay_manual)
        if (openedOverlayPage && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            showHint(context, R.string.settings_overlay_select_app)
        }
    }

    @JvmStatic fun openWifi(context: Context) {
        open(context, listOf(
            Intent(Settings.ACTION_WIFI_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        ), R.string.settings_wifi_manual)
    }

    private fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)

    /** Returns true only for the requested page; fallback pages always explain the manual path. */
    private fun open(context: Context, candidates: List<Intent>, fallbackHint: Int): Boolean {
        for ((index, intent) in candidates.withIndex()) {
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                // Attempt the launch itself: resolveActivity can hide a valid handler under
                // Android 11 package visibility, even when startActivity is allowed.
                context.startActivity(intent)
                if (index > 0) showHint(context, fallbackHint)
                return index == 0
            } catch (error: ActivityNotFoundException) {
                // Some ROMs omit an exported handler for an optional settings action.
                AppLogger.w(TAG, "Settings entry missing: ${intent.action}")
            } catch (error: SecurityException) {
                // A ROM or work-profile administrator can restrict an otherwise present entry.
                AppLogger.w(TAG, "Settings entry restricted: ${intent.action}")
            }
        }
        showHint(context, R.string.settings_entry_unavailable)
        return false
    }

    private fun showHint(context: Context, message: Int) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    private const val TAG = "GoSettingsNavigation"
}
