package com.hung.landplanggmap.utils

import com.hung.landplanggmap.data.model.LatLng
import com.mapbox.geojson.Point

fun List<Point>.toLatLngList(): List<LatLng> {
    return this.map { LatLng(it.latitude(), it.longitude()) }
}