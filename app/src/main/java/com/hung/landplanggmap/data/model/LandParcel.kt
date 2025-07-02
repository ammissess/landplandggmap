package com.hung.landplanggmap.data.model

data class LandParcel(
    val address: String = "",
    val registerDate: String = "",
    val ownerName: String = "",
    val area: Long = 0,
    val landType: Int = 1, // 1: thổ cư, 2: thổ cảnh, 3: khác
    val coordinates: List<LatLng> = emptyList(),
    val phone: String = "",
    val createdBy: String = "",
    val district: String = "",
    val province: String = "",
    val country: String = ""
)

data class LatLng(
    val lat: Double = 0.0,
    val lng: Double = 0.0
)