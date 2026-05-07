package com.example.classseek.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.classseek.models.Friend
import com.example.classseek.models.UserProfile
import com.example.classseek.ui.friends.SearchBar
import com.example.classseek.ui.friends.UserActionDialog
import com.example.classseek.ui.friends.UserSearchItem
import com.example.classseek.ui.theme.AppThemeMode
import com.google.api.services.calendar.model.Event
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.luminance

// Define colors based on the provided theme.css (Light mode mostly)
object ProfileTheme {
    val Primary: Color
        @Composable get() = MaterialTheme.colorScheme.onBackground

    val Background: Color
        @Composable get() = MaterialTheme.colorScheme.background

    val CardBackground: Color
        @Composable get() = MaterialTheme.colorScheme.surface

    val MutedForeground: Color
        @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant

    val Accent: Color
        @Composable get() = MaterialTheme.colorScheme.surfaceVariant

    val Border: Color
        @Composable get() = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)

    val GradientStart = Color(0xFFFF9E00)
    val GradientMid = Color(0xFFFF5400)
    val GradientEnd = Color(0xFFD00000)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    isMyProfile: Boolean,
    friends: List<UserSearchItem> = emptyList(),
    isFriend: Boolean = false,
    friendRequestStatus: String? = null, // null, "pending", "sent"
    bookmarkedEvents: List<Event> = emptyList(),
    onSignOut: () -> Unit,
    onEditProfile: () -> Unit,
    onDeleteAccount: () -> Unit,
    onFriendMessage: ((UserSearchItem) -> Unit)? = null,
    onViewFriendProfile: ((String) -> Unit)? = null,
    onRemoveFriendFromList: ((String) -> Unit)? = null,
    onAddFriend: (() -> Unit)? = null,
    onAcceptFriend: (() -> Unit)? = null,
    onDeclineFriend: (() -> Unit)? = null,
    onCancelFriend: (() -> Unit)? = null,
    onRemoveFriend: (() -> Unit)? = null,
    onRemoveBookmark: ((String) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onEditSchedule: () -> Unit
) {
    val scrollState = rememberScrollState()
    var selectedFriend by remember { mutableStateOf<UserSearchItem?>(null) }

    var eventToUnbookmark by remember { mutableStateOf<Event?>(null) }


    LazyColumn(
        modifier = Modifier.fillMaxSize().background(ProfileTheme.Background),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Box {
                // Header with Gradient and Settings Icon
                HeaderSection(
                    userProfile = userProfile,
                    isMyProfile = isMyProfile,
                    isFriend = isFriend,
                    friendRequestStatus = friendRequestStatus,
                    onAddFriend = onAddFriend,
                    onAcceptFriend = onAcceptFriend,
                    onDeclineFriend = onDeclineFriend,
                    onCancelFriend = onCancelFriend,
                    onRemoveFriend = onRemoveFriend,
                    onBack = onBack
                )

                // Profile Image (Overlapping the header and card)
                ProfileImageSection(userProfile.profilePictureUrl)
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            if (isMyProfile) {
                SettingsProfileInfoCard(userProfile)
            } else {
                PublicProfileInfoCard(userProfile)
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            ScheduleSection(
                userProfile = userProfile,
                onEditClick = onEditSchedule,
                showEditButton = isMyProfile
            )
        }

        if (isMyProfile) {
            item { Spacer(modifier = Modifier.height(16.dp)) }
            item {
                FavoriteFriendsCard(
                    friends = friends,
                    onFriendClick = { selectedFriend = it }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Bookmarked events section at the bottom
            if (bookmarkedEvents.isNotEmpty()) {
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item {
                    BookmarkedEventsSection(
                        events = bookmarkedEvents,
                        onRemoveBookmark = { event ->
                            eventToUnbookmark = event
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    selectedFriend?.let { friend ->
        UserActionDialog(
            user = friend,
            isFriend = true,
            onDismiss = { selectedFriend = null },
            onTogglePin = {},
            onMessage = {
                selectedFriend = null
                onFriendMessage?.invoke(friend)
            },
            onAddFriend = {},
            onRemoveFriend = {
                selectedFriend = null
                onRemoveFriendFromList?.invoke(friend.uid)
            },
            onViewProfile = {
                selectedFriend = null
                onViewFriendProfile?.invoke(friend.uid)
            }
        )
    }

    // Confirmation Bottom Sheet for removing bookmarks
    if (eventToUnbookmark != null) {
        ModalBottomSheet(
            onDismissRequest = { eventToUnbookmark = null },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Remove Bookmark",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Are you sure you want to remove this bookmark?",
                    textAlign = TextAlign.Center,
                    color = ProfileTheme.MutedForeground
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { eventToUnbookmark = null },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("Cancel", color = Color.White)
                    }
                    Button(
                        onClick = {
                            eventToUnbookmark?.id?.let { onRemoveBookmark?.invoke(it) }
                            eventToUnbookmark = null
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Remove", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsProfileScreen(
    userProfile: UserProfile,
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onUpdateSettings: (Map<String, Any>) -> Unit
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var emailConfirmation by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(ProfileTheme.Background),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Theme Setting
        item {
            SettingsSectionCard(title = "App Theme") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Notifications Settings
        item {
            SettingsSectionCard(title = "Notifications") {
                Column {
                    SettingsSwitchRow(
                        title = "Chat Notifications",
                        subtitle = "Receive alerts for new messages",
                        checked = userProfile.chatNotificationsEnabled,
                        onCheckedChange = { onUpdateSettings(mapOf("chatNotificationsEnabled" to it)) }
                    )
                    HorizontalDivider(color = ProfileTheme.Border)
                    SettingsSwitchRow(
                        title = "Calendar Reminders",
                        subtitle = "Get notified about upcoming classes",
                        checked = userProfile.calendarRemindersEnabled,
                        onCheckedChange = { onUpdateSettings(mapOf("calendarRemindersEnabled" to it)) }
                    )
                    HorizontalDivider(color = ProfileTheme.Border)
                    SettingsSwitchRow(
                        title = "Shared Events",
                        subtitle = "Alerts when friends share events",
                        checked = userProfile.eventNotificationsEnabled,
                        onCheckedChange = { onUpdateSettings(mapOf("eventNotificationsEnabled" to it)) }
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // Privacy Settings
        item {
            SettingsSectionCard(title = "Privacy") {
                Column {
                    SettingsSwitchRow(
                        title = "Show Online Status",
                        subtitle = "Let friends see when you're active",
                        checked = userProfile.showOnlineStatus,
                        onCheckedChange = { onUpdateSettings(mapOf("showOnlineStatus" to it)) }
                    )
                    HorizontalDivider(color = ProfileTheme.Border)
                    /*
                    SettingsSwitchRow(
                        title = "Location Sharing",
                        subtitle = "Allow sharing location in chats",
                        checked = userProfile.shareLocation,
                        onCheckedChange = { onUpdateSettings(mapOf("shareLocation" to it)) }
                    )

                     */
                    HorizontalDivider(color = ProfileTheme.Border)
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        Text("Profile Visibility", style = MaterialTheme.typography.titleSmall, color = ProfileTheme.Primary)
                        Text("Who can see your full profile and schedule", style = MaterialTheme.typography.bodySmall, color = ProfileTheme.MutedForeground)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Public", "Friends Only").forEach { visibility ->
                                FilterChip(
                                    selected = userProfile.profileVisibility == visibility,
                                    onClick = { onUpdateSettings(mapOf("profileVisibility" to visibility)) },
                                    label = { Text(visibility) }
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
        item { SettingsActionsCard(onEditProfile, onSignOut) { showDeleteConfirmDialog = true } }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                emailConfirmation = ""
            },
            title = { Text("Delete Account", color = Color.Red) },
            text = {
                Column {
                    Text("This action is permanent and cannot be undone. All your data will be deleted.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Please enter your email (${userProfile.email}) to confirm:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = emailConfirmation,
                        onValueChange = { emailConfirmation = it },
                        label = { Text("Email Address") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (emailConfirmation.trim().equals(userProfile.email.trim(), ignoreCase = true)) {
                            showDeleteConfirmDialog = false
                            onDeleteAccount()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    enabled = emailConfirmation.trim().equals(userProfile.email.trim(), ignoreCase = true)
                ) { Text("Delete Forever") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirmDialog = false
                    emailConfirmation = ""
                }) { Text("Cancel") }
            }
        )
    }

}

private fun formatEventDate(dateTime: com.google.api.client.util.DateTime?): String {
    if (dateTime == null) return ""
    val date = Date(dateTime.value)
    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return sdf.format(date)
}

@Composable
fun RestrictedProfileScreen(
    userProfile: UserProfile,
    friendRequestStatus: String?,
    onAddFriend: () -> Unit,
    onAcceptFriend: () -> Unit,
    onDeclineFriend: () -> Unit,
    onCancelFriend: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            HeaderSection(
                userProfile = userProfile,
                isMyProfile = false,
                isFriend = false,
                friendRequestStatus = friendRequestStatus,
                onAddFriend = onAddFriend,
                onAcceptFriend = onAcceptFriend,
                onDeclineFriend = onDeclineFriend,
                onCancelFriend = onCancelFriend,
                onBack = onBack
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(ProfileTheme.Background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ProfileImageSection(userProfile.profilePictureUrl)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = userProfile.name,
                style = MaterialTheme.typography.headlineSmall,
                color = ProfileTheme.Primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = userProfile.major,
                style = MaterialTheme.typography.bodyMedium,
                color = ProfileTheme.MutedForeground
            )
            Spacer(modifier = Modifier.height(32.dp))
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = ProfileTheme.MutedForeground,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "This profile is private",
                style = MaterialTheme.typography.titleMedium,
                color = ProfileTheme.Primary
            )
            Text(
                text = "Add ${userProfile.name} as a friend to see their full profile.",
                style = MaterialTheme.typography.bodySmall,
                color = ProfileTheme.MutedForeground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
fun HeaderSection(
    userProfile: UserProfile,
    isMyProfile: Boolean,
    isFriend: Boolean,
    friendRequestStatus: String?,
    onAddFriend: (() -> Unit)? = null,
    onAcceptFriend: (() -> Unit)? = null,
    onDeclineFriend: (() -> Unit)? = null,
    onCancelFriend: (() -> Unit)? = null,
    onRemoveFriend: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    var showRemoveFriendDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        if (userProfile.bannerUrl.isNotEmpty()) {
            AsyncImage(
                model = userProfile.bannerUrl,
                contentDescription = "Banner Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    brush = Brush.verticalGradient(
                        colors = listOf(ProfileTheme.GradientStart, ProfileTheme.GradientMid, ProfileTheme.GradientEnd)
                    )
                )
            )
        }

        Box(modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.background(Color.Black.copy(alpha = 0.2f), CircleShape)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
        }

        if (!isMyProfile) {
            Box(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                if (isFriend) {
                    Button(
                        onClick = { showRemoveFriendDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.PersonRemove, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.padding(2.dp))
                        Text("Remove Friend", color = Color.White, fontSize = 12.sp)
                    }
                } else {
                    when (friendRequestStatus) {
                        "sent" -> Button(
                            onClick = { onCancelFriend?.invoke() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) { Text("Requested", color = Color.White, fontSize = 12.sp) }
                        "pending" -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { onAcceptFriend?.invoke() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.height(36.dp)
                            ) { Text("Accept", color = Color.Black, fontSize = 12.sp) }
                            Spacer(modifier = Modifier.padding(4.dp))
                            Button(
                                onClick = { onDeclineFriend?.invoke() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                modifier = Modifier.height(36.dp)
                            ) { Text("Decline", color = Color.White, fontSize = 12.sp) }
                        }
                        else -> IconButton(
                            onClick = { onAddFriend?.invoke() },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add Friend", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog for friend removal
    if (showRemoveFriendDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveFriendDialog = false },
            title = { Text("Remove Friend") },
            text = { Text("Are you sure you want to remove ${userProfile.name} from your friends?") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveFriendDialog = false
                    onRemoveFriend?.invoke()
                }) { Text("Remove", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveFriendDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SettingsHeaderSection(userProfile: UserProfile) {
    Box(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        if (userProfile.bannerUrl.isNotEmpty()) {
            AsyncImage(
                model = userProfile.bannerUrl,
                contentDescription = "Banner Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    brush = Brush.verticalGradient(
                        colors = listOf(ProfileTheme.GradientStart, ProfileTheme.GradientMid, ProfileTheme.GradientEnd)
                    )
                )
            )
        }
    }
}

@Composable
fun SettingsProfileInfoCard(userProfile: UserProfile) {
    ProfileModuleSurface {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                userProfile.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ProfileTheme.Primary
            )

            Text(
                userProfile.email,
                style = MaterialTheme.typography.bodyMedium,
                color = ProfileTheme.MutedForeground
            )

            SettingsInfoRow("Major", userProfile.major.ifBlank { "" })
            SettingsInfoRow("Location", userProfile.location.ifBlank { "" })
            SettingsInfoRow("Website URL", userProfile.githubUrl.ifBlank { "" })
            SettingsInfoRow("Bio", userProfile.bio.ifBlank { "No bio provided." })
        }
    }
}

@Composable
fun PublicProfileInfoCard(userProfile: UserProfile) {
    val clipboardManager = LocalClipboardManager.current

    ProfileModuleSurface {
        CopyableProfileInfoRow(
            label = "Name",
            value = userProfile.name.ifBlank { "Not provided" },
            onCopy = {
                clipboardManager.setText(AnnotatedString(userProfile.name))
            }
        )

        SettingsInfoRow("Major", userProfile.major.ifBlank { "Not provided" })
        SettingsInfoRow("Location", userProfile.location.ifBlank { "Not provided" })

        CopyableProfileInfoRow(
            label = "Website URL",
            value = userProfile.githubUrl.ifBlank { "Not provided" },
            onCopy = {
                clipboardManager.setText(AnnotatedString(userProfile.githubUrl))
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Bio",
            style = MaterialTheme.typography.labelMedium,
            color = ProfileTheme.MutedForeground,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = userProfile.bio.ifBlank { "No bio provided." },
            style = MaterialTheme.typography.bodyMedium,
            color = ProfileTheme.Primary,
            lineHeight = 20.sp
        )
    }
}

@Composable
fun CopyableProfileInfoRow(
    label: String,
    value: String,
    onCopy: () -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = ProfileTheme.MutedForeground,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            SelectionContainer(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProfileTheme.Primary
                )
            }

            IconButton(
                onClick = onCopy,
                enabled = value != "Not provided"
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = ProfileTheme.MutedForeground
                )
            }
        }
    }
}

@Composable
fun SettingsInfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = ProfileTheme.MutedForeground,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = ProfileTheme.Primary
        )
    }
}
@Composable
fun FavoriteFriendsCard(
    friends: List<UserSearchItem>,
    onFriendClick: (UserSearchItem) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val normalizedQuery = searchQuery.trim()

    val filteredFriends = if (normalizedQuery.isBlank()) {
        friends
    } else {
        friends.filter { friend ->
            friend.displayName.contains(normalizedQuery, ignoreCase = true) ||
                    friend.name.contains(normalizedQuery, ignoreCase = true) ||
                    friend.email.contains(normalizedQuery, ignoreCase = true)
        }
    }

    val previewFriends = friends.take(3)

    SettingsSectionCard(title = "Friends") {
        if (isExpanded) {
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search friends"
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (friends.isEmpty()) {
            Text(
                text = "No friends added yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = ProfileTheme.MutedForeground,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else if (isExpanded && filteredFriends.isEmpty()) {
            Text(
                text = "No matching friends.",
                style = MaterialTheme.typography.bodyMedium,
                color = ProfileTheme.MutedForeground,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else if (isExpanded) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
            ) {
                items(filteredFriends) { friend ->
                    ProfileFriendItem(
                        user = friend,
                        onClick = { onFriendClick(friend) }
                    )
                }
            }
        } else {
            previewFriends.forEachIndexed { index, friend ->
                ProfileFriendItem(
                    user = friend,
                    onClick = { onFriendClick(friend) }
                )

                if (index != previewFriends.lastIndex) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(
            onClick = {
                isExpanded = !isExpanded
                if (!isExpanded) {
                    searchQuery = ""
                }
            },
            modifier = Modifier.align(Alignment.Start)
        ) {
            Text(if (isExpanded) "Show less" else "View all")
        }
    }
}

@Composable
fun ProfileImageSection(imageUrl: String) {
    Box(
        modifier = Modifier.padding(top = 130.dp, start = 24.dp)
            .size(100.dp)
            .border(4.dp, Color.White, CircleShape)
            .clip(CircleShape)
            .background(Color.Gray)
    ) {
        if (imageUrl.isNotEmpty()) {
            AsyncImage(model = imageUrl, contentDescription = "Profile Picture", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Image(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(20.dp),
                colorFilter = ColorFilter.tint(Color.White)
            )
        }

        Box(
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 4.dp, bottom = 4.dp)
                .size(16.dp)
                .border(2.dp, Color.White, CircleShape)
                .background(Color(0xFF22C55E), CircleShape)
        )
    }
}

@Composable
fun ProfileFriendItem(
    user: UserSearchItem,
    onClick: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val borderColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.18f)
    } else {
        Color.Black.copy(alpha = 0.10f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = borderColor
        ),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(ProfileTheme.Accent),
                contentAlignment = Alignment.Center
            ) {
                if (user.profilePictureUrl.isNotBlank()) {
                    AsyncImage(
                        model = user.profilePictureUrl,
                        contentDescription = "${user.displayName} profile picture",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        tint = ProfileTheme.MutedForeground
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.displayName.ifBlank { user.name.ifBlank { user.email } },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = ProfileTheme.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = user.major.ifBlank { user.email },
                    style = MaterialTheme.typography.bodySmall,
                    color = ProfileTheme.MutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (user.isOnline) {
                                Color(0xFF22C55E)
                            } else {
                                ProfileTheme.MutedForeground.copy(alpha = 0.45f)
                            },
                            shape = CircleShape
                        )
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = if (user.isOnline) "Online" else "Offline",
                    style = MaterialTheme.typography.bodySmall,
                    color = ProfileTheme.MutedForeground
                )
            }
        }
    }
}

@Composable
fun SettingsSectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val borderColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.22f)
    } else {
        Color.Black.copy(alpha = 0.14f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = borderColor
        ),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = ProfileTheme.Primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            content()
        }
    }
}

@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = ProfileTheme.Primary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = ProfileTheme.MutedForeground)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ProfileTheme.Primary,
                checkedTrackColor = ProfileTheme.Primary.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
fun SettingsActionsCard(
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    SettingsSectionCard(title = "Account") {
        SettingsActionRow(
            title = "Edit Profile",
            subtitle = "Update your profile information",
            onClick = onEditProfile
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = ProfileTheme.Border
        )

        SettingsActionRow(
            title = "Sign Out",
            subtitle = "Sign out of your account",
            onClick = onSignOut
        )

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 4.dp),
            color = ProfileTheme.Border
        )

        SettingsActionRow(
            title = "Delete Account",
            subtitle = "Permanently remove your account",
            onClick = onDeleteAccount,
            titleColor = Color.Red
        )
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    titleColor: Color = ProfileTheme.Primary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = ProfileTheme.MutedForeground
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = ProfileTheme.MutedForeground,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun ScheduleSection(
    userProfile: UserProfile,
    onEditClick: () -> Unit,
    showEditButton: Boolean = true
) {
    ProfileModuleSurface {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (showEditButton) {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit Schedule",
                        tint = ProfileTheme.MutedForeground
                    )
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Current Schedule",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ProfileTheme.Primary
                )

                if (userProfile.semester.isNotEmpty()) {
                    Text(
                        userProfile.semester,
                        style = MaterialTheme.typography.bodySmall,
                        color = ProfileTheme.MutedForeground,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (userProfile.classes.isEmpty()) {
                    Text(
                        text = "Empty",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProfileTheme.MutedForeground
                    )
                } else {
                    val activeClasses = userProfile.classes.filter { it.className.isNotBlank() }

                    activeClasses.forEachIndexed { index, classInfo ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = classInfo.className,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ProfileTheme.Primary,
                                    modifier = Modifier.weight(1f)
                                )

                                Column(horizontalAlignment = Alignment.End) {
                                    if (classInfo.building.isNotBlank() || classInfo.roomNumber.isNotBlank()) {
                                        Text(
                                            text = "${classInfo.building} ${classInfo.roomNumber}".trim(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ProfileTheme.MutedForeground
                                        )
                                    }

                                    val hasDay = classInfo.dayOfWeek.trim().isNotBlank()
                                    val hasTime = classInfo.startTime.trim().isNotBlank()

                                    if (hasDay || hasTime) {
                                        val timeText = buildString {
                                            append(classInfo.dayOfWeek.trim())
                                            if (hasDay && hasTime) {
                                                append(" at ")
                                            }
                                            append(classInfo.startTime.trim())
                                        }

                                        if (timeText.isNotBlank()) {
                                            Text(
                                                text = timeText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = ProfileTheme.MutedForeground.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }

                            if (index != activeClasses.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    color = ProfileTheme.Border
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun BookmarkedEventsSection(
    events: List<Event>,
    onRemoveBookmark: (Event) -> Unit
) {
    ProfileModuleSurface {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Bookmarked Events",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = ProfileTheme.Primary
            )

            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = null,
                tint = Color.Gray
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (events.isEmpty()) {
            Text(
                text = "No bookmarked events",
                style = MaterialTheme.typography.bodyMedium,
                color = ProfileTheme.MutedForeground,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            events.forEachIndexed { index, event ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = event.summary ?: "(No Title)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = ProfileTheme.Primary
                        )

                        val dateText = formatEventDate(event.start?.dateTime ?: event.start?.date)
                        if (dateText.isNotEmpty()) {
                            Text(
                                text = dateText,
                                style = MaterialTheme.typography.bodySmall,
                                color = ProfileTheme.MutedForeground
                            )
                        }

                        if (!event.location.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = ProfileTheme.MutedForeground
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = event.location,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ProfileTheme.MutedForeground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    IconButton(onClick = { onRemoveBookmark(event) }) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = "Remove Bookmark",
                            tint = Color(0xFFFFD700)
                        )
                    }
                }

                if (index != events.lastIndex) {
                    HorizontalDivider(color = ProfileTheme.Border)
                }
            }
        }
    }
}

@Composable
fun FriendItem(friend: Friend) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(ProfileTheme.Accent)) {
            if (friend.profilePictureUrl.isNotEmpty()) {
                AsyncImage(model = friend.profilePictureUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.padding(8.dp), tint = ProfileTheme.MutedForeground)
            }
        }

        Spacer(modifier = Modifier.padding(6.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(friend.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = ProfileTheme.Primary)
        }

        IconButton(onClick = {}) {
            Icon(Icons.Default.ChatBubbleOutline, contentDescription = "Message", modifier = Modifier.size(20.dp), tint = ProfileTheme.MutedForeground)
        }
    }
}

@Composable
fun ProfileModuleSurface(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val borderColor = if (isDarkTheme) {
        Color.White.copy(alpha = 0.22f)
    } else {
        Color.Black.copy(alpha = 0.14f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = borderColor
        ),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}
