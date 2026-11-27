package com.suku616miao.meow.miaowuwu.logging

import com.elvishew.xlog.LogLevel

/** Priority thresholds apply equally to console output and the persisted application log. */
enum class AppLogLevel(val preferenceValue: String, private val threshold: Int) {
    ALL("all", LogLevel.ALL),
    DEBUG("debug", LogLevel.DEBUG),
    INFO("info", LogLevel.INFO),
    WARN("warn", LogLevel.WARN),
    ERROR("error", LogLevel.ERROR),
    SILENT("silent", LogLevel.NONE);

    fun accepts(priority: Int): Boolean = this != SILENT && priority >= threshold

    companion object {
        // Preferences can be imported from backups containing an unknown value; retain the default.
        fun fromPreference(value: String?): AppLogLevel = entries.firstOrNull { it.preferenceValue == value } ?: ALL
    }
}
