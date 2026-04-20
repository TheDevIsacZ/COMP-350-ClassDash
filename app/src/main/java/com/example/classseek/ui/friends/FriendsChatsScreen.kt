
package com.example.classseek.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.classseek.data.ChatListItem
import com.example.classseek.data.ChatRepository
import com.example.classseek.ui.chat.ChatScreen
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class FriendListItem(
    val uid: String,
    val displayName: String,
    val email: String,
    val profilePictureUrl: String,
    val chatId: String? = null,
    val addedAt: Timestamp? = null
)

private data class FriendInboxRow(
    val friend: FriendListItem,
    val chat: ChatListItem? = null
)

private fun DocumentSnapshot.toFriendListItem(): FriendListItem {
    return FriendListItem(
        uid = getString("uid") ?: id,
        displayName = getString("displayName")
            ?.trim()
            .orEmpty()
            .ifBlank { getString("name")?.trim().orEmpty() }
            .ifBlank { getString("email")?.substringBefore("@").orEmpty() }
            .ifBlank { "Friend" },
        email = getString("email")?.trim().orEmpty(),
        profilePictureUrl = getString("profilePictureUrl")?.trim().orEmpty(),
        chatId = getString("chatId"),
        addedAt = getTimestamp("createdAt") ?: getTimestamp("addedAt")
    )
}

private suspend fun resolveFriendListItem(
    db: FirebaseFirestore,
    doc: DocumentSnapshot
): FriendListItem {
    val base = doc.toFriendListItem()

    if (base.displayName != "Friend" && base.email.isNotBlank()) {
        return base
    }

    val profileDoc = db.collection("users").document(base.uid).get().await()
    return base.copy(
        displayName = profileDoc.getString("displayName")
            ?.trim()
            .orEmpty()
            .ifBlank { profileDoc.getString("name")?.trim().orEmpty() }
            .ifBlank { profileDoc.getString("email")?.substringBefore("@").orEmpty() }
            .ifBlank { base.displayName },
        email = profileDoc.getString("email")?.trim().orEmpty().ifBlank { base.email },
        profilePictureUrl = profileDoc.getString("profilePictureUrl")
            ?.trim()
            .orEmpty()
            .ifBlank { base.profilePictureUrl }
    )
}

private fun formatLastActivity(timestamp: Timestamp?): String {
    val date = timestamp?.toDate() ?: return ""
    val now = Date()
    val diffMs = now.time - date.time
    val dayMs = 24L * 60L * 60L * 1000L

    return when {
        diffMs < dayMs -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        diffMs < 7 * dayMs -> SimpleDateFormat("EEE", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MM/dd/yy", Locale.getDefault()).format(date)
    }
}

@Composable
private fun InboxAvatar(
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
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun FriendsChatsScreen(
    modifier: Modifier = Modifier,
    repo: ChatRepository = remember { ChatRepository(FirebaseFirestore.getInstance()) },
    auth: FirebaseAuth = remember { FirebaseAuth.getInstance() },
    initialChatId: String? = null,
    initialChatTitle: String? = null,
    onInitialChatConsumed: (() -> Unit)? = null
) {
    val db = remember { FirebaseFirestore.getInstance() }
    val scope = rememberCoroutineScope()

    var myUid by remember { mutableStateOf<String?>(auth.currentUser?.uid) }
    var selectedChatId by remember { mutableStateOf<String?>(null) }
    var selectedChatTitle by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var requestCount by remember { mutableIntStateOf(0) }

    val friendItems = remember { mutableStateListOf<FriendListItem>() }
    val myChats = remember { mutableStateListOf<ChatListItem>() }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            myUid = firebaseAuth.currentUser?.uid
        }

        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(initialChatId, initialChatTitle) {
        if (!initialChatId.isNullOrBlank()) {
            selectedChatId = initialChatId
            selectedChatTitle = initialChatTitle ?: "Chat"
            onInitialChatConsumed?.invoke()
        }
    }

    DisposableEffect(myUid) {
        val uid = myUid
        if (uid == null) {
            friendItems.clear()
            myChats.clear()
            requestCount = 0
            onDispose { }
        } else {
            val friendsRegistration = db.collection("users")
                .document(uid)
                .collection("friends")
                .whereEqualTo("status", "accepted")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        errorText = error.message ?: "Failed to load friends"
                        return@addSnapshotListener
                    }

                    scope.launch {
                        try {
                            val resolvedFriends = snapshot?.documents
                                .orEmpty()
                                .map { resolveFriendListItem(db, it) }
                                .sortedBy { it.displayName.lowercase() }

                            friendItems.clear()
                            friendItems.addAll(resolvedFriends)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            errorText = e.message ?: "Failed to load friends"
                        }
                    }
                }

            val requestsRegistration = db.collection("users")
                .document(uid)
                .collection("friendRequests")
                .whereEqualTo("status", "pending")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        errorText = error.message ?: "Failed to load friend requests"
                        return@addSnapshotListener
                    }
                    requestCount = snapshot?.size() ?: 0
                }

            val chatsRegistration = repo.listenToMyChats(
                myUid = uid,
                onSnapshot = { chats ->
                    myChats.clear()
                    myChats.addAll(chats)
                },
                onError = { exception ->
                    errorText = exception.message ?: "Failed to load chats"
                }
            )

            onDispose {
                friendsRegistration.remove()
                requestsRegistration.remove()
                chatsRegistration.remove()
            }
        }
    }

    if (selectedChatId != null) {
        ChatScreen(
            chatId = selectedChatId!!,
            title = selectedChatTitle ?: "Chat",
            onBack = {
                selectedChatId = null
                selectedChatTitle = null
            }
        )
        return
    }

    val filteredRows = remember(friendItems.toList(), myChats.toList(), searchQuery) {
        val chatById = myChats.associateBy { it.id }

        val rows = friendItems.map { friend ->
            val matchingChat = when {
                !friend.chatId.isNullOrBlank() -> chatById[friend.chatId]
                else -> myChats.firstOrNull { chat ->
                    chat.type == "dm" && chat.title.trim().equals(friend.displayName.trim(), ignoreCase = true)
                }
            }

            FriendInboxRow(friend = friend, chat = matchingChat)
        }.sortedWith(
            compareByDescending<FriendInboxRow> { it.chat?.lastMessageAt?.toDate()?.time ?: 0L }
                .thenBy { it.friend.displayName.lowercase() }
        )

        val normalizedQuery = searchQuery.trim().lowercase()
        if (normalizedQuery.isBlank()) {
            rows
        } else {
            rows.filter { row ->
                row.friend.displayName.lowercase().contains(normalizedQuery) ||
                    row.friend.email.lowercase().contains(normalizedQuery) ||
                    row.chat?.lastMessageText.orEmpty().lowercase().contains(normalizedQuery)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Messages",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            if (requestCount > 0) {
                AssistChip(
                    onClick = { },
                    label = { Text("$requestCount requests") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PersonAddAlt1,
                            contentDescription = null
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            singleLine = true,
            label = { Text("Search friends and chats") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Friends",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (friendItems.isEmpty() && myUid != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "No friends yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Keep your current Friends screen for sending and approving requests. This new screen is ready to show accepted friends and their conversations.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(friendItems, key = { it.uid }) { friend ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(76.dp)
                            .clickable(enabled = !isWorking) {
                                scope.launch {
                                    try {
                                        isWorking = true
                                        errorText = null

                                        val currentUid = myUid ?: throw Exception("User not signed in")
                                        val existingChatId = repo.findExistingDmChatId(currentUid, friend.uid)
                                        val chatId = existingChatId ?: repo.openOrCreateDm(
                                            uidA = currentUid,
                                            uidB = friend.uid,
                                            title = friend.displayName
                                        )

                                        db.collection("users")
                                            .document(currentUid)
                                            .collection("friends")
                                            .document(friend.uid)
                                            .set(mapOf("chatId" to chatId), com.google.firebase.firestore.SetOptions.merge())
                                            .await()

                                        selectedChatId = chatId
                                        selectedChatTitle = friend.displayName
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        errorText = e.message ?: "Failed to open chat"
                                    } finally {
                                        isWorking = false
                                    }
                                }
                            }
                    ) {
                        Box {
                            InboxAvatar(
                                imageUrl = friend.profilePictureUrl,
                                label = friend.displayName,
                                modifier = Modifier.size(68.dp)
                            )

                            if (requestCount > 0 && friendItems.indexOf(friend) == 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(22.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = requestCount.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = friend.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            if (errorText != null) {
                item("error_message") {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            if (myUid == null) {
                item("signed_out") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Please sign in first.")
                    }
                }
            } else if (filteredRows.isEmpty()) {
                item("empty_inbox") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (friendItems.isEmpty()) "Your inbox will appear here" else "No matching chats found",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            } else {
                items(filteredRows, key = { it.friend.uid }) { row ->
                    val friend = row.friend
                    val chat = row.chat

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isWorking) {
                                scope.launch {
                                    try {
                                        isWorking = true
                                        errorText = null

                                        val currentUid = myUid ?: throw Exception("User not signed in")
                                        val existingChatId = repo.findExistingDmChatId(currentUid, friend.uid)
                                        val chatId = existingChatId ?: repo.openOrCreateDm(
                                            uidA = currentUid,
                                            uidB = friend.uid,
                                            title = friend.displayName
                                        )

                                        db.collection("users")
                                            .document(currentUid)
                                            .collection("friends")
                                            .document(friend.uid)
                                            .set(mapOf("chatId" to chatId), com.google.firebase.firestore.SetOptions.merge())
                                            .await()

                                        selectedChatId = chatId
                                        selectedChatTitle = friend.displayName
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        errorText = e.message ?: "Failed to open chat"
                                    } finally {
                                        isWorking = false
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        InboxAvatar(
                            imageUrl = friend.profilePictureUrl,
                            label = friend.displayName,
                            modifier = Modifier.size(58.dp)
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = friend.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = chat?.lastMessageText?.takeIf { it.isNotBlank() }
                                    ?: "You and ${friend.displayName} are now connected. Say hello 👋",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(
                                text = formatLastActivity(chat?.lastMessageAt ?: friend.addedAt),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (chat == null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Icon(
                                    imageVector = Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isWorking) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}
