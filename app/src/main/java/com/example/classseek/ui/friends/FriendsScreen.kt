package com.example.classseek.ui.friends

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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
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

    return UserSearchItem(
        uid = id,
        name = name,
        displayName = displayName,
        email = email,
        major = getString("major")?.trim().orEmpty(),
        profilePictureUrl = getString("profilePictureUrl")?.trim().orEmpty(),
        isVerified = false, // Default for now
        isOnline = false // Default for now
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
    onNavigateToProfile: ((String) -> Unit)? = null
) {
    var currentScreen by remember { mutableStateOf(FriendsNavigation.MAIN) }
    var activeChatId by remember { mutableStateOf<String?>(null) }
    var activeChatTitle by remember { mutableStateOf<String?>(null) }
    val chats = remember { mutableStateListOf<ChatListItem>() }
    val myUid = auth.currentUser?.uid ?: ""
    val scope = rememberCoroutineScope()
    val db = remember { FirebaseFirestore.getInstance() }
    
    var selectedUserForAction by remember { mutableStateOf<UserSearchItem?>(null) }
    var friendUids by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Handle initial chat for deep linking or returning from other screens
    LaunchedEffect(initialChatId) {
        if (initialChatId != null) {
            activeChatId = initialChatId
            activeChatTitle = initialChatTitle ?: "Chat"
            currentScreen = FriendsNavigation.CHAT
            onInitialChatConsumed?.invoke()
        }
    }

    // Listen for friend UIDs
    LaunchedEffect(myUid) {
        if (myUid.isBlank()) return@LaunchedEffect
        db.collection("users").document(myUid).collection("friends")
            .addSnapshotListener { snapshot, _ ->
                friendUids = snapshot?.documents?.map { it.id }?.toSet() ?: emptySet()
            }
    }

    // Listen for chats
    DisposableEffect(myUid) {
        if (myUid.isBlank()) return@DisposableEffect onDispose {}
        val reg = repo.listenToMyChats(
            myUid = myUid,
            onSnapshot = { updatedChats ->
                chats.clear()
                chats.addAll(updatedChats)
            },
            onError = { /* Log error */ }
        )
        onDispose { reg.remove() }
    }

    // Listen for Online Friends (Real friends from Firestore)
    val onlineFriends = remember { mutableStateListOf<UserSearchItem>() }
    LaunchedEffect(friendUids) {
        if (myUid.isBlank()) return@LaunchedEffect
        
        if (friendUids.isEmpty()) {
            onlineFriends.clear()
            return@LaunchedEffect
        }

        // Fetch the actual user data for these friends
        // Note: Firestore 'in' query is limited to 10 items. For a real app, you'd handle more.
        db.collection("users")
            .whereIn("uid", friendUids.toList().take(10))
            .get()
            .addOnSuccessListener { usersSnapshot ->
                val users = usersSnapshot.documents.mapNotNull { it.toUserSearchItem() }
                onlineFriends.clear()
                onlineFriends.addAll(users.map { user ->
                    // Mocking online status for now as we don't have real presence
                    user.copy(isOnline = true)
                })
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when (currentScreen) {
            FriendsNavigation.MAIN -> {
                MessagesMainScreen(
                    onlineFriends = onlineFriends,
                    chats = chats,
                    onChatClick = { chat ->
                        activeChatId = chat.id
                        activeChatTitle = chat.title
                        currentScreen = FriendsNavigation.CHAT
                    },
                    onNewMessageClick = {
                        currentScreen = FriendsNavigation.NEW_MESSAGE
                    },
                    onUserClick = { user ->
                        selectedUserForAction = user
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
                onDismiss = { selectedUserForAction = null },
                onMessage = {
                    val targetUser = selectedUserForAction!!
                    selectedUserForAction = null
                    scope.launch {
                        try {
                            val chatId = repo.openOrCreateDm(myUid, targetUser.uid, targetUser.displayName)
                            activeChatId = chatId
                            activeChatTitle = targetUser.displayName
                            currentScreen = FriendsNavigation.CHAT
                        } catch (e: Exception) {
                            // Handle error
                        }
                    }
                },
                onAddFriend = {
                    val targetUser = selectedUserForAction!!
                    selectedUserForAction = null
                    scope.launch {
                        try {
                            db.collection("users").document(myUid).collection("friends").document(targetUser.uid).set(
                                mapOf(
                                    "uid" to targetUser.uid,
                                    "name" to targetUser.displayName,
                                    "profilePictureUrl" to targetUser.profilePictureUrl,
                                    "addedAt" to FieldValue.serverTimestamp()
                                )
                            ).await()
                        } catch (e: Exception) {
                            // Error
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
    }
}

@Composable
fun UserActionDialog(
    user: UserSearchItem,
    isFriend: Boolean = false,
    onDismiss: () -> Unit,
    onMessage: () -> Unit,
    onAddFriend: () -> Unit,
    onViewProfile: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
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
                Text(user.email, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                
                Spacer(modifier = Modifier.height(24.dp))
                
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
                
                if (!isFriend) {
                    OutlinedButton(
                        onClick = onAddFriend,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Friend")
                    }
                } else {
                    OutlinedButton(
                        onClick = {}, // Already friends
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Friends")
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
    chats: List<ChatListItem>,
    onChatClick: (ChatListItem) -> Unit,
    onNewMessageClick: () -> Unit,
    onUserClick: (UserSearchItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredChats = if (searchQuery.isBlank()) chats else {
        chats.filter { it.title.contains(searchQuery, ignoreCase = true) }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
        // Header
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
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = "New Message",
                        modifier = Modifier.size(20.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp).padding(start = 14.dp, top = 14.dp)
                    )
                }
            }
        }

        // Search Bar
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
            // Online Friends Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 16.dp)) {
                        Text(
                            text = "Online friends",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
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

            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Recent Messages Section
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
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
                                modifier = Modifier.padding(vertical = 20.dp).align(Alignment.CenterHorizontally),
                                color = Color.Gray
                            )
                        } else {
                            filteredChats.forEachIndexed { index, chat ->
                                ChatListItemRow(chat = chat, onClick = { onChatClick(chat) })
                                if (index < filteredChats.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        color = Color.LightGray.copy(alpha = 0.3f)
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
        modifier = Modifier.width(70.dp).clickable(onClick = onClick)
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
fun ChatListItemRow(chat: ChatListItem, onClick: () -> Unit) {
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            imageUrl = "", // For now, no chat images
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
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = RoundedCornerShape(26.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color(0xFFF1F3F4),
            unfocusedContainerColor = Color(0xFFF1F3F4),
            disabledContainerColor = Color(0xFFF1F3F4),
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
    onGroupCreated: (String, String) -> Unit, // title, chatId
    repo: ChatRepository,
    auth: FirebaseAuth,
    db: FirebaseFirestore
) {
    var searchQuery by remember { mutableStateOf("") }
    val searchResults = remember { mutableStateListOf<UserSearchItem>() }
    
    // Group creation state
    var isCreatingGroup by remember { mutableStateOf(false) }
    var groupTitle by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<UserSearchItem>() }

    // Frequently contacted and all friends (Mocked/Limited for now)
    val frequentlyContacted = remember { mutableStateListOf<UserSearchItem>() }
    val allFriends = remember { mutableStateListOf<UserSearchItem>() }
    
    val myUid = auth.currentUser?.uid ?: ""

    LaunchedEffect(myUid) {
        if (myUid.isBlank()) return@LaunchedEffect
        try {
            val snapshot = db.collection("users")
                .whereEqualTo("isProfileComplete", true)
                .limit(20)
                .get()
                .await()
            val users = snapshot.documents.mapNotNull { it.toUserSearchItem() }
                .filter { it.uid != myUid }
            
            frequentlyContacted.clear()
            frequentlyContacted.addAll(users.take(3))
            
            allFriends.clear()
            allFriends.addAll(users)
        } catch (e: Exception) {
            // Log error
        }
    }

    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            searchResults.clear()
            return@LaunchedEffect
        }
        delay(300)
        // Search logic
        val normalized = query.lowercase()
        try {
            val docs = db.collection("users")
                .whereEqualTo("isProfileComplete", true)
                .get().await().documents
            
            searchResults.clear()
            searchResults.addAll(docs.mapNotNull { it.toUserSearchItem() }
                .filter { it.uid != myUid && (it.email.lowercase().contains(normalized) || it.name.lowercase().contains(normalized) || it.displayName.lowercase().contains(normalized)) })
        } catch (e: Exception) {
            // Log error
        }
    }

    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F9FA))) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (isCreatingGroup) isCreatingGroup = false
                    else onBack()
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
                                } catch (e: Exception) {
                                    // Handle error
                                }
                            }
                        }
                    },
                    enabled = groupTitle.isNotBlank() && selectedMembers.isNotEmpty()
                ) {
                    Text("Create")
                }
            } else {
                Spacer(modifier = Modifier.width(40.dp))
            }
        }

        if (isCreatingGroup) {
            OutlinedTextField(
                value = groupTitle,
                onValueChange = { groupTitle = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                placeholder = { Text("Group Title") },
                shape = RoundedCornerShape(28.dp),
                singleLine = true
            )
            
            if (selectedMembers.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(selectedMembers) { user ->
                        Box {
                            UserAvatar(user.profilePictureUrl, user.displayName, Modifier.size(40.dp))
                            IconButton(
                                onClick = { selectedMembers.remove(user) },
                                modifier = Modifier.size(16.dp).align(Alignment.TopEnd).background(Color.Gray, CircleShape)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }

        SearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "Search name or username",
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 20.dp)
        ) {
            if (searchQuery.isEmpty()) {
                if (!isCreatingGroup) {
                    // Create Group Section
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
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
                                        modifier = Modifier.size(48.dp).background(Color(0xFFF5F5F5), CircleShape),
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

                    item { Spacer(modifier = Modifier.height(16.dp)) }

                    // Frequently Contacted
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Frequently contacted",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                frequentlyContacted.forEach { user ->
                                    UserSearchRow(user = user, onClick = { onUserSelected(user) })
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }

                    // All Friends
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Suggested Users",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                allFriends.forEach { user ->
                                    UserSearchRow(user = user, onClick = { onUserSelected(user) })
                                }
                            }
                        }
                    }
                } else {
                    // Selection for group
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                allFriends.forEach { user ->
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
            } else {
                // Search Results
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Results",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            if (searchResults.isEmpty()) {
                                Text("No results found", modifier = Modifier.padding(vertical = 8.dp))
                            } else {
                                searchResults.forEach { user ->
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
        if (trailing != null) {
            trailing()
        }
    }
}
