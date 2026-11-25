package com.suku616miao.meow.miaowuwu.route

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePathTest {
    @Test
    fun equatorialSegmentHasExpectedDistance() {
        val path = RoutePath(listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 0.001)))

        assertEquals(111.19492664455873, path.totalDistanceMeters, 0.000001)
    }

    @Test
    fun takesAnImmutableSnapshotOfInputPoints() {
        val originalStart = RoutePoint(0.0, 0.0)
        val originalEnd = RoutePoint(0.0, 0.001)
        val input = mutableListOf(originalStart, originalEnd)
        val path = RoutePath(input)

        input[0] = RoutePoint(10.0, 10.0)
        input.add(RoutePoint(20.0, 20.0))

        assertEquals(listOf(originalStart, originalEnd), path.points)
        assertThrows(UnsupportedOperationException::class.java) {
            (path.points as MutableList<RoutePoint>).add(RoutePoint(30.0, 30.0))
        }
    }

    @Test
    fun rejectsRoutesWithFewerThanTwoPoints() {
        assertThrows(IllegalArgumentException::class.java) { RoutePath(emptyList()) }
        assertThrows(IllegalArgumentException::class.java) { RoutePath(listOf(RoutePoint(0.0, 0.0))) }
    }

    @Test
    fun rejectsAdjacentPointsAtTheSamePhysicalPosition() {
        val duplicatePairs = listOf(
            listOf(RoutePoint(1.0, 2.0), RoutePoint(1.0, 2.0)),
            listOf(RoutePoint(10.0, -180.0), RoutePoint(10.0, 180.0)),
            listOf(RoutePoint(90.0, 0.0), RoutePoint(90.0, 100.0))
        )
        for (points in duplicatePairs) {
            assertThrows(IllegalArgumentException::class.java) { RoutePath(points) }
        }
    }

    @Test
    fun rejectsInvalidCoordinates() {
        val invalidCoordinates = listOf(
            91.0 to 0.0,
            -91.0 to 0.0,
            0.0 to 181.0,
            0.0 to -181.0,
            Double.NaN to 0.0,
            0.0 to Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY to 0.0
        )
        for ((latitude, longitude) in invalidCoordinates) {
            assertThrows(IllegalArgumentException::class.java) {
                RoutePoint(latitude, longitude)
            }
        }
    }

    @Test
    fun acceptsShortNonzeroSegmentsWithoutAnArtificialMinimumDistance() {
        val path = RoutePath(listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 0.0000000001)))

        assertTrue(path.totalDistanceMeters > 0.0)
    }
}
