package com.example.classseek.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.classseek.models.Friend
import com.example.classseek.models.UserProfile
import com.example.classseek.ui.friends.UserActionDialog
import com.example.classseek.ui.friends.UserSearchItem

// Define colors based on the provided theme.css (Light mode mostly)
object ProfileTheme {
    val Primary = Color(0xFF030213)
    val Background = Color(0xFFF3F4F6) // Slightly gray background for the overall screen
    val CardBackground = Color.White
    val MutedForeground = Color(0xFF717182)
    val Accent = Color(0xFFE9EBEF)
    val Border = Color(0x1A000000)
    
    // Gradient colors for the header
    val GradientStart = Color(0xFFFF9E00)
    val GradientMid = Color(0xFFFF5400)
    val GradientEnd = Color(0xFFD00000)
}

@Composable
fun ProfileScreen(
    userProfile: UserProfile,
    isMyProfile: Boolean,
    friends: List<UserSearchItem> = emptyList(),
    isFriend: Boolean = false,
    friendRequestStatus: String? = null, // null, "pending", "sent"
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
    onBack: (() -> Unit)? = null,
    onEditSchedule: () -> Unit
) {
    val scrollState = rememberScrollState()
    var selectedFriend by remember { mutableStateOf<UserSearchItem?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ProfileTheme.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Box {
                // Header with Gradient and Settings Icon
                HeaderSection(
                    userProfile = userProfile,
                    isMyProfile = isMyProfile,
                    isFriend = isFriend,
                    friendRequestStatus = friendRequestStatus,
                    onEditProfile = onEditProfile,
                    onSignOut = onSignOut,
                    onDeleteAccount = onDeleteAccount,
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

            Spacer(modifier = Modifier.height(60.dp)) // Space for the overlapping profile image

            // Profile Info Card
            ProfileInfoCard(userProfile)

            Spacer(modifier = Modifier.height(16.dp))

            if (isMyProfile) {
                Spacer(modifier = Modifier.height(16.dp))

                FriendsListCard(
                    friends = friends,
                    onFriendClick = { friend ->
                        selectedFriend = friend
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            ScheduleSection(userProfile, onEditSchedule)

            Spacer(modifier = Modifier.height(32.dp))
        }

        selectedFriend?.let { friend ->
            UserActionDialog(
                user = friend,
                isFriend = true,
                onDismiss = { selectedFriend = null },
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
    }
}

@Composable
fun HeaderSection(
    userProfile: UserProfile,
    isMyProfile: Boolean,
    isFriend: Boolean,
    friendRequestStatus: String?,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onAddFriend: (() -> Unit)? = null,
    onAcceptFriend: (() -> Unit)? = null,
    onDeclineFriend: (() -> Unit)? = null,
    onCancelFriend: (() -> Unit)? = null,
    onRemoveFriend: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showRemoveFriendDialog by remember { mutableStateOf(false) }
    var emailConfirmation by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        if (userProfile.bannerUrl.isNotEmpty()) {
            AsyncImage(
                model = userProfile.bannerUrl,
                contentDescription = "Banner Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                ProfileTheme.GradientStart,
                                ProfileTheme.GradientMid,
                                ProfileTheme.GradientEnd
                            )
                        )
                    )
            )
        }
        
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            if (isMyProfile) {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit Profile") },
                        onClick = {
                            showMenu = false
                            onEditProfile()
                        },
                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Sign Out", color = Color.Red) },
                        onClick = {
                            showMenu = false
                            onSignOut()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                tint = Color.Red
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Account", color = Color.Red) },
                        onClick = {
                            showMenu = false
                            showDeleteConfirmDialog = true
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = Color.Red
                            )
                        }
                    )
                }
            } else {
                // Not my profile, show "Add Friend" or "Remove Friend"
                if (isFriend) {
                    Button(
                        onClick = { showRemoveFriendDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonRemove,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Remove Friend", color = Color.White, fontSize = 12.sp)
                    }
                } else {
                    when (friendRequestStatus) {
                        "sent" -> {
                            Button(
                                onClick = { onCancelFriend?.invoke() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text("Requested", color = Color.White, fontSize = 12.sp)
                            }
                        }
                        "pending" -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { onAcceptFriend?.invoke() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Accept", color = Color.Black, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onDeclineFriend?.invoke() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(20.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Decline", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                        else -> {
                            IconButton(
                                onClick = { onAddFriend?.invoke() },
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.2f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = "Add Friend",
                                    tint = Color.White
                                )
                            }
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
                TextButton(
                    onClick = {
                        showRemoveFriendDialog = false
                        onRemoveFriend?.invoke()
                    }
                ) {
                    Text("Remove", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveFriendDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation dialog for account deletion to prevent accidental clicks
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
                        // Validate email entry matches the user's email before allowing deletion
                        if (emailConfirmation.trim().equals(userProfile.email.trim(), ignoreCase = true)) {
                            onDeleteAccount()
                            showDeleteConfirmDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    enabled = emailConfirmation.trim().equals(userProfile.email.trim(), ignoreCase = true)
                ) {
                    Text("Delete Forever")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteConfirmDialog = false
                    emailConfirmation = ""
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ProfileImageSection(imageUrl: String) {
    Box(
        modifier = Modifier
            .padding(top = 130.dp, start = 24.dp)
            .size(100.dp)
            .border(4.dp, Color.White, CircleShape)
            .clip(CircleShape)
            .background(Color.Gray)
    ) {
        if (imageUrl.isNotEmpty()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Profile Picture",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Placeholder
            Image(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().padding(20.dp),
                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White)
            )
        }
        
        // Online status indicator
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 4.dp, bottom = 4.dp)
                .size(16.dp)
                .border(2.dp, Color.White, CircleShape)
                .background(Color(0xFF22C55E), CircleShape)
        )
    }
}

@Composable
fun ProfileInfoCard(userProfile: UserProfile) {
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
                .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            Text(
                text = userProfile.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = ProfileTheme.Primary
            )
            Text(
                text = userProfile.email,
                style = MaterialTheme.typography.bodyMedium,
                color = ProfileTheme.MutedForeground
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = userProfile.bio.ifEmpty { "No bio provided." },
                style = MaterialTheme.typography.bodyMedium,
                color = ProfileTheme.Primary,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Info rows: Location, Link, Joined
            InfoRow(icon = Icons.Default.LocationOn, text = userProfile.location.ifEmpty { "Not specified" })
            InfoRow(icon = Icons.Default.Link, text = userProfile.githubUrl.ifEmpty { "No link" })
            InfoRow(icon = Icons.Default.CalendarToday, text = userProfile.joinDate.ifEmpty { "Joined Recently" })
        }
    }
}

@Composable
fun FriendsListCard(
    friends: List<UserSearchItem>,
    onFriendClick: (UserSearchItem) -> Unit
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
                    text = "Friends",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ProfileTheme.Primary
                )
                Text(
                    text = friends.size.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = ProfileTheme.MutedForeground
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (friends.isEmpty()) {
                Text(
                    text = "No friends added yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProfileTheme.MutedForeground,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                friends.forEachIndexed { index, friend ->
                    ProfileFriendItem(
                        user = friend,
                        onClick = { onFriendClick(friend) }
                    )
                    if (index != friends.lastIndex) {
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

@Composable
fun ProfileFriendItem(
    user: UserSearchItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(ProfileTheme.Accent)
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
                        if (user.isOnline) Color(0xFF22C55E) else ProfileTheme.MutedForeground.copy(alpha = 0.45f),
                        CircleShape
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

@Composable
fun FriendItem(friend: Friend) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(ProfileTheme.Accent)
        ) {
            if (friend.profilePictureUrl.isNotEmpty()) {
                AsyncImage(
                    model = friend.profilePictureUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    tint = ProfileTheme.MutedForeground
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1.0f)) {
            Text(
                text = friend.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = ProfileTheme.Primary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val statusColor = when (friend.status) {
                    "Online" -> Color(0xFF22C55E)
                    "Away" -> Color(0xFFEAB308)
                    else -> ProfileTheme.MutedForeground
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = friend.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = ProfileTheme.MutedForeground
                )
            }
        }

        IconButton(onClick = { /* Open chat */ }) {
            Icon(
                imageVector = Icons.Default.ChatBubbleOutline,
                contentDescription = "Message",
                modifier = Modifier.size(20.dp),
                tint = ProfileTheme.MutedForeground
            )
        }
    }
}

@Composable
fun ScheduleSection(userProfile: UserProfile, onEditClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(containerColor = ProfileTheme.CardBackground),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onEditClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.05f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Schedule",
                    tint = ProfileTheme.MutedForeground
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Current Schedule",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ProfileTheme.Primary
                )

                if (userProfile.semester.isNotEmpty()) {
                    Text(
                        text = userProfile.semester,
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
                    userProfile.classes.forEach { classInfo ->
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
                                    Text(
                                        text = "${classInfo.building} ${classInfo.roomNumber}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ProfileTheme.MutedForeground
                                    )
                                    Text(
                                        text = "${classInfo.dayOfWeek} at ${classInfo.startTime}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ProfileTheme.MutedForeground.copy(alpha = 0.8f)
                                    )
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
fun InfoRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = ProfileTheme.MutedForeground
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = ProfileTheme.MutedForeground
        )
    }
}

@Composable
fun StatColumn(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ProfileTheme.Primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = ProfileTheme.MutedForeground,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun WelcomeCard(onSignOut: () -> Unit) {
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Welcome to your Profile",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ProfileTheme.Primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "This is your modern profile page. Connect with followers and showcase your presence on the platform.",
                style = MaterialTheme.typography.bodyMedium,
                color = ProfileTheme.MutedForeground,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(32.dp))
            
            HorizontalDivider(color = ProfileTheme.Border)
            
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier
                    .fillMaxWidth()
                ) {
                    Text("Sign Out")
                }
        }
    }
}
