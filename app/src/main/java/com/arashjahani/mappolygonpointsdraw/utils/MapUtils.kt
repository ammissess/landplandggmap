package com.arashjahani.mappolygonpointsdraw.utils

import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.turf.TurfMeasurement
import java.util.*
import kotlin.math.roundToLong

fun List<Point>.centerOfPolygon(): Point {
    var latSum = 0.0
    var lngSum = 0.0

    for (vertex in this) {
        latSum += vertex.latitude()
        lngSum += vertex.longitude()
    }

    val latAvg: Double = latSum / this.size
    val lngAvg: Double = lngSum / this.size

    return Point.fromLngLat(lngAvg, latAvg)
}

 fun List<Point>.calcPolygonArea(): Long {
    val polygon: Polygon = Polygon.fromLngLats(Collections.singletonList(this))
    return TurfMeasurement.area(polygon).roundToLong()
}