package com.arashjahani.mappolygonpointsdraw.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "points")
data class PointsModel(

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    var _id: Long,


    @ColumnInfo(name = "polygon_id")
    var polygon_id: Long,

    @ColumnInfo(name = "lat")
    var lat:Double,

    @ColumnInfo(name = "lng")
    var lng:Double
)
