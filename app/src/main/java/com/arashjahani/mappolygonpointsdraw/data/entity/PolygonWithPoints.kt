package com.arashjahani.mappolygonpointsdraw.data.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.mapbox.geojson.Point

data class PolygonWithPoints(
    @Embedded val polygon: PolygonModel,
    @Relation(
        parentColumn = "id",
        entityColumn = "polygon_id"
    )
    var polygonPoints: List<PointsModel>
) {

    fun toPointsList(): ArrayList<Point> {
        var result = ArrayList<Point>()
        polygonPoints.map {
            result.add(Point.fromLngLat(it.lng, it.lat))
        }

        return result
    }

    fun toCopy():String{

        var result=java.lang.StringBuilder()
        result.append(polygon.title)
        result.append("\n")
        result.append(polygon.area)
        result.append("\n")

        polygonPoints.map {
            result.append("lat: ${it.lat} - lng:${it.lng}")
            result.append("\n")
        }

        return  result.toString()
    }
}
