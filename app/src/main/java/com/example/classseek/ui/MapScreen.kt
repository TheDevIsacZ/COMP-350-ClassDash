package com.example.classseek.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

enum class MarkerCategory(val label: String) {
    ALL("All"),
    BUILDING("Building"),
    STUDENT_SERVICE("Student Service"),
    DINING("Dining")
}

// temp data class to represent a building on the map
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
    var searchQuery by remember { mutableStateOf("") } // tracks the search text
    var selectedCategory by remember { mutableStateOf(MarkerCategory.ALL) }
    var isListVisible by remember { mutableStateOf(false) }
    var selectedPlace by remember { mutableStateOf<MapPlace?>(null) }

    // VERY TEMPORARY LIST BEFORE FIRESTORE
    val places = remember {
        listOf(
            MapPlace("West Bell Tower", LatLng(34.160741, -119.043908), MarkerCategory.BUILDING),
            MapPlace("East Bell Tower", LatLng(34.16135, -119.04195), MarkerCategory.BUILDING),
            MapPlace("Ojai Hall", LatLng(34.16164, -119.04253), MarkerCategory.BUILDING),
            MapPlace("Sierra Hall", LatLng(34.162240, -119.044611), MarkerCategory.BUILDING),
            MapPlace("Del Norte Hall", LatLng(34.163146, -119.044120), MarkerCategory.BUILDING),
            MapPlace("Napa Hall", LatLng(34.163696, -119.045389), MarkerCategory.BUILDING),
            MapPlace("Student Union", LatLng(34.16147, -119.04409), MarkerCategory.STUDENT_SERVICE),
            MapPlace("Tortillas Grill", LatLng(34.16300, -119.03939), MarkerCategory.DINING),
            MapPlace("Mom Wong Kitchen", LatLng(34.16280, -119.03934), MarkerCategory.DINING),
            MapPlace("Richard R Rush Hall", LatLng(34.16259, -119.04344), MarkerCategory.BUILDING),
            MapPlace("Academic Advising", LatLng(34.16118, -119.04292), MarkerCategory.STUDENT_SERVICE),
            MapPlace("Madera Hall", LatLng(34.16219, -119.04407), MarkerCategory.BUILDING),
            MapPlace("Solano Hall", LatLng(34.16335, -119.04513), MarkerCategory.BUILDING),
            MapPlace("Manzanita Hall", LatLng(34.16274, -119.04505), MarkerCategory.BUILDING),
            MapPlace("Chaparral Hall", LatLng(34.16209, -119.04570), MarkerCategory.BUILDING),
            MapPlace("Gateway Hall", LatLng(34.16460, -119.04458), MarkerCategory.BUILDING),
            MapPlace("Marin Hall", LatLng(34.16445, -119.04532), MarkerCategory.BUILDING),
            MapPlace("El Dorado Hall", LatLng(34.16421, -119.04714), MarkerCategory.BUILDING),
            MapPlace("Enrollment Ctr", LatLng(34.16407, -119.04224), MarkerCategory.STUDENT_SERVICE),
            MapPlace("Student Health Services", LatLng(34.16399, -119.04109), MarkerCategory.STUDENT_SERVICE),
            MapPlace("John Spoor Broome Library", LatLng(34.16270, -119.04086), MarkerCategory.BUILDING),
            MapPlace("Learning Resource Center", LatLng(34.16257, -119.04067), MarkerCategory.STUDENT_SERVICE),
            MapPlace("Malibu Hall", LatLng(34.16122, -119.04098), MarkerCategory.BUILDING),
            MapPlace("Arroyo Hall", LatLng(34.16038, -119.04496), MarkerCategory.BUILDING),
            MapPlace("Recreational Center", LatLng(34.16062, -119.04519), MarkerCategory.STUDENT_SERVICE),
            MapPlace("Aliso Hall", LatLng(34.16110, -119.04531), MarkerCategory.BUILDING),
            MapPlace("Chaparral Hall", LatLng(34.16207, -119.04566), MarkerCategory.BUILDING),
            MapPlace("Ironwood Hall", LatLng(34.16256, -119.04654), MarkerCategory.BUILDING),
            MapPlace("Modoc Hall", LatLng(34.16408, -119.04839), MarkerCategory.BUILDING),
            MapPlace("Islands Cafe", LatLng(34.16031, -119.04186), MarkerCategory.DINING),
            MapPlace("Anacapa Village", LatLng(34.15938, -119.04491), MarkerCategory.BUILDING),
            MapPlace("Lindero Hall", LatLng(34.15948, -119.04143), MarkerCategory.BUILDING),
        )
    }

    // Filter places is used to update the list of places currently displayed on map (because of filter AND selection)
    val filteredPlaces = remember(searchQuery, selectedCategory) {
        places.filter { place ->
            val matchesSearch = place.name.contains(searchQuery, ignoreCase = true)
            val matchesCategory = selectedCategory == MarkerCategory.ALL || place.category == selectedCategory
            matchesSearch && matchesCategory
        }
    }

    // Clear selection if the filtered list no longer contains the selected place
    LaunchedEffect(filteredPlaces) {
        if (selectedPlace != null && !filteredPlaces.any { it.name == selectedPlace?.name }) {
            selectedPlace = null
        }
    }

    // fusedLocationClient is for access to Google Play services location API
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var location by remember { mutableStateOf<Location?>(null) }
    
    // Map control is all based in cameraPositionState
    val cameraPositionState = rememberCameraPositionState()
    var hasCenteredCamera by remember { mutableStateOf(false) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // launcher requests the necessary location permissions from the user
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> hasPermission = isGranted }
    )

    // DisposableEffect starts tracking when the screen is open and when it is not
    DisposableEffect(hasPermission) {
        if (hasPermission) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { newLoc ->
                        location = newLoc // Updates the location on first open

                        if (!hasCenteredCamera) {
                            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                                LatLng(newLoc.latitude, newLoc.longitude), 17f
                            )
                            hasCenteredCamera = true
                        }
                    }
                }
            }
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            onDispose {
                // Stops location updates when off map screen
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        } else {
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) // this displays location request
            onDispose {}
        }
    }

    // Box to put the map and search bar on
    Box(modifier = modifier.fillMaxSize()) {
        if (hasPermission && location != null) {
            val currentLatLng = LatLng(location!!.latitude, location!!.longitude)

            GoogleMap( // GoogleMap creates the map
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(mapType = MapType.SATELLITE), // google maps api version
                uiSettings = MapUiSettings(mapToolbarEnabled = false), // this disables the directions/google maps toolbar when a marker is clicked
                onMapClick = { selectedPlace = null } // clear selection when tapping map
            ) {
                // User location marker
                MarkerComposable(
                    state = rememberMarkerState(position = currentLatLng),
                    anchor = Offset(0.5f, 0.5f),
                    onClick = { true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(24.dp) // size of marker. reduce if too big
                    )
                }

                // Observing zoom level directly from state to ensure markers react to zooming
                val currentZoom = cameraPositionState.position.zoom
                // fades markers from 1.0 at 15 zoom to 0.0 at 13 zoom
                val buildingAlpha = ((currentZoom - 13f) / (15f - 13f)).coerceIn(0f, 1f)

                // Render markers (keeping them in composition even if faded avoids logic reset)
                filteredPlaces.forEach { place ->
                    val isSelected = selectedPlace?.name == place.name
                    val markerAlpha = if (selectedPlace == null || isSelected) buildingAlpha else buildingAlpha * 0.3f

                    // FIX: We use 'key' to ensure each marker keeps its own correct state/position
                    key(place.name) {
                        val markerColor = when(place.category) {
                            MarkerCategory.BUILDING -> Color.Blue
                            MarkerCategory.STUDENT_SERVICE -> Color.Magenta
                            MarkerCategory.DINING -> Color(0xFFFFA500) // Orange
                            else -> Color.Gray
                        }

                        MarkerComposable(
                            state = rememberMarkerState(position = place.location),
                            alpha = markerAlpha,
                            anchor = Offset(0.5f, 1.0f),
                            onClick = {
                                selectedPlace = place
                                true // return true to prevent default info window
                            }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Bubbles and text show up when selected when zoomed in
                                val isZoomedIn = currentZoom >= 16.5f
                                val showBubble = isSelected || (selectedPlace == null && isZoomedIn)

                                if (showBubble) {
                                    Surface(
                                        shape = RoundedCornerShape(3.3.dp), // size of text bubble. reduce if too big
                                        color = Color.White.copy(alpha = if (isSelected) 0.95f else 0.85f),
                                        modifier = Modifier.padding(bottom = 1.4.dp) // size of text bubble. reduce if too big
                                    ) {
                                        Text(
                                            text = place.name,
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), // size of text in bubble. reduce if too big
                                            modifier = Modifier.padding(horizontal = 3.6.dp, vertical = 1.5.dp), // size of text in bubble. reduce if too big
                                            color = Color.Black.copy(alpha = if (selectedPlace != null && !isSelected) 0.3f else 1f)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = markerColor,
                                    modifier = Modifier.size(19.8.dp) // Reduced to 90% of 22dp
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

        // Search bar and filter (filter is temporary, there will be more than 3 selections)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter)
        ) {
            // Search bar updates searchQuery
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search facilities...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.9f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.9f),
                    disabledContainerColor = Color.White.copy(alpha = 0.9f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // chips on the search bar for filter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MarkerCategory.entries.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.White.copy(alpha = 0.8f),
                            selectedContainerColor = Color.White,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedLabelColor = Color.Black
                        )
                    )
                }
            }
        }

        // This is the list of buildings/services
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
                                    text = "Campus Locations",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(places) { place ->
                                    ListItem(
                                        headlineContent = { Text(place.name) },
                                        supportingContent = { Text(place.category.label) },
                                        leadingContent = {
                                            val iconColor = when(place.category) {
                                                MarkerCategory.BUILDING -> Color.Blue
                                                MarkerCategory.STUDENT_SERVICE -> Color.Magenta
                                                MarkerCategory.DINING -> Color(0xFFFFA500)
                                                else -> Color.Gray
                                            }
                                            Icon(Icons.Default.Place, contentDescription = null, tint = iconColor)
                                        },
                                        modifier = Modifier.clickable {
                                            scope.launch {
                                                selectedPlace = place // select item when clicking list
                                                cameraPositionState.animate(
                                                    update = CameraUpdateFactory.newLatLngZoom(place.location, 18f),
                                                    durationMs = 1000
                                                )
                                            }
                                            isListVisible = false // Close list after selection
                                        }
                                    )
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }

                // Button to pull out filter
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
            }
        }
    }
}
