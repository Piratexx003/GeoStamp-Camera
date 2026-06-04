package com.example.data

import kotlinx.coroutines.flow.Flow

class GeoPhotoRepository(private val geoPhotoDao: GeoPhotoDao) {
    val allPhotos: Flow<List<GeoPhoto>> = geoPhotoDao.getAllPhotos()

    suspend fun insert(photo: GeoPhoto): Long {
        return geoPhotoDao.insertPhoto(photo)
    }

    suspend fun update(photo: GeoPhoto) {
        geoPhotoDao.updatePhoto(photo)
    }

    suspend fun updateNotes(id: Long, notes: String) {
        geoPhotoDao.updateNotes(id, notes)
    }

    suspend fun delete(photo: GeoPhoto) {
        geoPhotoDao.deletePhoto(photo)
    }

    suspend fun deleteById(id: Long) {
        geoPhotoDao.deleteById(id)
    }
}
