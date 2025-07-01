package com.arashjahani.mappolygonpointsdraw.utils

import com.arashjahani.mappolygonpointsdraw.data.model.LatLng
import com.mapbox.geojson.Point

fun List<Point>.toLatLngList(): List<LatLng> {
    return this.map { LatLng(it.latitude(), it.longitude()) }
}