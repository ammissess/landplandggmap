package com.arashjahani.mappolygonpointsdraw.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.arashjahani.mappolygonpointsdraw.data.entity.PolygonModel
import com.arashjahani.mappolygonpointsdraw.data.entity.PointsModel

@Database(entities = [PolygonModel::class,PointsModel::class], version = 1, exportSchema = false)
abstract class RoomDB : RoomDatabase() {

    abstract fun appDao(): AppDao
}