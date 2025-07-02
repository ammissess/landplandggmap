package com.hung.landplanggmap.ui.map

import com.hung.landplanggmap.data.model.LandParcel

interface LandItemClickListener {
    fun deleteLand(land: LandParcel)
    fun copyLand(land: LandParcel)
    fun displayOnMap(land: LandParcel)
}