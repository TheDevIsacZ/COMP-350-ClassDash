package com.example.classseek.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    favoriteFriends: List<UserSearchItem> = emptyList(),
    isFriend: Boolean = false,
    friendRequestStatus: String? = null, // null, "pending", "sent"
    bookmarkedEvents: List<Event> = emptyList(),
    onSignOut: () -> Unit,
    onEditProfile: () -> Unit,
    onDeleteAccount: () -> Unit,
    onFriendMessage: ((UserSearchItem) -> Unit)? = null,
    onViewFriendProfile: ((String) -> Unit)? = null,
    onRemoveFriendFromList: ((String) -> Unit)? = null,
    onTogglePinFriend: ((String) -> Unit)? = null,
    onAddFriend: (() -> Unit)? = null,
    onAcceptFriend: (() -> Unit)? = null,
    onDeclineFriend: (() -> Unit)? = null,
    onCancelFriend: (() -> Unit)? = null,
    onRemoveFriend: (() -> Unit)? = null,
    onRemoveBookmark: ((String) -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onEditSchedule: () -> Unit,
    onViewAllFriends: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()
    var selectedFriend by remember { mutableStateOf<UserSearchItem?>(null) }
    val pinnedFriends = remember(favoriteFriends) { favoriteFriends.take(3) }

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

        if (!isMyProfile) {
            item {
                PublicProfileInfoCard(userProfile)
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

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
                    friends = pinnedFriends,
                    onFriendClick = { selectedFriend = it },
                    onViewAllClick = { onViewAllFriends?.invoke() }
                )
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
            isPinned = true,
            onDismiss = { selectedFriend = null },
            onTogglePin = {
                selectedFriend = null
                onTogglePinFriend?.invoke(friend.uid)
            },
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
    onDeleteAccount: () -> Unit
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var emailConfirmation by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(ProfileTheme.Background),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { Box { SettingsHeaderSection(userProfile); ProfileImageSection(userProfile.profilePictureUrl) } }
        item { Spacer(modifier = Modifier.height(16.dp)) }
        item { SettingsProfileInfoCard(userProfile) }
        item { Spacer(modifier = Modifier.height(16.dp)) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(containerColor = ProfileTheme.CardBackground),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("App Theme", style = MaterialTheme.typography.titleMedium, color = ProfileTheme.Primary, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = ProfileTheme.CardBackground),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = ProfileTheme.CardBackground),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
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
    onFriendClick: (UserSearchItem) -> Unit,
    onViewAllClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = ProfileTheme.CardBackground),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Favorited Friends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ProfileTheme.Primary)
            Spacer(modifier = Modifier.height(8.dp))

            if (friends.isEmpty()) {
                Text(
                    text = "No favorited friends yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProfileTheme.MutedForeground,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                friends.forEachIndexed { index, friend ->
                    ProfileFriendItem(user = friend, onClick = { onFriendClick(friend) })
                    if (index != friends.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = ProfileTheme.Border)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onViewAllClick, modifier = Modifier.align(Alignment.Start)) { Text("View all") }
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
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(ProfileTheme.Accent)) {
            if (user.profilePictureUrl.isNotBlank()) {
                AsyncImage(
                    model = user.profilePictureUrl,
                    contentDescription = "${user.displayName} profile picture",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.fillMaxSize().padding(10.dp), tint = ProfileTheme.MutedForeground)
            }
        }

        Spacer(modifier = Modifier.padding(6.dp))

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
                modifier = Modifier.size(8.dp).background(
                    if (user.isOnline) Color(0xFF22C55E) else ProfileTheme.MutedForeground.copy(alpha = 0.45f),
                    CircleShape
                )
            )
            Spacer(modifier = Modifier.padding(3.dp))
            Text(
                text = if (user.isOnline) "Online" else "Offline",
                style = MaterialTheme.typography.bodySmall,
                color = ProfileTheme.MutedForeground
            )
        }
    }
}

@Composable
fun SettingsActionsCard(
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = ProfileTheme.CardBackground),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsActionRow("Edit Profile", "Update your profile information", onEditProfile)
            HorizontalDivider(color = ProfileTheme.Border)
            SettingsActionRow("Sign Out", "Sign out of your account", onSignOut)
            HorizontalDivider(color = ProfileTheme.Border)
            SettingsActionRow("Delete Account", "Permanently remove your account", onDeleteAccount, titleColor = Color.Red)
        }
    }
}

@Composable
fun SettingsActionRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    titleColor: Color = ProfileTheme.Primary
) {
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ProfileTheme.MutedForeground)
    }
}

@Composable
fun ScheduleSection(
    userProfile: UserProfile,
    onEditClick: () -> Unit,
    showEditButton: Boolean = true
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = ProfileTheme.CardBackground),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (showEditButton) {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(MaterialTheme.colorScheme.secondary, CircleShape)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Schedule", tint = ProfileTheme.MutedForeground)
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Current Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ProfileTheme.Primary)

                if (userProfile.semester.isNotEmpty()) {
                    Text(userProfile.semester, style = MaterialTheme.typography.bodySmall, color = ProfileTheme.MutedForeground, modifier = Modifier.padding(bottom = 6.dp))
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = ProfileTheme.CardBackground),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
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
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                            imageVector = Icons.Default.Star,
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
