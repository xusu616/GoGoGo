package com.suku616miao.meow.miaowuwu.route

/** Published atomically by the service so the UI sees matching state and WGS-84 position. */
data class RoutePlaybackSnapshot(val state: Int, val latitude: Double, val longitude: Double)
