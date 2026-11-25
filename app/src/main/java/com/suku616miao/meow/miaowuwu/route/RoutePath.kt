package com.suku616miao.meow.miaowuwu.route

import java.util.Collections
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RoutePath(points: List<RoutePoint>) {
    val points: List<RoutePoint> = Collections.unmodifiableList(ArrayList(points))
    val totalDistanceMeters: Double
    internal val segments: List<RouteSegment>

    init {
        require(this.points.size >= 2) { "A route needs at least two points" }
        val routeSegments = ArrayList<RouteSegment>(this.points.size - 1)
        var distanceMeters = 0.0
        for (index in 0 until this.points.lastIndex) {
            val start = this.points[index]
            val end = this.points[index + 1]
            val samePosition = start.latitude == end.latitude &&
                (start.longitude == end.longitude ||
                    abs(start.longitude - end.longitude) == 360.0 ||
                    abs(start.latitude) == 90.0)
            require(!samePosition) { "Adjacent route points must be different positions" }

            val segment = RouteSegment(start, end, distanceMeters)
            routeSegments.add(segment)
            distanceMeters = segment.endDistanceMeters
        }
        segments = Collections.unmodifiableList(routeSegments)
        totalDistanceMeters = distanceMeters
    }
}

internal class RouteSegment(
    private val start: RoutePoint,
    private val end: RoutePoint,
    val startDistanceMeters: Double
) {
    private val startVector = RouteVector.fromPoint(start)
    private val endVector = RouteVector.fromPoint(end)
    private val normal = startVector.cross(endVector)
    private val normalLength = normal.length()
    private val antipodal = start.latitude == -end.latitude &&
        (abs(start.longitude - end.longitude) == 180.0 || abs(start.latitude) == 90.0)
    private val angleRadians = if (antipodal) {
        Math.PI
    } else {
        atan2(normalLength, startVector.dot(endVector))
    }

    val lengthMeters = angleRadians * EARTH_RADIUS_METERS
    val endDistanceMeters = startDistanceMeters + lengthMeters

    init {
        require(lengthMeters > 0.0) { "Every route segment must have nonzero length" }
    }

    // Antipodal endpoints have no unique short arc; choose the arc heading north.
    private val startTangent = if (antipodal || normalLength == 0.0) {
        RouteVector.northAt(start)
    } else {
        normal.scaled(1.0 / normalLength).cross(startVector).normalized()
    }

    fun positionAt(distanceMeters: Double): RoutePoint {
        if (distanceMeters == 0.0) return start
        if (distanceMeters == lengthMeters) return end
        return vectorAt(distanceMeters).toPoint()
    }

    fun bearingAt(distanceMeters: Double): Float {
        val angle = distanceMeters / lengthMeters * angleRadians
        val velocity = startTangent.scaled(cos(angle)) + startVector.scaled(-sin(angle))
        val point = positionAt(distanceMeters)
        val eastComponent = velocity.dot(RouteVector.eastAt(point))
        val northComponent = velocity.dot(RouteVector.northAt(point))
        val degrees = Math.toDegrees(atan2(eastComponent, northComponent))
        return ((degrees + 360.0) % 360.0).toFloat()
    }

    private fun vectorAt(distanceMeters: Double): RouteVector {
        val angle = distanceMeters / lengthMeters * angleRadians
        return startVector.scaled(cos(angle)) + startTangent.scaled(sin(angle))
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}

private class RouteVector(val x: Double, val y: Double, val z: Double) {
    operator fun plus(other: RouteVector) = RouteVector(x + other.x, y + other.y, z + other.z)

    fun scaled(scale: Double) = RouteVector(x * scale, y * scale, z * scale)

    fun dot(other: RouteVector): Double = x * other.x + y * other.y + z * other.z

    fun cross(other: RouteVector) = RouteVector(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun length(): Double = sqrt(x * x + y * y + z * z)

    fun normalized(): RouteVector = scaled(1.0 / length())

    fun toPoint() = RoutePoint(
        Math.toDegrees(atan2(z, sqrt(x * x + y * y))),
        Math.toDegrees(atan2(y, x))
    )

    companion object {
        fun fromPoint(point: RoutePoint): RouteVector {
            val latitude = Math.toRadians(point.latitude)
            val longitude = Math.toRadians(point.longitude)
            return RouteVector(cos(latitude) * cos(longitude), cos(latitude) * sin(longitude), sin(latitude))
        }

        fun northAt(point: RoutePoint): RouteVector {
            val latitude = Math.toRadians(point.latitude)
            val longitude = Math.toRadians(point.longitude)
            return RouteVector(-sin(latitude) * cos(longitude), -sin(latitude) * sin(longitude), cos(latitude))
        }

        fun eastAt(point: RoutePoint): RouteVector {
            val longitude = Math.toRadians(point.longitude)
            return RouteVector(-sin(longitude), cos(longitude), 0.0)
        }
    }
}
