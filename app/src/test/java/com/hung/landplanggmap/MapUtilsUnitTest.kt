package com.hung.landplanggmap

import com.hung.landplanggmap.utils.centerOfPolygon
import com.mapbox.geojson.Point
import org.junit.Assert
import org.junit.Test

class MapUtilsUnitTest {

    @Test
    fun addition_isCorrect() {

        var pointsList=ArrayList<Point>()

        pointsList.add(Point.fromLngLat(1.0,1.0))
        pointsList.add(Point.fromLngLat(2.0,2.0))
        pointsList.add(Point.fromLngLat(3.0,3.0))
        pointsList.add(Point.fromLngLat(4.0,4.0))
        pointsList.add(Point.fromLngLat(5.0,5.0))

        Assert.assertEquals(3.0, pointsList.centerLOfPolygon().latitude(),0.001)
        Assert.assertEquals(3.0, pointsList.centerOfPolygon().longitude(),0.001)

    }

}