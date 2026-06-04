package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.GeoPhoto
import com.example.data.GeoPhotoDatabase
import com.example.data.GeoPhotoRepository
import com.example.ui.GeoPhotoViewModel
import com.example.ui.GeoPhotoViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = GeoPhotoDatabase.getDatabase(applicationContext)
        val repository = GeoPhotoRepository(database.geoPhotoDao())
        
        val viewModel: GeoPhotoViewModel by viewModels {
            GeoPhotoViewModelFactory(applicationContext, repository)
        }

        setContent {
            MyApplicationTheme(darkTheme = false, dynamicColor = false) {
                GeoStampAppScreen(viewModel)
            }
        }
    }
}

@SuppressLint("InlinedApi")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeoStampAppScreen(viewModel: GeoPhotoViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Screen State
    var currentTab by remember { mutableStateOf("camera") } // "camera" or "gallery"
    var selectedPhotoInspect by remember { mutableStateOf<GeoPhoto?>(null) }

    // Check Permissions
    val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    var permissionsGranted by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsResult ->
        val cameraOk = permissionsResult[Manifest.permission.CAMERA] ?: false
        val fineLocationOk = permissionsResult[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationOk = permissionsResult[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        permissionsGranted = cameraOk && (fineLocationOk || coarseLocationOk)
        if (permissionsGranted) {
            viewModel.startLocationUpdates()
        }
    }

    // Handle Toast events from Viewmodel
    val toastMessage by viewModel.toastEvent.collectAsStateWithLifecycle()
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEF7FF)),
        bottomBar = {
            if (permissionsGranted) {
                NavigationBar(
                    containerColor = Color(0xFFF3EDF7),
                    tonalElevation = 8.dp,
                    modifier = Modifier.testTag("navigation_bar")
                ) {
                    NavigationBarItem(
                        selected = currentTab == "camera",
                        onClick = { currentTab = "camera" },
                        icon = { Icon(Icons.Rounded.PhotoCamera, contentDescription = "Camera Mode") },
                        label = { Text("Camera", fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF21005D),
                            selectedTextColor = Color(0xFF21005D),
                            unselectedIconColor = Color(0xFF49454F),
                            unselectedTextColor = Color(0xFF49454F),
                            indicatorColor = Color(0xFFEADDFF)
                        ),
                        modifier = Modifier.testTag("tab_camera")
                    )
                    NavigationBarItem(
                        selected = currentTab == "gallery",
                        onClick = { currentTab = "gallery" },
                        icon = { Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Survey Gallery") },
                        label = { Text("Log Gallery", fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF21005D),
                            selectedTextColor = Color(0xFF21005D),
                            unselectedIconColor = Color(0xFF49454F),
                            unselectedTextColor = Color(0xFF49454F),
                            indicatorColor = Color(0xFFEADDFF)
                        ),
                        modifier = Modifier.testTag("tab_gallery")
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFFEF7FF))
        ) {
            if (!permissionsGranted) {
                // Onboarding permission requester layout
                PermissionRequesterWall(
                    onGrantClick = {
                        launcher.launch(requiredPermissions)
                    }
                )
            } else {
                when (currentTab) {
                    "camera" -> {
                        CameraViewLayout(
                            viewModel = viewModel,
                            onGalleryShortcutClick = {
                                currentTab = "gallery"
                            }
                        )
                    }
                    "gallery" -> {
                        GalleryViewLayout(
                            viewModel = viewModel,
                            onPhotoSelect = { photo ->
                                selectedPhotoInspect = photo
                            }
                        )
                    }
                }
            }

            // Photo full inspect sheet / dialog overlay
            selectedPhotoInspect?.let { photo ->
                PhotoInspectorOverlay(
                    photo = photo,
                    viewModel = viewModel,
                    onDismiss = { selectedPhotoInspect = null }
                )
            }
        }
    }
}

@Composable
fun PermissionRequesterWall(onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Identity logo representation
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(100.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x333B82F6), Color(0x003B82F6))
                    ),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Rounded.ShareLocation,
                contentDescription = "Geotag Cam",
                tint = Color(0xFF3B82F6),
                modifier = Modifier.size(60.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "GeoStamp Camera",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            fontFamily = FontFamily.SansSerif
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Surveys, Inspections, and Captures",
            fontSize = 14.sp,
            color = Color(0xFF3B82F6),
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Card detailing reason
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161E2E)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, Color(0xFF1E293B))
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                PermissionInfoItem(
                    icon = Icons.Rounded.PhotoCamera,
                    title = "Camera Permissions",
                    desc = "Used to capture real-time high fidelity survey photos of inspection sites."
                )

                Spacer(modifier = Modifier.height(16.dp))

                PermissionInfoItem(
                    icon = Icons.Rounded.MyLocation,
                    title = "GPS Geolocation",
                    desc = "Retrieves live latitude, longitude, and absolute altitude to automatically stamp coordinates directly onto the photo structure."
                )
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onGrantClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("grant_permissions_button"),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3B82F6),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(Icons.Rounded.LockOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("GRANT APP PERMISSIONS", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
fun PermissionInfoItem(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFF1E293B), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp))
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(desc, color = Color(0xFF94A3B8), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
fun CameraViewLayout(
    viewModel: GeoPhotoViewModel,
    onGalleryShortcutClick: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    // Live VM States
    val lat by viewModel.latitude.collectAsStateWithLifecycle()
    val lon by viewModel.longitude.collectAsStateWithLifecycle()
    val alt by viewModel.altitude.collectAsStateWithLifecycle()
    val acc by viewModel.accuracy.collectAsStateWithLifecycle()
    val address by viewModel.address.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()

    val flashMode by viewModel.flashMode.collectAsStateWithLifecycle()
    val lensFacing by viewModel.lensFacing.collectAsStateWithLifecycle()

    val stampColorHex by viewModel.stampColorHex.collectAsStateWithLifecycle()
    val stampStyle by viewModel.stampStyle.collectAsStateWithLifecycle()

    val allPhotosList by viewModel.allPhotosState.collectAsStateWithLifecycle()

    // Camera Setup Process
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(lensFacing) {
        val cameraProvider = cameraProviderFuture.get()
        cameraProvider.unbindAll()

        val preview = Preview.Builder().build()
        val capture = ImageCapture.Builder()
            .setFlashMode(flashMode)
            .build()
        imageCapture = capture

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            preview.setSurfaceProvider(previewView.surfaceProvider)
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                capture
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Dynamic Flash update when button is toggled without restarting feed
    LaunchedEffect(flashMode) {
        imageCapture?.flashMode = flashMode
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFEF7FF))
    ) {
        // 1. Bento Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFFEADDFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
                        contentDescription = "System Geolocation",
                        tint = Color(0xFF21005D),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "GeoStamp Cam",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D1B20),
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Bento Grid Active",
                        fontSize = 11.sp,
                        color = Color(0xFF6750A4),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Gallery log shortcut
            IconButton(
                onClick = onGalleryShortcutClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFF3EDF7), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoLibrary,
                    contentDescription = "Logs",
                    tint = Color(0xFF1D1B20),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 2. Viewfinder Card (Fully contained within rounded edges)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFF1D1B20))
                .border(BorderStroke(1.dp, Color(0xFFEADDFF).copy(alpha = 0.5f)), RoundedCornerShape(28.dp))
        ) {
            // Camera feed
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("camera_preview")
            )

            // Dynamic float signpost for Camera state
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF10B981), CircleShape)
                    )
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Live WYSIWYG Stamp preview overlay floating at the bottom inside the viewfinder
            LiveStampOverlayPreview(
                lat = lat,
                lon = lon,
                alt = alt,
                address = address,
                colorHex = stampColorHex,
                style = stampStyle
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Bento Info Grid (2x2 grid setup of GPS lock details, altitude details, and thumbnail shortcuts)
        BentoGridSection(
            lat = lat,
            lon = lon,
            alt = alt,
            acc = acc,
            stampStyle = stampStyle,
            stampColorHex = stampColorHex,
            lastPhotoPath = allPhotosList.firstOrNull()?.filePath,
            onGalleryClick = onGalleryShortcutClick,
            onColorSelect = { hex -> viewModel.setStampColorHex(hex) },
            onStyleSelect = { style -> viewModel.setStampStyle(style) }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Footbar / Controls area
        BentoFooterControls(
            flashMode = flashMode,
            isSaving = isSaving,
            onFlashToggle = {
                val nextFlash = when (flashMode) {
                    ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                    ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                    else -> ImageCapture.FLASH_MODE_AUTO
                }
                viewModel.setFlashMode(nextFlash)
            },
            onShutterClick = {
                imageCapture?.let { capture ->
                    viewModel.setFlashMode(flashMode) // assert state
                    capture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: androidx.camera.core.ImageProxy) {
                                viewModel.processAndSavePhoto(image)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Toast.makeText(context, "Capture Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            },
            onLensFlip = { viewModel.toggleLensFacing() }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 5. Saving / Rendering Dialog Overlay Spinner
        if (isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable(enabled = false) {}, // absorb clicks
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF7FF)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color(0xFFEADDFF)),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF6750A4),
                            strokeWidth = 4.dp,
                            modifier = Modifier.testTag("saving_progress_indicator")
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "STAMPING GEODATA...",
                            color = Color(0xFF1D1B20),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Calculating coordinates & rendering overlays",
                            color = Color(0xFF1D1B20).copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TopGpsSignalOverlay(lat: Double, lon: Double, alt: Double, acc: Float) {
    val isGoodLock = acc > 0 && acc <= 20.0f
    val isSearching = acc == 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xD30F172A)), // slate-900 85% background
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Signal Lock Visual Dot Indicator
            val dotColor = when {
                isSearching -> Color(0xFFEAB308) // Amber
                isGoodLock -> Color(0xFF10B981) // Emerald Green
                else -> Color(0xFF3B82F6) // Standard blue medium accuracy
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(dotColor, CircleShape)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isSearching) "SEARCHING FOR GPS LOCK..." else "GPS LOCK ACTIVE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "±${if (isSearching) "--" else String.format(Locale.getDefault(), "%.1fm", acc)}",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Lat: ${String.format(Locale.US, "%.5f°", lat)}",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Lon: ${String.format(Locale.US, "%.5f°", lon)}",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Alt: ${String.format(Locale.US, "%.1fm", alt)}",
                        color = Color(0xFF60A5FA),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun LiveStampOverlayPreview(
    lat: Double,
    lon: Double,
    alt: Double,
    address: String,
    colorHex: String,
    style: String
) {
    val stampColor = Color(android.graphics.Color.parseColor(colorHex))
    val todayDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        when (style) {
            "Classic" -> {
                // Retro Amber Classic Film Stamp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "$todayDate UTC",
                        color = Color(0xFFFF8C00),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = String.format(Locale.US, "LAT: %.5f°  LON: %.5f°", lat, lon),
                        color = Color(0xFFFF8C00),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (address.isNotEmpty() && address != "Retrieving location...") {
                        Text(
                            text = address,
                            color = Color(0xFFFF8C00),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
            "Minimalist" -> {
                // Minimalist Line Text
                Column(modifier = Modifier.wrapContentWidth()) {
                    Text(
                        text = "$todayDate | LAT: ${String.format(Locale.US, "%.5f°", lat)} LON: ${String.format(Locale.US, "%.5f°", lon)}",
                        color = stampColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (address.isNotEmpty() && address != "Retrieving location...") {
                        Text(
                            text = address,
                            color = stampColor.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            else -> {
                // Modern Glass Card Style
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xD9111827)), // slate-900 85% opacity
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, Color(0x33FFFFFF)),
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "GEOSTAMP PREVIEW",
                                color = stampColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp
                            )
                            Icon(
                                imageVector = Icons.Rounded.AddLocationAlt,
                                contentDescription = null,
                                tint = stampColor,
                                modifier = Modifier.size(12.dp)
                            )
                        }

                        Divider(
                            color = Color(0x22FFFFFF),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Text(
                            text = todayDate,
                            color = stampColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = String.format(Locale.US, "LAT: %.5f°  LON: %.5f°  ALT: %.1fm", lat, lon, alt),
                            color = stampColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        if (address.isNotEmpty() && address != "Retrieving location...") {
                            Text(
                                text = address,
                                color = stampColor.copy(alpha = 0.9f),
                                fontSize = 9.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StampConfigPillRow(
    selectedColor: String,
    onColorSelect: (String) -> Unit,
    selectedStyle: String,
    onStyleSelect: (String) -> Unit
) {
    val colorsList = listOf(
        "#FFFFFF" to Color.White,
        "#FFC107" to Color(0xFFFFC107), // Golden Amber
        "#00E5FF" to Color(0xFF00E5FF), // Cyber Cyan
        "#26D07C" to Color(0xFF26D07C)  // Leaf Green
    )

    val stylesList = listOf("Modern", "Classic", "Minimalist")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Line 1: Color selection swatches
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "STAMP COLOR: ",
                color = Color(0xFF94A3B8),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.width(10.dp))
            colorsList.forEach { (hex, color) ->
                val isSelected = selectedColor.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(24.dp)
                        .background(color, CircleShape)
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = Color.Blue,
                            shape = CircleShape
                        )
                        .border(
                            width = 1.dp,
                            color = Color(0xFF374151),
                            shape = CircleShape
                        )
                        .clickable { onColorSelect(hex) }
                        .testTag("color_$hex")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Line 2: Style pills
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            stylesList.forEach { style ->
                val isSelected = selectedStyle == style
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .background(
                            color = if (isSelected) Color(0xFF3B82F6) else Color(0x661F2937),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .border(
                            width = 0.5.dp,
                            color = if (isSelected) Color(0xFF60A5FA) else Color(0xFF4B5563),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onStyleSelect(style) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("style_$style"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = style.uppercase(),
                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

@Composable
fun GalleryViewLayout(
    viewModel: GeoPhotoViewModel,
    onPhotoSelect: (GeoPhoto) -> Unit
) {
    val photosList by viewModel.allPhotosState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "SURVEY LOGS",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1D1B20),
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${photosList.size} Geotagged pictures recorded",
                    fontSize = 12.sp,
                    color = Color(0xFF6750A4),
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                imageVector = Icons.Rounded.GridOn,
                contentDescription = null,
                tint = Color(0xFF6750A4)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Body Log grid list
        if (photosList.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.Camera,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color(0xFF6750A4).copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Geotagged Photos Yet",
                        color = Color(0xFF1D1B20),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Photos you capture with automatically stamped GPS meta will appear here.",
                        color = Color(0xFF49454F),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("gallery_grid")
            ) {
                items(photosList, key = { it.id }) { photo ->
                    GalleryPhotoItem(photo = photo, onClick = { onPhotoSelect(photo) })
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GalleryPhotoItem(photo: GeoPhoto, onClick: () -> Unit) {
    val dateString = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(photo.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .testTag("gallery_item_${photo.id}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF7FF)),
        border = BorderStroke(1.dp, Color(0xFFEADDFF))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Image loaded using Coil
            AsyncImage(
                model = photo.filePath,
                contentDescription = photo.address,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Dynamic Styling Info Overlay Banner at bottom of grid item
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC000000))
                        )
                    )
                    .padding(10.dp)
            ) {
                Column {
                    Text(
                        text = photo.notes.ifEmpty { "Survey Recording" },
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dateString,
                            color = Color(0xFFE8DEF8),
                            fontSize = 10.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.MyLocation,
                                contentDescription = null,
                                tint = Color(0xFFEADDFF),
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = String.format(Locale.US, "%.3f°", photo.latitude),
                                color = Color(0xFFEADDFF),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoInspectorOverlay(
    photo: GeoPhoto,
    viewModel: GeoPhotoViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isDeleteWarningVisible by remember { mutableStateOf(false) }

    // Notes controller
    var localNotesValue by remember { mutableStateOf(photo.notes) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss (z)", Locale.getDefault()).format(Date(photo.timestamp))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color(0xFF1D1B20))
                        }
                    },
                    title = {
                        Text(
                            "SURVEY RECORD DETAILS",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D1B20),
                            letterSpacing = 1.2.sp
                        )
                    },
                    actions = {
                        // Delete Action Button
                        IconButton(
                            onClick = { isDeleteWarningVisible = true },
                            modifier = Modifier.testTag("inspect_delete")
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete Photo", tint = Color(0xFFB3261E))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF3EDF7))
                )
            },
            containerColor = Color(0xFFFEF7FF),
            modifier = Modifier.fillMaxSize()
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. Image Viewer Frame
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .border(1.dp, Color(0xFFEADDFF), RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = photo.filePath,
                            contentDescription = photo.address,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Float button to open in device photos if they want to
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(12.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    try {
                                        val file = File(photo.filePath)
                                        val uri: Uri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            file
                                        )
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, "image/jpeg")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "No app available to view images", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(Icons.Rounded.Fullscreen, contentDescription = "Fullscreen", tint = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 2. Action Grid (Share/Email, Copy Coordinates, etc.)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Share Image
                    Button(
                        onClick = {
                            try {
                                val file = File(photo.filePath)
                                val uri: Uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "image/jpeg"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Geotagged Survey Capture")
                                    val mailText = """
                                        Geotagged Photo Captured via GeoStamp Cam
                                        Notes: ${photo.notes.ifEmpty { "N/A" }}
                                        Coordinates: ${photo.latitude}, ${photo.longitude}
                                        Altitude: ${photo.altitude}m
                                        Address: ${photo.address}
                                        Timestamp: $formattedDate
                                    """.trimIndent()
                                    putExtra(Intent.EXTRA_TEXT, mailText)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Geotagged Photo"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error sharing: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("inspect_share")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Share", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SHARE JPG", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Copy GPS
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText(
                                "GPS Coordinates",
                                "${photo.latitude}, ${photo.longitude}"
                            )
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Coordinates Copied!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF6750A4)),
                        border = BorderStroke(1.dp, Color(0xFF6750A4)),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy Coordinates", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("COPY GPS", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 3. Metadata details board Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color(0xFFEADDFF))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "GEOTAGGED PARAMETERS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF6750A4),
                            letterSpacing = 1.sp
                        )

                        Divider(color = Color(0xFFEADDFF), modifier = Modifier.padding(vertical = 8.dp))

                        MetaTextLine(Icons.Rounded.AccessTime, "UTC Timestamp", formattedDate)
                        MetaTextLine(Icons.Rounded.MyLocation, "Latitude", String.format(Locale.US, "%.7f°", photo.latitude))
                        MetaTextLine(Icons.Rounded.MyLocation, "Longitude", String.format(Locale.US, "%.7f°", photo.longitude))
                        MetaTextLine(Icons.Rounded.Height, "Altitude (Elevation)", "${photo.altitude} meters")
                        MetaTextLine(Icons.Rounded.Signpost, "Resolved Address", photo.address)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 4. Edit notes text block
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, Color(0xFFEADDFF))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "SURVEY INSPECTION NOTES",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF6750A4),
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = localNotesValue,
                            onValueChange = {
                                localNotesValue = it
                                // Save notes into room dynamically
                                viewModel.updateNotes(photo.id, it)
                            },
                            placeholder = { Text("E.g., Cracks on north foundation beam, Pipeline pressure check...", color = Color(0xFF49454F).copy(alpha = 0.6f)) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .testTag("notes_input_field"),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color(0xFF1D1B20),
                                unfocusedTextColor = Color(0xFF1D1B20),
                                focusedBorderColor = Color(0xFF6750A4),
                                unfocusedBorderColor = Color(0xFFEADDFF),
                                focusedContainerColor = Color(0xFFF3EDF7),
                                unfocusedContainerColor = Color(0xFFF3EDF7)
                            ),
                            keyboardOptions = KeyboardOptions.Default.copy(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done
                            )
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(30.dp))
            }
        }

        // Delete alert notification dialog
        if (isDeleteWarningVisible) {
            AlertDialog(
                onDismissRequest = { isDeleteWarningVisible = false },
                title = { Text("Delete Photo?", fontWeight = FontWeight.Bold) },
                text = { Text("This will permanently delete this stamped picture file and database record. This action is irreversible.") },
                confirmButton = {
                    Button(
                        onClick = {
                            isDeleteWarningVisible = false
                            viewModel.deletePhoto(photo)
                            onDismiss() // Close inspector
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E)),
                        modifier = Modifier.testTag("confirm_delete")
                    ) {
                        Text("DELETE", fontWeight = FontWeight.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { isDeleteWarningVisible = false }) {
                        Text("CANCEL", color = Color(0xFF6750A4))
                    }
                },
                containerColor = Color(0xFFFEF7FF),
                titleContentColor = Color(0xFF1D1B20),
                textContentColor = Color(0xFF49454F)
            )
        }
    }
}

@Composable
fun MetaTextLine(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF6750A4),
            modifier = Modifier
                .padding(top = 2.dp)
                .size(15.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(label.uppercase(), color = Color(0xFF6750A4).copy(alpha = 0.8f), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Text(value, color = Color(0xFF1D1B20), fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp)
        }
    }
}

@Composable
fun BentoGridSection(
    lat: Double,
    lon: Double,
    alt: Double,
    acc: Float,
    stampStyle: String,
    stampColorHex: String,
    lastPhotoPath: String?,
    onGalleryClick: () -> Unit,
    onColorSelect: (String) -> Unit,
    onStyleSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth().height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 1: GPS Lock / Precision Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEADDFF)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MyLocation,
                            contentDescription = null,
                            tint = Color(0xFF21005D),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "ACCURACY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF21005D),
                            letterSpacing = 0.5.sp
                        )
                    }
                    Column {
                        val accuracyText = if (acc > 0) String.format(Locale.getDefault(), "±%.1fm", acc) else "±--m"
                        Text(
                            text = accuracyText,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF21005D)
                        )
                        Text(
                            text = if (acc > 0 && acc <= 15f) "GPS Fix: Strong" else if (acc > 15f) "GPS Fix: Weak" else "GPS Searching...",
                            fontSize = 10.sp,
                            color = Color(0xFF21005D).copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Card 2: Stamp Config Quick Selector Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8DEF8)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Style,
                            contentDescription = null,
                            tint = Color(0xFF1D192B),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "STAMP SETUP",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D192B),
                            letterSpacing = 0.5.sp
                        )
                    }
                    
                    // Display details of stamp
                    Column {
                        // Horizontal style scroll-like row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("Modern", "Classic", "Minimalist").forEach { style ->
                                val isSelected = stampStyle == style
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (isSelected) Color(0xFF6750A4) else Color(0x1F000000),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .clickable { onStyleSelect(style) }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = style.uppercase(),
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else Color(0xFF1D192B)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))

                        // Mini row of color option swatches
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val colorsList = listOf("#FFFFFF", "#FFC107", "#00E5FF", "#26D07C")
                            colorsList.forEach { hex ->
                                val swatchColor = Color(android.graphics.Color.parseColor(hex))
                                val isSelected = stampColorHex.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(swatchColor, CircleShape)
                                        .border(
                                            width = if (isSelected) 1.5.dp else 0.5.dp,
                                            color = if (isSelected) Color(0xFF1D192B) else Color.Gray.copy(alpha = 0.5f),
                                            shape = CircleShape
                                        )
                                        .clickable { onColorSelect(hex) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth().height(96.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Card 3: Altitude & Position Info
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD8E4)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Explore,
                            contentDescription = null,
                            tint = Color(0xFF31111D),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "POSITION",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF31111D),
                            letterSpacing = 0.5.sp
                        )
                    }
                    Column {
                        val altText = if (alt != 0.0) String.format(Locale.getDefault(), "%.1fm Alt", alt) else "0.0m Alt"
                        Text(
                            text = altText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF31111D)
                        )
                        // Subtext coordinate snippets
                        Text(
                            text = String.format(Locale.US, "%.4f, %.4f", lat, lon),
                            fontSize = 10.sp,
                            color = Color(0xFF31111D).copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Card 4: Last Shot Preview of album
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onGalleryClick() },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF49454F)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (lastPhotoPath != null) {
                        AsyncImage(
                            model = lastPhotoPath,
                            contentDescription = "Last captured",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Rounded.Image,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "No captures",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Style selector pill overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Last Capture",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BentoFooterControls(
    flashMode: Int,
    isSaving: Boolean,
    onFlashToggle: () -> Unit,
    onShutterClick: () -> Unit,
    onLensFlip: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Flip Camera Button
        IconButton(
            onClick = onLensFlip,
            modifier = Modifier
                .size(48.dp)
                .background(Color.Transparent, CircleShape)
                .border(1.dp, Color(0xFF1D1B20).copy(alpha = 0.12f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Rounded.FlipCameraAndroid,
                contentDescription = "Flip Lens",
                tint = Color(0xFF1D1B20),
                modifier = Modifier.size(22.dp)
            )
        }

        // 2. Primary Shutter Button with purple accent border
        Box(
            modifier = Modifier
                .size(76.dp)
                .border(2.dp, Color(0xFF6750A4), CircleShape)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(if (isSaving) Color(0xFF4F378B) else Color(0xFF6750A4))
                    .clickable(enabled = !isSaving) { onShutterClick() }
                    .testTag("shutter_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PhotoCamera,
                    contentDescription = "Capture stamp shot",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        // 3. Flash Toggle
        IconButton(
            onClick = onFlashToggle,
            modifier = Modifier
                .size(48.dp)
                .background(Color.Transparent, CircleShape)
                .border(1.dp, Color(0xFF1D1B20).copy(alpha = 0.12f), CircleShape)
        ) {
            val icon = when (flashMode) {
                ImageCapture.FLASH_MODE_ON -> Icons.Rounded.FlashOn
                ImageCapture.FLASH_MODE_OFF -> Icons.Rounded.FlashOff
                else -> Icons.Rounded.FlashAuto
            }
            Icon(
                imageVector = icon,
                contentDescription = "Flash Toggle",
                tint = Color(0xFF1D1B20),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

