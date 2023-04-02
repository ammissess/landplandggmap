package com.arashjahani.mappolygonpointsdraw.data

import com.arashjahani.mappolygonpointsdraw.data.db.AppDao
import com.arashjahani.mappolygonpointsdraw.data.entity.PointsModel
import com.arashjahani.mappolygonpointsdraw.data.entity.PolygonModel
import com.arashjahani.mappolygonpointsdraw.data.entity.PolygonWithPoints
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class DataRepositoryImpl @Inject constructor(
    private val appDao: AppDao
) : DataRepository {

    override fun getPolygonsWithPoints(): Flow<List<PolygonWithPoints>> {
        return appDao.getPolygonsWithPoints()
    }

    override suspend fun addPolygon(polygonModel: PolygonModel): Long {
        return appDao.addPolygon(polygonModel)
    }

    override suspend fun addPoints(points: List<PointsModel>) {
        return appDao.addPoints(points)
    }

    override suspend fun addPolygonWithPoints(
        polygonModel: PolygonModel,
        points: List<PointsModel>
    ): Long {
        return appDao.addPolygonWithPoints(polygonModel, points)
    }

    override suspend fun deletePolygon(id: Long) {
        return appDao.deletePolygon(id)
    }
}