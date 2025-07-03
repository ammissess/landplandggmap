package com.hung.landplanggmap.ui.map.theme

import androidx.compose.ui.graphics.Color

fun getLandColor(landType: Int): Color = when (landType) {
    1 -> Color(0xFFFF3333) // Đỏ
    2 -> Color(0xFF00FF00) // Xanh lá
    3 -> Color(0xFFFFFF00) // Vàng
    else -> Color.Gray
}

fun getLandColorHex(landType: Int): String = when (landType) {
    1 -> "#FF3333" // Đỏ
    2 -> "#00FF00" // Xanh lá
    3 -> "#FFFF00" // Vàng
    else -> "#0000FF" // Xám mặc định
}