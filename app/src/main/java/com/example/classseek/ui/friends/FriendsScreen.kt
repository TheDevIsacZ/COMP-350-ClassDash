package com.example.classseek.ui.friends

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.classseek.data.ChatListItem
import com.example.classseek.data.ChatRepository
import com.example.classseek.ui.chat.ChatScreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class UserSearchItem(
    val uid: String,
    val name: String,
    val displayName: String,
    val email: String,
    val major: String = "",
    val profilePictureUrl: String = ""
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
        profilePictureUrl = getString("profilePictureUrl")?.trim().orEmpty()
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

@Composable
fun FriendsScreen(
    modifier: Modifier = Modifier,
    repo: ChatRepository = remember { ChatRepository(FirebaseFirestore.getInstance()) },
    auth: FirebaseAuth = remember { FirebaseAuth.getInstance() },
    initialChatId: String? = null,
    initialChatTitle: String? = null,
    onInitialChatConsumed: (() -> Unit)? = null
){
    val scope = rememberCoroutineScope()
    val db = remember { FirebaseFirestore.getInstance() }

    var selectedChatId by remember { mutableStateOf<String?>(null) }
    var selectedChatTitle by remember { mutableStateOf<String?>(null) }

    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    var myUid by remember { mutableStateOf<String?>(null) }
    var myEmail by remember { mutableStateOf<String?>(null) }
    var isSignedIn by remember { mutableStateOf(false) }

    var groupTitle by remember { mutableStateOf("") }

    var searchQuery by remember { mutableStateOf("") }
    val searchResults = remember { mutableStateListOf<UserSearchItem>() }

    val selectedGroupMembers = remember { mutableStateListOf<UserSearchItem>() }
    val myChats = remember { mutableStateListOf<ChatListItem>() }

    var showDmNameDialog by remember { mutableStateOf(false) }
    var pendingDmUser by remember { mutableStateOf<UserSearchItem?>(null) }
    var pendingDmTitle by remember { mutableStateOf("") }



    suspend fun refreshChats() {
        val uid = auth.currentUser?.uid ?: return
        val chats = repo.getMyChats(uid)
        myChats.clear()
        myChats.addAll(chats)
    }

    suspend fun searchUsers(query: String, currentUid: String): List<UserSearchItem> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return emptyList()

        val emailDocs = db.collection("users")
            .whereEqualTo("isProfileComplete", true)
            .orderBy("searchEmail")
            .startAt(normalized)
            .endAt(normalized + "\uf8ff")
            .limit(10)
            .get()
            .await()
            .documents

        val nameDocs = db.collection("users")
            .whereEqualTo("isProfileComplete", true)
            .orderBy("searchName")
            .startAt(normalized)
            .endAt(normalized + "\uf8ff")
            .limit(10)
            .get()
            .await()
            .documents

        return (emailDocs + nameDocs)
            .mapNotNull { it.toUserSearchItem() }
            .filter { it.uid != currentUid }
            .distinctBy { it.uid }
            .sortedWith(
                compareBy<UserSearchItem> {
                    when {
                        it.email.lowercase() == normalized -> 0
                        it.email.lowercase().startsWith(normalized) -> 1
                        it.displayName.lowercase().startsWith(normalized) -> 2
                        it.name.lowercase().startsWith(normalized) -> 3
                        else -> 4
                    }
                }.thenBy {
                    when {
                        it.displayName.isNotBlank() -> it.displayName.lowercase()
                        it.name.isNotBlank() -> it.name.lowercase()
                        else -> it.email.lowercase()
                    }
                }
            )
    }

    fun userLabel(user: UserSearchItem): String {
        return when {
            user.displayName.isNotBlank() -> user.displayName
            user.name.isNotBlank() -> user.name
            else -> user.email
        }
    }

    suspend fun openExistingOrCreateDm(target: UserSearchItem) {
        val currentUid = auth.currentUser?.uid
            ?: throw Exception("User not signed in")

        if (target.uid == currentUid) {
            throw Exception("You cannot message yourself")
        }

        val existingChatId = repo.findExistingDmChatId(currentUid, target.uid)

        if (existingChatId != null) {
            val existingTitle = repo.getChatTitle(existingChatId)
            refreshChats()
            selectedChatId = existingChatId
            selectedChatTitle = existingTitle
            return
        }

        pendingDmUser = target
        pendingDmTitle = ""
        showDmNameDialog = true
    }

    suspend fun createNewDmWithUser(target: UserSearchItem) {
        val currentUid = auth.currentUser?.uid
            ?: throw Exception("User not signed in")

        if (target.uid == currentUid) {
            throw Exception("You cannot message yourself")
        }

        val defaultTitle = userLabel(target)
        val finalRequestedTitle = pendingDmTitle.trim().ifBlank { defaultTitle }

        val createdChatId = repo.openOrCreateDm(
            uidA = currentUid,
            uidB = target.uid,
            title = finalRequestedTitle
        )

        val finalTitle = repo.getChatTitle(createdChatId)

        refreshChats()
        selectedChatId = createdChatId
        selectedChatTitle = finalTitle

        pendingDmUser = null
        pendingDmTitle = ""
        showDmNameDialog = false
    }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            myUid = user?.uid
            myEmail = user?.email
            isSignedIn = user != null
        }

        auth.addAuthStateListener(listener)
        listener.onAuthStateChanged(auth)

        onDispose {
            auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(initialChatId, initialChatTitle) {
        if (!initialChatId.isNullOrBlank()) {
            selectedChatId = initialChatId
            selectedChatTitle = initialChatTitle ?: "Chat"
            onInitialChatConsumed?.invoke()
        }
    }

    LaunchedEffect(myUid) {
        try {
            if (myUid != null) {
                refreshChats()
                if (status == "Please sign in first.") {
                    status = null
                }
            } else {
                myChats.clear()
                searchResults.clear()
                selectedGroupMembers.clear()
                status = "Please sign in first."
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = "Failed to load chats: ${e.message}"
        }
    }

    LaunchedEffect(searchQuery, myUid) {
        val uid = myUid ?: return@LaunchedEffect
        val query = searchQuery.trim()

        if (query.isBlank()) {
            searchResults.clear()
            if (status?.startsWith("Search failed:") == true) {
                status = null
            }
            return@LaunchedEffect
        }

        try {
            delay(250)
            val results = searchUsers(query, uid)
            searchResults.clear()
            searchResults.addAll(results)

            if (status?.startsWith("Search failed:") == true) {
                status = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            status = "Search failed: ${e.message}"
        }
    }

    if (showDmNameDialog && pendingDmUser != null) {
        val user = pendingDmUser!!

        AlertDialog(
            onDismissRequest = {
                if (!working) {
                    showDmNameDialog = false
                    pendingDmUser = null
                    pendingDmTitle = ""
                }
            },
            title = { Text("Start chat with ${userLabel(user)}") },
            text = {
                Column {
                    Text("Enter a chat name, or leave it blank to use the default name.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pendingDmTitle,
                        onValueChange = { pendingDmTitle = it },
                        label = { Text("Chat name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !working
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !working,
                    onClick = {
                        scope.launch {
                            try {
                                working = true
                                status = null
                                createNewDmWithUser(user)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                status = e.message ?: "Failed to create chat"
                            } finally {
                                working = false
                            }
                        }
                    }
                ) {
                    Text(if (working) "Opening..." else "Open")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !working,
                    onClick = {
                        showDmNameDialog = false
                        pendingDmUser = null
                        pendingDmTitle = ""
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
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

    DisposableEffect(myUid) {
        val uid = myUid
        if (uid == null) {
            onDispose { }
        } else {
            val registration = repo.listenToMyChats(
                myUid = uid,
                onSnapshot = { chats ->
                    myChats.clear()
                    myChats.addAll(chats)
                },
                onError = { e ->
                    status = e.message ?: "Failed to listen to chats"
                }
            )

            onDispose {
                registration.remove()
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        item("find_users_header") {

            Text(
                text = "Find Users",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        item("search_box") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search by name or Gmail") },
                singleLine = true,
                enabled = !working && isSignedIn
            )
        }

        if (searchQuery.isNotBlank()) {
            if (searchResults.isEmpty()) {
                item("no_search_results") {
                    Text("No users found.")
                }
            } else {
                items(searchResults, key = { "search_${it.uid}" }) { user ->
                    val alreadySelected = selectedGroupMembers.any { it.uid == user.uid }

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            UserAvatar(
                                imageUrl = user.profilePictureUrl,
                                label = userLabel(user),
                                modifier = Modifier.size(56.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userLabel(user),
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Spacer(Modifier.height(4.dp))

                                Text(
                                    text = user.email,
                                    style = MaterialTheme.typography.bodyMedium
                                )

                                if (user.major.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = user.major,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }

                                Spacer(Modifier.height(8.dp))

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    working = true
                                                    status = null
                                                    openExistingOrCreateDm(user)
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    status = e.message ?: "Failed to open chat"
                                                } finally {
                                                    working = false
                                                }
                                            }
                                        },
                                        enabled = !working
                                    ) {
                                        Text("Message")
                                    }

                                    Button(
                                        onClick = {
                                            if (!alreadySelected) {
                                                selectedGroupMembers.add(user)
                                                status = "Added ${user.email} to group"
                                            }
                                        },
                                        enabled = !working && !alreadySelected
                                    ) {
                                        Text(if (alreadySelected) "Added" else "Add to Group")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item("group_header") {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            Text(
                text = "Create Group Chat",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        item("group_title_input") {
            OutlinedTextField(
                value = groupTitle,
                onValueChange = { groupTitle = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Group name") },
                singleLine = true,
                enabled = !working
            )
        }

        if (selectedGroupMembers.isNotEmpty()) {
            item("selected_group_members_header") {
                Text(
                    text = "Selected group members",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            items(selectedGroupMembers, key = { "group_${it.uid}" }) { member ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        UserAvatar(
                            imageUrl = member.profilePictureUrl,
                            label = userLabel(member),
                            modifier = Modifier.size(44.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userLabel(member),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(member.email)
                        }

                        Text(
                            text = "Remove",
                            modifier = Modifier.clickable {
                                selectedGroupMembers.removeAll { it.uid == member.uid }
                                if (status?.startsWith("Added ") == true) {
                                    status = null
                                }
                            }
                        )
                    }
                }
            }
        }

        item("group_create_button") {
            Button(
                enabled = groupTitle.trim().isNotEmpty() &&
                        selectedGroupMembers.isNotEmpty() &&
                        !working &&
                        isSignedIn,
                onClick = {
                    scope.launch {
                        try {
                            working = true
                            status = null

                            val currentUid = auth.currentUser?.uid
                                ?: throw Exception("User not signed in")

                            val allMembers = (listOf(currentUid) + selectedGroupMembers.map { it.uid })
                                .distinct()

                            if (allMembers.size < 2) {
                                throw Exception("Add at least one other member")
                            }

                            val newChatId = repo.openOrCreateGroupChat(
                                createdBy = currentUid,
                                memberIds = allMembers,
                                title = groupTitle.trim()
                            )

                            val actualTitle = repo.getChatTitle(newChatId)

                            refreshChats()
                            selectedChatId = newChatId
                            selectedChatTitle = actualTitle

                            groupTitle = ""
                            selectedGroupMembers.clear()
                            searchQuery = ""
                            searchResults.clear()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            status = e.message ?: "Failed to create group"
                        } finally {
                            working = false
                        }
                    }
                }
            ) {
                Text(if (working) "Working..." else "Create Group")
            }

            if (status != null) {
                Spacer(Modifier.height(12.dp))
                Text("Status: $status")
            }
        }

        item("saved_chats_header") {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            Text(
                text = "Saved Chats",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        if (myChats.isEmpty()) {
            item("no_chats") {
                Text("No chats yet.")
            }
        } else {
            items(myChats, key = { "chat_${it.id}" }) { chat ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    var menuExpanded by remember { mutableStateOf(false) }

                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        selectedChatId = chat.id
                                        selectedChatTitle = chat.title
                                    }
                            ) {
                                Text(
                                    text = chat.title,
                                    style = MaterialTheme.typography.titleMedium
                                )

                                Spacer(Modifier.height(4.dp))

                                Text(
                                    text = chat.lastMessageText ?: "No messages yet",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }

                            Box {
                                IconButton(
                                    onClick = { menuExpanded = true }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = "Chat options"
                                    )
                                }

                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Delete") },
                                        onClick = {
                                            menuExpanded = false
                                            scope.launch {
                                                try {
                                                    val uid = auth.currentUser?.uid ?: return@launch
                                                    repo.hideChatForUser(chat.id, uid)
                                                    refreshChats()
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    status = e.message ?: "Failed to delete chat"
                                                }
                                            }
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
}