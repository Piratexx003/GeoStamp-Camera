package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.GeoPhoto
import com.example.data.GeoPhotoRepository
import com.example.utils.ImageStamper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale

class GeoPhotoViewModel(
    private val context: Context,
    private val repository: GeoPhotoRepository
) : ViewModel() {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Room Database Flow
    val allPhotosState: StateFlow<List<GeoPhoto>> = repository.allPhotos
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Location State
    private val _latitude = MutableStateFlow(0.0)
    val latitude: StateFlow<Double> = _latitude.asStateFlow()

    private val _longitude = MutableStateFlow(0.0)
    val longitude: StateFlow<Double> = _longitude.asStateFlow()

    private val _altitude = MutableStateFlow(0.0)
    val altitude: StateFlow<Double> = _altitude.asStateFlow()

    private val _accuracy = MutableStateFlow(0f)
    val accuracy: StateFlow<Float> = _accuracy.asStateFlow()

    private val _address = MutableStateFlow("Retrieving location...")
    val address: StateFlow<String> = _address.asStateFlow()

    // Camera Configuration States
    private val _flashMode = MutableStateFlow(ImageCapture.FLASH_MODE_AUTO)
    val flashMode: StateFlow<Int> = _flashMode.asStateFlow()

    private val _lensFacing = MutableStateFlow(CameraSelector.LENS_FACING_BACK)
    val lensFacing: StateFlow<Int> = _lensFacing.asStateFlow()

    // Style Configuration States
    private val _stampColorHex = MutableStateFlow("#FFFFFF")
    val stampColorHex: StateFlow<String> = _stampColorHex.asStateFlow()

    private val _stampStyle = MutableStateFlow("Modern")
    val stampStyle: StateFlow<String> = _stampStyle.asStateFlow()

    // Saving State Overlay
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    // UI Toast Messages or Notify states
    private val _toastEvent = MutableStateFlow<String?>(null)
    val toastEvent: StateFlow<String?> = _toastEvent.asStateFlow()

    private var locationCallback: LocationCallback? = null

    init {
        startLocationUpdates()
    }

    fun clearToast() {
        _toastEvent.value = null
    }

    fun setFlashMode(mode: Int) {
        _flashMode.value = mode
    }

    fun toggleLensFacing() {
        _lensFacing.value = if (_lensFacing.value == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
    }

    fun setStampColorHex(hex: String) {
        _stampColorHex.value = hex
    }

    fun setStampStyle(style: String) {
        _stampStyle.value = style
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        try {
            // Stop any existing loop
            stopLocationUpdates()

            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000L)
                .setMinUpdateIntervalMillis(2000L)
                .build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    updateLocationState(location)
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )

            // Immediately check last location as a quick starting point
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    updateLocationState(location)
                }
            }
        } catch (e: Exception) {
            _address.value = "GPS Service Unavailable"
        }
    }

    private fun updateLocationState(location: Location) {
        _latitude.value = location.latitude
        _longitude.value = location.longitude
        _altitude.value = location.altitude
        _accuracy.value = location.accuracy
        resolveAddress(location.latitude, location.longitude)
    }

    private fun stopLocationUpdates() {
        locationCallback?.let {
            fusedLocationClient.removeLocationUpdates(it)
        }
        locationCallback = null
    }

    private fun resolveAddress(lat: Double, lon: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addressObj = addresses[0]
                    val streetLine = addressObj.getAddressLine(0) ?: ""
                    // Keep address line clean and relatively short for displaying
                    _address.value = streetLine
                } else {
                    _address.value = "Coordinates Captured (Street details unclear)"
                }
            } catch (e: Exception) {
                _address.value = "Coordinates Captured (Address resolution offline)"
            }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    fun processAndSavePhoto(imageProxy: ImageProxy) {
        _isSaving.value = true
        viewModelScope.launch(Dispatchers.Default) {
            try {
                // Determine orientation degrees to rotate Bitmap if necessary
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees

                // Extract Bitmap from imageProxy
                val buffer = imageProxy.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                
                var originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                
                // Rotated to standard straight portrait or orientation
                if (rotationDegrees != 0) {
                    val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                    // Also mirror if we took a selfie!
                    if (_lensFacing.value == CameraSelector.LENS_FACING_FRONT) {
                        matrix.postScale(-1f, 1f, originalBitmap.width / 2f, originalBitmap.height / 2f)
                    }
                    val rotated = Bitmap.createBitmap(
                        originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                    )
                    originalBitmap.recycle()
                    originalBitmap = rotated
                } else if (_lensFacing.value == CameraSelector.LENS_FACING_FRONT) {
                    // Mirror face front
                    val matrix = Matrix().apply { postScale(-1f, 1f, originalBitmap.width / 2f, originalBitmap.height / 2f) }
                    val mirrored = Bitmap.createBitmap(
                        originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                    )
                    originalBitmap.recycle()
                    originalBitmap = mirrored
                }

                // Draw stamp values
                val stampedBitmap = ImageStamper.stampImage(
                    bitmap = originalBitmap,
                    timestamp = System.currentTimeMillis(),
                    latitude = _latitude.value,
                    longitude = _longitude.value,
                    altitude = _altitude.value,
                    address = _address.value,
                    stampColorHex = _stampColorHex.value,
                    stampStyle = _stampStyle.value
                )

                originalBitmap.recycle()

                // Save stamped photo to files workspace
                val storageDir = File(context.filesDir, "geostamp_photos").apply {
                    if (!exists()) mkdirs()
                }
                val filename = "GEO_${System.currentTimeMillis()}.jpg"
                val photoFile = File(storageDir, filename)

                FileOutputStream(photoFile).use { out ->
                    stampedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }

                stampedBitmap.recycle()

                // Insert into Database Room
                val newGeoPhoto = GeoPhoto(
                    filePath = photoFile.absolutePath,
                    timestamp = System.currentTimeMillis(),
                    latitude = _latitude.value,
                    longitude = _longitude.value,
                    altitude = _altitude.value,
                    address = _address.value,
                    stampColorHex = _stampColorHex.value,
                    stampStyle = _stampStyle.value
                )

                repository.insert(newGeoPhoto)

                withContext(Dispatchers.Main) {
                    _toastEvent.value = "Photo Stamped & Saved!"
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    _toastEvent.value = "Failed to stamp photo: ${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    _isSaving.value = false
                    imageProxy.close()
                }
            }
        }
    }

    fun updateNotes(photoId: Long, notes: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateNotes(photoId, notes)
        }
    }

    fun deletePhoto(photo: GeoPhoto) {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete actual file from device storage as well to keep clean space
            try {
                val file = File(photo.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            repository.delete(photo)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationUpdates()
    }
}

class GeoPhotoViewModelFactory(
    private val context: Context,
    private val repository: GeoPhotoRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GeoPhotoViewModel::class.java)) {
            return GeoPhotoViewModel(context, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
