package com.suku616miao.meow.miaowuwu.route

import java.util.Locale
import kotlin.math.roundToLong

/** Pace is elapsed seconds per kilometre; smaller pace means faster movement. */
object RoutePace {
    @JvmStatic
    fun speedFromPace(value: String): Double? {
        val parts = value.trim().split(':')
        if (parts.size != 2) return null
        val minutes = parts[0].toLongOrNull() ?: return null
        val seconds = parts[1].toLongOrNull() ?: return null
        if (minutes !in 0..999 || seconds !in 0..59) return null
        val totalSeconds = minutes * 60 + seconds
        if (totalSeconds == 0L) return null
        return 1000.0 / totalSeconds
    }

    fun paceFromSpeed(speed: Double): String {
        require(speed.isFinite() && speed > 0.0)
        val seconds = (1000.0 / speed).roundToLong()
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }

    fun duration(distance: Double, speed: Double): String {
        require(distance.isFinite() && distance >= 0 && speed.isFinite() && speed > 0)
        val seconds = (distance / speed).roundToLong()
        return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }
}
