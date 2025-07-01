package com.arashjahani.mappolygonpointsdraw.utils
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import java.util.*
import kotlin.math.roundToLong
import com.arashjahani.mappolygonpointsdraw.data.model.LatLng
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.Polygon as JTSPolygon
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

fun List<LatLng>.toJtsPolygon(): JTSPolygon {
    val geometryFactory = GeometryFactory()
    val coords = this.map { Coordinate(it.lng, it.lat) }.toMutableList()
    // Đảm bảo polygon khép kín
    if (coords.first() != coords.last()) coords.add(coords.first())
    val coordArray = coords.toTypedArray()
    return geometryFactory.createPolygon(coordArray)
}

fun isPolygonIntersect(newPolygon: List<LatLng>, oldPolygons: List<List<LatLng>>): Boolean {
    val newJts = newPolygon.toJtsPolygon()
    for (poly in oldPolygons) {
        val oldJts = poly.toJtsPolygon()
        if (newJts.intersects(oldJts)) return true
    }
    return false
}

fun List<Point>.calcPolygonArea(): Long {
    val polygon: Polygon = Polygon.fromLngLats(listOf(this))
    return TurfUtils.area(polygon).roundToLong()
}