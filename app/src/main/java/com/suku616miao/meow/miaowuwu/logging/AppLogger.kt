package com.suku616miao.meow.miaowuwu.logging

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.elvishew.xlog.LogLevel
import com.elvishew.xlog.XLog
import com.elvishew.xlog.interceptor.Interceptor

/** Application-scoped logging preference: changes take effect before any printer receives a log. */
object AppLogger {
    const val PREFERENCE_KEY = "setting_log_level"
    @Volatile private var level = AppLogLevel.ALL
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == PREFERENCE_KEY) level = AppLogLevel.fromPreference(prefs.getString(PREFERENCE_KEY, "all"))
    }
    private val levelInterceptor = Interceptor { item -> if (level.accepts(item.level)) item else null }

    @JvmStatic fun initialize(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        level = AppLogLevel.fromPreference(prefs.getString(PREFERENCE_KEY, "all"))
        // SharedPreferences holds this listener for the Application lifetime, without Activity references.
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    @JvmStatic fun interceptor(): Interceptor = levelInterceptor

    @JvmStatic fun d(tag: String, message: String) = write(LogLevel.DEBUG, tag, message, null)
    @JvmStatic fun i(tag: String, message: String) = write(LogLevel.INFO, tag, message, null)
    @JvmStatic fun w(tag: String, message: String) = write(LogLevel.WARN, tag, message, null)
    @JvmStatic fun e(tag: String, message: String) = write(LogLevel.ERROR, tag, message, null)
    @JvmStatic fun d(tag: String, message: String, error: Throwable) = write(LogLevel.DEBUG, tag, message, error)
    @JvmStatic fun i(tag: String, message: String, error: Throwable) = write(LogLevel.INFO, tag, message, error)
    @JvmStatic fun w(tag: String, message: String, error: Throwable) = write(LogLevel.WARN, tag, message, error)
    @JvmStatic fun e(tag: String, message: String, error: Throwable) = write(LogLevel.ERROR, tag, message, error)

    private fun write(priority: Int, tag: String, message: String, error: Throwable?) {
        if (!level.accepts(priority)) return
        val logger = XLog.tag(tag)
        when (priority) {
            LogLevel.DEBUG -> if (error == null) logger.d(message) else logger.d(message, error)
            LogLevel.INFO -> if (error == null) logger.i(message) else logger.i(message, error)
            LogLevel.WARN -> if (error == null) logger.w(message) else logger.w(message, error)
            LogLevel.ERROR -> if (error == null) logger.e(message) else logger.e(message, error)
        }
    }
}
