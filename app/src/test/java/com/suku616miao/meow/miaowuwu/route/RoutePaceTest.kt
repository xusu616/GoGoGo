package com.suku616miao.meow.miaowuwu.route

import org.junit.Assert.*
import org.junit.Test

class RoutePaceTest {
    @Test fun fiveMinutesSixSecondsEquals306SecondsPerKilometre() {
        assertEquals(1000.0 / 306, RoutePace.speedFromPace("5:06")!!, 0.000001)
        assertEquals("5:06", RoutePace.paceFromSpeed(1000.0 / 306))
        assertEquals("10:12", RoutePace.duration(2000.0, 1000.0 / 306))
    }
    @Test fun lowerPaceIsFaster() { assertTrue(RoutePace.speedFromPace("3:00")!! > RoutePace.speedFromPace("5:00")!!) }
    @Test fun invalidPaceIsRejected() {
        for (pace in listOf("0:00", "5:60", "-1:05", "abc", "5", "999999999999:00")) assertNull(RoutePace.speedFromPace(pace))
    }
    @Test fun repeatWalksBackToStartBeforeBeginningTheNextLap() {
        val path = RoutePath(listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 0.001))).closed()
        val player = RoutePlayer(path, path.totalDistanceMeters, true)
        player.advance(0.75)
        assertEquals(0.0005, player.position.longitude, 0.00000001)
        player.advance(0.5)
        assertEquals(0.0005, player.position.longitude, 0.00000001)
        assertFalse(player.isComplete)
        player.advance(2.75)
        assertEquals(0.0, player.position.longitude, 0.00000001)
    }
    @Test fun completedNonRepeatingRouteStaysAtEnd() {
        val path = RoutePath(listOf(RoutePoint(0.0, 0.0), RoutePoint(0.0, 0.001)))
        val player = RoutePlayer(path, 5.0)
        player.advance(10000.0)
        assertTrue(player.isComplete)
        assertEquals(path.points.last(), player.position)
    }
}
