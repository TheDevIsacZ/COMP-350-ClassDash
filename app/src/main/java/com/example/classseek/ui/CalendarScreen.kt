package com.example.classseek.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.CalendarView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event
import java.text.SimpleDateFormat
import java.util.Calendar as JavaCalendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun CalendarScreen(
    signedInAccount: GoogleSignInAccount?,
    calendarEvents: List<Event>,
    onSignInClick: (Intent) -> Unit,
    onSignOutClick: () -> Unit,
    onRefreshClick: suspend (GoogleSignInAccount) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // For runtime permission requests
    var hasCalendarPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCalendarPermission = permissions[Manifest.permission.READ_CALENDAR] == true &&
                permissions[Manifest.permission.WRITE_CALENDAR] == true
    }

    // Configure Google Sign-In
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/calendar"))
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (!hasCalendarPermission) {
            Text(text = "Calendar access is required to sync your schedule.")
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                )
            }) {
                Text("Grant Calendar Permissions")
            }
        } else {
            if (signedInAccount == null) {
                Button(onClick = { onSignInClick(googleSignInClient.signInIntent) }) {
                    Text("go to calendar")
                }
            } else {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Schedule", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(text = signedInAccount.email ?: "", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Button(onClick = {
                        googleSignInClient.signOut().addOnCompleteListener {
                            onSignOutClick()
                        }
                    }) {
                        Text("Sign Out")
                    }
                }

                Spacer(modifier = Modifier.height(0.dp))

                // Calendar Widget
                AndroidView(
                    factory = { ctx -> CalendarView(ctx) },
                    modifier = Modifier.fillMaxWidth().wrapContentHeight()
                )

                HorizontalDivider(thickness = 0.5.dp, color = Color.LightGray)

                // Events List
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Upcoming Events", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        scope.launch { onRefreshClick(signedInAccount) }
                    }) {
                        Text("Refresh")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                // Filtering Logic
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

                val filteredEvents = calendarEvents.filter { event ->
                    val eventTime = event.start?.dateTime?.value ?: event.start?.date?.value ?: 0L
                    eventTime >= todayStart
                }

                val groupedEvents = filteredEvents.groupBy { event ->
                    formatDate(event.start?.dateTime ?: event.start?.date)
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = todayLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        if (groupedEvents[todayLabel] == null) {
                            Text(text = "No events scheduled for today.", color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
                        }
                    }

                    val todayEvents = groupedEvents[todayLabel]
                    if (todayEvents != null) {
                        items(todayEvents) { event -> AgendaItem(event) }
                    }

                    val futureDateLabels = groupedEvents.keys
                        .filter { it != todayLabel }
                        .sortedBy { label ->
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
                        items(groupedEvents[dateLabel]!!) { event -> AgendaItem(event) }
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
    val eventColor = Color(0xFF4285F4)

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.width(60.dp), horizontalAlignment = Alignment.End) {
                Text(text = startTime, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                if (endTime.isNotEmpty()) {
                    Text(text = endTime, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(eventColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(4.dp).height(24.dp).background(eventColor, RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = event.summary ?: "(No Title)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        if (!event.location.isNullOrEmpty()) {
                            Text(text = event.location, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

private fun formatDate(dateTime: DateTime?): String {
    if (dateTime == null) return "Unknown Date"
    val date = Date(dateTime.value)
    val sdf = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    return sdf.format(date)
}

private fun formatTime(dateTime: DateTime?): String {
    if (dateTime == null) return ""
    val date = Date(dateTime.value)
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(date)
}
