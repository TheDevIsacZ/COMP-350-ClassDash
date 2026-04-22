package com.example.classseek.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.filled.People
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.classseek.Notification.MyFirebaseMessagingService
import com.example.classseek.R
import com.example.classseek.data.ChatRepository
import com.example.classseek.models.ClassSchedule
import com.example.classseek.models.UserProfile
import com.example.classseek.ui.calendar.AddEventScreen
import com.example.classseek.ui.calendar.CalendarScreen
import com.example.classseek.ui.chat.ChatScreen
import com.example.classseek.ui.friends.FriendsScreen
import com.example.classseek.ui.map.MapScreen
import com.example.classseek.ui.theme.ClassSeekTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val schoolCalendarID =
    "c_d036dc6b1c2f9cf0ee499356cc98d2e8f058d29b901ea774320f587ed01805bb@group.calendar.google.com"

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
            ClassSeekTheme {
                ClassSeekApp(
                    initialChatId = launchChatIdState.value,
                    initialChatTitle = launchChatTitleState.value,
                    onNotificationChatConsumed = {
                        launchChatIdState.value = null
                        launchChatTitleState.value = null
                    }
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

                val eventsResult = service.events().list("primary")
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .setMaxResults(50)
                    .execute()

                val schoolEvents = try {
                    service.events().list(schoolCalendarID)
                        .setOrderBy("startTime")
                        .setSingleEvents(true)
                        .setMaxResults(50)
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

                val event = Event().apply {
                    summary = schedule.className
                    location = schedule.location
                    description = "Added via ClassSeek"
                }

                val firstOccurrence = getFirstOccurrence(schedule)
                val durationMs = getDurationMs(schedule.startTime, schedule.endTime)

                val startDateTime = DateTime(firstOccurrence.time)
                event.start = EventDateTime()
                    .setDateTime(startDateTime)
                    .setTimeZone(TimeZone.getDefault().id)

                val endDateTime = DateTime(firstOccurrence.timeInMillis + durationMs)
                event.end = EventDateTime()
                    .setDateTime(endDateTime)
                    .setTimeZone(TimeZone.getDefault().id)

                if (schedule.startDate != schedule.endDate || schedule.daysOfWeek.size > 1) {
                    val daysMap = mapOf(
                        java.util.Calendar.MONDAY to "MO",
                        java.util.Calendar.TUESDAY to "TU",
                        java.util.Calendar.WEDNESDAY to "WE",
                        java.util.Calendar.THURSDAY to "TH",
                        java.util.Calendar.FRIDAY to "FR",
                        java.util.Calendar.SATURDAY to "SA",
                        java.util.Calendar.SUNDAY to "SU"
                    )
                    val byDay = schedule.daysOfWeek.joinToString(",") { daysMap[it] ?: "" }
                    val untilDate = SimpleDateFormat(
                        "yyyyMMdd'T'HHmmss'Z'",
                        Locale.getDefault()
                    ).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date(schedule.endDate))

                    event.recurrence =
                        listOf("RRULE:FREQ=WEEKLY;BYDAY=$byDay;UNTIL=$untilDate")
                }

                service.events().insert("primary", event).execute()
                true
            } catch (e: Exception) {
                Log.e("CALENDAR_DEBUG", "addEventToCalendar: ERROR", e)
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

        val timeParts = schedule.startTime.split(":")
        cal.set(
            java.util.Calendar.HOUR_OF_DAY,
            if (timeParts.isNotEmpty()) timeParts[0].toInt() else 9
        )
        cal.set(
            java.util.Calendar.MINUTE,
            if (timeParts.size > 1) timeParts[1].toInt() else 0
        )
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)

        var safetyCounter = 0
        while (
            !schedule.daysOfWeek.contains(cal.get(java.util.Calendar.DAY_OF_WEEK)) &&
            safetyCounter < 8
        ) {
            cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
            safetyCounter++
        }
        return cal
    }

    private fun getDurationMs(start: String, end: String): Long {
        return try {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
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

@Composable
fun ClassSeekApp(
    initialChatId: String? = null,
    initialChatTitle: String? = null,
    onNotificationChatConsumed: () -> Unit = {}
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    val firebaseUser = remember { mutableStateOf(auth.currentUser) }
    val userProfile = remember { mutableStateOf<UserProfile?>(null) }
    val isLoadingProfile = remember { mutableStateOf(true) }

    val currentDestination = rememberSaveable { mutableStateOf(AppDestinations.CALENDAR) }
    val isAddingEvent = remember { mutableStateOf(false) }
    val isEditingProfile = remember { mutableStateOf(false) }
    val viewOtherUserId = remember { mutableStateOf<String?>(null) }
    val otherUserProfile = remember { mutableStateOf<UserProfile?>(null) }
    val isFriendWithOther = remember { mutableStateOf(false) }
    val friendRequestStatus = remember { mutableStateOf<String?>(null) } // null, "pending", "sent"
    val initialDateForNewEvent = remember { mutableStateOf<Long?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context as? ClassSeekActivity }

    val calendarEvents = remember { mutableStateOf<List<Event>>(emptyList()) }
    val signedInAccount = remember { mutableStateOf<GoogleSignInAccount?>(null) }

    val pendingNotificationChatId = remember { mutableStateOf<String?>(null) }
    val pendingNotificationChatTitle = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialChatId, initialChatTitle) {
        pendingNotificationChatId.value = initialChatId
        pendingNotificationChatTitle.value = initialChatTitle
    }

    fun consumePendingNotificationChat() {
        pendingNotificationChatId.value = null
        pendingNotificationChatTitle.value = null
        onNotificationChatConsumed()
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

    LaunchedEffect(firebaseUser.value?.uid) {
        if (firebaseUser.value != null) {
            saveCurrentFcmTokenForUser()
        }
    }

    LaunchedEffect(firebaseUser.value?.uid) {
        if (firebaseUser.value != null && pendingNotificationChatId.value != null) {
            currentDestination.value = AppDestinations.FRIENDS
        }
    }

    DisposableEffect(firebaseUser.value?.uid, lifecycleOwner) {
        val uid = firebaseUser.value?.uid
        if (uid == null) return@DisposableEffect onDispose {}

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    db.collection("users").document(uid).update("isOnline", true)
                }
                Lifecycle.Event.ON_STOP -> {
                    db.collection("users").document(uid).update("isOnline", false)
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // If user logs out or component is destroyed, ensure they are offline
            db.collection("users").document(uid).update("isOnline", false)
        }
    }

    LaunchedEffect(firebaseUser.value) {
        if (firebaseUser.value != null) {
            isLoadingProfile.value = true
            try {
                val doc = db.collection("users").document(firebaseUser.value!!.uid).get().await()
                userProfile.value = if (doc.exists()) {
                    doc.toObject(UserProfile::class.java)
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("AUTH_DEBUG", "Error fetching profile", e)
            } finally {
                isLoadingProfile.value = false
            }
        } else {
            userProfile.value = null
            isLoadingProfile.value = false
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
                signedInAccount.value = account
                scope.launch {
                    val events = activity?.getCalendarEvents(account)
                    if (events != null) calendarEvents.value = events
                }
            } else {
                val account = GoogleSignIn.getLastSignedInAccount(context)
                if (account != null) {
                    signedInAccount.value = account
                    scope.launch {
                        val events = activity?.getCalendarEvents(account)
                        if (events != null) calendarEvents.value = events
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
            signedInAccount.value = account
            account?.idToken?.let { idToken ->
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                scope.launch {
                    try {
                        val authResult = auth.signInWithCredential(credential).await()
                        firebaseUser.value = authResult.user
                        saveCurrentFcmTokenForUser()

                        val events = activity?.getCalendarEvents(account)
                        if (events != null) calendarEvents.value = events
                    } catch (e: Exception) {
                        Log.e("AUTH_DEBUG", "Firebase auth failed", e)
                    }
                }
            }
        } catch (e: ApiException) {
            Log.e("CALENDAR_DEBUG", "signInLauncher: Sign-in failed", e)
        }
    }

    DisposableEffect(viewOtherUserId.value, firebaseUser.value?.uid) {
        val currentUid = firebaseUser.value?.uid
        val targetUid = viewOtherUserId.value

        if (targetUid == null) {
            otherUserProfile.value = null
            isFriendWithOther.value = false
            friendRequestStatus.value = null
            onDispose {}
        } else {
            // Profile listener
            val profileReg = db.collection("users").document(targetUid)
                .addSnapshotListener { snapshot, _ ->
                    otherUserProfile.value = snapshot?.toObject(UserProfile::class.java)
                }

            var friendsReg: com.google.firebase.firestore.ListenerRegistration? = null
            var incomingReg: com.google.firebase.firestore.ListenerRegistration? = null
            var outgoingReg: com.google.firebase.firestore.ListenerRegistration? = null

            if (currentUid != null) {
                // Friends listener
                friendsReg = db.collection("users").document(currentUid)
                    .collection("friends").document(targetUid)
                    .addSnapshotListener { snapshot, _ ->
                        isFriendWithOther.value = snapshot?.exists() == true
                    }

                var incomingExists = false
                var outgoingExists = false

                // Incoming request listener
                incomingReg = db.collection("users").document(currentUid)
                    .collection("friendRequests").document(targetUid)
                    .addSnapshotListener { snapshot, _ ->
                        incomingExists = snapshot?.exists() == true
                        friendRequestStatus.value = when {
                            incomingExists -> "pending"
                            outgoingExists -> "sent"
                            else -> null
                        }
                    }

                // Outgoing request listener
                outgoingReg = db.collection("users").document(currentUid)
                    .collection("sentFriendRequests").document(targetUid)
                    .addSnapshotListener { snapshot, _ ->
                        outgoingExists = snapshot?.exists() == true
                        friendRequestStatus.value = when {
                            incomingExists -> "pending"
                            outgoingExists -> "sent"
                            else -> null
                        }
                    }
            }

            onDispose {
                profileReg.remove()
                friendsReg?.remove()
                incomingReg?.remove()
                outgoingReg?.remove()
            }
        }
    }

    if (pendingNotificationChatId.value != null && firebaseUser.value != null && userProfile.value != null) {
        ChatScreen(
            chatId = pendingNotificationChatId.value!!,
            title = pendingNotificationChatTitle.value ?: "Chat",
            onBack = {
                consumePendingNotificationChat()
                currentDestination.value = AppDestinations.FRIENDS
            }
        )
        return
    }

    if (otherUserProfile.value != null) {
        ProfileScreen(
            userProfile = otherUserProfile.value!!,
            isMyProfile = false,
            isFriend = isFriendWithOther.value,
            friendRequestStatus = friendRequestStatus.value,
            onSignOut = {
                scope.launch {
                    firebaseUser.value?.uid?.let { uid ->
                        db.collection("users").document(uid).update("isOnline", false).await()
                    }
                    auth.signOut()
                    googleSignInClient.signOut()
                    firebaseUser.value = null
                    signedInAccount.value = null
                    calendarEvents.value = emptyList()
                    consumePendingNotificationChat()
                    viewOtherUserId.value = null
                }
            },
            onEditProfile = {}, // Can't edit others
            onDeleteAccount = {}, // Can't delete others
            onAddFriend = {
                scope.launch {
                    try {
                        val currentUid = firebaseUser.value?.uid ?: return@launch
                        val targetUid = viewOtherUserId.value ?: return@launch
                        val repo = ChatRepository(db)
                        repo.sendFriendRequest(currentUid, targetUid)
                    } catch (e: Exception) {
                        Log.e("FRIEND_DEBUG", "Error sending friend request", e)
                    }
                }
            },
            onAcceptFriend = {
                scope.launch {
                    try {
                        val currentUid = firebaseUser.value?.uid ?: return@launch
                        val targetUid = viewOtherUserId.value ?: return@launch
                        val repo = ChatRepository(db)
                        repo.acceptFriendRequest(currentUid, targetUid)
                    } catch (e: Exception) {
                        Log.e("FRIEND_DEBUG", "Error accepting friend request", e)
                    }
                }
            },
            onDeclineFriend = {
                scope.launch {
                    try {
                        val currentUid = firebaseUser.value?.uid ?: return@launch
                        val targetUid = viewOtherUserId.value ?: return@launch
                        val repo = ChatRepository(db)
                        repo.declineFriendRequest(currentUid, targetUid)
                    } catch (e: Exception) {
                        Log.e("FRIEND_DEBUG", "Error declining friend request", e)
                    }
                }
            },
            onCancelFriend = {
                scope.launch {
                    try {
                        val currentUid = firebaseUser.value?.uid ?: return@launch
                        val targetUid = viewOtherUserId.value ?: return@launch
                        val repo = ChatRepository(db)
                        repo.cancelFriendRequest(currentUid, targetUid)
                    } catch (e: Exception) {
                        Log.e("FRIEND_DEBUG", "Error cancelling friend request", e)
                    }
                }
            },
            onRemoveFriend = {
                scope.launch {
                    try {
                        val currentUid = firebaseUser.value?.uid ?: return@launch
                        val targetUid = viewOtherUserId.value ?: return@launch
                        val repo = ChatRepository(db)
                        repo.removeFriend(currentUid, targetUid)
                    } catch (e: Exception) {
                        Log.e("FRIEND_DEBUG", "Error removing friend", e)
                    }
                }
            },
            onBack = {
                viewOtherUserId.value = null
            }
        )
        return
    }

    if (firebaseUser.value == null) {
        LoginScreen(
            onSignInClick = {
                signInLauncher.launch(googleSignInClient.signInIntent)
            }
        )
    } else if (isLoadingProfile.value) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (userProfile.value == null || isEditingProfile.value) {
        ProfileCreationScreen(
            initialProfile = userProfile.value,
            initialName = firebaseUser.value?.displayName ?: "",
            initialEmail = firebaseUser.value?.email ?: "",
            onSaveProfile = { newProfile ->
                scope.launch {
                    try {
                        val user = firebaseUser.value ?: throw Exception("No signed-in user")

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
                            "isProfileComplete" to true,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )

                        db.collection("users")
                            .document(user.uid)
                            .set(
                                userDoc,
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                            .await()

                        userProfile.value = profileWithId
                        isEditingProfile.value = false
                        saveCurrentFcmTokenForUser()
                    } catch (e: Exception) {
                        Log.e("AUTH_DEBUG", "Error saving profile", e)
                    }
                }
            },
            onBack = if (isEditingProfile.value) {
                { isEditingProfile.value = false }
            } else {
                null
            }
        )
    } else if (isAddingEvent.value) {
        AddEventScreen(
            initialDateMillis = initialDateForNewEvent.value,
            onBackClick = { isAddingEvent.value = false },
            onSaveClick = { schedule ->
                scope.launch {
                    signedInAccount.value?.let { account ->
                        val success = activity?.addEventToCalendar(account, schedule) ?: false
                        if (success) {
                            val events = activity?.getCalendarEvents(account)
                            if (events != null) calendarEvents.value = events
                            isAddingEvent.value = false
                        }
                    }
                }
            }
        )
    } else {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                AppDestinations.entries.forEach {
                    item(
                        icon = { Icon(it.icon, contentDescription = it.label) },
                        label = { Text(it.label) },
                        selected = it == currentDestination.value,
                        onClick = { currentDestination.value = it }
                    )
                }
            }
        ) {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (currentDestination.value) {
                        AppDestinations.CALENDAR -> {
                            CalendarScreen(
                                signedInAccount = signedInAccount.value,
                                calendarEvents = calendarEvents.value,
                                onSignInClick = { intent -> signInLauncher.launch(intent) },
                                onAddEventClick = { dateMillis ->
                                    initialDateForNewEvent.value = dateMillis
                                    isAddingEvent.value = true
                                },
                                onDeleteEventClick = { eventId ->
                                    scope.launch {
                                        signedInAccount.value?.let { account ->
                                            val success = activity
                                                ?.deleteEventFromCalendar(account, eventId) ?: false
                                            if (success) {
                                                val events = activity?.getCalendarEvents(account)
                                                if (events != null) calendarEvents.value = events
                                            }
                                        }
                                    }
                                }
                            )
                        }

                        AppDestinations.PROFILE -> {
                            ProfileScreen(
                                userProfile = userProfile.value!!,
                                isMyProfile = true,
                                onSignOut = {
                                    scope.launch {
                                        firebaseUser.value?.uid?.let { uid ->
                                            db.collection("users").document(uid).update("isOnline", false).await()
                                        }
                                        auth.signOut()
                                        googleSignInClient.signOut()
                                        firebaseUser.value = null
                                        signedInAccount.value = null
                                        calendarEvents.value = emptyList()
                                        consumePendingNotificationChat()
                                    }
                                },
                                onEditProfile = {
                                    isEditingProfile.value = true
                                },
                                onDeleteAccount = {
                                    scope.launch {
                                        try {
                                            db.collection("users")
                                                .document(firebaseUser.value!!.uid)
                                                .delete()
                                                .await()

                                            auth.signOut()
                                            googleSignInClient.signOut()
                                            firebaseUser.value = null
                                            signedInAccount.value = null
                                            calendarEvents.value = emptyList()
                                            userProfile.value = null
                                            consumePendingNotificationChat()
                                        } catch (e: Exception) {
                                            Log.e("AUTH_DEBUG", "Error deleting account", e)
                                        }
                                    }
                                }
                            )
                        }

                        AppDestinations.FRIENDS -> {
                            FriendsScreen(
                                initialChatId = pendingNotificationChatId.value,
                                initialChatTitle = pendingNotificationChatTitle.value,
                                onInitialChatConsumed = {
                                    consumePendingNotificationChat()
                                },
                                onNavigateToProfile = { uid ->
                                    viewOtherUserId.value = uid
                                }
                            )
                        }

                        AppDestinations.MAP -> {
                            MapScreen()
                        }

                        AppDestinations.SETTINGS -> {
                            Greeting("Settings")
                        }
                    }
                }
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    PROFILE("Profile", Icons.Default.Person),
    FRIENDS("Messages", Icons.Default.ChatBubbleOutline),
    CALENDAR("Calendar", Icons.Default.DateRange),
    MAP("Map", Icons.Default.Place),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}