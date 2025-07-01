package com.arashjahani.mappolygonpointsdraw.utils
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import java.util.*
import kotlin.math.roundToLong
import com.arashjahani.mappolygonpointsdraw.data.model.LatLng

fun List<LatLng>.centerOfLatLngPolygon(): LatLng {
    var latSum = 0.0
    var lngSum = 0.0
    for (vertex in this) {
        latSum += vertex.lat
        lngSum += vertex.lng
    }
    val latAvg = latSum / this.size
    val lngAvg = lngSum / this.size
    return LatLng(latAvg, lngAvg)
}

fun List<Point>.calcPolygonArea(): Long {
    val polygon: Polygon = Polygon.fromLngLats(listOf(this))
    return TurfUtils.area(polygon).roundToLong()
}