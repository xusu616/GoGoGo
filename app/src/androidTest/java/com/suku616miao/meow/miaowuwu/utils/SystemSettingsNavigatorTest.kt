package com.suku616miao.meow.miaowuwu.utils

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Records settings launches without opening or changing any system settings. */
@RunWith(AndroidJUnit4::class)
class SystemSettingsNavigatorTest {
    private class RecordingContext(base: Context, val reject: (Intent) -> Unit = {}) : ContextWrapper(base) {
        val launches = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) {
            launches.add(Intent(intent))
            reject(intent)
        }
    }

    @Test fun missingDeveloperEntryFallsBackToDeviceInformation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = RecordingContext(instrumentation.targetContext) {
            if (it.action == Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) throw ActivityNotFoundException()
        }
        instrumentation.runOnMainSync { SystemSettingsNavigator.openDeveloperOptions(context) }
        assertEquals(listOf(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, Settings.ACTION_DEVICE_INFO_SETTINGS),
            context.launches.map { it.action })
    }

    @Test fun restrictedLocationEntryFallsBackToSystemSettings() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = RecordingContext(instrumentation.targetContext) {
            if (it.action == Settings.ACTION_LOCATION_SOURCE_SETTINGS) throw SecurityException()
        }
        instrumentation.runOnMainSync { SystemSettingsNavigator.openLocation(context) }
        assertEquals(listOf(Settings.ACTION_LOCATION_SOURCE_SETTINGS, Settings.ACTION_SETTINGS),
            context.launches.map { it.action })
    }

    @Test fun nonActivityContextGetsNewTaskFlag() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = RecordingContext(instrumentation.targetContext)
        instrumentation.runOnMainSync { SystemSettingsNavigator.openWifi(context) }
        assertEquals(Settings.ACTION_WIFI_SETTINGS, context.launches.single().action)
        assertTrue(context.launches.single().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test fun overlayUsesPlatformSupportedDestination() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = RecordingContext(instrumentation.targetContext)
        instrumentation.runOnMainSync { SystemSettingsNavigator.openOverlayPermission(context) }
        val launch = context.launches.single()
        assertEquals(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, launch.action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) assertNull(launch.data)
        else assertEquals("package:${context.packageName}", launch.data.toString())
    }
}
