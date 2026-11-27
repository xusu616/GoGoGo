package com.suku616miao.meow.miaowuwu.route

class RoutePlayer @JvmOverloads constructor(val path: RoutePath, val speedMetersPerSecond: Double, val repeat: Boolean = false) {
    private var distanceTravelledMeters = 0.0
    private var segmentIndex = 0
    private var currentPosition = path.points.first()
    private var currentBearing = path.segments.first().bearingAt(0.0)

    val position: RoutePoint
        get() = currentPosition

    val bearingDegrees: Float
        get() = currentBearing

    val isComplete: Boolean
        get() = !repeat && distanceTravelledMeters == path.totalDistanceMeters

    init {
        require(speedMetersPerSecond.isFinite() && speedMetersPerSecond > 0.0) {
            "Route speed must be finite and greater than zero"
        }
    }

    fun advance(elapsedSeconds: Double) {
        require(elapsedSeconds.isFinite() && elapsedSeconds >= 0.0) {
            "Elapsed time must be finite and nonnegative"
        }
        if (elapsedSeconds == 0.0 || isComplete) return

        val stepMeters = speedMetersPerSecond * elapsedSeconds
        val nextDistanceMeters = if (repeat) {
            // Consume the returning segment too; retain excess distance across the next lap.
            (distanceTravelledMeters + stepMeters) % path.totalDistanceMeters
        } else distanceTravelledMeters + stepMeters
        if (nextDistanceMeters >= path.totalDistanceMeters) {
            distanceTravelledMeters = path.totalDistanceMeters
            currentPosition = path.points.last()
            val lastSegment = path.segments.last()
            currentBearing = lastSegment.bearingAt(lastSegment.lengthMeters)
            return
        }

        distanceTravelledMeters = nextDistanceMeters
        if (repeat && distanceTravelledMeters < path.segments[segmentIndex].startDistanceMeters) segmentIndex = 0
        while (distanceTravelledMeters >= path.segments[segmentIndex].endDistanceMeters) {
            segmentIndex++
        }
        val segment = path.segments[segmentIndex]
        val distanceAlongSegment = distanceTravelledMeters - segment.startDistanceMeters
        currentPosition = segment.positionAt(distanceAlongSegment)
        currentBearing = segment.bearingAt(distanceAlongSegment)
    }
}
