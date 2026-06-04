package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "geo_photos")
data class GeoPhoto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val filePath: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val address: String,
    val notes: String = "",
    val stampColorHex: String = "#FFFFFF",
    val stampStyle: String = "Modern"
)
