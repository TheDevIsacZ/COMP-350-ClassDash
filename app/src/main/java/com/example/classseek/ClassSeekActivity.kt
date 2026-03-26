package com.example.classseek

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.classseek.models.ClassSchedule
import com.example.classseek.ui.AddEventScreen
import com.example.classseek.ui.CalendarScreen
import com.example.classseek.ui.MapScreen
import com.example.classseek.ui.friends.FriendsScreen
import com.example.classseek.ui.theme.ClassSeekTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.Calendar
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.firebase.FirebaseApp
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

                eventsResult.items ?: emptyList()
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
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    var isAddingEvent by remember { mutableStateOf(false) }
    var initialDateForNewEvent by remember { mutableStateOf<Long?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context as? ClassSeekActivity }

    var calendarEvents by remember { mutableStateOf<List<Event>>(emptyList()) }
    var signedInAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            signedInAccount = account
            account?.let {
                scope.launch {
                    val events = activity?.getCalendarEvents(it)
                    if (events != null) calendarEvents = events
                }
            }
        } catch (e: ApiException) {
            Log.e("CALENDAR_DEBUG", "signInLauncher: Sign-in failed", e)
        }
    }

    if (isAddingEvent) {
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
                                onSignOutClick = {
                                    signedInAccount = null
                                    calendarEvents = emptyList()
                                },
                                onRefreshClick = { account ->
                                    val events = activity?.getCalendarEvents(account)
                                    if (events != null) calendarEvents = events
                                },
                                onAddEventClick = { dateMillis ->
                                    initialDateForNewEvent = dateMillis
                                    isAddingEvent = true
                                }
                            )
                        }
                        AppDestinations.HOME -> Greeting("Home")
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
    HOME("Home", Icons.Default.Home),
    FRIENDS("Friends", Icons.Default.Person),
    MAP("Map", Icons.Default.Place),
    CALENDAR("Calendar", Icons.Default.DateRange),
    SETTINGS("Settings", Icons.Default.Settings),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}
