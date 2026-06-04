package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [GeoPhoto::class], version = 1, exportSchema = false)
abstract class GeoPhotoDatabase : RoomDatabase() {
    abstract fun geoPhotoDao(): GeoPhotoDao

    companion object {
        @Volatile
        private var INSTANCE: GeoPhotoDatabase? = null

        fun getDatabase(context: Context): GeoPhotoDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GeoPhotoDatabase::class.java,
                    "geostamp_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
