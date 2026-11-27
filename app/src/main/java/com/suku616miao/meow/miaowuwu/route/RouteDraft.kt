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
    }

    fun updateCandidate(point: RoutePoint) { candidate = point }

    fun finishMapMove(point: RoutePoint) {
        candidate = point
        isMapMoving = false
    }

    fun confirmCandidate(): Boolean {
        val point = candidate ?: return false
        val previous = confirmedPoints.lastOrNull()
        val samePosition = previous?.isSamePosition(point) == true
        if (samePosition) return false
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
