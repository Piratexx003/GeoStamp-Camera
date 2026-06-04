package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GeoPhotoDao {
    @Query("SELECT * FROM geo_photos ORDER BY timestamp DESC")
    fun getAllPhotos(): Flow<List<GeoPhoto>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: GeoPhoto): Long

    @Update
    suspend fun updatePhoto(photo: GeoPhoto)

    @Query("UPDATE geo_photos SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: Long, notes: String)

    @Delete
    suspend fun deletePhoto(photo: GeoPhoto)

    @Query("DELETE FROM geo_photos WHERE id = :id")
    suspend fun deleteById(id: Long)
}
