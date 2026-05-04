package com.example.classseek.ui.friends

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.classseek.data.ChatListItem
import com.example.classseek.data.ChatRepository
import com.example.classseek.ui.chat.ChatScreen
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Locale

data class UserSearchItem(
    val uid: String,
    val name: String,
    val displayName: String,
    val email: String,
    val major: String = "",
    val profilePictureUrl: String = "",
    val isVerified: Boolean = false,
    val isOnline: Boolean = false
)

private fun DocumentSnapshot.toUserSearchItem(): UserSearchItem? {
    val email = getString("email")?.trim().orEmpty()
    if (email.isBlank()) return null

    val name = getString("name")?.trim().orEmpty()
    val displayName = getString("displayName")?.trim().orEmpty()
    val isOnline = getBoolean("isOnline") ?: false

    return UserSearchItem(
        uid = id,
        name = name,
        displayName = displayName,
        email = email,
        major = getString("major")?.trim().orEmpty(),
        profilePictureUrl = getString("profilePictureUrl")?.trim().orEmpty(),
        isVerified = false,
        isOnline = isOnline
    )
}

@Composable
private fun UserAvatar(
    imageUrl: String,
    label: String,
    modifier: Modifier = Modifier
) {
    if (imageUrl.isNotBlank()) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "$label profile picture",
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(
            modifier = modifier.clip(CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

enum class FriendsNavigation {
    MAIN, NEW_MESSAGE, CHAT
}

@Composable
fun FriendsScreen(
    modifier: Modifier = Modifier,
    repo: ChatRepository = remember { ChatRepository(FirebaseFirestore.getInstance()) },
    auth: FirebaseAuth = remember { FirebaseAuth.getInstance() },
    initialChatId: String? = null,
    initialChatTitle: String? = null,
    onInitialChatConsumed: (() -> Unit)? = null,
    onNavigateToProfile: ((String) -> Unit)? = null,
    onLocationClick: (LatLng, String, String) -> Unit = { _, _, _ -> }
) {
    var currentScreen by remember { mutableStateOf(FriendsNavigation.MAIN) }
    var activeChatId by remember { mutableStateOf<String?>(null) }
    var activeChatTitle by remember { mutableStateOf<String?>(null) }
    var showNotFriendsDialog by remember { mutableStateOf(false) }
    var pendingFriendToAdd by remember { mutableStateOf<UserSearchItem?>(null) }
    val chats = remember { mutableStateListOf<ChatListItem>() }
    val myUid = auth.currentUser?.uid ?: ""

    val scope = rememberCoroutineScope()
    val db = remember { FirebaseFirestore.getInstance() }

    var selectedUserForAction by remember { mutableStateOf<UserSearchItem?>(null) }
    var friendUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingRequestUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var sentRequestUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var favoriteFriendUids by remember { mutableStateOf<Set<String>>(emptySet()) }
    var chatToDelete by remember { mutableStateOf<ChatListItem?>(null) }

    BackHandler(enabled = currentScreen != FriendsNavigation.MAIN) {
        when (currentScreen) {
            FriendsNavigation.CHAT -> {
                activeChatId = null
                activeChatTitle = null
                currentScreen = FriendsNavigation.MAIN
            }
            FriendsNavigation.NEW_MESSAGE -> {
                currentScreen = FriendsNavigation.MAIN
            }
            FriendsNavigation.MAIN -> Unit
        }
    }

    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    LaunchedEffect(initialChatId) {
        if (initialChatId != null) {
            activeChatId = initialChatId
            activeChatTitle = initialChatTitle ?: "Chat"
            currentScreen = FriendsNavigation.CHAT
            onInitialChatConsumed?.invoke()
        }
    }

    val pendingRequests = remember { mutableStateListOf<UserSearchItem>() }

    DisposableEffect(myUid) {
        if (myUid.isBlank()) return@DisposableEffect onDispose {}

        val friendsReg = db.collection("users").document(myUid).collection("friends")
            .addSnapshotListener { snapshot, _ ->
                friendUids = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
            }

        val incomingReg = db.collection("users").document(myUid).collection("friendRequests")
            .addSnapshotListener { snapshot, _ ->
                pendingRequestUids = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
                pendingRequests.clear()
                snapshot?.documents?.forEach { doc ->
                    val uid = doc.getString("uid") ?: doc.id
                    pendingRequests.add(
                        UserSearchItem(
                            uid = uid,
                            name = doc.getString("displayName") ?: "User",
                            displayName = doc.getString("displayName") ?: "User",
                            email = doc.getString("email") ?: "",
                            profilePictureUrl = doc.getString("profilePictureUrl") ?: ""
                        )
                    )
                }
            }

        val outgoingReg = db.collection("users").document(myUid).collection("sentFriendRequests")
            .addSnapshotListener { snapshot, _ ->
                sentRequestUids = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
            }

        val favoritesReg = db.collection("users").document(myUid).collection("favoriteFriends")
            .addSnapshotListener { snapshot, _ ->
                favoriteFriendUids = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
            }

        onDispose {
            friendsReg.remove()
            incomingReg.remove()
            outgoingReg.remove()
            favoritesReg.remove()
        }
    }

    DisposableEffect(myUid) {
        if (myUid.isBlank()) return@DisposableEffect onDispose {}
        val reg = repo.listenToMyChats(
            myUid = myUid,
            onSnapshot = { updatedChats ->
                chats.clear()
                chats.addAll(updatedChats)
            },
            onError = { }
        )
        onDispose { reg.remove() }
    }

    val onlineFriends = remember { mutableStateListOf<UserSearchItem>() }
    var onlineFriendsError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(myUid, friendUids) {
        if (myUid.isBlank() || friendUids.isEmpty()) {
            onlineFriends.clear()
            onlineFriendsError = null
            return@DisposableEffect onDispose {}
        }

        val onlineByUid = linkedMapOf<String, UserSearchItem>()
        val registrations = friendUids
            .toList()
            .chunked(10)
            .map { uidChunk ->
                db.collection("users")
                    .whereIn(FieldPath.documentId(), uidChunk)
                    .whereEqualTo("isOnline", true)
                    .addSnapshotListener { usersSnapshot, error ->
                        if (error != null) {
                            onlineFriendsError = error.message
                            onlineFriends.clear()
                            return@addSnapshotListener
                        }

                        onlineFriendsError = null
                        val chunkIds = uidChunk.toSet()
                        onlineByUid.keys.removeAll(chunkIds)
                        usersSnapshot?.documents
                            ?.mapNotNull { it.toUserSearchItem() }
                            ?.forEach { user ->
                                onlineByUid[user.uid] = user
                            }

                        onlineFriends.clear()
                        onlineFriends.addAll(
                            onlineByUid.values.sortedBy { user ->
                                user.displayName.ifBlank { user.email }.lowercase()
                            }
                        )
                    }
            }

        onDispose {
            registrations.forEach { it.remove() }
        }
    }

    fun addFavorite(friendUid: String) {
        if (myUid.isBlank()) return

        db.collection("users")
            .document(myUid)
            .collection("favoriteFriends")
            .document(friendUid)
            .set(
                mapOf(
                    "uid" to friendUid,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
    }

    fun removeFavorite(friendUid: String) {
        if (myUid.isBlank()) return

        db.collection("users")
            .document(myUid)
            .collection("favoriteFriends")
            .document(friendUid)
            .delete()
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (currentScreen) {
            FriendsNavigation.MAIN -> {
                MessagesMainScreen(
                    onlineFriends = onlineFriends,
                    onlineFriendsError = onlineFriendsError,
                    pendingRequests = pendingRequests,
                    chats = chats,
                    onChatClick = { chat ->
                        activeChatId = chat.id
                        activeChatTitle = chat.title
                        currentScreen = FriendsNavigation.CHAT
                    },
                    onDeleteChat = { chat ->
                        chatToDelete = chat
                    },
                    onNewMessageClick = {
                        currentScreen = FriendsNavigation.NEW_MESSAGE
                    },
                    onUserClick = { user ->
                        selectedUserForAction = user
                    },
                    onAcceptFriend = { user ->
                        scope.launch {
                            try {
                                repo.acceptFriendRequest(myUid, user.uid)
                            } catch (_: Exception) {
                            }
                        }
                    },
                    onDeclineFriend = { user ->
                        scope.launch {
                            try {
                                repo.declineFriendRequest(myUid, user.uid)
                            } catch (_: Exception) {
                            }
                        }
                    }
                )
            }

            FriendsNavigation.NEW_MESSAGE -> {
                NewMessageScreen(
                    onBack = { currentScreen = FriendsNavigation.MAIN },
                    onUserSelected = { user ->
                        selectedUserForAction = user
                    },
                    onGroupCreated = { groupTitle, chatId ->
                        activeChatId = chatId
                        activeChatTitle = groupTitle
                        currentScreen = FriendsNavigation.CHAT
                    },
                    repo = repo,
                    auth = auth,
                    db = db
                )
            }

            FriendsNavigation.CHAT -> {
                if (activeChatId != null) {
                    ChatScreen(
                        chatId = activeChatId!!,
                        title = activeChatTitle ?: "Chat",
                        onBack = {
                            currentScreen = FriendsNavigation.MAIN
                            activeChatId = null
                            activeChatTitle = null
                        },
                        onLocationClick = onLocationClick,
                        repo = repo,
                        auth = auth
                    )
                }
            }
        }

        selectedUserForAction?.let { user ->
            UserActionDialog(
                user = user,
                isFriend = friendUids.contains(user.uid),
                isPinned = favoriteFriendUids.contains(user.uid),
                requestStatus = when {
                    pendingRequestUids.contains(user.uid) -> "pending"
                    sentRequestUids.contains(user.uid) -> "sent"
                    else -> null
                },
                onDismiss = { selectedUserForAction = null },
                onTogglePin = {
                    val targetUser = selectedUserForAction ?: return@UserActionDialog
                    val isPinned = favoriteFriendUids.contains(targetUser.uid)

                    if (isPinned) {
                        removeFavorite(targetUser.uid)
                    } else {
                        addFavorite(targetUser.uid)
                    }
                },
                onMessage = {
                    val targetUser = selectedUserForAction!!
                    val isFriendNow = friendUids.contains(targetUser.uid)

                    if (isFriendNow) {
                        selectedUserForAction = null
                        scope.launch {
                            try {
                                val chatId = repo.openOrCreateDm(myUid, targetUser.uid, targetUser.displayName)
                                activeChatId = chatId
                                activeChatTitle = targetUser.displayName
                                currentScreen = FriendsNavigation.CHAT
                            } catch (_: Exception) {
                            }
                        }
                    } else {
                        pendingFriendToAdd = targetUser
                        selectedUserForAction = null
                        showNotFriendsDialog = true
                    }
                },
                onAddFriend = {
                    val targetUser = selectedUserForAction!!
                    selectedUserForAction = null
                    scope.launch {
                        try {
                            repo.sendFriendRequest(myUid, targetUser.uid)
                        } catch (_: Exception) {
                        }
                    }
                },
                onAcceptFriend = {
                    val targetUser = selectedUserForAction!!
                    selectedUserForAction = null
                    scope.launch {
                        try {
                            repo.acceptFriendRequest(myUid, targetUser.uid)
                        } catch (_: Exception) {
                        }
                    }
                },
                onDeclineFriend = {
                    val targetUser = selectedUserForAction!!
                    selectedUserForAction = null
                    scope.launch {
                        try {
                            repo.declineFriendRequest(myUid, targetUser.uid)
                        } catch (_: Exception) {
                        }
                    }
                },
                onCancelFriend = {
                    val targetUser = selectedUserForAction!!
                    selectedUserForAction = null
                    scope.launch {
                        try {
                            repo.cancelFriendRequest(myUid, targetUser.uid)
                        } catch (_: Exception) {
                        }
                    }
                },
                onRemoveFriend = {
                    val targetUser = selectedUserForAction!!
                    selectedUserForAction = null
                    scope.launch {
                        try {
                            repo.removeFriend(myUid, targetUser.uid)
                            removeFavorite(targetUser.uid)
                        } catch (_: Exception) {
                        }
                    }
                },
                onViewProfile = {
                    val uid = selectedUserForAction!!.uid
                    selectedUserForAction = null
                    onNavigateToProfile?.invoke(uid)
                }
            )
        }

        if (showNotFriendsDialog) {
            AlertDialog(
                onDismissRequest = {
                    showNotFriendsDialog = false
                    pendingFriendToAdd = null
                },
                title = { Text("Not Friends Yet") },
                text = { Text("You must be friends with someone to send them a direct message. Would you like to send a friend request?") },
                confirmButton = {
                    Button(
                        onClick = {
                            val user = pendingFriendToAdd
                            showNotFriendsDialog = false
                            pendingFriendToAdd = null
                            if (user != null) {
                                scope.launch {
                                    try {
                                        repo.sendFriendRequest(myUid, user.uid)
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Send Request")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showNotFriendsDialog = false
                            pendingFriendToAdd = null
                        }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        chatToDelete?.let { chat ->
            AlertDialog(
                onDismissRequest = { chatToDelete = null },
                title = { Text("Delete conversation?") },
                text = { Text("This will remove the conversation from your inbox. You will still be a member of group chats.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val targetChatId = chat.id
                            chatToDelete = null
                            scope.launch {
                                try {
                                    repo.deleteChatListItem(myUid, targetChatId)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { chatToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun UserActionDialog(
    user: UserSearchItem,
    isFriend: Boolean = false,
    isPinned: Boolean = false,
    requestStatus: String? = null,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onMessage: () -> Unit,
    onAddFriend: () -> Unit,
    onAcceptFriend: () -> Unit = {},
    onDeclineFriend: () -> Unit = {},
    onCancelFriend: () -> Unit = {},
    onRemoveFriend: () -> Unit = {},
    onViewProfile: () -> Unit
) {
    var showConfirmRemove by remember { mutableStateOf(false) }

    if (showConfirmRemove) {
        AlertDialog(
            onDismissRequest = { showConfirmRemove = false },
            title = { Text("Remove Friend") },
            text = { Text("Are you sure you want to remove ${user.displayName} from your friends list?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmRemove = false
                        onRemoveFriend()
                    }
                ) {
                    Text("Remove", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRemove = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                UserAvatar(user.profilePictureUrl, user.displayName, Modifier.size(80.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    user.displayName,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    user.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (isFriend) {
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = if (isPinned) "Unpin friend" else "Pin friend",
                            tint = if (isPinned) Color.Red else Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Button(
                    onClick = onMessage,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Chat, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Message")
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isFriend) {
                    OutlinedButton(
                        onClick = { showConfirmRemove = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                    ) {
                        Icon(Icons.Default.PersonRemove, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Remove Friend")
                    }
                } else {
                    when (requestStatus) {
                        "sent" -> {
                            OutlinedButton(
                                onClick = onCancelFriend,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel Request")
                            }
                        }

                        "pending" -> {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = onAcceptFriend,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Accept")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = onDeclineFriend,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Decline")
                                }
                            }
                        }

                        else -> {
                            OutlinedButton(
                                onClick = onAddFriend,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add Friend")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onViewProfile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Profile")
                }
            }
        }
    }
}

@Composable
fun MessagesMainScreen(
    onlineFriends: List<UserSearchItem>,
    onlineFriendsError: String? = null,
    pendingRequests: List<UserSearchItem>,
    chats: List<ChatListItem>,
    onChatClick: (ChatListItem) -> Unit,
    onDeleteChat: (ChatListItem) -> Unit,
    onNewMessageClick: () -> Unit,
    onUserClick: (UserSearchItem) -> Unit,
    onAcceptFriend: (UserSearchItem) -> Unit,
    onDeclineFriend: (UserSearchItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredChats = if (searchQuery.isBlank()) {
        chats
    } else {
        chats.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Messages",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
            )
            IconButton(
                onClick = onNewMessageClick,
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, Color.LightGray, CircleShape)
                    .background(MaterialTheme.colorScheme.secondary, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AddComment,
                    contentDescription = "New Message",
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "Search name or username",
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 20.dp, bottom = 20.dp)
        ) {
            if (pendingRequests.isNotEmpty() && searchQuery.isBlank()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Friend Requests (${pendingRequests.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            pendingRequests.forEach { request ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    UserAvatar(
                                        request.profilePictureUrl,
                                        request.displayName,
                                        Modifier
                                            .size(40.dp)
                                            .clickable { onUserClick(request) }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        request.displayName,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { onUserClick(request) },
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                                    )
                                    IconButton(onClick = { onAcceptFriend(request) }) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Accept",
                                            tint = Color(0xFF4CAF50)
                                        )
                                    }
                                    IconButton(onClick = { onDeclineFriend(request) }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Decline",
                                            tint = Color.Red
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 16.dp)) {
                        Text(
                            text = "Online friends",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        if (onlineFriendsError != null) {
                            Text(
                                text = "Unable to load online status",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        } else if (onlineFriends.isEmpty()) {
                            Text(
                                text = "No friends online",
                                modifier = Modifier.padding(horizontal = 16.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(onlineFriends) { friend ->
                                    OnlineFriendItem(friend, onClick = { onUserClick(friend) })
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Recent Messages",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        if (filteredChats.isEmpty()) {
                            Text(
                                if (searchQuery.isBlank()) "No messages yet" else "No results found",
                                modifier = Modifier
                                    .padding(vertical = 20.dp)
                                    .align(Alignment.CenterHorizontally),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            filteredChats.forEachIndexed { index, chat ->
                                ChatListItemRow(
                                    chat = chat,
                                    onClick = { onChatClick(chat) },
                                    onDelete = { onDeleteChat(chat) }
                                )
                                if (index < filteredChats.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
fun OnlineFriendItem(user: UserSearchItem, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(70.dp)
            .clickable(onClick = onClick)
    ) {
        Box {
            UserAvatar(
                imageUrl = user.profilePictureUrl,
                label = user.displayName,
                modifier = Modifier.size(60.dp)
            )
            if (user.isOnline) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(Color(0xFF4CAF50), CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = user.name.split(" ").first(),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ChatListItemRow(
    chat: ChatListItem,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            imageUrl = chat.profilePictureUrl,
            label = chat.title,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                chat.lastMessageAt?.let {
                    Text(
                        text = timeFormatter.format(it.toDate()),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            Text(
                text = chat.lastMessageText ?: "No messages yet",
                style = MaterialTheme.typography.bodyMedium,
                color = if (chat.lastMessageText == null) Color.Gray else Color.DarkGray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete chat",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder, color = Color.Gray) },
        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Clear, contentDescription = "Clear") } },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(26.dp),
        colors = TextFieldDefaults.colors(
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black,

            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            disabledContainerColor = Color.White,

            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
        },
        singleLine = true
    )
}

@Composable
fun NewMessageScreen(
    onBack: () -> Unit,
    onUserSelected: (UserSearchItem) -> Unit,
    onGroupCreated: (String, String) -> Unit,
    repo: ChatRepository,
    auth: FirebaseAuth,
    db: FirebaseFirestore
) {
    var searchQuery by remember { mutableStateOf("") }
    val searchResults = remember { mutableStateListOf<UserSearchItem>() }

    var isCreatingGroup by remember { mutableStateOf(false) }

    BackHandler(enabled = isCreatingGroup) {
        isCreatingGroup = false
    }

    var groupTitle by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<UserSearchItem>() }

    val allFriends = remember { mutableStateListOf<UserSearchItem>() }

    val myUid = auth.currentUser?.uid ?: ""

    LaunchedEffect(myUid) {
        if (myUid.isBlank()) return@LaunchedEffect
        try {
            val friendIds = db.collection("users")
                .document(myUid)
                .collection("friends")
                .get()
                .await()
                .documents
                .map { it.id }

            allFriends.clear()
            if (friendIds.isNotEmpty()) {
                val users = friendIds
                    .chunked(10)
                    .flatMap { uidChunk ->
                        db.collection("users")
                            .whereIn(FieldPath.documentId(), uidChunk)
                            .get()
                            .await()
                            .documents
                    }
                    .mapNotNull { it.toUserSearchItem() }
                    .sortedBy { it.displayName.ifBlank { it.email }.lowercase() }

                allFriends.addAll(users)
            }
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(searchQuery, isCreatingGroup) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            searchResults.clear()
            return@LaunchedEffect
        }
        if (isCreatingGroup) {
            searchResults.clear()
            return@LaunchedEffect
        }
        delay(300)
        val normalized = query.lowercase()
        try {
            val docs = db.collection("users")
                .whereEqualTo("isProfileComplete", true)
                .get().await().documents

            searchResults.clear()
            searchResults.addAll(
                docs.mapNotNull { it.toUserSearchItem() }
                    .filter {
                        it.uid != myUid && (
                                it.email.lowercase().contains(normalized) ||
                                        it.name.lowercase().contains(normalized) ||
                                        it.displayName.lowercase().contains(normalized)
                                )
                    }
            )
        } catch (_: Exception) {
        }
    }

    val filteredGroupFriends = if (searchQuery.isBlank()) {
        allFriends
    } else {
        val normalized = searchQuery.trim().lowercase()
        allFriends.filter { user ->
            user.email.lowercase().contains(normalized) ||
                    user.name.lowercase().contains(normalized) ||
                    user.displayName.lowercase().contains(normalized)
        }
    }

    val displayedSearchResults = if (isCreatingGroup) filteredGroupFriends else searchResults

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isCreatingGroup) isCreatingGroup = false else onBack()
                },
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, Color.LightGray, CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (isCreatingGroup) "Create Group" else "New message",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.weight(1f))
            if (isCreatingGroup) {
                TextButton(
                    onClick = {
                        if (groupTitle.isNotBlank() && selectedMembers.isNotEmpty()) {
                            scope.launch {
                                try {
                                    val memberIds = selectedMembers.map { it.uid } + myUid
                                    val chatId = repo.openOrCreateGroupChat(myUid, memberIds, groupTitle)
                                    onGroupCreated(groupTitle, chatId)
                                } catch (_: Exception) {
                                }
                            }
                        }
                    },
                    enabled = groupTitle.isNotBlank() && selectedMembers.isNotEmpty()
                ) {
                    Text("Create")
                }
            }
        }

        if (isCreatingGroup) {
            OutlinedTextField(
                value = groupTitle,
                onValueChange = { groupTitle = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                placeholder = { Text("Group Title") },
                shape = RoundedCornerShape(28.dp),
                singleLine = true

            )

            if (selectedMembers.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedMembers) { user ->
                        Box(
                            modifier = Modifier.size(44.dp)
                        ) {
                            UserAvatar(user.profilePictureUrl, user.displayName, Modifier.size(40.dp))
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(16.dp)
                                    .background(Color(0xFFD32F2F), CircleShape)
                                    .border(1.dp, Color.White, CircleShape)
                                    .clickable { selectedMembers.remove(user) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = if (isCreatingGroup) "Search friends" else "Search name or username",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp)
        ) {
            if (searchQuery.isEmpty()) {
                if (!isCreatingGroup) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Create group",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { isCreatingGroup = true }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(Color(0xFFF5F5F5), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Groups, contentDescription = null, tint = Color.Gray)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("New group", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (filteredGroupFriends.isEmpty()) {
                                    Text(
                                        text = "No friends available to add.",
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = Color.Gray
                                    )
                                } else {
                                    filteredGroupFriends.forEach { user ->
                                        val isSelected = selectedMembers.any { it.uid == user.uid }
                                        UserSearchRow(
                                            user = user,
                                            onClick = {
                                                if (isSelected) selectedMembers.removeIf { it.uid == user.uid }
                                                else selectedMembers.add(user)
                                            },
                                            trailing = {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = {
                                                        if (it) selectedMembers.add(user)
                                                        else selectedMembers.removeIf { m -> m.uid == user.uid }
                                                    }
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Results",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            if (displayedSearchResults.isEmpty()) {
                                Text("No results found", modifier = Modifier.padding(vertical = 8.dp))
                            } else {
                                displayedSearchResults.forEach { user ->
                                    if (isCreatingGroup) {
                                        val isSelected = selectedMembers.any { it.uid == user.uid }
                                        UserSearchRow(
                                            user = user,
                                            onClick = {
                                                if (isSelected) selectedMembers.removeIf { it.uid == user.uid }
                                                else selectedMembers.add(user)
                                            },
                                            trailing = {
                                                Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = {
                                                        if (it) selectedMembers.add(user)
                                                        else selectedMembers.removeIf { m -> m.uid == user.uid }
                                                    }
                                                )
                                            }
                                        )
                                    } else {
                                        UserSearchRow(user = user, onClick = { onUserSelected(user) })
                                    }
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
fun UserSearchRow(
    user: UserSearchItem,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            UserAvatar(
                imageUrl = user.profilePictureUrl,
                label = user.displayName,
                modifier = Modifier.size(48.dp)
            )
            if (user.isOnline) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color(0xFF4CAF50), CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                        .align(Alignment.BottomEnd)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
                if (user.isVerified) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified",
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
        trailing?.invoke()
    }
}