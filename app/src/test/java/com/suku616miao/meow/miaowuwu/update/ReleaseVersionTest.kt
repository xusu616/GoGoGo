package com.suku616miao.meow.miaowuwu.update

import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionTest {
    @Test fun unpublishedLocalBuildDoesNotOfferAnOlderRelease() {
        assertFalse(ReleaseVersion.isNewer("v1.13.0", "1.13.1"))
    }

    @Test fun sameVersionDoesNotOfferAnUpdate() {
        assertFalse(ReleaseVersion.isNewer("v1.13.1", "1.13.1"))
    }

    @Test fun newerPatchIsDetectedEvenWhenItsTagContainsTheCurrentVersion() {
        assertTrue(ReleaseVersion.isNewer("v1.13.10", "1.13.1"))
    }

    @Test fun componentsAreComparedNumerically() {
        assertTrue(ReleaseVersion.isNewer("v1.14.0", "1.13.9"))
        assertTrue(ReleaseVersion.isNewer("v2.0.0", "1.99.99"))
        assertFalse(ReleaseVersion.isNewer("v1.9.0", "1.13.0"))
    }

    @Test fun unsupportedRemoteTagsAreReportedInsteadOfTreatedAsUpdates() {
        for (tag in listOf("", "GoGoGo 1.13.2", "v1.13", "v1.13.2-beta", "v1.13.999999999999999999999999")) {
            assertThrows(IllegalArgumentException::class.java) { ReleaseVersion.isNewer(tag, "1.13.1") }
        }
    }
}
