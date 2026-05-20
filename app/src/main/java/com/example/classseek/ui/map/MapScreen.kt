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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.classseek.data.ChatListItem
import com.example.classseek.data.ChatRepository
import com.example.classseek.models.UserProfile
import com.example.classseek.ui.theme.AppPrimary
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.firestore
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MarkerCategory(val label: String, val icon: ImageVector, val color: Color) {
    ALL("All", Icons.Default.Place, Color.Gray),
    BUILDING("Building", Icons.Default.OtherHouses, Color(0xFF2596BE)), // Tealish
    STUDENT_SERVICE("Student Service", Icons.Default.School, Color(0xFFE580FF)),
    DINING("Dining", Icons.Default.Restaurant, Color(0xFFFFA500)), // Orange
    BOOKMARK("Bookmark", Icons.Default.Bookmark, Color(0xFFF8CF6B)),
    SHARED("Shared", Icons.Default.ShareLocation, Color(0xFFC6BBE8)),
    CLASS("Class", Icons.Default.Explore, Color(0xFF6BD36E))
}

data class MapPlace(
    val name: String,
    val location: LatLng,
    val category: MarkerCategory,
    val description: String = "",
    val senderId: String? = null,
    val eventId: String? = null,
    val eventStart: String? = null,
    val eventEnd: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    userProfile: UserProfile? = null,
    calendarEvents: List<Event> = emptyList(),
    temporaryMarkers: List<MapPlace> = emptyList(),
    onAddTemporaryMarker: (MapPlace) -> Unit = {},
    sharedLocation: LatLng? = null,
    sharedLocationName: String? = null,
    sharedByUid: String? = null,
    isDarkTheme: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = Firebase.firestore
    val auth = FirebaseAuth.getInstance()
    val repo = remember { ChatRepository(db) }

    val userProfiles = remember { mutableStateMapOf<String, UserProfile>() }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(MarkerCategory.ALL) }
    var isListVisible by remember { mutableStateOf(false) }
    var selectedPlace by remember { mutableStateOf<MapPlace?>(null) }
    var mapType by remember { mutableStateOf(MapType.NORMAL) }
    var isFilterMenuExpanded by remember { mutableStateOf(false) }
    var isMapReady by remember { mutableStateOf(false) }

    var userMarkerIcon by remember { mutableStateOf<com.google.android.gms.maps.model.BitmapDescriptor?>(null) }
    val sharedMarkerIcons = remember { mutableStateMapOf<String, com.google.android.gms.maps.model.BitmapDescriptor?>() }

    // State for local share picking
    var showShareDialog by remember { mutableStateOf(false) }
    var myChats by remember { mutableStateOf<List<ChatListItem>>(emptyList()) }
    var selectedChatFilter by remember { mutableStateOf("All") }

    // State for compass heading
    var heading by remember { mutableStateOf(0f) }

    fun fetchUserProfile(uid: String) {
        if (uid.isBlank() || userProfiles.containsKey(uid)) return

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    userProfiles[uid] = UserProfile(
                        uid = uid,
                        name = doc.getString("name") ?: "User",
                        email = doc.getString("email") ?: "",
                        profilePictureUrl = doc.getString("profilePictureUrl") ?: ""
                    )
                }
            }
            .addOnFailureListener { e ->
                Log.e("MapScreen", "Failed to fetch profile for $uid: ${e.message}")
                userProfiles[uid] = UserProfile(uid, "User", "", "")
            }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(34.16206611807, -119.0434737072), 17f)
    }

    if (isListVisible) {
        BackHandler {
            isListVisible = false
        }
    }

    val bounds = remember {
        LatLngBounds(
            LatLng(34.14521297909, -119.0623117489),
            LatLng(34.1736221957, -119.0200830498)
        )
    }

    // List of places loaded from Firestore
    var places by remember { mutableStateOf<List<MapPlace>>(emptyList()) }

    // Bookmark markers derived from calendar events
    val bookmarkMarkers = remember(calendarEvents, userProfile?.bookmarkedEventIds, places) {
        val bookmarkedIds = userProfile?.bookmarkedEventIds ?: emptyList()
        calendarEvents.filter { it.id in bookmarkedIds && !it.location.isNullOrEmpty() }
            .mapNotNull { event ->
                val eventLoc = event.location.lowercase()

                // 1. Try exact or partial match with building names
                var match = places.find { place ->
                    val placeName = place.name.lowercase()
                    eventLoc.contains(placeName) || placeName.contains(eventLoc)
                }

                // 2. If no match, try matching individual words (excluding common room/floor words)
                if (match == null) {
                    val stopWords = setOf("room", "floor", "level", "suite", "rm", "fl", "den", "east", "west", "north", "south")
                    val locWords = eventLoc.split(" ", "-", ",").filter { it.length > 2 && it !in stopWords }

                    match = places.find { place ->
                        val placeName = place.name.lowercase()
                        locWords.any { word -> placeName.contains(word) }
                    }
                }

                if (match != null) {
                    MapPlace(
                        name = event.summary ?: match.name,
                        location = match.location,
                        category = MarkerCategory.BOOKMARK,
                        eventId = event.id,
                        eventStart = formatEventDateTime(event.start),
                        eventEnd = formatEventDateTime(event.end),
                        description = event.location ?: ""
                    )
                } else {
                    null
                }
            }
    }

    // Schedule markers derived from user profile classes
    val scheduleMarkers = remember(calendarEvents, userProfile?.classes, places) {
        userProfile?.classes?.mapNotNull { classInfo ->
            val buildingInput = classInfo.building.lowercase()

            // Re-use robust matching logic
            var match = places.find { place ->
                val placeName = place.name.lowercase()
                buildingInput.contains(placeName) || placeName.contains(buildingInput)
            }

            if (match == null) {
                val stopWords = setOf("room", "floor", "level", "suite", "rm", "fl", "den", "east", "west", "north", "south")
                val words = buildingInput.split(" ", "-", ",").filter { it.length > 2 && it !in stopWords }
                match = places.find { place ->
                    val placeName = place.name.lowercase()
                    words.any { word -> placeName.contains(word) }
                }
            }

            if (match != null) {
                // Find a corresponding event to get times
                val originalEvent = calendarEvents.find { it.summary == classInfo.className }
                MapPlace(
                    name = classInfo.className,
                    location = match.location,
                    category = MarkerCategory.CLASS,
                    description = "${classInfo.building} ${classInfo.roomNumber}",
                    eventId = originalEvent?.id,
                    eventStart = originalEvent?.let { formatEventDateTime(it.start) },
                    eventEnd = originalEvent?.let { formatEventDateTime(it.end) }
                )
            } else null
        } ?: emptyList()
    }

    // Include the shared location from DM if it exists
    val incomingSharedMarker = remember(sharedLocation, sharedLocationName, sharedByUid) {
        if (sharedLocation != null && sharedLocationName != null) {
            listOf(MapPlace(sharedLocationName, sharedLocation, MarkerCategory.SHARED, "Shared with you", sharedByUid))
        } else emptyList()
    }

    val allMarkers = remember(places, bookmarkMarkers, temporaryMarkers, incomingSharedMarker, scheduleMarkers) {
        places + bookmarkMarkers + temporaryMarkers + incomingSharedMarker + scheduleMarkers
    }

    // Filtered list based on selected category
    val displayPlaces = remember(allMarkers, selectedCategory) {
        allMarkers.filter { place ->
            selectedCategory == MarkerCategory.ALL || place.category == selectedCategory
        }
    }

    LaunchedEffect(allMarkers, userProfiles.toMap()) {
        allMarkers.forEach { place ->
            if (place.category == MarkerCategory.SHARED && place.senderId != null) {
                fetchUserProfile(place.senderId)
                val profile = userProfiles[place.senderId]
                if (profile?.profilePictureUrl?.isNotBlank() == true && !sharedMarkerIcons.containsKey(place.senderId)) {
                    scope.launch {
                        val icon = loadMarkerBitmap(context, profile.profilePictureUrl)
                        sharedMarkerIcons[place.senderId] = icon
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
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
                Log.d("MapScreen", "Loaded ${places.size} places from Firestore")
            }

        // Load chats for sharing
        auth.currentUser?.uid?.let { uid ->
            repo.listenToMyChats(uid, { chats -> myChats = chats }, { Log.e("MapScreen", "Error loading chats", it) })
        }
    }

    var currentUserProfile by remember { mutableStateOf<UserProfile?>(userProfile) }

    LaunchedEffect(auth.currentUser?.uid) {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            // Use a SnapshotListener for real-time updates, or .get() for a fresh fetch
            db.collection("users").document(uid)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.w("MapScreen", "Listen failed", e)
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        // Directly convert the document to your UserProfile model
                        currentUserProfile = snapshot.toObject(UserProfile::class.java)
                        Log.d("MapScreen", "Live Profile Loaded: ${currentUserProfile?.profilePictureUrl}")
                    }
                }
        }
    }

    // Handle initial shared location from DM
    LaunchedEffect(sharedLocation, isMapReady) {
        if (sharedLocation != null) {
            if (isMapReady) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(sharedLocation, 18f))
            } else {
                cameraPositionState.position = CameraPosition.fromLatLngZoom(sharedLocation, 18f)
            }
            selectedPlace = incomingSharedMarker.firstOrNull()
        }
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var location by remember { mutableStateOf<Location?>(null) }


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
                    heading = Math.toDegrees(orientation[0].toDouble()).toFloat()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    DisposableEffect(hasPermission) {
        if (hasPermission) {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    if (userProfile?.shareLocation == false) return

                    result.lastLocation?.let { newLoc ->
                        location = newLoc
                    }
                }
            }
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            onDispose { fusedLocationClient.removeLocationUpdates(locationCallback) }
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
                onMapLoaded = { isMapReady = true },
                properties = MapProperties(
                    mapType = mapType,
                    latLngBoundsForCameraTarget = bounds,
                    minZoomPreference = 14.5f,
                    mapStyleOptions = MapStyleOptions(
                        if (isDarkTheme) darkMapStyleJson else lightMapStyleJson
                    )
                ),
                uiSettings = MapUiSettings(mapToolbarEnabled = false),
                onMapClick = { selectedPlace = null }
            ) {
                val profilePicUrl = currentUserProfile?.profilePictureUrl

                // Fetch the bitmap whenever the profile picture URL changes
                LaunchedEffect(profilePicUrl) {
                    Log.d("MapScreen", "User profilePicUrl updated in MapScreen: '$profilePicUrl'")
                    if (!profilePicUrl.isNullOrBlank()) {
                        userMarkerIcon = loadMarkerBitmap(context, profilePicUrl)
                    }
                }

                // Only draw the marker once the icon is ready OR use a fallback
                Marker(
                    state = rememberMarkerState(position = currentLatLng),
                    icon = userMarkerIcon ?: com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(),
                    anchor = Offset(0.5f, 0.5f),
                    title = "My Location",
                    onClick = {
                        selectedPlace = MapPlace("My Location", currentLatLng, MarkerCategory.SHARED, "Your live location")
                        true
                    }
                )

                allMarkers.forEach { place ->
                    val isSelected = selectedPlace?.name == place.name && selectedPlace?.location == place.location
                    val isInSelectedCategory = selectedCategory == MarkerCategory.ALL || place.category == selectedCategory

                    // Logic to hide location markers if overlapped by specific categories (Bookmarks, Classes)
                    val hasOverlappingMarker = remember(place, bookmarkMarkers, scheduleMarkers) {
                        if (place.category == MarkerCategory.BUILDING ||
                            place.category == MarkerCategory.STUDENT_SERVICE ||
                            place.category == MarkerCategory.DINING) {
                            bookmarkMarkers.any { it.location == place.location } ||
                                    scheduleMarkers.any { it.location == place.location }
                        } else false
                    }

                    // Hide building/service name if overlapped, unless filter active or specifically selected
                    val shouldShowName = if (hasOverlappingMarker) {
                        selectedCategory == place.category || isSelected
                    } else true

                    // Hide building/service icon if overlapped, unless category filter used or specifically selected/searched
                    val shouldShowBuildingIcon = if (hasOverlappingMarker) {
                        selectedCategory == place.category || isSelected
                    } else true

                    val markerAlpha = if (selectedPlace != null) {
                        if (isSelected) 1.0f else 0.35f
                    } else if (selectedCategory != MarkerCategory.ALL) {
                        if (isInSelectedCategory) 1.0f else 0.35f
                    } else {
                        1.0f
                    }

                    if (shouldShowBuildingIcon) {
                        key("${place.name}_${place.location.latitude}_${place.location.longitude}_${place.category}") {
                            if (place.category == MarkerCategory.SHARED && place.senderId != null) {
                                val icon = sharedMarkerIcons[place.senderId]
                                Marker(
                                    state = rememberMarkerState(position = place.location),
                                    icon = icon ?: com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(),
                                    alpha = markerAlpha,
                                    anchor = Offset(0.5f, 0.5f),
                                    title = place.name,
                                    onClick = {
                                        selectedPlace = place
                                        true
                                    }
                                )
                            } else {
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
                                        if (shouldShowName) {
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
                                        }

                                        Box(
                                            modifier = Modifier
                                                .background(Color.White, CircleShape)
                                                .border(2.dp, place.category.color, CircleShape)
                                                .padding(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = place.category.icon,
                                                contentDescription = null,
                                                tint = place.category.color,
                                                modifier = Modifier.size(if (place.category == MarkerCategory.CLASS) 14.dp else 19.8.dp)
                                            )
                                        }
                                    }
                                }
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

        selectedPlace?.let { place ->
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .padding(bottom = 80.dp)
                    .fillMaxWidth()
                    .clickable { },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(8.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        if (place.category == MarkerCategory.SHARED && place.senderId != null) {
                            val profile = userProfiles[place.senderId]
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(profile?.profilePictureUrl ?: "")
                                        .allowHardware(false)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.LightGray)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Shared by ${profile?.name ?: "Loading..."}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else if (place.description.isNotEmpty()) {
                            Text(text = place.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Button(onClick = { showShareDialog = true }) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Share")
                    }
                }
            }
        }

        if (showShareDialog) {
            val shareSheetState = rememberModalBottomSheetState()
            ModalBottomSheet(
                onDismissRequest = { showShareDialog = false },
                sheetState = shareSheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Share with...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedChatFilter == "All",
                            onClick = { selectedChatFilter = "All" },
                            label = { Text("All") },
                            leadingIcon = if (selectedChatFilter == "All") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = selectedChatFilter == "DMs",
                            onClick = { selectedChatFilter = "DMs" },
                            label = { Text("DMs") },
                            leadingIcon = if (selectedChatFilter == "DMs") {
                                { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                        FilterChip(
                            selected = selectedChatFilter == "Groups",
                            onClick = { selectedChatFilter = "Groups" },
                            label = { Text("Groups") },
                            leadingIcon = if (selectedChatFilter == "Groups") {
                                { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }

                    val filteredChats = remember(myChats, selectedChatFilter) {
                        when (selectedChatFilter) {
                            "DMs" -> myChats.filter { it.type == "dm" }
                            "Groups" -> myChats.filter { it.type == "group" }
                            else -> myChats
                        }
                    }

                    if (filteredChats.isEmpty()) {
                        Text("No chats available", modifier = Modifier.padding(vertical = 16.dp))
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(filteredChats) { chat ->
                                ListItem(
                                    headlineContent = { Text(chat.title) },
                                    leadingContent = {
                                        if (chat.type == "dm" && chat.profilePictureUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(chat.profilePictureUrl)
                                                    .allowHardware(false)
                                                    .build(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Icon(
                                                imageVector = if (chat.type == "group") Icons.Default.Group else Icons.Default.Person,
                                                contentDescription = null,
                                                modifier = Modifier.size(40.dp)
                                            )
                                        }
                                    },
                                    modifier = Modifier.clickable {
                                        scope.launch {
                                            auth.currentUser?.uid?.let { myUid ->
                                                val place = selectedPlace!!
                                                if (place.eventId != null) {
                                                    // Share as an Event (Sync with CalendarScreen logic)
                                                    repo.sendEventMessage(
                                                        chatId = chat.id,
                                                        senderId = myUid,
                                                        eventTitle = place.name,
                                                        eventStart = place.eventStart ?: "",
                                                        eventEnd = place.eventEnd ?: "",
                                                        eventLocation = place.description.ifBlank { place.name },
                                                        eventId = place.eventId
                                                    )
                                                } else {
                                                    // Share as a Location
                                                    repo.sendLocationMessage(
                                                        chatId = chat.id,
                                                        senderId = myUid,
                                                        latitude = place.location.latitude,
                                                        longitude = place.location.longitude,
                                                        locationName = place.name
                                                    )
                                                }

                                                if (place.name == "Current Location") {
                                                    onAddTemporaryMarker(place)
                                                }
                                                showShareDialog = false
                                                selectedPlace = null
                                            }
                                        }
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }

        Column(modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .align(Alignment.TopCenter)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = searchQuery,
                    onValueChange = { newValue ->
                        searchQuery = newValue
                        allMarkers.find { it.name.contains(newValue, ignoreCase = true) }?.let { match ->
                            selectedPlace = match
                            scope.launch {
                                if (isMapReady) {
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(match.location, 18f))
                                } else {
                                    cameraPositionState.position = CameraPosition.fromLatLngZoom(match.location, 18f)
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search facilities...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = ""; selectedPlace = null }) { Icon(Icons.Default.Clear, contentDescription = "Clear") } },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,

                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        disabledContainerColor = Color.White,

                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    )
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Dropdown menu for filters
                Box {
                    FloatingActionButton(
                        onClick = { isFilterMenuExpanded = true },
                        modifier = Modifier.size(55.dp),
                        containerColor = Color.White.copy(alpha = 0.9f),
                        contentColor = AppPrimary,
                        shape = RoundedCornerShape(18.dp),
                        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter Options"
                        )
                    }

                    DropdownMenu(
                        expanded = isFilterMenuExpanded,
                        shape = RoundedCornerShape(18.dp),
                        onDismissRequest = { isFilterMenuExpanded = false },
                        containerColor = Color.White.copy(alpha = 0.85f)
                    ) {
                        MarkerCategory.entries.filter { it != MarkerCategory.SHARED }.forEach { category ->
                            DropdownMenuItem(
                                text = {
                                    Text (
                                         text = category.label,
                                         color = Color.Black
                                    )
                                       },
                                onClick = {
                                    selectedCategory = category
                                    selectedPlace = null
                                    isFilterMenuExpanded = false
                                },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .background(Color.White, CircleShape)
                                            .border(2.dp, category.color, CircleShape)
                                            .padding(3.dp)
                                    ) {
                                        Icon(
                                            imageVector = category.icon,
                                            contentDescription = null,
                                            tint = category.color,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                },
                                trailingIcon = if (selectedCategory == category) {
                                    { Icon(Icons.Default.Check, contentDescription = "Selected", modifier = Modifier.size(16.dp), tint = Color.Black ) }
                                } else null
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            FloatingActionButton(
                onClick = {
                    if (location != null) {
                        selectedPlace = MapPlace("Current Location", LatLng(location!!.latitude, location!!.longitude), MarkerCategory.SHARED, "Share your live location")
                        showShareDialog = true
                    }
                },
                modifier = Modifier.size(48.dp),
                containerColor = AppPrimary.copy(alpha = 0.9f),
                contentColor = Color.White,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Share Current Location")
            }
        }

        Box(modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(16.dp)) {
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
                                            Box(
                                                modifier = Modifier
                                                    .background(Color.White, CircleShape)
                                                    .border(2.dp, place.category.color, CircleShape)
                                                    .padding(3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = place.category.icon,
                                                    contentDescription = null,
                                                    tint = place.category.color,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        },
                                        modifier = Modifier.clickable {
                                            scope.launch {
                                                selectedPlace = place
                                                if (isMapReady) {
                                                    cameraPositionState.animate(
                                                        update = CameraUpdateFactory.newLatLngZoom(place.location, 18f),
                                                        durationMs = 1000
                                                    )
                                                } else {
                                                    cameraPositionState.position = CameraPosition.fromLatLngZoom(place.location, 18f)
                                                }
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
                        onClick = {
                            mapType = if (mapType == MapType.SATELLITE) {
                                MapType.NORMAL
                            } else {
                                MapType.SATELLITE
                            }
                        },
                        containerColor = if (mapType == MapType.SATELLITE) {
                            AppPrimary
                        } else {
                            Color.White
                        },
                        contentColor = if (mapType == MapType.SATELLITE) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            Color.Black
                        },
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

private fun formatDate(dateTime: com.google.api.client.util.DateTime?): String {
    if (dateTime == null) return "Unknown Date"
    val date = Date(dateTime.value)
    val sdf = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    return sdf.format(date)
}

private fun formatEventDateTime(dateTime: EventDateTime?): String {
    if (dateTime == null) return ""
    return if (dateTime.dateTime != null) {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        sdf.format(Date(dateTime.dateTime.value))
    } else if (dateTime.date != null) {
        val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        sdf.format(Date(dateTime.date.value))
    } else ""
}

suspend fun loadMarkerBitmap(context: Context, url: String?): com.google.android.gms.maps.model.BitmapDescriptor? {
    if (url.isNullOrBlank()) return null
    return withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val loader = coil.ImageLoader(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false) // CRITICAL: Map thread cannot read hardware bitmaps
                .build()

            val result = (loader.execute(request) as? coil.request.SuccessResult)?.drawable
            val bitmap = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return@withContext null

            // Manually draw the circular marker with white border
            val size = 74
            val output = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(output)
            val paint = android.graphics.Paint().apply { isAntiAlias = true }

            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, null, android.graphics.Rect(0, 0, size, size), paint)

            paint.xfermode = null
            paint.style = android.graphics.Paint.Style.STROKE
            paint.color = android.graphics.Color.WHITE
            paint.strokeWidth = 4f
            canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - 2f, paint)

            com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(output)
        } catch (e: Exception) {
            null
        }
    }


}

//Styling for dark and light mode
private val lightMapStyleJson = """
[
  { "elementType": "labels", "stylers": [ { "visibility": "off" } ] },
  { "featureType": "poi", "stylers": [ { "visibility": "off" } ] },
  { "featureType": "transit", "stylers": [ { "visibility": "off" } ] }
]
""".trimIndent()

private val darkMapStyleJson = """
[
  { "elementType": "geometry", "stylers": [ { "color": "#242f3e" } ] },
  { "elementType": "labels", "stylers": [ { "visibility": "off" } ] },
  { "featureType": "poi", "stylers": [ { "visibility": "off" } ] },
  { "featureType": "transit", "stylers": [ { "visibility": "off" } ] },
  { "featureType": "road", "elementType": "geometry", "stylers": [ { "color": "#38414e" } ] },
  { "featureType": "water", "elementType": "geometry", "stylers": [ { "color": "#17263c" } ] },
  { "featureType": "landscape", "elementType": "geometry", "stylers": [ { "color": "#1f2937" } ] }
]
""".trimIndent()
