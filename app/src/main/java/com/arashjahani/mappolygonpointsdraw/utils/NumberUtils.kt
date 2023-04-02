package com.arashjahani.mappolygonpointsdraw.utils

import java.text.NumberFormat
import java.util.*

fun Long?.areaFormat():String{

    if(this==null){
        return "0"
    }

    return NumberFormat.getNumberInstance(Locale.US).format(this)

}