package com.suku616miao.meow.miaowuwu.route

data class RoutePoint(val latitude: Double, val longitude: Double) {
    fun isSamePosition(other: RoutePoint): Boolean = latitude == other.latitude &&
        (longitude == other.longitude || kotlin.math.abs(longitude - other.longitude) == 360.0 || kotlin.math.abs(latitude) == 90.0)
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Invalid latitude" }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Invalid longitude" }
    }
}
