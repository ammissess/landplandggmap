package com.arashjahani.mappolygonpointsdraw.utils

import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import kotlin.math.*

object TurfUtils {
    /**
     * Tính diện tích (m2) của Polygon (theo thuật toán Haversine)
     */
    fun area(polygon: Polygon): Double {
        val coordinates = polygon.coordinates()[0]
        if (coordinates.size < 3) return 0.0

        val radius = 6378137.0 // Earth radius in meters
        var area = 0.0

        for (i in coordinates.indices) {
            val p1 = coordinates[i]
            val p2 = coordinates[(i + 1) % coordinates.size]
            area += Math.toRadians(p2.longitude() - p1.longitude()) *
                    (2 + sin(Math.toRadians(p1.latitude())) + sin(Math.toRadians(p2.latitude())))
        }
        area = area * radius * radius / 2.0
        return abs(area)
    }
}