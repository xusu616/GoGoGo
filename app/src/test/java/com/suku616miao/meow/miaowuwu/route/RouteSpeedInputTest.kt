package com.suku616miao.meow.miaowuwu.route

import org.junit.Assert.*
import org.junit.Test

class RouteSpeedInputTest {
    @Test fun restoringExactSpeedKeepsPrecisionWhileShorteningDisplay() {
        val input = RouteSpeedInput()
        val savedSpeed = 1000.0 / 306.0
        input.restore(savedSpeed)
        assertEquals("3.2680", input.speedText)
        assertEquals(savedSpeed, input.speedMetersPerSecond!!, 0.0)
        input.editSpeed("")
        assertNull(input.speedMetersPerSecond)
        input.editPace("5:07")
        assertEquals(1000.0 / 307.0, input.speedMetersPerSecond!!, 0.0)
    }

    @Test fun restoringSmallSpeedDoesNotDisplayZero() {
        val input = RouteSpeedInput()
        input.restore(0.00001)
        assertEquals(0.00001, input.speedText.toDouble(), 0.0)
        assertEquals(0.00001, input.speedMetersPerSecond!!, 0.0)
    }
    @Test fun paceEditUsesExactConversionRatherThanTheRoundedSpeedDisplay() {
        val input = RouteSpeedInput()
        input.editPace("5:06")
        assertEquals(1000.0 / 306.0, input.speedMetersPerSecond!!, 0.0000000001)
        assertEquals("3.2680", input.speedText)
    }

    @Test fun incompletePaceCannotReuseThePreviousValidSpeedAfterLosingFocus() {
        val input = RouteSpeedInput()
        input.editPace("5:06")
        input.editPace("5:")
        assertEquals(RouteSpeedSource.PACE, input.source)
        assertNull(input.speedMetersPerSecond)
        input.editPace("5:07")
        assertEquals(1000.0 / 307.0, input.speedMetersPerSecond!!, 0.0000000001)
    }

    @Test fun incompleteSpeedCannotReuseThePreviousPace() {
        val input = RouteSpeedInput()
        input.editPace("5:06")
        input.editSpeed("")
        assertNull(input.speedMetersPerSecond)
        assertEquals(RouteSpeedSource.SPEED, input.source)
        input.editSpeed("4")
        assertEquals("4:10", input.paceText)
        assertEquals(4.0, input.speedMetersPerSecond!!, 0.0)
    }

    @Test fun validSpeedIsIndependentOfPaceDisplayLimits() {
        val input = RouteSpeedInput()
        input.editSpeed("0.01")
        assertEquals(0.01, input.speedMetersPerSecond!!, 0.0)
        assertEquals("1666:40", input.paceText)
        input.editSpeed("2500")
        assertEquals("0:00", input.paceText)
        assertEquals(2500.0, input.speedMetersPerSecond!!, 0.0)
    }

    @Test fun restoreMakesLoadedSpeedAuthoritativeAndRefreshesPace() {
        val input = RouteSpeedInput()
        input.editPace("5:")
        input.restore(4.0)
        assertEquals(RouteSpeedSource.SPEED, input.source)
        assertEquals("4:10", input.paceText)
        assertEquals(4.0, input.speedMetersPerSecond!!, 0.0)
    }
}
