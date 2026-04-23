package com.example.classseek.ui.calendar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.CalendarView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.classseek.data.ChatListItem
import com.example.classseek.data.ChatRepository
import com.example.classseek.models.UserProfile
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.util.DateTime
import com.google.api.services.calendar.model.Event
import com.google.api.services.calendar.model.EventDateTime
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar as JavaCalendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    signedInAccount: GoogleSignInAccount?,
    calendarEvents: List<Event>,
    userProfile: UserProfile?,
    onSignInClick: (Intent) -> Unit,
    onAddEventClick: (Long) -> Unit,
    onDeleteEventClick: (String) -> Unit,
    chatRepository: ChatRepository,
    myUid: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    var showStarredOnly by remember { mutableStateOf(false) }
    var showChatPicker by remember { mutableStateOf(false) }
    var selectedEventForSharing by remember { mutableStateOf<Event?>(null) }

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

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/calendar"))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    if (!hasCalendarPermission) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.DateRange, contentDescription = "Calendar Icon", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Calendar access is required to sync your schedule.", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                )
            }) {
                Text("Grant Calendar Permissions")
            }
        }
    } else if (signedInAccount == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.AccountCircle, contentDescription = "Sign In Icon", modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Sign in to Google to view your events.", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { onSignInClick(googleSignInClient.signInIntent) }) {
                Text("Sign in with Google")
            }
        }
    } else {
        Scaffold { paddingValues ->
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
                val isUpcoming = eventTime >= todayStart
                val isStarredMatch = !showStarredOnly || userProfile?.bookmarkedEventIds?.contains(event.id) == true
                isUpcoming && isStarredMatch
            }

            val groupedEvents = filteredEvents.groupBy { event ->
                formatDate(event.start?.dateTime ?: event.start?.date)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        Text(
                            text = "Schedule",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = signedInAccount.email ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))

                        AndroidView(
                            factory = { ctx ->
                                CalendarView(ctx).apply {
                                    setOnDateChangeListener { _, year, month, dayOfMonth ->
                                        val cal = JavaCalendar.getInstance()
                                        cal.set(year, month, dayOfMonth, 0, 0, 0)
                                        cal.set(JavaCalendar.MILLISECOND, 0)
                                        selectedDateMillis = cal.timeInMillis
                                        showBottomSheet = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight()
                        )

                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = Color.LightGray,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                            Text(
                                text = if (showStarredOnly) "Bookmarked Events" else "Upcoming Events",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.align(Alignment.Center)
                            )
                            IconToggleButton(
                                checked = showStarredOnly,
                                onCheckedChange = { showStarredOnly = it },
                                modifier = Modifier.align(Alignment.CenterEnd).size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (showStarredOnly) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Show Starred Only",
                                    tint = if (showStarredOnly) Color(0xFFFFD700) else Color.Gray,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    item {
                        Text(
                            text = todayLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        if (groupedEvents[todayLabel] == null) {
                            Text(
                                text = "No events scheduled for today.",
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                    }

                    val todayEvents = groupedEvents[todayLabel]
                    if (todayEvents != null) {
                        items(todayEvents) { event ->
                            AgendaItem(
                                event = event,
                                userProfile = userProfile,
                                canDelete = signedInAccount?.email != null && event.organizer?.email == signedInAccount.email,
                                onDeleteClick = { event.id?.let { onDeleteEventClick(it) } },
                                onShareClick = {
                                    selectedEventForSharing = event
                                    showChatPicker = true
                                }
                            )
                        }
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
                        items(groupedEvents[dateLabel]!!) { event ->
                            AgendaItem(
                                event = event,
                                userProfile = userProfile,
                                canDelete = signedInAccount?.email != null && event.organizer?.email == signedInAccount.email,
                                onDeleteClick = { event.id?.let { onDeleteEventClick(it) } },
                                onShareClick = {
                                    selectedEventForSharing = event
                                    showChatPicker = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBottomSheet && selectedDateMillis != null) {
        val dateLabel = formatDate(DateTime(selectedDateMillis!!))
        val eventsForSelectedDate = calendarEvents.filter { event ->
            val eventStartTime = event.start?.dateTime?.value ?: event.start?.date?.value ?: 0L
            formatDate(DateTime(eventStartTime)) == dateLabel
        }

        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            @Suppress("DEPRECATION")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Button(onClick = {
                        showBottomSheet = false
                        onAddEventClick(selectedDateMillis!!)
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add Event")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (eventsForSelectedDate.isEmpty()) {
                    Text(
                        text = "No events scheduled for this day.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn {
                        items(eventsForSelectedDate) { event ->
                            AgendaItem(
                                event = event,
                                userProfile = userProfile,
                                canDelete = signedInAccount?.email != null && event.organizer?.email == signedInAccount.email,
                                onDeleteClick = { event.id?.let { onDeleteEventClick(it) } },
                                onShareClick = {
                                    selectedEventForSharing = event
                                    showChatPicker = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showChatPicker && selectedEventForSharing != null && myUid.isNotBlank()) {
        var chats by remember { mutableStateOf<List<ChatListItem>>(emptyList()) }
        LaunchedEffect(Unit) {
            try {
                chats = chatRepository.getMyChats(myUid)
            } catch (e: Exception) {
                // Optionally show error message
            }
        }

        val pickerSheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showChatPicker = false },
            sheetState = pickerSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Share event with...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (chats.isEmpty()) {
                    Text("No chats available", modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    LazyColumn {
                        items(chats) { chat ->
                            ListItem(
                                headlineContent = { Text(chat.title) },
                                leadingContent = {
                                    Icon(
                                        if (chat.type == "group") Icons.Default.Group else Icons.Default.Person,
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier.clickable {
                                    scope.launch {
                                        try {
                                            val event = selectedEventForSharing!!
                                            val start = formatEventDateTime(event.start)
                                            val end = formatEventDateTime(event.end)
                                            chatRepository.sendEventMessage(
                                                chatId = chat.id,
                                                senderId = myUid,
                                                eventTitle = event.summary ?: "Untitled Event",
                                                eventStart = start,
                                                eventEnd = end,
                                                eventLocation = event.location ?: "",
                                                eventId = event.id
                                            )
                                            showChatPicker = false
                                            selectedEventForSharing = null
                                        } catch (e: Exception) {
                                            // Optionally show error message
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
}

@Composable
fun AgendaItem(
    event: Event,
    userProfile: UserProfile?,
    canDelete: Boolean = false,
    onDeleteClick: () -> Unit = {},
    onShareClick: () -> Unit = {}
) {
    val startTime = formatTime(event.start?.dateTime)
    val endTime = formatTime(event.end?.dateTime)
    val eventColor = Color(0xFF4285F4)
    var showMenu by remember { mutableStateOf(false) }
    val isBookmarked = userProfile?.bookmarkedEventIds?.contains(event.id) ?: false

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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = event.summary ?: "(No Title)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        if (!event.location.isNullOrEmpty()) {
                            Text(text = event.location, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }

                    IconButton(onClick = {
                        val uid = FirebaseAuth.getInstance().currentUser?.uid
                        if (uid != null) {
                            val ref = FirebaseFirestore.getInstance().collection("users").document(uid)
                            if (isBookmarked) {
                                ref.update("bookmarkedEventIds", FieldValue.arrayRemove(event.id))
                            } else {
                                ref.update("bookmarkedEventIds", FieldValue.arrayUnion(event.id))
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Bookmark",
                            tint = if (isBookmarked) Color(0xFFFFD700) else Color.Gray
                        )
                    }

                    IconButton(onClick = onShareClick) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share event",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (canDelete) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        showMenu = false
                                        onDeleteClick()
                                    }
                                )
                            }
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