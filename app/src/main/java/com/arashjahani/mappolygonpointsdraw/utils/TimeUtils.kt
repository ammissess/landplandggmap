package com.arashjahani.mappolygonpointsdraw.utils

import java.text.SimpleDateFormat
import java.util.*

fun getTime(): String {
    val currentTime = Date()
    val formatter =
        SimpleDateFormat("yyyy-MM-dd hh:mm", Locale.ENGLISH)

    return formatter.format(currentTime)
}