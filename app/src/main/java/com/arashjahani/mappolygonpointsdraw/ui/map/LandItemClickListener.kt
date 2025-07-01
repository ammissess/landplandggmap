package com.arashjahani.mappolygonpointsdraw.ui.map

import com.arashjahani.mappolygonpointsdraw.data.model.LandParcel

interface LandItemClickListener {
    fun deleteLand(land: LandParcel)
    fun copyLand(land: LandParcel)
    fun displayOnMap(land: LandParcel)
}