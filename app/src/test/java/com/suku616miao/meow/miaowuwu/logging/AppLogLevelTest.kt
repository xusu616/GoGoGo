package com.suku616miao.meow.miaowuwu.logging

import com.elvishew.xlog.LogLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogLevelTest {
    @Test fun defaultRetainsEveryExistingPriority() {
        assertEquals(AppLogLevel.ALL, AppLogLevel.fromPreference(null))
        for (priority in LogLevel.VERBOSE..LogLevel.ERROR) assertTrue(AppLogLevel.ALL.accepts(priority))
    }

    @Test fun eachChoiceSuppressesOnlyLowerPriorityMessages() {
        val cases = listOf(AppLogLevel.DEBUG to LogLevel.DEBUG, AppLogLevel.INFO to LogLevel.INFO,
            AppLogLevel.WARN to LogLevel.WARN, AppLogLevel.ERROR to LogLevel.ERROR)
        for ((selected, threshold) in cases) {
            for (priority in LogLevel.VERBOSE..LogLevel.ERROR) assertEquals(priority >= threshold, selected.accepts(priority))
        }
    }

    @Test fun silentSuppressesEvenErrorAndCustomHigherPriorities() {
        for (priority in LogLevel.VERBOSE..LogLevel.ERROR) assertFalse(AppLogLevel.SILENT.accepts(priority))
        assertFalse(AppLogLevel.SILENT.accepts(Int.MAX_VALUE))
    }

    @Test fun importedUnknownValueRestoresDocumentedDefault() {
        assertEquals(AppLogLevel.ALL, AppLogLevel.fromPreference("unknown"))
        for (selected in AppLogLevel.entries) assertEquals(selected, AppLogLevel.fromPreference(selected.preferenceValue))
    }
}
