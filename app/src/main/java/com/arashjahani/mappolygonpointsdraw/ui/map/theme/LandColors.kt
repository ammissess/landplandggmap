package com.arashjahani.mappolygonpointsdraw.ui.map.theme

import androidx.compose.ui.graphics.Color

fun getLandColor(landType: Int): Color = when (landType) {
    1 -> Color(0xFFE53935) // Đỏ
    2 -> Color(0xFF43A047) // Xanh lá
    3 -> Color(0xFFFFB300) // Vàng
    else -> Color.Gray
}
fun getLandColorHex(landType: Int): String = when (landType) {
    1 -> "#E53935" // Đỏ
    2 -> "#43A047" // Xanh lá
    3 -> "#FFB300" // Vàng
    else -> "#888888"
}