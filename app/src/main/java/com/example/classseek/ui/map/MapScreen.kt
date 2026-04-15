package com.example.classseek.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

enum class MarkerCategory(val label: String, val icon: ImageVector, val color: Color) {
    ALL("All", Icons.Default.Place, Color.Gray),
    BUILDING("Building", Icons.Default.OtherHouses, Color(0xFF2596BE)), // Tealish
    STUDENT_SERVICE("Student Service", Icons.Default.School, Color(0xFFE580FF)),
    DINING("Dining", Icons.Default.Restaurant, Color(0xFFFFA500)) // Orange
}

data class MapPlace(
    val name: String,
    val location: LatLng,
    val category: MarkerCategory
)

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(MarkerCategory.ALL) }
    var isListVisible by remember { mutableStateOf(false) }
    var selectedPlace by remember { mutableStateOf<MapPlace?>(null) }
    var mapType by remember { mutableStateOf(MapType.SATELLITE) }
    var isFilterMenuExpanded by remember { mutableStateOf(false) }

    // State for compass heading
    var heading by remember { mutableStateOf(0f) }

    val bounds = remember {
        LatLngBounds(
            LatLng(34.14521297909, -119.0623117489),
            LatLng(34.1736221957, -119.0200830498)
        )
    }

    // List of places loaded from Firestore
    var places by remember { mutableStateOf<List<MapPlace>>(emptyList()) }

    // Filtered list based on selected category
    val displayPlaces = remember(places, selectedCategory) {
        places.filter { place ->
            selectedCategory == MarkerCategory.ALL || place.category == selectedCategory
        }
    }

    LaunchedEffect(Unit) {
        val db = Firebase.firestore
        db.collection("mapScreenData")
            .get()
            .addOnSuccessListener { result ->
                val fetchedPlaces = result.mapNotNull { doc ->
                    try {
                        val name = doc.getString("name") ?: return@mapNotNull null
                        val lat = doc.getDouble("latitude") ?: 0.0
                        val lng = doc.getDouble("longitude") ?: 0.0
                        val markerType = doc.getString("markerType") ?: "BUILDING"
                        
                        MapPlace(
                            name = name,
                            location = LatLng(lat, lng),
                            category = try {
                                MarkerCategory.valueOf(markerType)
                            } catch (e: Exception) {
                                MarkerCategory.BUILDING
                            }
                        )
                    } catch (e: Exception) {
                        Log.e("MapScreen", "Error parsing document ${doc.id}", e)
                        null
                    }
                }
                places = fetchedPlaces
            }
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var location by remember { mutableStateOf<Location?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(34.16206611807, -119.0434737072), 17f
        ) // lat long is center of campus
    }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasPermission = isGranted }
    )

    // Sensor logic for directional marker
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                    val rotationMatrix = FloatArray(9)
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    val orientation = FloatArray(3)
                    SensorManager.getOrientation(rotationMatrix, orientation)
                    // orientation[0] is azimuth (heading around the z-axis) in radians
                    heading = Math.toDegrees(orientation[0].toDouble()).toFloat()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    DisposableEffect(hasPermission) {
        if (hasPermission) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { newLoc ->
                        location = newLoc
                    }
                }
            }
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            onDispose {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        } else {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            onDispose {}
        }
    }

    // Box to put the map and search bar on
    Box(modifier = modifier.fillMaxSize()) {
        if (hasPermission && location != null) {
            val currentLatLng = LatLng(location!!.latitude, location!!.longitude)

            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(
                    mapType = mapType,
                    latLngBoundsForCameraTarget = bounds,
                    minZoomPreference = 14.5f,
                    mapStyleOptions = MapStyleOptions(
                        """
                        [
                          { "elementType": "labels", "stylers": [ { "visibility": "off" } ] },
                          { "featureType": "poi", "stylers": [ { "visibility": "off" } ] },
                          { "featureType": "transit", "stylers": [ { "visibility": "off" } ] }
                        ]
                        """.trimIndent()
                    )
                ),
                uiSettings = MapUiSettings(mapToolbarEnabled = false),
                onMapClick = { selectedPlace = null }
            ) {
                // Directional User Location Marker
                MarkerComposable(
                    state = rememberMarkerState(position = currentLatLng),
                    anchor = Offset(0.5f, 0.5f),
                    onClick = { true }
                ) {
                    // Using Navigation icon (arrow) and rotating it based on heading
                    // Navigation icon naturally points top-right, so we add an offset if needed, 
                    // but usually, an upward arrow like 'Navigation' works best.
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "User Location",
                        tint = Color(0xFF4285F4), // Google Blue
                        modifier = Modifier
                            .size(32.dp)
                            .rotate(heading)
                    )
                }

                places.forEach { place ->
                    val isSelected = selectedPlace?.name == place.name
                    val isInSelectedCategory = selectedCategory == MarkerCategory.ALL || place.category == selectedCategory
                    
                    val markerAlpha = if (selectedPlace != null) {
                        if (isSelected) 1.0f else 0.35f
                    } else if (selectedCategory != MarkerCategory.ALL) {
                        if (isInSelectedCategory) 1.0f else 0.35f
                    } else {
                        1.0f
                    }

                    key(place.name) {
                        MarkerComposable(
                            state = rememberMarkerState(position = place.location),
                            alpha = markerAlpha,
                            anchor = Offset(0.5f, 1.0f),
                            onClick = {
                                selectedPlace = place
                                true
                            }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Surface(
                                    shape = RoundedCornerShape(3.3.dp),
                                    color = Color.White.copy(alpha = if (isSelected) 0.95f else 0.85f),
                                    modifier = Modifier.padding(bottom = 1.4.dp)
                                ) {
                                    Text(
                                        text = place.name,
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        modifier = Modifier.padding(horizontal = 3.6.dp, vertical = 1.5.dp),
                                        color = Color.Black
                                    )
                                }
                                Icon(
                                    imageVector = place.category.icon,
                                    contentDescription = null,
                                    tint = place.category.color,
                                    modifier = Modifier.size(19.8.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else if (!hasPermission) {
            Text("Location permission required.", Modifier.align(Alignment.Center))
        } else {
            Text("Fetching live location...", Modifier.align(Alignment.Center))
        }

        // Search bar and filter menu
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { newValue ->
                        searchQuery = newValue
                        val match = if (newValue.isNotEmpty()) {
                            places.find { it.name.contains(newValue, ignoreCase = true) }
                        } else null
                        
                        if (match != null) {
                            if (match != selectedPlace) {
                                selectedPlace = match
                                scope.launch {
                                    cameraPositionState.animate(
                                        update = CameraUpdateFactory.newLatLngZoom(match.location, 18f),
                                        durationMs = 1000
                                    )
                                }
                            }
                        } else {
                            selectedPlace = null
                        }
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search facilities...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                selectedPlace = null
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.9f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.9f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Dropdown menu for filters
                Box {
                    FloatingActionButton(
                        onClick = { isFilterMenuExpanded = true },
                        modifier = Modifier.size(48.dp),
                        containerColor = Color.White.copy(alpha = 0.9f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(24.dp),
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter Options"
                        )
                    }

                    DropdownMenu(
                        expanded = isFilterMenuExpanded,
                        onDismissRequest = { isFilterMenuExpanded = false },
                        modifier = Modifier.background(Color.White.copy(alpha = 0.95f))
                    ) {
                        MarkerCategory.entries.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.label) },
                                onClick = {
                                    selectedCategory = category
                                    selectedPlace = null
                                    isFilterMenuExpanded = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = category.icon,
                                        contentDescription = null,
                                        tint = category.color,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                trailingIcon = if (selectedCategory == category) {
                                    { Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }
        }

        // This is the list of buildings/services and map type toggle
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                if (isListVisible) {
                    Card(
                        modifier = Modifier
                            .width(250.dp)
                            .heightIn(max = 400.dp)
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = if (selectedCategory == MarkerCategory.ALL) "Campus Locations" else "${selectedCategory.label} Locations",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(displayPlaces) { place ->
                                    ListItem(
                                        headlineContent = { Text(place.name) },
                                        supportingContent = { Text(place.category.label) },
                                        leadingContent = {
                                            Icon(
                                                imageVector = place.category.icon,
                                                contentDescription = null,
                                                tint = place.category.color
                                            )
                                        },
                                        modifier = Modifier.clickable {
                                            scope.launch {
                                                selectedPlace = place
                                                cameraPositionState.animate(
                                                    update = CameraUpdateFactory.newLatLngZoom(place.location, 18f),
                                                    durationMs = 1000
                                                )
                                            }
                                            isListVisible = false
                                        }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FloatingActionButton(
                        onClick = { isListVisible = !isListVisible },
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = if (isListVisible) Icons.Default.Close else Icons.AutoMirrored.Filled.List,
                            contentDescription = "Toggle List"
                        )
                    }

                    FloatingActionButton(
                        onClick = { mapType = if (mapType == MapType.SATELLITE) MapType.NORMAL else MapType.SATELLITE },
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Toggle Map Type"
                        )
                    }
                }
            }
        }
    }
}
