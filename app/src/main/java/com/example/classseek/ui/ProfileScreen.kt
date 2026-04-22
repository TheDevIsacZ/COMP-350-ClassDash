package com.example.classseek.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.classseek.R
import com.example.classseek.models.Friend
import com.example.classseek.models.UserProfile

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
    onSignOut: () -> Unit,
    onEditProfile: () -> Unit,
    onDeleteAccount: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val scrollState = rememberScrollState()

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
            // Header with Gradient and Settings Icon
            HeaderSection(userProfile, onEditProfile, onSignOut, onDeleteAccount, onBack)

            Spacer(modifier = Modifier.height(60.dp)) // Space for the overlapping profile image

            // Profile Info Card
            ProfileInfoCard(userProfile)

            Spacer(modifier = Modifier.height(16.dp))

            // Messages Card
            RecentMessagesListCard(
                chats = emptyList() // We could fetch real recent messages here if needed, but let's just make it a link to the Messages screen
            )

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Profile Image (Overlapping the header and card)
        ProfileImageSection(userProfile.profilePictureUrl)
    }
}

@Composable
fun HeaderSection(
    userProfile: UserProfile,
    onEditProfile: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
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
                // Standard dropdown menu for profile actions
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
                // New action to trigger account deletion flow
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
        }
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
fun RecentMessagesListCard(chats: List<com.example.classseek.data.ChatListItem>) {
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
                    text = "Recent Messages",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ProfileTheme.Primary
                )
                TextButton(onClick = { /* Could navigate to Messages tab */ }) {
                    Text(
                        text = "See all",
                        style = MaterialTheme.typography.labelLarge,
                        color = ProfileTheme.MutedForeground
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (chats.isEmpty()) {
                Text(
                    text = "No recent messages.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProfileTheme.MutedForeground,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                // ... logic to show some chats
            }
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
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, ProfileTheme.Border)
            ) {
                Text("Sign Out", color = ProfileTheme.Primary, fontWeight = FontWeight.Medium)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Version 2.0.1 (Social Redesign)",
                style = MaterialTheme.typography.labelSmall,
                color = ProfileTheme.MutedForeground.copy(alpha = 0.6f)
            )
        }
    }
}
