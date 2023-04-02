package com.arashjahani.mappolygonpointsdraw.di

import android.app.Application
import android.content.Context
import com.arashjahani.mappolygonpointsdraw.MapPolygonPointsDrawApplication
import com.arashjahani.mappolygonpointsdraw.data.DataRepository
import com.arashjahani.mappolygonpointsdraw.data.DataRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class ApplicationModule {

    @Provides
    @Singleton
    fun providesApplication(mapPolygonPointsDrawApplication: MapPolygonPointsDrawApplication): MapPolygonPointsDrawApplication {
        return mapPolygonPointsDrawApplication
    }

    @Provides
    @Singleton
    fun provideContext(application: Application): Context {
        return application
    }

    @Provides
    @Singleton
    fun provideDataRepository(dataRepositoryImpl: DataRepositoryImpl): DataRepository =dataRepositoryImpl
}