package com.suku616miao.meow.miaowuwu.update

/** Stable release tags use vMAJOR.MINOR.PATCH; release titles are display text only. */
data class ReleaseVersion(val major: Long, val minor: Long, val patch: Long) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int {
        val majorOrder = major.compareTo(other.major)
        if (majorOrder != 0) return majorOrder
        val minorOrder = minor.compareTo(other.minor)
        if (minorOrder != 0) return minorOrder
        return patch.compareTo(other.patch)
    }

    companion object {
        private val stableVersionPattern = Regex("^v?([0-9]+)\\.([0-9]+)\\.([0-9]+)$")

        @JvmStatic
        fun parse(value: String): ReleaseVersion? {
            val match = stableVersionPattern.matchEntire(value) ?: return null
            val major = match.groupValues[1].toLongOrNull() ?: return null
            val minor = match.groupValues[2].toLongOrNull() ?: return null
            val patch = match.groupValues[3].toLongOrNull() ?: return null
            return ReleaseVersion(major, minor, patch)
        }

        @JvmStatic
        fun isNewer(releaseTag: String, currentVersion: String): Boolean {
            val current = checkNotNull(parse(currentVersion)) { "Invalid application version: $currentVersion" }
            val release = requireNotNull(parse(releaseTag)) { "Invalid stable release tag: $releaseTag" }
            return release > current
        }
    }
}
