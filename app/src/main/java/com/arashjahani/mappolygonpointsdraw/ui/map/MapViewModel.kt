package com.arashjahani.mappolygonpointsdraw.ui.map

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.arashjahani.mappolygonpointsdraw.base.BaseViewModel
import com.arashjahani.mappolygonpointsdraw.data.DataRepository
import com.arashjahani.mappolygonpointsdraw.data.entity.PointsModel
import com.arashjahani.mappolygonpointsdraw.data.entity.PolygonModel
import com.arashjahani.mappolygonpointsdraw.data.entity.PolygonWithPoints
import com.arashjahani.mappolygonpointsdraw.utils.centerOfPolygon
import com.arashjahani.mappolygonpointsdraw.utils.getTime
import com.mapbox.geojson.Point
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(dataRepository: DataRepository) :
    BaseViewModel(dataRepository) {


    fun savePolygonWithPoints(area:Long,points: List<Point>) {

        var polygonCenter=points.centerOfPolygon()

        var polygonModel = PolygonModel(0, getTime(), area, polygonCenter.latitude(), polygonCenter.longitude())
        var pointsModels = ArrayList<PointsModel>()

        points.forEach {
            pointsModels.add(PointsModel(0, 0, it.latitude(), it.longitude()))
        }

        viewModelScope.launch {
            var result = dataRepository.addPolygonWithPoints(polygonModel, pointsModels)
            Log.v(TAG, result.toString())
        }
    }

    fun getAllPolygons(): Flow<List<PolygonWithPoints>> {
        return dataRepository.getPolygonsWithPoints()
    }

    fun deletePolygon(id:Long){
        viewModelScope.launch {
            dataRepository.deletePolygon(id)
        }
    }

}