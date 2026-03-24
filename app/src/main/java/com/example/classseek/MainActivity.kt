package com.example.classseek

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.CalendarView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar as JavaCalendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // --- Added code to print SHA-1 to Logcat ---
        try {
            val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                packageInfo.signingInfo?.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                packageInfo.signatures
            }

            signatures?.forEach { signature ->
                val md = MessageDigest.getInstance("SHA1")
                val digest = md.digest(signature.toByteArray())
                val sha1 = digest.joinToString(":") { "%02X".format(it) }
                Log.d("MY_SHA1_CERT", "Your SHA-1 Fingerprint is: $sha1")
            }
        } catch (e: Exception) {
            Log.e("MY_SHA1_CERT", "Error getting signature", e)
        }
        // -------------------------------------------

        enableEdgeToEdge()
        setContent {
            ClassSeekTheme {
                ClassSeekApp()
            }
        }
    }

    // for creating the calendar service: Instantiate the Calendar service and fetch events
    suspend fun getCalendarEvents(account: GoogleSignInAccount): List<Event>? {
        return withContext(Dispatchers.IO) {
            try {
                val credential = GoogleAccountCredential.usingOAuth2(
                    this@MainActivity, listOf("https://www.googleapis.com/auth/calendar")
                )
                credential.selectedAccount = account.account

                val service = Calendar.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("ClassSeek").build()

                // Fetch calendar events
                val events = service.events().list("primary")
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute()
                
                events.items
            } catch (e: Exception) {
                Log.e("CALENDAR_API", "Error fetching events", e)
                null
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun ClassSeekApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = remember(context) { context as? MainActivity }
    
    // State to store fetched events
    var calendarEvents by remember { mutableStateOf<List<Event>>(emptyList()) }
    
    // For runtime permission requests: Android 6.0+ logic
    var hasCalendarPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCalendarPermission = permissions[Manifest.permission.READ_CALENDAR] == true &&
                permissions[Manifest.permission.WRITE_CALENDAR] == true
    }

    // for initiating sign-in flow: Configure Google Sign-In Options
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/calendar"))
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    // for handling sign-in result
    var signedInAccount by remember { mutableStateOf<GoogleSignInAccount?>(null) }
    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            signedInAccount = account
            Log.d("GOOGLE_SIGN_IN", "Sign-in successful: ${account?.email}")
            
            account?.let { 
                scope.launch {
                    val events = activity?.getCalendarEvents(it)
                    if (events != null) {
                        calendarEvents = events
                    }
                }
            }
        } catch (e: ApiException) {
            Log.e("GOOGLE_SIGN_IN", "signInResult:failed code=" + e.statusCode)
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            it.icon,
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
                if (!hasCalendarPermission) {
                    Text(text = "Calendar access is required to sync your schedule.")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.WRITE_CALENDAR
                            )
                        )
                    }) {
                        Text("Grant Calendar Permissions")
                    }
                } else {
                    if (signedInAccount == null) {
                        Button(onClick = {
                            val signInIntent = googleSignInClient.signInIntent
                            signInLauncher.launch(signInIntent)
                        }) {
                            Text("go to calendar")
                        }
                    } else {
                        // Integrated Calendar View Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Schedule", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                                Text(text = signedInAccount?.email ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                            Button(onClick = {
                                googleSignInClient.signOut().addOnCompleteListener {
                                    signedInAccount = null
                                    calendarEvents = emptyList()
                                }
                            }) {
                                Text("Sign Out")
                            }
                        }

                        Spacer(modifier = Modifier.height(0.dp))

                        // In-app Calendar Widget
                        AndroidView(
                            factory = { context ->
                                CalendarView(context).apply {
                                    // Optional: You could sync selection here
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                        )

                        //Spacer(modifier = Modifier.height(0.dp))
                        HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)
                        Spacer(modifier = Modifier.height(0.dp))

                        // Display the Events List in "Schedule" style
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Upcoming Events", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Button(onClick = {
                                scope.launch {
                                    signedInAccount?.let { 
                                        val events = activity?.getCalendarEvents(it)
                                        if (events != null) calendarEvents = events
                                    }
                                }
                            }) {
                                Text("Refresh")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Today's Date Calculation (pure Kotlin/Android SDK)
                        val now = System.currentTimeMillis()
                        val todayCal = JavaCalendar.getInstance().apply {
                            timeInMillis = now
                            set(JavaCalendar.HOUR_OF_DAY, 0)
                            set(JavaCalendar.MINUTE, 0)
                            set(JavaCalendar.SECOND, 0)
                            set(JavaCalendar.MILLISECOND, 0)
                        }
                        val todayStart = todayCal.timeInMillis
                        val todayLabel = formatDate(DateTime(todayStart))

                        // Filter events: Keep Today and Future
                        val filteredEvents = calendarEvents.filter { event ->
                            val eventTime = event.start?.dateTime?.value ?: event.start?.date?.value ?: 0L
                            eventTime >= todayStart
                        }

                        // Group the filtered events
                        val groupedEvents = filteredEvents.groupBy { event ->
                            formatDate(event.start?.dateTime ?: event.start?.date)
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            // 1. Always show Today header
                            item {
                                Text(
                                    text = todayLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                                
                                val todayEvents = groupedEvents[todayLabel]
                                if (todayEvents == null) {
                                    Text(
                                        text = "No events scheduled for today.",
                                        color = Color.Gray,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                            }

                            // 2. Show Today's events if they exist
                            val todayEvents = groupedEvents[todayLabel]
                            if (todayEvents != null) {
                                items(todayEvents) { event ->
                                    AgendaItem(event)
                                }
                            }

                            // 3. Show future days only if they have events
                            // Filter out todayLabel key and sort the remaining keys
                            val futureDateLabels = groupedEvents.keys
                                .filter { it != todayLabel }
                                .sortedBy { label ->
                                    // Extract first matching event's time for accurate sorting
                                    groupedEvents[label]?.firstOrNull()?.let {
                                        it.start?.dateTime?.value ?: it.start?.date?.value ?: 0L
                                    } ?: 0L
                                }

                            futureDateLabels.forEach { dateLabel ->
                                item {
                                    Text(
                                        text = dateLabel,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                                items(groupedEvents[dateLabel]!!) { event ->
                                    AgendaItem(event)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AgendaItem(event: Event) {
    val startTime = formatTime(event.start?.dateTime)
    val endTime = formatTime(event.end?.dateTime)
    val eventColor = Color(0xFF4285F4) // Google Calendar Blue

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Time Column
            Column(
                modifier = Modifier.width(60.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(text = startTime, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                if (endTime.isNotEmpty()) {
                    Text(text = endTime, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Event Content with Colored Bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(eventColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Colored Vertical Bar
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(24.dp)
                            .background(eventColor, RoundedCornerShape(2.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = event.summary ?: "(No Title)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!event.location.isNullOrEmpty()) {
                            Text(
                                text = event.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatDate(dateTime: DateTime?): String {
    if (dateTime == null) return "Unknown Date"
    val date = Date(dateTime.value)
    val sdf = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    return sdf.format(date)
}

fun formatTime(dateTime: DateTime?): String {
    if (dateTime == null) return ""
    val date = Date(dateTime.value)
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(date)
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    FAVORITES("Favorites", Icons.Default.Favorite),
    PROFILE("Profile", Icons.Default.AccountBox),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ClassSeekTheme {
        Greeting("Android")
    }
}
