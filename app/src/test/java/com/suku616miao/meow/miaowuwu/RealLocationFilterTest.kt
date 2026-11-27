package com.suku616miao.meow.miaowuwu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealLocationFilterTest {
    @Test fun simulationCannotOverwriteOrBlockRealPosition() {
        val filter = RealLocationFilter(100)
        assertTrue(filter.accept(false, 110, 20f))
        assertFalse(filter.accept(true, 200, 1f))
        assertTrue(filter.accept(false, 120, 20f))
    }

    @Test fun restartWaitsForFreshFixInsteadOfCachedPosition() {
        val filter = RealLocationFilter(100)
        assertFalse(filter.accept(false, 90, 10f))
        assertTrue(filter.accept(false, 110, 20f))
    }

    @Test fun delayedProviderCannotMovePositionBackInTime() {
        val filter = RealLocationFilter(100)
        assertTrue(filter.accept(false, 120, 20f))
        assertFalse(filter.accept(false, 110, 1f))
        assertTrue(filter.accept(false, 130, 20f))
    }

    @Test fun sameTimestampUsesMoreAccurateProvider() {
        val filter = RealLocationFilter(100)
        assertTrue(filter.accept(false, 120, 40f))
        assertTrue(filter.accept(false, 120, 20f))
        assertFalse(filter.accept(false, 120, 30f))
        assertFalse(filter.accept(false, 120, 20f))
    }
}
