package com.example.classseek.ui

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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.example.classseek.R
import com.example.classseek.models.ClassSchedule
import com.example.classseek.models.UserProfile
import com.example.classseek.ui.calendar.AddEventScreen
import com.example.classseek.ui.calendar.CalendarScreen
import com.example.classseek.ui.map.MapScreen
import com.example.classseek.ui.friends.FriendsScreen
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
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val schoolCalendarID = "c_d036dc6b1c2f9cf0ee499356cc98d2e8f058d29b901ea774320f587ed01805bb@group.calendar.google.com"

class ClassSeekActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FirebaseApp.initializeApp(this)
        setContent {
            ClassSeekTheme {
                ClassSeekApp()
            }
        }
    }

    suspend fun getCalendarEvents(account: GoogleSignInAccount): List<Event>? {
        return withContext(Dispatchers.IO) {
            try {
                val calendarScope = "https://www.googleapis.com/auth/calendar"
                val credential = GoogleAccountCredential.usingOAuth2(
                    this@ClassSeekActivity, listOf(calendarScope)
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
                        .setMaxResults(70)
                        .execute().items ?: emptyList()
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

    suspend fun addEventToCalendar(account: GoogleSignInAccount, schedule: ClassSchedule): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val calendarScope = "https://www.googleapis.com/auth/calendar"
                val credential = GoogleAccountCredential.usingOAuth2(
                    this@ClassSeekActivity, listOf(calendarScope)
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
                event.start = EventDateTime().setDateTime(startDateTime).setTimeZone(TimeZone.getDefault().id)

                val endDateTime = DateTime(firstOccurrence.timeInMillis + durationMs)
                event.end = EventDateTime().setDateTime(endDateTime).setTimeZone(TimeZone.getDefault().id)

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
                    val untilDate = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.getDefault()).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date(schedule.endDate))

                    event.recurrence = listOf("RRULE:FREQ=WEEKLY;BYDAY=$byDay;UNTIL=$untilDate")
                }

                service.events().insert("primary", event).execute()
                true
            } catch (e: Exception) {
                Log.e("CALENDAR_DEBUG", "addEventToCalendar: ERROR", e)
                false
            }
        }
    }

    private fun getFirstOccurrence(schedule: ClassSchedule): java.util.Calendar {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = schedule.startDate

        val timeParts = schedule.startTime.split(":")
        cal.set(java.util.Calendar.HOUR_OF_DAY, if (timeParts.isNotEmpty()) timeParts[0].toInt() else 9)
        cal.set(java.util.Calendar.MINUTE, if (timeParts.size > 1) timeParts[1].toInt() else 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)

        var safetyCounter = 0
        while (!schedule.daysOfWeek.contains(cal.get(java.util.Calendar.DAY_OF_WEEK)) && safetyCounter < 8) {
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
            3600000L // 1 hour default
        }
    }
}

@Composable
fun ClassSeekApp() {
    val auth = FirebaseAuth.getInstance()
    var firebaseUser by remember { mutableStateOf(auth.currentUser) }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    var isLoadingProfile by remember { mutableStateOf(true) }

    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.CALENDAR) }
    var isAddingEvent by remember { mutableStateOf(false) }
    var isEditingProfile by remember { mutableStateOf(false) }
    var initialDateForNewEvent by remember { mutableStateOf<Long?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context as? ClassSeekActivity }

    var calendarEvents by remember { mutableStateOf<List<Event>>(emptyList()) }
    var signedInAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }

    val db = FirebaseFirestore.getInstance()

    // Fetch profile whenever firebaseUser changes
    LaunchedEffect(firebaseUser) {
        if (firebaseUser != null) {
            isLoadingProfile = true
            try {
                val doc = db.collection("users").document(firebaseUser!!.uid).get().await()
                if (doc.exists()) {
                    userProfile = doc.toObject(UserProfile::class.java)
                } else {
                    userProfile = null
                }
            } catch (e: Exception) {
                Log.e("AUTH_DEBUG", "Error fetching profile", e)
            } finally {
                isLoadingProfile = false
            }
        } else {
            userProfile = null
            isLoadingProfile = false
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

                        // After signing in, try to fetch calendar events
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

    if (firebaseUser == null) {
        LoginScreen(onSignInClick = {
            signInLauncher.launch(googleSignInClient.signInIntent)
        })
    } else if (isLoadingProfile) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (userProfile == null || isEditingProfile) {
        ProfileCreationScreen(
            initialProfile = userProfile,
            initialName = firebaseUser?.displayName ?: "",
            initialEmail = firebaseUser?.email ?: "",
            onSaveProfile = { newProfile ->
                scope.launch {
                    val profileWithId = newProfile.copy(uid = firebaseUser!!.uid)
                    try {
                        // Persist user profile data to Firestore
                        db.collection("users").document(firebaseUser!!.uid).set(profileWithId).await()
                        userProfile = profileWithId
                        isEditingProfile = false
                    } catch (e: Exception) {
                        Log.e("AUTH_DEBUG", "Error saving profile", e)
                    }
                }
            },
            // Show back button only when editing an existing profile
            onBack = if (isEditingProfile) {
                { isEditingProfile = false }
            } else null
        )
    } else if (isAddingEvent) {
        AddEventScreen(
            initialDateMillis = initialDateForNewEvent,
            onBackClick = { isAddingEvent = false },
            onSaveClick = { schedule ->
                scope.launch {
                    signedInAccount?.let { account ->
                        val success = activity?.addEventToCalendar(account, schedule) ?: false
                        if (success) {
                            val events = activity?.getCalendarEvents(account)
                            if (events != null) calendarEvents = events
                            isAddingEvent = false
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
                        selected = it == currentDestination,
                        onClick = { currentDestination = it }
                    )
                }
            }
        ) {
            Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    when (currentDestination) {
                        AppDestinations.CALENDAR -> {
                                CalendarScreen(
                                    signedInAccount = signedInAccount,
                                    calendarEvents = calendarEvents,
                                    onSignInClick = { intent -> signInLauncher.launch(intent) },
                                    onAddEventClick = { dateMillis ->
                                        initialDateForNewEvent = dateMillis
                                        isAddingEvent = true
                                    }
                                )
                        }
                        AppDestinations.PROFILE -> {
                            ProfileScreen(
                                userProfile = userProfile!!,
                                onSignOut = {
                                    auth.signOut()
                                    googleSignInClient.signOut()
                                    firebaseUser = null
                                    signedInAccount = null
                                    calendarEvents = emptyList()
                                },
                                onEditProfile = {
                                    isEditingProfile = true
                                },
                                onDeleteAccount = {
                                    scope.launch {
                                        try {
                                            // WARNING: This only deletes the Firestore profile data.
                                            // To fully delete the account, re-authentication is required to delete from Firebase Auth.
                                            // 1. Delete user document from Firestore
                                            db.collection("users").document(firebaseUser!!.uid).delete().await()

                                            // 2. Sign out and reset local app state
                                            auth.signOut()
                                            googleSignInClient.signOut()
                                            firebaseUser = null
                                            signedInAccount = null
                                            calendarEvents = emptyList()
                                            userProfile = null
                                        } catch (e: Exception) {
                                            Log.e("AUTH_DEBUG", "Error deleting account", e)
                                        }
                                    }
                                }
                            )
                        }
                        AppDestinations.FRIENDS -> FriendsScreen()
                        AppDestinations.MAP -> MapScreen()
                        AppDestinations.SETTINGS -> Greeting("Settings")
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
    FRIENDS("Friends", Icons.Default.People),
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
