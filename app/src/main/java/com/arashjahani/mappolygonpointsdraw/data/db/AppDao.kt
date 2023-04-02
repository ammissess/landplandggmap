package com.arashjahani.mappolygonpointsdraw.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.arashjahani.mappolygonpointsdraw.data.entity.PointsModel
import com.arashjahani.mappolygonpointsdraw.data.entity.PolygonModel
import com.arashjahani.mappolygonpointsdraw.data.entity.PolygonWithPoints
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    //get all
    @Transaction
    @Query("SELECT * FROM polygons ORDER BY id DESC")
    fun getPolygonsWithPoints(): Flow<List<PolygonWithPoints>>


    //save polygon
    @Insert
    suspend fun addPolygon(polygonModel: PolygonModel): Long

    @Insert
    suspend fun addPoints(points: List<PointsModel>)

    @Transaction
    suspend fun addPolygonWithPoints(polygonModel: PolygonModel, points: List<PointsModel>) : Long {

        val listId = addPolygon(polygonModel)

        points.forEach { it.polygon_id = listId }
        addPoints(points)

        return listId
    }

    //delete polygon
    @Query("DELETE FROM polygons WHERE id=:id")
    suspend fun deletePolygon(id: Long)

}