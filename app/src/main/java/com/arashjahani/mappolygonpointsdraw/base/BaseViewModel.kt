package com.arashjahani.mappolygonpointsdraw.base

import androidx.lifecycle.ViewModel
import com.arashjahani.mappolygonpointsdraw.data.DataRepository

open class BaseViewModel constructor(val dataRepository: DataRepository) : ViewModel() {


    companion object {

        val TAG = this::class.java.simpleName

    }
}