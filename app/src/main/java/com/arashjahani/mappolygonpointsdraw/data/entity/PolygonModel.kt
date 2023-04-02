package com.arashjahani.mappolygonpointsdraw.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "polygons")
data class PolygonModel(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var _id: Long,

    @ColumnInfo(name = "title")
    var title:String,

    @ColumnInfo(name = "area")
    var area:Long,

    @ColumnInfo(name = "center_lat")
    var centerLat:Double,

    @ColumnInfo(name = "center_lng")
    var centerLng:Double





)
