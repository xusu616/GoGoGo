package com.suku616miao.meow.miaowuwu.route

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutePlayerTest {
    @Test
    fun startsAtTheFirstPointAndZeroElapsedTimeDoesNotMove() {
        val start = RoutePoint(0.0, 0.0)
        val player = RoutePlayer(RoutePath(listOf(start, RoutePoint(0.0, 0.001))), 10.0)

        assertEquals(start, player.position)
        assertEquals(90.0f, player.bearingDegrees, 0.0001f)
        player.advance(0.0)
        assertEquals(start, player.position)
        assertFalse(player.isComplete)
    }

    @Test
    fun consumesTheEntireStepAcrossMultipleNodes() {
        val path = RoutePath(
            listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 0.001), RoutePoint(0.0, 0.002), RoutePoint(0.0, 0.003))
        )
        val player = RoutePlayer(path, 111.19492664455873)

        player.advance(2.5)

        assertEquals(0.0, player.position.latitude, 0.000000001)
        assertEquals(0.0025, player.position.longitude, 0.000000001)
        assertFalse(player.isComplete)
    }

    @Test
    fun carriesRemainingDistanceIntoATurnAndUpdatesBearing() {
        val path = RoutePath(listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 0.001), RoutePoint(0.001, 0.001)))
        val player = RoutePlayer(path, 100.0)

        player.advance(1.5)

        assertEquals(0.0003489824088780956, player.position.latitude, 0.000000001)
        assertEquals(0.001, player.position.longitude, 0.000000001)
        assertEquals(0.0f, player.bearingDegrees, 0.0001f)
    }

    @Test
    fun reachesTheExactEndpointWithoutOvershootingAndStaysThere() {
        val end = RoutePoint(1.0, 1.0)
        val player = RoutePlayer(RoutePath(listOf(RoutePoint(0.0, 0.0), end)), 10.0)

        player.advance(1_000_000.0)

        assertEquals(end, player.position)
        assertTrue(player.isComplete)
        val finalBearing = player.bearingDegrees
        player.advance(5.0)
        assertEquals(end, player.position)
        assertEquals(finalBearing, player.bearingDegrees, 0.0f)
    }

    @Test
    fun followsTheShortArcAcrossTheAntimeridian() {
        val path = RoutePath(listOf(RoutePoint(10.0, 179.9), RoutePoint(10.0, -179.9)))
        val player = RoutePlayer(path, path.totalDistanceMeters)

        player.advance(0.5)

        assertTrue(path.totalDistanceMeters < 25_000.0)
        assertEquals(180.0, abs(player.position.longitude), 0.000000001)
        assertTrue(player.position.latitude > 10.0)
        assertTrue(player.position.latitude < 10.001)
        player.advance(0.5)
        assertEquals(path.points.last(), player.position)
        assertTrue(player.isComplete)
    }

    @Test
    fun highLatitudeRouteFollowsTheGreatCircleOverThePole() {
        val path = RoutePath(listOf(RoutePoint(80.0, -90.0), RoutePoint(80.0, 90.0)))
        val player = RoutePlayer(path, path.totalDistanceMeters)

        player.advance(0.5)

        assertEquals(2_223_898.532891175, path.totalDistanceMeters, 0.000001)
        assertEquals(90.0, player.position.latitude, 0.000000001)
    }

    @Test
    fun antipodalEndpointsUseADeterministicArcAndFinishExactly() {
        val end = RoutePoint(0.0, 180.0)
        val path = RoutePath(listOf(RoutePoint(0.0, 0.0), end))
        val player = RoutePlayer(path, path.totalDistanceMeters)

        player.advance(0.5)
        assertEquals(90.0, player.position.latitude, 0.000000001)
        player.advance(0.5)
        assertEquals(end, player.position)
        assertTrue(player.isComplete)
    }

    @Test
    fun oneAdvanceAndSeveralAdvancesCoverTheSameDistance() {
        val path = RoutePath(listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 0.001), RoutePoint(0.001, 0.001)))
        val singleStep = RoutePlayer(path, 100.0)
        val repeatedSteps = RoutePlayer(path, 100.0)

        singleStep.advance(1.5)
        repeat(15) { repeatedSteps.advance(0.1) }

        assertEquals(singleStep.position.latitude, repeatedSteps.position.latitude, 0.000000001)
        assertEquals(singleStep.position.longitude, repeatedSteps.position.longitude, 0.000000001)
        assertEquals(singleStep.bearingDegrees, repeatedSteps.bearingDegrees, 0.0001f)
    }

    @Test
    fun rejectsInvalidSpeeds() {
        val path = RoutePath(listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 0.001)))
        for (speed in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { RoutePlayer(path, speed) }
        }
    }

    @Test
    fun rejectsInvalidElapsedTimeWithoutChangingPosition() {
        val start = RoutePoint(0.0, 0.0)
        val player = RoutePlayer(RoutePath(listOf(start, RoutePoint(0.0, 0.001))), 10.0)

        for (elapsedSeconds in listOf(-0.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { player.advance(elapsedSeconds) }
            assertEquals(start, player.position)
            assertFalse(player.isComplete)
        }
    }
}
