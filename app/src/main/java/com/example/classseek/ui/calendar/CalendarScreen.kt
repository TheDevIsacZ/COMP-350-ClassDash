package com.example.classseek.ui.calendar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.classseek.data.ChatListItem
import com.example.classseek.data.ChatRepository
import com.example.classseek.models.UserProfile
import com.example.classseek.ui.friends.SearchBar
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface

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
    var showReminderDialog by remember { mutableStateOf(false) }
    var selectedEventForReminder by remember { mutableStateOf<Event?>(null) }
    var userReminders by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

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

    LaunchedEffect(signedInAccount?.email) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("reminders")
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val reminders = mutableMapOf<String, Boolean>()
                        snapshot.documents.forEach { doc ->
                            val eventId = doc.getString("eventId")
                            if (eventId != null) {
                                reminders[eventId] = true
                            }
                        }
                        userReminders = reminders
                    }
                }
        }
    }

    if (!hasCalendarPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
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
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
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
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
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
                    .background(MaterialTheme.colorScheme.background)
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
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = signedInAccount.email ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 6.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))

                        ClassSeekCalendarCard(
                            events = calendarEvents,
                            selectedDateMillis = selectedDateMillis,
                            onDateSelected = { millis ->
                                selectedDateMillis = millis
                                showBottomSheet = true
                            }
                        )

                        HorizontalDivider(
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
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
                                    imageVector = if (showStarredOnly) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                },
                                onSetReminder = {
                                    selectedEventForReminder = event
                                    showReminderDialog = true
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
                                },
                                onSetReminder = {
                                    selectedEventForReminder = event
                                    showReminderDialog = true
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
                                },
                                onSetReminder = {
                                    selectedEventForReminder = event
                                    showReminderDialog = true
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
        var chatSearchQuery by remember(selectedEventForSharing?.id) { mutableStateOf("") }
        var selectedChatFilter by remember { mutableStateOf("All") }
        LaunchedEffect(Unit) {
            try {
                chats = chatRepository.getMyChats(myUid)
            } catch (e: Exception) {
            }
        }

        val pickerSheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = {
                showChatPicker = false
                chatSearchQuery = ""
            },
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

                SearchBar(
                    query = chatSearchQuery,
                    onQueryChange = { chatSearchQuery = it },
                    placeholder = "Search chats or people",
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

                val finalFilteredChats = remember(chats, chatSearchQuery, selectedChatFilter) {
                    var filtered = if (chatSearchQuery.isBlank()) {
                        chats
                    } else {
                        val normalizedQuery = chatSearchQuery.trim()
                        chats.filter { chat ->
                            chat.title.contains(normalizedQuery, ignoreCase = true) ||
                                    chat.lastMessageText.orEmpty().contains(normalizedQuery, ignoreCase = true)
                        }
                    }

                    when (selectedChatFilter) {
                        "DMs" -> filtered.filter { it.type == "dm" }
                        "Groups" -> filtered.filter { it.type == "group" }
                        else -> filtered
                    }
                }

                if (finalFilteredChats.isEmpty()) {
                    Text(
                        text = if (chatSearchQuery.isBlank()) "No chats available" else "No matching chats",
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(finalFilteredChats) { chat ->
                            ListItem(
                                headlineContent = { Text(chat.title) },
                                leadingContent = {
                                    if (chat.type == "dm" && chat.profilePictureUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(chat.profilePictureUrl)
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
                                            chatSearchQuery = ""
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
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showReminderDialog && selectedEventForReminder != null) {
        ReminderDialog(
            eventTitle = selectedEventForReminder?.summary ?: "Untitled Event",
            onDismiss = {
                showReminderDialog = false
                selectedEventForReminder = null
            },
            onSetReminder = { minutes ->
                val uid = FirebaseAuth.getInstance().currentUser?.uid
                val event = selectedEventForReminder
                if (uid != null && event != null) {
                    val eventTimeMillis = event.start?.dateTime?.value
                        ?: event.start?.date?.value
                        ?: System.currentTimeMillis()

                    // Format the event time for notification
                    val eventDate = Date(eventTimeMillis)
                    val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
                    val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    val formattedTime = timeFormatter.format(eventDate)
                    val formattedDate = dateFormatter.format(eventDate)

                    // Calculate reminder time (when to send the notification)
                    val reminderTimeInMillis = eventTimeMillis - (minutes * 60 * 1000L)

                    val reminderData = hashMapOf(
                        "eventId" to (event.id ?: "test"),
                        "eventTitle" to (event.summary ?: "Test Event"),
                        "eventTime" to eventTimeMillis,
                        "eventTimeFormatted" to formattedTime,  
                        "eventDateFormatted" to formattedDate,  
                        "reminderMinutes" to minutes,
                        "reminderTime" to reminderTimeInMillis,
                        "notificationSent" to false,
                    )

                    Log.d("REMINDER_DEBUG", "Saving reminder: $reminderData")

                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(uid)
                        .collection("reminders")
                        .document(event.id ?: "test")
                        .set(reminderData)
                        .addOnSuccessListener {
                            Log.d("REMINDER_DEBUG", "✅ Reminder saved successfully!")
                            // Show a toast or snackbar to confirm
                        }
                        .addOnFailureListener { e ->
                            Log.e("REMINDER_DEBUG", "❌ Failed to save reminder: ${e.message}", e)
                        }
                }
                showReminderDialog = false
                selectedEventForReminder = null
            }
        )
    }
}

@Composable
private fun ClassSeekCalendarCard(
    events: List<Event>,
    selectedDateMillis: Long?,
    onDateSelected: (Long) -> Unit
) {
    var visibleMonthMillis by remember {
        mutableStateOf(
            JavaCalendar.getInstance().apply {
                set(JavaCalendar.DAY_OF_MONTH, 1)
                set(JavaCalendar.HOUR_OF_DAY, 0)
                set(JavaCalendar.MINUTE, 0)
                set(JavaCalendar.SECOND, 0)
                set(JavaCalendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }

    val monthCalendar = remember(visibleMonthMillis) {
        JavaCalendar.getInstance().apply {
            timeInMillis = visibleMonthMillis
            set(JavaCalendar.DAY_OF_MONTH, 1)
            set(JavaCalendar.HOUR_OF_DAY, 0)
            set(JavaCalendar.MINUTE, 0)
            set(JavaCalendar.SECOND, 0)
            set(JavaCalendar.MILLISECOND, 0)
        }
    }

    val monthTitle = remember(visibleMonthMillis) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(visibleMonthMillis))
    }

    val eventDayKeys = remember(events) {
        events.mapNotNull { event ->
            val millis = event.start?.dateTime?.value ?: event.start?.date?.value
            millis?.let { dayKey(it) }
        }.toSet()
    }

    val todayKey = dayKey(System.currentTimeMillis())
    val selectedKey = selectedDateMillis?.let { dayKey(it) }

    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val calendarBorderColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.22f)
    } else {
        Color.Black.copy(alpha = 0.14f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = calendarBorderColor
        ),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    visibleMonthMillis = JavaCalendar.getInstance().apply {
                        timeInMillis = visibleMonthMillis
                        add(JavaCalendar.MONTH, -1)
                    }.timeInMillis
                }) { Text("‹", style = MaterialTheme.typography.titleLarge) }

                Text(text = monthTitle, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                TextButton(onClick = {
                    visibleMonthMillis = JavaCalendar.getInstance().apply {
                        timeInMillis = visibleMonthMillis
                        add(JavaCalendar.MONTH, 1)
                    }.timeInMillis
                }) { Text("›", style = MaterialTheme.typography.titleLarge) }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                    Text(text = day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val dayCells = remember(visibleMonthMillis) { buildCalendarCells(monthCalendar) }

            dayCells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { dayMillis ->
                        CalendarDayCell(dayMillis, eventDayKeys, todayKey, selectedKey, onDateSelected, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    dayMillis: Long?, eventDayKeys: Set<String>, todayKey: String,
    selectedKey: String?, onDateSelected: (Long) -> Unit, modifier: Modifier = Modifier
) {
    if (dayMillis == null) { Box(modifier = modifier.height(44.dp).padding(2.dp)); return }
    val cal = remember(dayMillis) { JavaCalendar.getInstance().apply { timeInMillis = dayMillis } }
    val key = dayKey(dayMillis)
    val isToday = key == todayKey
    val isSelected = key == selectedKey
    val hasEvent = key in eventDayKeys

    Box(
        modifier = modifier.height(44.dp).padding(2.dp).clip(CircleShape)
            .background(when { isSelected -> MaterialTheme.colorScheme.primary; hasEvent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f); else -> Color.Transparent })
            .clickable { onDateSelected(dayMillis) },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = cal.get(JavaCalendar.DAY_OF_MONTH).toString(),

                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal)
            if (hasEvent) Box(modifier = Modifier.size(4.dp).clip(CircleShape)
                .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary))
            else Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

private fun buildCalendarCells(monthCalendar: JavaCalendar): List<Long?> {
    val cal = monthCalendar.clone() as JavaCalendar
    val cells = mutableListOf<Long?>()
    repeat(cal.get(JavaCalendar.DAY_OF_WEEK) - JavaCalendar.SUNDAY) { cells.add(null) }
    for (day in 1..cal.getActualMaximum(JavaCalendar.DAY_OF_MONTH)) {
        val dayCal = monthCalendar.clone() as JavaCalendar
        dayCal.set(JavaCalendar.DAY_OF_MONTH, day)
        cells.add(dayCal.timeInMillis)
    }
    while (cells.size % 7 != 0) cells.add(null)
    return cells
}

private fun dayKey(millis: Long): String {
    val cal = JavaCalendar.getInstance().apply { timeInMillis = millis }
    return "${cal.get(JavaCalendar.YEAR)}-${cal.get(JavaCalendar.MONTH)}-${cal.get(JavaCalendar.DAY_OF_MONTH)}"
}

@Composable
fun ReminderDialog(eventTitle: String, onDismiss: () -> Unit, onSetReminder: (Int) -> Unit) {
    var selectedMinutes by remember { mutableStateOf(15) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Reminder for") },
        text = {
            Column {
                Text(eventTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Remind me:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))
                listOf(0 to "At time of event", 5 to "5 minutes before", 15 to "15 minutes before",
                    30 to "30 minutes before", 60 to "1 hour before", 120 to "2 hours before", 1440 to "1 day before"
                ).forEach { (minutes, label) ->
                    Row(modifier = Modifier.fillMaxWidth().clickable { selectedMinutes = minutes }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedMinutes == minutes, onClick = { selectedMinutes = minutes })
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSetReminder(selectedMinutes) }) { Text("Set Reminder") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AgendaItem(
    event: Event, userProfile: UserProfile?, canDelete: Boolean = false,
    onDeleteClick: () -> Unit = {}, onShareClick: () -> Unit = {}, onSetReminder: (Event) -> Unit = {}
) {
    val startTime = formatTime(event.start?.dateTime)
    val endTime = formatTime(event.end?.dateTime)
    val eventColor = Color(0xFF4285F4)
    var showMenu by remember { mutableStateOf(false) }
    val isBookmarked = userProfile?.bookmarkedEventIds?.contains(event.id) ?: false
    var optimisticIsBookmarked by remember(event.id, isBookmarked) { mutableStateOf(isBookmarked) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(60.dp), horizontalAlignment = Alignment.End) {
                Text(startTime, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
                if (endTime.isNotEmpty()) Text(endTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.weight(1f).background(eventColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(4.dp).height(24.dp).background(eventColor, RoundedCornerShape(2.dp)))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(event.summary ?: "(No Title)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        if (!event.location.isNullOrEmpty()) Text(event.location, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        val uid = FirebaseAuth.getInstance().currentUser?.uid
                        if (uid != null) {
                            val ref = FirebaseFirestore.getInstance().collection("users").document(uid)
                            val wasBookmarked = optimisticIsBookmarked
                            optimisticIsBookmarked = !wasBookmarked

                            if (wasBookmarked) {
                                ref.update("bookmarkedEventIds", FieldValue.arrayRemove(event.id))
                                FirebaseFirestore.getInstance().collection("users").document(uid).collection("reminders").document(event.id ?: "").delete()
                            } else {
                                ref.update("bookmarkedEventIds", FieldValue.arrayUnion(event.id))
                            }
                        }
                    }) {
                        Icon(
                            imageVector = if (optimisticIsBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (optimisticIsBookmarked) Color(0xFFFFD700) else Color.Gray
                        )
                    }
                    IconButton(onClick = { onSetReminder(event) }) {
                        Icon(Icons.Default.Notifications, "Set Reminder", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onShareClick) {
                        Icon(Icons.Default.Share, "Share event", tint = MaterialTheme.colorScheme.primary)
                    }

                    if (canDelete) {
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Default.MoreVert, "More options") }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Delete") }, onClick = { showMenu = false; onDeleteClick() })
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
    return SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(dateTime.value))
}

private fun formatTime(dateTime: DateTime?): String {
    if (dateTime == null) return ""
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(dateTime.value))
}

private fun formatEventDateTime(dateTime: EventDateTime?): String {
    if (dateTime == null) return ""
    return if (dateTime.dateTime != null) {
        SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(dateTime.dateTime.value))
    } else if (dateTime.date != null) {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(dateTime.date.value))
    } else ""
}
