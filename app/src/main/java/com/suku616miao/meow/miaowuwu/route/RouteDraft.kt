package com.suku616miao.meow.miaowuwu.route

/** Map gestures change only the candidate. A point belongs to the route after confirmation. */
class RouteDraft {
    private val confirmedPoints = mutableListOf<RoutePoint>()
    val points: List<RoutePoint>
        get() = confirmedPoints.toList()

    var candidate: RoutePoint? = null
        private set
    var isMapMoving: Boolean = false
        private set

    fun beginMapMove() {
        isMapMoving = true
        candidate = null
    }

    fun finishMapMove(point: RoutePoint) {
        candidate = point
        isMapMoving = false
    }

    fun confirmCandidate(): Boolean {
        val point = candidate ?: return false
        val previous = confirmedPoints.lastOrNull()
        val samePosition = previous != null && previous.latitude == point.latitude &&
            ((previous.longitude - point.longitude) % 360.0 == 0.0 || kotlin.math.abs(point.latitude) == 90.0)
        if (isMapMoving || samePosition) return false
        confirmedPoints.add(point)
        return true
    }

    fun undo() {
        if (confirmedPoints.isNotEmpty()) confirmedPoints.removeAt(confirmedPoints.lastIndex)
    }

    fun replace(points: List<RoutePoint>) {
        confirmedPoints.clear()
        confirmedPoints.addAll(points)
    }
}
