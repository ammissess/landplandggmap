package com.arashjahani.mappolygonpointsdraw.ui.map

import com.arashjahani.mappolygonpointsdraw.data.entity.PolygonWithPoints

interface PolygonsItemClickListener {

    fun deletePolygon(itemId:Long)

    fun copyPolygon(item:PolygonWithPoints)

    fun displayOnMap(item:PolygonWithPoints)

}