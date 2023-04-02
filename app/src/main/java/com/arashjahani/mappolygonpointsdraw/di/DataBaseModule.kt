package com.arashjahani.mappolygonpointsdraw.di

import android.content.Context
import androidx.room.Room
import com.arashjahani.mappolygonpointsdraw.data.db.AppDao
import com.arashjahani.mappolygonpointsdraw.data.db.RoomDB
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@InstallIn(SingletonComponent::class)
@Module
class DataBaseModule {

    @Provides
    @Singleton
    open fun provideApplicationDatabase(context: Context): RoomDB {
        var applicationDatabase: RoomDB =
            Room.databaseBuilder(context, RoomDB::class.java, "database")
                //.addMigrations(RoomDB.MIGRATION_1_2)
                .allowMainThreadQueries()
                .fallbackToDestructiveMigration()
                .build()
        return applicationDatabase
    }

    @Provides
    @Singleton
    open fun provideVenueDao(applicationDatabase: RoomDB): AppDao {
        return applicationDatabase.appDao()
    }

}