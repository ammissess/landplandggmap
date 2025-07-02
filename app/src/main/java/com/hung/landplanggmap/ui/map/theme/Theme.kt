package com.hung.landplanggmap.ui.map.theme


import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable

private val DarkColorPalette = darkColors(
    primary = androidx.compose.ui.graphics.Color(0xFF4F46E5),
    primaryVariant = androidx.compose.ui.graphics.Color(0xFF4338CA),
    secondary = androidx.compose.ui.graphics.Color(0xFF64748B)
)

private val LightColorPalette = lightColors(
    primary = androidx.compose.ui.graphics.Color(0xFF4F46E5),
    primaryVariant = androidx.compose.ui.graphics.Color(0xFF4338CA),
    secondary = androidx.compose.ui.graphics.Color(0xFF64748B)
)

@Composable
fun MapPolygonPointsDrawTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}