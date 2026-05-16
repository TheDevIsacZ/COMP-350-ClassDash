package com.example.classseek.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.classseek.Notification.MyFirebaseMessagingService
import com.example.classseek.R
import com.example.classseek.data.ChatRepository
import com.example.classseek.models.ClassInfo
import com.example.classseek.models.ClassSchedule
import com.example.classseek.models.UserProfile
import com.example.classseek.ui.calendar.AddEventScreen
import com.example.classseek.ui.calendar.CalendarScreen
import com.example.classseek.ui.chat.ChatScreen
import com.example.classseek.ui.friends.FriendsScreen
import com.example.classseek.ui.friends.UserSearchItem
import com.example.classseek.ui.map.MapPlace
import com.example.classseek.ui.map.MapScreen
import com.example.classseek.ui.theme.AppThemeMode
import com.example.classseek.ui.theme.ClassSeekTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.maps.model.LatLng
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme

private const val schoolCalendarID =
    "c_d51f6135decfa961f6e26e7b759dd6d102ec5eb050afb040b211b749b04c0084@group.calendar.google.com"

class ClassSeekActivity : ComponentActivity() {

    private var launchChatIdState = mutableStateOf<String?>(null)
    private var launchChatTitleState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FirebaseApp.initializeApp(this)

        requestNotificationPermissionIfNeeded()
        updateNotificationRouteFromIntent(intent)

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                Log.d("FCM_TOKEN", "Device token: $token")
            }
            .addOnFailureListener { e ->
                Log.w("FCM_TOKEN", "Failed to get token", e)
            }

        setContent {
            val themePrefs = remember {
                getSharedPreferences("classseek_settings", MODE_PRIVATE)
            }

            var themeMode by rememberSaveable {
                mutableStateOf(
                    runCatching {
                        AppThemeMode.valueOf(
                            themePrefs.getString("theme_mode", AppThemeMode.SYSTEM.name)
                                ?: AppThemeMode.SYSTEM.name
                        )
                    }.getOrDefault(AppThemeMode.SYSTEM)
                )
            }

            ClassSeekTheme(themeMode = themeMode) { isDarkTheme ->
                ClassSeekApp(
                    initialChatId = launchChatIdState.value,
                    initialChatTitle = launchChatTitleState.value,
                    onNotificationChatConsumed = {
                        launchChatIdState.value = null
                        launchChatTitleState.value = null
                    },
                    themeMode = themeMode,
                    onThemeModeChange = { newThemeMode ->
                        themeMode = newThemeMode
                        themePrefs.edit()
                            .putString("theme_mode", newThemeMode.name)
                            .apply()
                    },
                    isDarkTheme = isDarkTheme
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        updateNotificationRouteFromIntent(intent)
    }

    private fun updateNotificationRouteFromIntent(intent: Intent?) {
        launchChatIdState.value =
            intent?.getStringExtra(MyFirebaseMessagingService.EXTRA_CHAT_ID)
        launchChatTitleState.value =
            intent?.getStringExtra(MyFirebaseMessagingService.EXTRA_CHAT_TITLE)
    }

    suspend fun getCalendarEvents(account: GoogleSignInAccount): List<Event>? {
        return withContext(Dispatchers.IO) {
            try {
                val calendarScope = "https://www.googleapis.com/auth/calendar"
                val credential = GoogleAccountCredential.usingOAuth2(
                    this@ClassSeekActivity,
                    listOf(calendarScope)
                )
                credential.selectedAccountName = account.email

                val service = Calendar.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("ClassSeek").build()

                // Fetch from the start of today to ensure all events for the day are shown
                val todayStartCal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                val timeMin = DateTime(todayStartCal.time)

                val eventsResult = service.events().list("primary")
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .setTimeMin(timeMin)
                    .setMaxResults(100)
                    .execute()

                val schoolEvents = try {
                    service.events().list(schoolCalendarID)
                        .setOrderBy("startTime")
                        .setSingleEvents(true)
                        .setTimeMin(timeMin)
                        .setMaxResults(100)
                        .execute()
                        .items ?: emptyList()
                } catch (e: Exception) {
                    Log.e("CALENDAR_DEBUG", "Failed to fetch school events", e)
                    emptyList()
                }

                (eventsResult.items ?: emptyList()) + schoolEvents
            } catch (e: Exception) {
                Log.e("CALENDAR_DEBUG", "getCalendarEvents: ERROR", e)
                null
            }
        }
    }

    suspend fun addEventToCalendar(
        account: GoogleSignInAccount,
        schedule: ClassSchedule
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val calendarScope = "https://www.googleapis.com/auth/calendar"
                val credential = GoogleAccountCredential.usingOAuth2(
                    this@ClassSeekActivity,
                    listOf(calendarScope)
                )
                credential.selectedAccountName = account.email

                val service = Calendar.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("ClassSeek").build()

                val firstStartCal = getFirstOccurrence(schedule)
                val durationMs = getDurationMs(schedule.startTime, schedule.endTime)

                val event = Event().apply {
                    summary = schedule.className
                    location = schedule.location
                    description = "Added via ClassSeek"
                }

                val timeZoneId = TimeZone.getDefault().id

                val startDateTime = DateTime(firstStartCal.time, TimeZone.getDefault())
                event.start = EventDateTime()
                    .setDateTime(startDateTime)
                    .setTimeZone(timeZoneId)

                val endDateTime = DateTime(java.util.Date(firstStartCal.timeInMillis + durationMs), TimeZone.getDefault())
                event.end = EventDateTime()
                    .setDateTime(endDateTime)
                    .setTimeZone(timeZoneId)

                if (schedule.daysOfWeek.isNotEmpty()) {
                    val daysMap = mapOf(
                        java.util.Calendar.MONDAY to "MO",
                        java.util.Calendar.TUESDAY to "TU",
                        java.util.Calendar.WEDNESDAY to "WE",
                        java.util.Calendar.THURSDAY to "TH",
                        java.util.Calendar.FRIDAY to "FR",
                        java.util.Calendar.SATURDAY to "SA",
                        java.util.Calendar.SUNDAY to "SU"
                    )
                    val byDay = schedule.daysOfWeek.mapNotNull { daysMap[it] }.joinToString(",")

                    val df = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val untilDate = df.format(java.util.Date(schedule.endDate))

                    event.recurrence = listOf("RRULE:FREQ=WEEKLY;BYDAY=$byDay;UNTIL=$untilDate")
                }

                val createdEvent = service.events().insert("primary", event).execute()
                Log.d("CALENDAR_DEBUG", "Event created successfully: ${createdEvent.htmlLink}")
                createdEvent.id
            } catch (e: Exception) {
                Log.e("CALENDAR_DEBUG", "addEventToCalendar: ERROR", e)
                null
            }
        }
    }

    suspend fun updateEventInCalendar(
        account: GoogleSignInAccount,
        eventId: String,
        schedule: ClassSchedule
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val calendarScope = "https://www.googleapis.com/auth/calendar"
                val credential = GoogleAccountCredential.usingOAuth2(
                    this@ClassSeekActivity,
                    listOf(calendarScope)
                )
                credential.selectedAccountName = account.email

                val service = Calendar.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("ClassSeek").build()

                val event = service.events().get("primary", eventId).execute()

                val firstStartCal = getFirstOccurrence(schedule)
                val durationMs = getDurationMs(schedule.startTime, schedule.endTime)

                event.summary = schedule.className
                event.location = schedule.location

                val timeZoneId = TimeZone.getDefault().id

                val startDateTime = DateTime(firstStartCal.time, TimeZone.getDefault())
                event.start = EventDateTime()
                    .setDateTime(startDateTime)
                    .setTimeZone(timeZoneId)

                val endDateTime = DateTime(java.util.Date(firstStartCal.timeInMillis + durationMs), TimeZone.getDefault())
                event.end = EventDateTime()
                    .setDateTime(endDateTime)
                    .setTimeZone(timeZoneId)

                if (schedule.daysOfWeek.isNotEmpty()) {
                    val daysMap = mapOf(
                        java.util.Calendar.MONDAY to "MO",
                        java.util.Calendar.TUESDAY to "TU",
                        java.util.Calendar.WEDNESDAY to "WE",
                        java.util.Calendar.THURSDAY to "TH",
                        java.util.Calendar.FRIDAY to "FR",
                        java.util.Calendar.SATURDAY to "SA",
                        java.util.Calendar.SUNDAY to "SU"
                    )
                    val byDay = schedule.daysOfWeek.mapNotNull { daysMap[it] }.joinToString(",")

                    val df = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                    val untilDate = df.format(java.util.Date(schedule.endDate))

                    event.recurrence = listOf("RRULE:FREQ=WEEKLY;BYDAY=$byDay;UNTIL=$untilDate")
                } else {
                    event.recurrence = null
                }

                service.events().update("primary", eventId, event).execute()
                true
            } catch (e: Exception) {
                Log.e("CALENDAR_DEBUG", "updateEventInCalendar: ERROR", e)
                false
            }
        }
    }

    suspend fun deleteEventFromCalendar(
        account: GoogleSignInAccount,
        eventId: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val calendarScope = "https://www.googleapis.com/auth/calendar"
                val credential = GoogleAccountCredential.usingOAuth2(
                    this@ClassSeekActivity,
                    listOf(calendarScope)
                )
                credential.selectedAccountName = account.email

                val service = Calendar.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("ClassSeek").build()

                service.events().delete("primary", eventId).execute()
                true
            } catch (e: Exception) {
                Log.e("CALENDAR_DEBUG", "deleteEventFromCalendar: ERROR", e)
                false
            }
        }
    }

    private fun getFirstOccurrence(schedule: ClassSchedule): java.util.Calendar {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = schedule.startDate

        try {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = sdf.parse(schedule.startTime)
            if (date != null) {
                val timeCal = java.util.Calendar.getInstance()
                timeCal.time = date
                cal.set(java.util.Calendar.HOUR_OF_DAY, timeCal.get(java.util.Calendar.HOUR_OF_DAY))
                cal.set(java.util.Calendar.MINUTE, timeCal.get(java.util.Calendar.MINUTE))
            }
        } catch (e: Exception) {
            Log.e("CALENDAR_DEBUG", "Failed to parse startTime: ${schedule.startTime}", e)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 9)
            cal.set(java.util.Calendar.MINUTE, 0)
        }

        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)

        var attempts = 0
        while (!schedule.daysOfWeek.contains(cal.get(java.util.Calendar.DAY_OF_WEEK)) && attempts < 7) {
            cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
            attempts++
        }

        return cal
    }

    private fun getDurationMs(start: String, end: String): Long {
        return try {
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val startTime = sdf.parse(start)
            val endTime = sdf.parse(end)
            (endTime?.time ?: 0) - (startTime?.time ?: 0)
        } catch (e: Exception) {
            3600000L
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(permission), 1001)
            }
        }
    }
}

private fun UserProfile?.needsProfileSetup(): Boolean {
    return this == null || !this.isProfileComplete
}

private suspend fun hasCompleteProfile(
    db: FirebaseFirestore,
    uid: String
): Boolean {
    return try {
        val snapshot = db.collection("users").document(uid).get().await()
        if (!snapshot.exists()) return false

        val name = snapshot.getString("name").orEmpty().trim()
        val major = snapshot.getString("major").orEmpty().trim()

        name.isNotBlank() && major.isNotBlank()
    } catch (e: Exception) {
        Log.e("AUTH_DEBUG", "Error checking profile completeness", e)
        false
    }
}

private fun updatePresenceSafely(
    scope: CoroutineScope,
    db: FirebaseFirestore,
    uid: String,
    isOnline: Boolean
) {
    scope.launch {
        try {
            val profileComplete = hasCompleteProfile(db, uid)
            if (!profileComplete) {
                Log.d("AUTH_DEBUG", "Skipping presence update because profile is incomplete")
                return@launch
            }

            db.collection("users")
                .document(uid)
                .set(
                    mapOf(
                        "uid" to uid,
                        "isOnline" to isOnline,
                        "lastPresenceUpdate" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
                .await()
        } catch (e: Exception) {
            Log.e("AUTH_DEBUG", "Error updating presence", e)
        }
    }
}

private suspend fun deleteCurrentAccount(
    auth: FirebaseAuth,
    db: FirebaseFirestore,
    googleSignInClient: GoogleSignInClient,
    onLocalStateCleared: () -> Unit
) {
    try {
        val user = auth.currentUser ?: throw IllegalStateException("No authenticated user")
        val uid = user.uid

        try {
            db.collection("users").document(uid).delete().await()
        } catch (e: Exception) {
            Log.e("AUTH_DEBUG", "Failed deleting Firestore user document", e)
        }

        user.delete().await()

        try {
            googleSignInClient.signOut().await()
        } catch (e: Exception) {
            Log.w("AUTH_DEBUG", "Google sign out warning", e)
        }

        auth.signOut()
        onLocalStateCleared()
    } catch (e: Exception) {
        Log.e("AUTH_DEBUG", "Error deleting account", e)
    }
}

@Composable
fun ClassSeekApp(
    initialChatId: String? = null,
    initialChatTitle: String? = null,
    onNotificationChatConsumed: () -> Unit = {},
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    isDarkTheme: Boolean
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val repo = ChatRepository(db)
    val lifecycleOwner = LocalLifecycleOwner.current

    var firebaseUser by remember { mutableStateOf(auth.currentUser) }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var isLoadingProfile by remember { mutableStateOf(true) }

    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.PROFILE) }
    var isAddingEvent by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<Event?>(null) }
    var isEditingProfile by remember { mutableStateOf(false) }
    
    var showReminderDialogInEditor by remember { mutableStateOf(false) }
    var eventForReminderInEditor by remember { mutableStateOf<Event?>(null) }
    var viewOtherUserId by remember { mutableStateOf<String?>(null) }
    var otherUserProfile by remember { mutableStateOf<UserProfile?>(null) }
    var isFriendWithOther by remember { mutableStateOf(false) }
    var friendRequestStatus by remember { mutableStateOf<String?>(null) } // null, "pending", "sent"
    var isEditingSchedule by remember { mutableStateOf(false) }
    var initialDateForNewEvent by remember { mutableStateOf<Long?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context as? ClassSeekActivity }

    var calendarEvents by remember { mutableStateOf<List<Event>>(emptyList()) }
    var sharedEvents by remember { mutableStateOf<List<Event>>(emptyList()) }
    var signedInAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }

    var pendingNotificationChatId by remember { mutableStateOf<String?>(null) }
    var pendingNotificationChatTitle by remember { mutableStateOf<String?>(null) }

    var routedChatId by remember { mutableStateOf<String?>(null) }
    var routedChatTitle by remember { mutableStateOf<String?>(null) }

    val profileFriends = remember { mutableStateListOf<UserSearchItem>() }
    val temporaryMarkers = remember { mutableStateListOf<MapPlace>() }
    var sharedLocationToView by remember { mutableStateOf<LatLng?>(null) }
    var sharedLocationNameToView by remember { mutableStateOf<String?>(null) }
    var sharedLocationByUidToView by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialChatId, initialChatTitle) {
        pendingNotificationChatId = initialChatId
        pendingNotificationChatTitle = initialChatTitle
    }

    fun consumePendingNotificationChat() {
        pendingNotificationChatId = null
        pendingNotificationChatTitle = null
        onNotificationChatConsumed()
    }

    fun consumeRoutedChat() {
        routedChatId = null
        routedChatTitle = null
    }

    fun clearSessionState() {
        firebaseUser = null
        signedInAccount = null
        calendarEvents = emptyList()
        userProfile = null
        isEditingProfile = false
        isEditingSchedule = false
        viewOtherUserId = null
        otherUserProfile = null
        friendRequestStatus = null
        sharedLocationToView = null
        sharedLocationNameToView = null
        sharedLocationByUidToView = null
        profileFriends.clear()
        consumeRoutedChat()
        consumePendingNotificationChat()
        sharedEvents = emptyList()
        currentDestination = AppDestinations.PROFILE
    }

    fun saveCurrentFcmTokenForUser() {
        val user = auth.currentUser ?: return

        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                val deviceDoc = mapOf(
                    "token" to token,
                    "platform" to "android",
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                db.collection("users")
                    .document(user.uid)
                    .collection("devices")
                    .document(token)
                    .set(deviceDoc)
                    .addOnSuccessListener {
                        Log.d("FCM_DEBUG", "Saved FCM token from activity")
                    }
                    .addOnFailureListener { e ->
                        Log.e("FCM_DEBUG", "Failed to save FCM token from activity", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("FCM_DEBUG", "Failed to get current FCM token", e)
            }
    }

    LaunchedEffect(firebaseUser?.uid) {
        if (firebaseUser != null) {
            saveCurrentFcmTokenForUser()
        }
    }

    LaunchedEffect(firebaseUser?.uid, pendingNotificationChatId) {
        if (firebaseUser != null && pendingNotificationChatId != null) {
            currentDestination = AppDestinations.FRIENDS
        }
    }

    DisposableEffect(firebaseUser?.uid, lifecycleOwner) {
        val uid = firebaseUser?.uid
        if (uid == null) return@DisposableEffect onDispose {}

        fun updatePresence(isOnline: Boolean) {
            val showOnline = userProfile?.showOnlineStatus ?: true
            val effectiveOnline = isOnline && showOnline

            db.collection("users")
                .document(uid)
                .set(
                    mapOf(
                        "uid" to uid,
                        "isOnline" to effectiveOnline,
                        "lastPresenceUpdate" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
        }

        updatePresence(true)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> updatePresence(true)
                Lifecycle.Event.ON_STOP -> updatePresence(false)
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            updatePresence(false)
        }
    }

    DisposableEffect(firebaseUser?.uid) {
        val uid = firebaseUser?.uid
        if (uid == null) {
            profileFriends.clear()
            return@DisposableEffect onDispose {}
        }

        val friendProfilesByUid = linkedMapOf<String, UserSearchItem>()
        val friendProfileRegistrations = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()

        val friendsRegistration = db.collection("users")
            .document(uid)
            .collection("friends")
            .addSnapshotListener { snapshot: QuerySnapshot?, _ ->
                friendProfilesByUid.clear()
                friendProfileRegistrations.forEach { it.remove() }
                friendProfileRegistrations.clear()

                val friendIds = snapshot?.documents?.map { document -> document.id }.orEmpty()
                if (friendIds.isEmpty()) {
                    profileFriends.clear()
                    return@addSnapshotListener
                }

                friendIds.forEach { friendUid: String ->
                    val registration = db.collection("users")
                        .document(friendUid)
                        .addSnapshotListener { friendSnapshot: DocumentSnapshot?, _ ->
                            if (friendSnapshot != null && friendSnapshot.exists()) {
                                val email = friendSnapshot.getString("email")?.trim().orEmpty()
                                if (email.isNotBlank()) {
                                    friendProfilesByUid[friendUid] = UserSearchItem(
                                        uid = friendSnapshot.id,
                                        name = friendSnapshot.getString("name")?.trim().orEmpty(),
                                        displayName = friendSnapshot.getString("displayName")?.trim().orEmpty(),
                                        email = email,
                                        major = friendSnapshot.getString("major")?.trim().orEmpty(),
                                        profilePictureUrl = friendSnapshot.getString("profilePictureUrl")?.trim().orEmpty(),
                                        isVerified = false,
                                        isOnline = friendSnapshot.getBoolean("isOnline") ?: false
                                    )
                                }
                            } else {
                                friendProfilesByUid.remove(friendUid)
                            }

                            profileFriends.clear()
                            profileFriends.addAll(
                                friendProfilesByUid.values.sortedBy { friend: UserSearchItem ->
                                    friend.displayName.ifBlank { friend.email }.lowercase()
                                }
                            )
                        }
                    friendProfileRegistrations.add(registration)
                }
            }

        onDispose {
            friendsRegistration.remove()
            friendProfileRegistrations.forEach { it.remove() }
            profileFriends.clear()
        }
    }

    DisposableEffect(firebaseUser?.uid) {
        val uid = firebaseUser?.uid
        var profileRegistration: ListenerRegistration? = null

        if (uid == null) {
            userProfile = null
            isLoadingProfile = false
            return@DisposableEffect onDispose {}
        }

        isLoadingProfile = true

        profileRegistration = db.collection("users")
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("AUTH_DEBUG", "Profile listener error", error)
                    isLoadingProfile = false
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val profile = snapshot.toObject(UserProfile::class.java)
                    userProfile = profile?.takeIf { it.isProfileComplete }
                    isLoadingProfile = false
                } else {
                    userProfile = null
                    isLoadingProfile = false
                }
            }

        onDispose {
            profileRegistration?.remove()
        }
    }

    DisposableEffect(firebaseUser?.uid) {
        val uid = firebaseUser?.uid
        if (uid == null) {
            sharedEvents = emptyList()
            return@DisposableEffect onDispose {}
        }

        val registration = db.collection("users")
            .document(uid)
            .collection("sharedEvents")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("CALENDAR_DEBUG", "Shared events listener error", error)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val events = snapshot.documents.mapNotNull { doc ->
                        try {
                            Event().apply {
                                id = doc.getString("eventId")
                                summary = doc.getString("title")
                                location = doc.getString("location")

                                val startStr = doc.getString("start")
                                val endStr = doc.getString("end")

                                val fullSdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                                val dateSdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

                                start = EventDateTime().apply {
                                    val date = try {
                                        fullSdf.parse(startStr ?: "")
                                    } catch (e: Exception) {
                                        try {
                                            dateSdf.parse(startStr ?: "")
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    if (date != null) dateTime = DateTime(date)
                                }
                                end = EventDateTime().apply {
                                    val date = try {
                                        fullSdf.parse(endStr ?: "")
                                    } catch (e: Exception) {
                                        try {
                                            dateSdf.parse(endStr ?: "")
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    if (date != null) dateTime = DateTime(date)
                                }
                            }
                        } catch (e: Exception) {
                            null
                        }
                    }
                    sharedEvents = events
                }
            }

        onDispose {
            registration.remove()
        }
    }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/calendar"))
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    LaunchedEffect(Unit) {
        googleSignInClient.silentSignIn().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val account = task.result
                signedInAccount = account
                scope.launch {
                    val events = activity?.getCalendarEvents(account)
                    if (events != null) calendarEvents = events
                }
            } else {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account != null) {
                    signedInAccount = account
                    scope.launch {
                        val events = activity?.getCalendarEvents(account)
                        if (events != null) calendarEvents = events
                    }
                }
            }
        }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            signedInAccount = account
            account?.idToken?.let { idToken ->
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                scope.launch {
                    try {
                        val authResult = auth.signInWithCredential(credential).await()
                        firebaseUser = authResult.user
                        saveCurrentFcmTokenForUser()

                        val events = activity?.getCalendarEvents(account)
                        if (events != null) calendarEvents = events
                    } catch (e: Exception) {
                        Log.e("AUTH_DEBUG", "Firebase auth failed", e)
                    }
                }
            }
        } catch (e: ApiException) {
            Log.e("CALENDAR_DEBUG", "signInLauncher: Sign-in failed", e)
        }
    }

    LaunchedEffect(viewOtherUserId, firebaseUser?.uid) {
        if (viewOtherUserId != null) {
            try {
                val currentUid = firebaseUser?.uid
                val targetUid = viewOtherUserId!!

                val doc = db.collection("users").document(targetUid).get().await()
                otherUserProfile = doc.toObject(UserProfile::class.java)

                if (currentUid != null) {
                    val friendDoc = db.collection("users")
                        .document(currentUid)
                        .collection("friends")
                        .document(targetUid)
                        .get()
                        .await()
                    isFriendWithOther = friendDoc.exists()

                    if (!isFriendWithOther) {
                        val incomingReq = db.collection("users")
                            .document(currentUid)
                            .collection("friendRequests")
                            .document(targetUid)
                            .get()
                            .await()

                        if (incomingReq.exists()) {
                            friendRequestStatus = "pending"
                        } else {
                            val outgoingReq = db.collection("users")
                                .document(currentUid)
                                .collection("sentFriendRequests")
                                .document(targetUid)
                                .get()
                                .await()

                            friendRequestStatus = if (outgoingReq.exists()) "sent" else null
                        }
                    } else {
                        friendRequestStatus = null
                    }
                }
            } catch (e: Exception) {
                Log.e("PROFILE_DEBUG", "Error fetching other profile", e)
            }
        } else {
            otherUserProfile = null
            isFriendWithOther = false
            friendRequestStatus = null
        }
    }

    val displayedEvents = remember(calendarEvents, userProfile?.classes, sharedEvents) {
        val virtualEvents = userProfile?.classes?.flatMap { classInfo: ClassInfo ->
            val daysMap = mapOf(
                "Monday" to java.util.Calendar.MONDAY,
                "Tuesday" to java.util.Calendar.TUESDAY,
                "Wednesday" to java.util.Calendar.WEDNESDAY,
                "Thursday" to java.util.Calendar.THURSDAY,
                "Friday" to java.util.Calendar.FRIDAY,
                "Saturday" to java.util.Calendar.SATURDAY,
                "Sunday" to java.util.Calendar.SUNDAY,
                "M" to java.util.Calendar.MONDAY,
                "T" to java.util.Calendar.TUESDAY,
                "W" to java.util.Calendar.WEDNESDAY,
                "TH" to java.util.Calendar.THURSDAY,
                "R" to java.util.Calendar.THURSDAY,
                "F" to java.util.Calendar.FRIDAY,
                "SA" to java.util.Calendar.SATURDAY,
                "S" to java.util.Calendar.SATURDAY,
                "SU" to java.util.Calendar.SUNDAY,
                "U" to java.util.Calendar.SUNDAY
            )

            val selectedDays = classInfo.dayOfWeek.split(Regex("[,\\s]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            selectedDays.mapNotNull { dayKey ->
                val dayOfWeek = daysMap[dayKey] ?: return@mapNotNull null

                val now = java.util.Calendar.getInstance()
                val eventCal = java.util.Calendar.getInstance()

                eventCal.set(java.util.Calendar.DAY_OF_WEEK, dayOfWeek)

                val sdf = SimpleDateFormat("hh:mm a", Locale.US)
                val startCal = java.util.Calendar.getInstance()
                try {
                    sdf.parse(classInfo.startTime)?.let { startCal.time = it }
                } catch (e: Exception) {
                    startCal.set(java.util.Calendar.HOUR_OF_DAY, 9)
                    startCal.set(java.util.Calendar.MINUTE, 0)
                }
                val startHour = startCal.get(java.util.Calendar.HOUR_OF_DAY)
                val startMin = startCal.get(java.util.Calendar.MINUTE)

                eventCal.set(java.util.Calendar.HOUR_OF_DAY, startHour)
                eventCal.set(java.util.Calendar.MINUTE, startMin)
                eventCal.set(java.util.Calendar.SECOND, 0)
                eventCal.set(java.util.Calendar.MILLISECOND, 0)

                if (eventCal.get(java.util.Calendar.WEEK_OF_YEAR) < now.get(java.util.Calendar.WEEK_OF_YEAR)) {
                    eventCal.add(java.util.Calendar.WEEK_OF_YEAR, 1)
                }

                val endCal = java.util.Calendar.getInstance()
                try {
                    sdf.parse(classInfo.endTime)?.let { endCal.time = it }
                } catch (e: Exception) {
                    endCal.set(java.util.Calendar.HOUR_OF_DAY, startHour + 1)
                    endCal.set(java.util.Calendar.MINUTE, startMin)
                }
                val endHour = endCal.get(java.util.Calendar.HOUR_OF_DAY)
                val endMin = endCal.get(java.util.Calendar.MINUTE)

                val endDateTimeCal = eventCal.clone() as java.util.Calendar
                endDateTimeCal.set(java.util.Calendar.HOUR_OF_DAY, endHour)
                endDateTimeCal.set(java.util.Calendar.MINUTE, endMin)

                Event().apply {
                    summary = classInfo.className
                    location = "${classInfo.building} ${classInfo.roomNumber}"
                    start = EventDateTime().setDateTime(DateTime(eventCal.time))
                    end = EventDateTime().setDateTime(DateTime(endDateTimeCal.time))
                    id = "virtual_${classInfo.className}_$dayKey"
                }
            }
        } ?: emptyList()

        val bookmarkedSharedEvents = sharedEvents.filter { userProfile?.bookmarkedEventIds?.contains(it.id) == true }

        calendarEvents + virtualEvents + bookmarkedSharedEvents
    }

    val myBookmarkedEvents = displayedEvents.filter { userProfile?.bookmarkedEventIds?.contains(it.id) == true }

    if (viewOtherUserId != null) {
        BackHandler {
            viewOtherUserId = null
            otherUserProfile = null
        }

        if (otherUserProfile != null) {
            val canViewFullProfile = when (otherUserProfile?.profileVisibility) {
                "Friends Only" -> isFriendWithOther
                else -> true // Default to Public
            }

            if (canViewFullProfile) {
                val otherBookmarkedEvents =
                    displayedEvents.filter { otherUserProfile?.bookmarkedEventIds?.contains(it.id) == true }
                ProfileScreen(
                    userProfile = otherUserProfile!!,
                    isMyProfile = false,
                    friends = emptyList(),
                    isFriend = isFriendWithOther,
                    friendRequestStatus = friendRequestStatus,
                    bookmarkedEvents = otherBookmarkedEvents,
                    onSignOut = {},
                    onEditProfile = {},
                    onDeleteAccount = {},
                    onAddFriend = {
                        scope.launch {
                            try {
                                val currentUid = firebaseUser?.uid ?: return@launch
                                val targetUid = viewOtherUserId ?: return@launch
                                val chatRepo = ChatRepository(db)
                                chatRepo.sendFriendRequest(currentUid, targetUid)
                                friendRequestStatus = "sent"
                            } catch (e: Exception) {
                                Log.e("FRIEND_DEBUG", "Error sending friend request", e)
                            }
                        }
                    },
                    onAcceptFriend = {
                        scope.launch {
                            try {
                                val currentUid = firebaseUser?.uid ?: return@launch
                                val targetUid = viewOtherUserId ?: return@launch
                                val chatRepo = ChatRepository(db)
                                chatRepo.acceptFriendRequest(currentUid, targetUid)
                                isFriendWithOther = true
                                friendRequestStatus = null
                            } catch (e: Exception) {
                                Log.e("FRIEND_DEBUG", "Error accepting friend request", e)
                            }
                        }
                    },
                    onDeclineFriend = {
                        scope.launch {
                            try {
                                val currentUid = firebaseUser?.uid ?: return@launch
                                val targetUid = viewOtherUserId ?: return@launch
                                val chatRepo = ChatRepository(db)
                                chatRepo.declineFriendRequest(currentUid, targetUid)
                                friendRequestStatus = null
                            } catch (e: Exception) {
                                Log.e("FRIEND_DEBUG", "Error declining friend request", e)
                            }
                        }
                    },
                    onCancelFriend = {
                        scope.launch {
                            try {
                                val currentUid = firebaseUser?.uid ?: return@launch
                                val targetUid = viewOtherUserId ?: return@launch
                                val chatRepo = ChatRepository(db)
                                chatRepo.cancelFriendRequest(currentUid, targetUid)
                                friendRequestStatus = null
                            } catch (e: Exception) {
                                Log.e("FRIEND_DEBUG", "Error cancelling friend request", e)
                            }
                        }
                    },
                    onRemoveFriend = {
                        scope.launch {
                            try {
                                val currentUid = firebaseUser?.uid ?: return@launch
                                val targetUid = viewOtherUserId ?: return@launch
                                val chatRepo = ChatRepository(db)
                                chatRepo.removeFriend(currentUid, targetUid)
                                isFriendWithOther = false
                                friendRequestStatus = null
                            } catch (e: Exception) {
                                Log.e("FRIEND_DEBUG", "Error removing friend", e)
                            }
                        }
                    },
                    onBack = {
                        viewOtherUserId = null
                        otherUserProfile = null
                    },
                    onEditSchedule = {}
                )
            } else {
                // Restricted View for "Friends Only" profiles when not friends
                RestrictedProfileScreen(
                    userProfile = otherUserProfile!!,
                    friendRequestStatus = friendRequestStatus,
                    onAddFriend = {
                        scope.launch {
                            try {
                                val currentUid = firebaseUser?.uid ?: return@launch
                                val targetUid = viewOtherUserId ?: return@launch
                                val chatRepo = ChatRepository(db)
                                chatRepo.sendFriendRequest(currentUid, targetUid)
                                friendRequestStatus = "sent"
                            } catch (e: Exception) {
                                Log.e("FRIEND_DEBUG", "Error sending friend request", e)
                            }
                        }
                    },
                    onAcceptFriend = {
                        scope.launch {
                            try {
                                val currentUid = firebaseUser?.uid ?: return@launch
                                val targetUid = viewOtherUserId ?: return@launch
                                val chatRepo = ChatRepository(db)
                                chatRepo.acceptFriendRequest(currentUid, targetUid)
                                isFriendWithOther = true
                                friendRequestStatus = null
                            } catch (e: Exception) {
                                Log.e("FRIEND_DEBUG", "Error accepting friend request", e)
                            }
                        }
                    },
                    onDeclineFriend = {
                        scope.launch {
                            try {
                                val currentUid = firebaseUser?.uid ?: return@launch
                                val targetUid = viewOtherUserId ?: return@launch
                                val chatRepo = ChatRepository(db)
                                chatRepo.declineFriendRequest(currentUid, targetUid)
                                friendRequestStatus = null
                            } catch (e: Exception) {
                                Log.e("FRIEND_DEBUG", "Error declining friend request", e)
                            }
                        }
                    },
                    onCancelFriend = {
                        scope.launch {
                            try {
                                val currentUid = firebaseUser?.uid ?: return@launch
                                val targetUid = viewOtherUserId ?: return@launch
                                val chatRepo = ChatRepository(db)
                                chatRepo.cancelFriendRequest(currentUid, targetUid)
                                friendRequestStatus = null
                            } catch (e: Exception) {
                                Log.e("FRIEND_DEBUG", "Error cancelling friend request", e)
                            }
                        }
                    },
                    onBack = {
                        viewOtherUserId = null
                        otherUserProfile = null
                    }
                )
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    if (pendingNotificationChatId != null && firebaseUser != null && userProfile != null) {
        ChatScreen(
            chatId = pendingNotificationChatId!!,
            title = pendingNotificationChatTitle ?: "Chat",
            onBack = {
                viewOtherUserId = null
                consumePendingNotificationChat()
                currentDestination = AppDestinations.FRIENDS
            },
            onLocationClick = { latLng, name, senderId ->
                sharedLocationToView = latLng
                sharedLocationNameToView = name
                sharedLocationByUidToView = senderId
                consumePendingNotificationChat()
                currentDestination = AppDestinations.MAP
            }
        )
        return
    }

    val needsProfileSetup = userProfile.needsProfileSetup()

    if (firebaseUser == null) {
        LoginScreen(
            onSignInClick = {
                signInLauncher.launch(googleSignInClient.signInIntent)
            }
        )
    } else if (isLoadingProfile) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (isEditingSchedule && userProfile != null) {
        BackHandler {
            isEditingSchedule = false
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            ScheduleEditScreen(
                userProfile = userProfile!!,
                onSave = { updatedClasses, newSemester ->
                    scope.launch {
                        try {
                            val user = firebaseUser ?: throw Exception("No signed-in user")
                            db.collection("users").document(user.uid)
                                .update(
                                    mapOf(
                                        "classes" to updatedClasses,
                                        "semester" to newSemester
                                    )
                                ).await()
                            isEditingSchedule = false
                        } catch (e: Exception) {
                            Log.e("PROFILE_DEBUG", "Error saving schedule", e)
                        }
                    }
                },
                onBack = { isEditingSchedule = false }
            )
        }
    } else if (needsProfileSetup || isEditingProfile) {
        ProfileCreationScreen(
            initialProfile = userProfile,
            initialName = firebaseUser?.displayName ?: "",
            initialEmail = firebaseUser?.email ?: "",
            onSaveProfile = { newProfile ->
                scope.launch {
                    try {
                        val user = firebaseUser ?: throw Exception("No signed-in user")
                        val profileWithId = newProfile.copy(uid = user.uid)
                        val normalizedEmail = profileWithId.email.trim().lowercase()
                        val trimmedDisplayName = profileWithId.name.trim()

                        val userDoc = mapOf(
                            "uid" to user.uid,
                            "name" to trimmedDisplayName,
                            "displayName" to trimmedDisplayName,
                            "email" to normalizedEmail,
                            "searchEmail" to normalizedEmail,
                            "searchName" to trimmedDisplayName.lowercase(),
                            "major" to profileWithId.major.trim(),
                            "bio" to profileWithId.bio.trim(),
                            "location" to profileWithId.location.trim(),
                            "githubUrl" to profileWithId.githubUrl.trim(),
                            "profilePictureUrl" to profileWithId.profilePictureUrl,
                            "bannerUrl" to profileWithId.bannerUrl,
                            "joinDate" to profileWithId.joinDate,
                            "followersCount" to profileWithId.followersCount,
                            "followingCount" to profileWithId.followingCount,
                            "isOnline" to true,
                            "isProfileComplete" to true,
                            "updatedAt" to FieldValue.serverTimestamp(),
                            "bookmarkedEventIds" to profileWithId.bookmarkedEventIds,
                            "semester" to profileWithId.semester,
                            "classes" to profileWithId.classes
                        )

                        db.collection("users")
                            .document(user.uid)
                            .set(userDoc, SetOptions.merge())
                            .await()

                        userProfile = profileWithId
                        isEditingProfile = false
                        saveCurrentFcmTokenForUser()
                    } catch (e: Exception) {
                        Log.e("AUTH_DEBUG", "Error saving profile", e)
                    }
                }
            },
            onBack = if (isEditingProfile) {
                { isEditingProfile = false }
            } else {
                null
            }
        )
    } else if (isAddingEvent || editingEvent != null) {
        AddEventScreen(
            initialDateMillis = initialDateForNewEvent,
            editingEvent = editingEvent,
            onBackClick = { 
                isAddingEvent = false
                editingEvent = null
            },
            onSaveClick = { schedule, eventId ->
                scope.launch {
                    signedInAccount?.let { account ->
                        val success = if (eventId != null) {
                            activity?.updateEventInCalendar(account, eventId, schedule) ?: false
                        } else {
                            (activity?.addEventToCalendar(account, schedule) != null)
                        }
                        
                        if (success) {
                            val events = activity?.getCalendarEvents(account)
                            if (events != null) calendarEvents = events
                            isAddingEvent = false
                            editingEvent = null
                        }
                    }
                }
            },
            onDeleteClick = { eventId ->
                scope.launch {
                    signedInAccount?.let { account ->
                        val success = activity?.deleteEventFromCalendar(account, eventId) ?: false
                        if (success) {
                            val events = activity?.getCalendarEvents(account)
                            if (events != null) calendarEvents = events
                            editingEvent = null
                        }
                    }
                }
            },
            onSetReminder = { event ->
                eventForReminderInEditor = event
                showReminderDialogInEditor = true
            }
        )

        if (showReminderDialogInEditor && eventForReminderInEditor != null) {
            com.example.classseek.ui.calendar.ReminderDialog(
                eventTitle = eventForReminderInEditor?.summary ?: "Untitled Event",
                onDismiss = {
                    showReminderDialogInEditor = false
                    eventForReminderInEditor = null
                },
                onSetReminder = { minutes ->
                    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                    val event = eventForReminderInEditor
                    if (uid != null && event != null) {
                        val eventTimeMillis = event.start?.dateTime?.value
                            ?: event.start?.date?.value
                            ?: System.currentTimeMillis()

                        val testReminderTime = System.currentTimeMillis() - 60000

                        val reminderData = hashMapOf(
                            "eventId" to (event.id ?: "test"),
                            "eventTitle" to (event.summary ?: "Test Event"),
                            "eventTime" to eventTimeMillis,
                            "reminderMinutes" to minutes,
                            "reminderTime" to testReminderTime,
                            "notificationSent" to false,
                        )

                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("users")
                            .document(uid)
                            .collection("reminders")
                            .document(event.id ?: "test")
                            .set(reminderData)
                    }
                    showReminderDialogInEditor = false
                    eventForReminderInEditor = null
                }
            )
        }
    } else {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                AppDestinations.entries.forEach { destination: AppDestinations ->
                    item(
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                        selected = destination == currentDestination,
                        onClick = { currentDestination = destination }
                    )
                }
            }
        ) {
            Scaffold( modifier = Modifier.fillMaxSize(),containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
                Box(modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(innerPadding)) {
                    when (currentDestination) {
                        AppDestinations.CALENDAR -> {
                            CalendarScreen(
                                signedInAccount = signedInAccount,
                                calendarEvents = displayedEvents,
                                userProfile = userProfile,
                                onSignInClick = { intent -> signInLauncher.launch(intent) },
                                onAddEventClick = { dateMillis ->
                                    initialDateForNewEvent = dateMillis
                                    isAddingEvent = true
                                },
                                onEditEventClick = { event ->
                                    editingEvent = event
                                },
                                onDeleteEventClick = { eventId ->
                                    scope.launch {
                                        signedInAccount?.let { account ->
                                            val success = activity?.deleteEventFromCalendar(account, eventId) ?: false
                                            if (success) {
                                                val events = activity?.getCalendarEvents(account)
                                                if (events != null) calendarEvents = events
                                            }
                                        }
                                    }
                                },
                                chatRepository = repo,
                                myUid = firebaseUser?.uid ?: ""
                            )
                        }

                        AppDestinations.PROFILE -> {
                            val myBookmarkedEvents = displayedEvents.filter { userProfile?.bookmarkedEventIds?.contains(it.id) == true }
                            ProfileScreen(
                                userProfile = userProfile!!,
                                isMyProfile = true,
                                bookmarkedEvents = myBookmarkedEvents,
                                friends = profileFriends,
                                onSignOut = {},
                                onEditProfile = {},
                                onDeleteAccount = {},
                                onFriendMessage = { friend ->
                                    scope.launch {
                                        try {
                                            val currentUid = firebaseUser?.uid ?: return@launch
                                            val title = friend.displayName.ifBlank {
                                                friend.name.ifBlank { friend.email }
                                            }
                                            val chatRepo = ChatRepository(db)
                                            val chatId = chatRepo.openOrCreateDm(currentUid, friend.uid, title)
                                            routedChatId = chatId
                                            routedChatTitle = title
                                            currentDestination = AppDestinations.FRIENDS
                                        } catch (e: Exception) {
                                            Log.e("FRIEND_DEBUG", "Error opening profile DM", e)
                                        }
                                    }
                                },
                                onViewFriendProfile = { friendUid ->
                                    viewOtherUserId = friendUid
                                },
                                onRemoveFriendFromList = { friendUid ->
                                    scope.launch {
                                        try {
                                            val currentUid = firebaseUser?.uid ?: return@launch
                                            val chatRepo = ChatRepository(db)
                                            chatRepo.removeFriend(currentUid, friendUid)
                                        } catch (e: Exception) {
                                            Log.e("FRIEND_DEBUG", "Error removing friend from profile list", e)
                                        }
                                    }
                                },
                                onRemoveBookmark = { eventId ->
                                    scope.launch {
                                        try {
                                            val uid = firebaseUser?.uid ?: return@launch
                                            db.collection("users").document(uid)
                                                .update("bookmarkedEventIds", FieldValue.arrayRemove(eventId))
                                                .await()
                                        } catch (e: Exception) {
                                            Log.e("PROFILE_DEBUG", "Error removing bookmark", e)
                                        }
                                    }
                                },
                                onEditSchedule = {
                                    isEditingSchedule = true
                                }
                            )
                        }

                        AppDestinations.FRIENDS -> {
                            FriendsScreen(
                                friends = profileFriends,
                                initialChatId = routedChatId ?: pendingNotificationChatId,
                                initialChatTitle = routedChatTitle ?: pendingNotificationChatTitle,
                                onInitialChatConsumed = {
                                    consumeRoutedChat()
                                    consumePendingNotificationChat()
                                },
                                onNavigateToProfile = { uid ->
                                    viewOtherUserId = uid
                                },
                                onLocationClick = { latLng, name, senderId ->
                                    sharedLocationToView = latLng
                                    sharedLocationNameToView = name
                                    sharedLocationByUidToView = senderId
                                    currentDestination = AppDestinations.MAP
                                },
                                auth = auth
                            )
                        }

                        AppDestinations.MAP -> {
                            MapScreen(
                                userProfile = userProfile,
                                calendarEvents = displayedEvents,
                                temporaryMarkers = temporaryMarkers,
                                onAddTemporaryMarker = { temporaryMarkers.add(it) },
                                sharedLocation = sharedLocationToView,
                                sharedLocationName = sharedLocationNameToView,
                                sharedByUid = sharedLocationByUidToView,
                                isDarkTheme = isDarkTheme
                            )
                        }

                        AppDestinations.SETTINGS -> {
                            SettingsProfileScreen(
                                userProfile = userProfile!!,
                                themeMode = themeMode,
                                onThemeModeChange = onThemeModeChange,
                                onEditProfile = {
                                    isEditingProfile = true
                                },
                                onSignOut = {
                                    auth.signOut()
                                    googleSignInClient.signOut()
                                    clearSessionState()
                                },
                                onDeleteAccount = {
                                    scope.launch {
                                        deleteCurrentAccount(
                                            auth = auth,
                                            db = db,
                                            googleSignInClient = googleSignInClient,
                                            onLocalStateCleared = {
                                                clearSessionState()
                                            }
                                        )
                                    }
                                },
                                onUpdateSettings = { newSettings ->
                                    scope.launch {
                                        try {
                                            db.collection("users").document(firebaseUser?.uid ?: return@launch)
                                                .update(newSettings)
                                                .await()
                                        } catch (e: Exception) {
                                            Log.e("SETTINGS_DEBUG", "Error updating settings", e)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

enum class AppDestinations(val label: String, val icon: ImageVector) {
    PROFILE("Profile", Icons.Default.Person),
    FRIENDS("Messages", Icons.Default.ChatBubbleOutline),
    CALENDAR("Calendar", Icons.Default.DateRange),
    MAP("Map", Icons.Default.Place),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
