package com.suku616miao.meow.miaowuwu.logging

import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.printer.Printer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLoggerTest {
    @Test fun changingPreferenceFiltersThePrinterImmediatelyAndSilentDropsErrors() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = PreferenceManager.getDefaultSharedPreferences(instrumentation.targetContext)
        val hadValue = prefs.contains(AppLogger.PREFERENCE_KEY)
        val originalValue = prefs.getString(AppLogger.PREFERENCE_KEY, null)
        instrumentation.runOnMainSync {
            val priorities = mutableListOf<Int>()
            val collectingPrinter = Printer { priority, _, _ -> priorities.add(priority) }
            val logger = XLog.printers(collectingPrinter)
            try {
                prefs.edit().putString(AppLogger.PREFERENCE_KEY, "warn").commit()
                logger.d("debug regression")
                logger.i("info regression")
                logger.w("warn regression")
                logger.e("error regression")
                assertEquals(listOf(LogLevel.WARN, LogLevel.ERROR), priorities)

                priorities.clear()
                prefs.edit().putString(AppLogger.PREFERENCE_KEY, "silent").commit()
                logger.e("silent regression")
                assertTrue(priorities.isEmpty())

                prefs.edit().remove(AppLogger.PREFERENCE_KEY).commit()
                logger.v("default regression")
                assertEquals(listOf(LogLevel.VERBOSE), priorities)
            } finally {
                val edit = prefs.edit()
                if (hadValue) edit.putString(AppLogger.PREFERENCE_KEY, originalValue)
                else edit.remove(AppLogger.PREFERENCE_KEY)
                edit.commit()
            }
        }
    }
}
