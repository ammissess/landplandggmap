package com.hung.landplanggmap

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Không cần initialize ResourcesLoader nữa vì Mapbox version mới
        // đã tự động handle việc này
    }
}