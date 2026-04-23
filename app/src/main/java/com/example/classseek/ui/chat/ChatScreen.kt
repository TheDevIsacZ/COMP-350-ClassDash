package com.example.classseek.ui.chat

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.classseek.data.ChatInfo
import com.example.classseek.data.ChatRepository
import com.example.classseek.data.GroupMember
import com.example.classseek.data.Message
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.foundation.layout.FlowRow

data class ChatUserProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val profilePictureUrl: String
)

private val ChatHeaderAccent = Color(0xFF8B7CFF)
private val ChatHeaderDivider = Color(0xFFE6E1FF)

@Composable
private fun ProfileAvatar(
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
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun GroupChatHeader(
    title: String,
    onBack: () -> Unit,
    onManage: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White,
            navigationIconContentColor = ChatHeaderAccent,
            actionIconContentColor = ChatHeaderAccent,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = ChatHeaderAccent.copy(alpha = 0.14f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = title.trim().take(1).ifBlank { "G" }.uppercase(),
                            color = ChatHeaderAccent,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        actions = {
            IconButton(onClick = onManage) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options"
                )
            }
        }
    )
    HorizontalDivider(color = ChatHeaderDivider, thickness = 1.dp)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DirectMessageHeader(
    title: String,
    profilePictureUrl: String,
    onBack: () -> Unit
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White,
            navigationIconContentColor = ChatHeaderAccent,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(
                    imageUrl = profilePictureUrl,
                    label = title,
                    modifier = Modifier.size(34.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = "Back",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    )
    HorizontalDivider(color = ChatHeaderDivider, thickness = 1.dp)
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onLocationClick: (LatLng, String) -> Unit = { _, _ -> },
    repo: ChatRepository = remember { ChatRepository(FirebaseFirestore.getInstance()) },
    auth: FirebaseAuth = remember { FirebaseAuth.getInstance() }
) {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val db = remember { FirebaseFirestore.getInstance() }
    val myUid = auth.currentUser?.uid

    val messages = remember(chatId) { mutableStateListOf<Message>() }
    val listState = rememberLazyListState()

    var input by remember(chatId) { mutableStateOf("") }
    var error by remember(chatId) { mutableStateOf<String?>(null) }
    var sending by remember(chatId) { mutableStateOf(false) }

    var initialReadMarked by remember(chatId) { mutableStateOf(false) }
    var initialScrollDone by remember(chatId) { mutableStateOf(false) }
    var isChatVisible by remember(chatId) { mutableStateOf(false) }
    var lastMarkedIncomingMessageId by remember(chatId) { mutableStateOf<String?>(null) }

    var myLastReadMessageId by remember(chatId) { mutableStateOf<String?>(null) }

    var pendingScrollToMessageId by remember(chatId) { mutableStateOf<String?>(null) }
    var lastAutoScrolledToMessageId by remember(chatId) { mutableStateOf<String?>(null) }

    var myReadStateLoaded by remember(chatId) { mutableStateOf(false) }
    var hasSentMessageThisSession by remember(chatId) { mutableStateOf(false) }

    val memberLastReadByUid = remember(chatId) { mutableStateMapOf<String, String?>() }
    val userProfiles = remember(chatId) { mutableStateMapOf<String, ChatUserProfile>() }

    var chatInfo by remember(chatId) { mutableStateOf<ChatInfo?>(null) }
    var myRole by remember(chatId) { mutableStateOf<String?>(null) }
    var showManageDialog by remember(chatId) { mutableStateOf(false) }
    var groupMembers by remember(chatId) { mutableStateOf<List<GroupMember>>(emptyList()) }
    var memberSearchQuery by remember(chatId) { mutableStateOf("") }
    val memberSearchResults = remember(chatId) { mutableStateListOf<ChatUserProfile>() }
    var managingGroup by remember(chatId) { mutableStateOf(false) }
    var confirmDeleteGroup by remember(chatId) { mutableStateOf(false) }
    var confirmLeaveGroup by remember(chatId) { mutableStateOf(false) }
    var transferToUid by remember(chatId) { mutableStateOf<String?>(null) }

    val newestVisible = messages.firstOrNull()
    val myLatestMessage = messages.firstOrNull { it.senderId == myUid }
    val latestMyMessageId = myLatestMessage?.id

    fun normalizedRole(role: String?): String = when (role?.trim()?.lowercase()) {
        "host", "owner" -> "owner"
        "cohost" -> "cohost"
        else -> "member"
    }

    fun isGroupChat(): Boolean = chatInfo?.type == "group"
    fun canManageMembers(): Boolean {
        val role = normalizedRole(myRole)
        return role == "owner" || role == "cohost"
    }
    fun canEditRoles(): Boolean = normalizedRole(myRole) == "owner"
    fun canDeleteGroup(): Boolean = normalizedRole(myRole) == "owner"
    fun canRemoveMember(member: GroupMember): Boolean {
        val myRoleNorm = normalizedRole(myRole)
        val memberRoleNorm = normalizedRole(member.role)

        if (member.uid == myUid) return false

        return when (myRoleNorm) {
            "owner" -> memberRoleNorm == "member" || memberRoleNorm == "cohost"
            "cohost" -> memberRoleNorm == "member"
            else -> false
        }
    }

    fun userLabel(uid: String): String {
        val profile = userProfiles[uid]
        return when {
            uid == myUid -> "You"
            profile?.displayName?.isNotBlank() == true -> profile.displayName
            profile?.email?.isNotBlank() == true -> profile.email.substringBefore("@")
            else -> "User"
        }
    }

    fun hasUserSeenMessage(lastReadMessageId: String?, targetMessageId: String): Boolean {
        if (lastReadMessageId == null) return false

        val lastReadIndex = messages.indexOfFirst { it.id == lastReadMessageId }
        val targetIndex = messages.indexOfFirst { it.id == targetMessageId }

        if (lastReadIndex == -1 || targetIndex == -1) return false

        return lastReadIndex <= targetIndex
    }

    val directMessagePartnerUid = remember(chatInfo?.memberIds, myUid) {
        chatInfo?.memberIds?.firstOrNull { it != myUid }
    }
    val directMessagePartnerProfile = directMessagePartnerUid?.let { userProfiles[it] }
    val directMessageTitle = directMessagePartnerProfile?.displayName
        ?.takeIf { it.isNotBlank() }
        ?: directMessagePartnerProfile?.email
            ?.substringBefore("@")
            ?.takeIf { it.isNotBlank() }
        ?: title
    val directMessageAvatarUrl = directMessagePartnerProfile?.profilePictureUrl.orEmpty()

    val latestSeen = remember(
        latestMyMessageId,
        messages.size,
        memberLastReadByUid.toMap()
    ) {
        val latestId = latestMyMessageId ?: return@remember false

        memberLastReadByUid.any { (uid, lastReadId) ->
            uid != myUid && hasUserSeenMessage(lastReadId, latestId)
        }
    }

    suspend fun loadMissingUserProfiles(uids: List<String>) {
        val missing = uids
            .filter { it.isNotBlank() }
            .distinct()
            .filter { uid ->
                val existing = userProfiles[uid]
                existing == null || (
                        existing.displayName.isBlank() &&
                                existing.email.isBlank() &&
                                existing.profilePictureUrl.isBlank()
                        )
            }

        for (uid in missing) {
            try {
                val doc = db.collection("users")
                    .document(uid)
                    .get()
                    .await()

                val displayName = doc.getString("displayName")?.trim().orEmpty()
                    .ifBlank { doc.getString("name")?.trim().orEmpty() }

                val email = doc.getString("email")?.trim().orEmpty()
                val profilePictureUrl = doc.getString("profilePictureUrl")?.trim().orEmpty()

                if (displayName.isNotBlank() || email.isNotBlank() || profilePictureUrl.isNotBlank()) {
                    userProfiles[uid] = ChatUserProfile(
                        uid = uid,
                        displayName = displayName,
                        email = email,
                        profilePictureUrl = profilePictureUrl
                    )
                }
            } catch (_: Exception) {
            }
        }
    }

    suspend fun refreshGroupMeta() {
        val uid = myUid ?: return
        val info = repo.getChatInfo(chatId)
        chatInfo = info
        myRole = repo.getMyRole(chatId, uid)
        if (info.type == "group") {
            groupMembers = repo.getGroupMembers(chatId)
        } else {
            repo.refreshDmInboxMetadata(chatId)
            groupMembers = emptyList()
        }
    }

    suspend fun searchUsersForGroup(query: String): List<ChatUserProfile> {
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
            .distinctBy { it.id }
            .mapNotNull { doc ->
                val uid = doc.id
                val displayName = doc.getString("displayName")?.trim().orEmpty()
                    .ifBlank { doc.getString("name")?.trim().orEmpty() }
                val email = doc.getString("email")?.trim().orEmpty()
                val profilePictureUrl = doc.getString("profilePictureUrl")?.trim().orEmpty()

                if (email.isBlank()) return@mapNotNull null

                ChatUserProfile(
                    uid = uid,
                    displayName = displayName,
                    email = email,
                    profilePictureUrl = profilePictureUrl
                )
            }
            .filter { candidate ->
                candidate.uid != myUid &&
                        groupMembers.none { it.uid == candidate.uid }
            }
            .sortedWith(
                compareBy<ChatUserProfile> {
                    when {
                        it.email.lowercase() == normalized -> 0
                        it.email.lowercase().startsWith(normalized) -> 1
                        it.displayName.lowercase().startsWith(normalized) -> 2
                        else -> 3
                    }
                }.thenBy {
                    it.displayName.ifBlank { it.email }.lowercase()
                }
            )
    }

    DisposableEffect(lifecycleOwner, chatId) {
        val observer = LifecycleEventObserver { _, event ->
            isChatVisible = event == Lifecycle.Event.ON_RESUME
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(chatId, myUid) {
        try {
            refreshGroupMeta()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Failed to load chat info"
        }
    }

    val senderIdsKey = messages.map { it.senderId }.distinct().sorted().joinToString("|")
    val memberIdsKey = memberLastReadByUid.keys.sorted().joinToString("|")
    val groupMemberIdsKey = groupMembers.map { it.uid }.distinct().sorted().joinToString("|")

    LaunchedEffect(chatId, senderIdsKey, memberIdsKey, groupMemberIdsKey) {
        try {
            val allUids = buildList {
                addAll(messages.map { it.senderId })
                addAll(memberLastReadByUid.keys)
                addAll(groupMembers.map { it.uid })
            }.distinct()

            loadMissingUserProfiles(allUids)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(memberSearchQuery, showManageDialog, chatId, groupMemberIdsKey) {
        if (!showManageDialog) {
            memberSearchResults.clear()
            return@LaunchedEffect
        }

        val query = memberSearchQuery.trim()
        if (query.isBlank()) {
            memberSearchResults.clear()
            return@LaunchedEffect
        }

        try {
            delay(250)
            val results = searchUsersForGroup(query)
            memberSearchResults.clear()
            memberSearchResults.addAll(results)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: "Failed to search users"
        }
    }

    LaunchedEffect(chatId, myUid, messages.size) {
        val uid = myUid ?: return@LaunchedEffect
        val newest = newestVisible ?: return@LaunchedEffect

        if (!initialReadMarked) {
            try {
                repo.updateMyLastRead(chatId, uid, newest.id)
                lastMarkedIncomingMessageId = if (newest.senderId != uid) newest.id else null
                initialReadMarked = true
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(chatId, myUid, isChatVisible, newestVisible?.id) {
        val uid = myUid ?: return@LaunchedEffect
        val newest = newestVisible ?: return@LaunchedEffect

        if (
            isChatVisible &&
            newest.senderId != uid &&
            newest.id != lastMarkedIncomingMessageId
        ) {
            try {
                repo.updateMyLastRead(chatId, uid, newest.id)
                lastMarkedIncomingMessageId = newest.id
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(
        messages.size,
        myLastReadMessageId,
        myReadStateLoaded,
        pendingScrollToMessageId,
        hasSentMessageThisSession,
        chatId
    ) {
        if (initialScrollDone) return@LaunchedEffect
        if (messages.isEmpty()) return@LaunchedEffect
        if (!myReadStateLoaded) return@LaunchedEffect
        if (pendingScrollToMessageId != null) return@LaunchedEffect
        if (hasSentMessageThisSession) return@LaunchedEffect

        val targetIndex = when {
            myLastReadMessageId != null -> {
                val idx = messages.indexOfFirst { it.id == myLastReadMessageId }
                if (idx >= 0) (idx - 1).coerceAtLeast(0) else 0
            }
            else -> 0
        }

        awaitFrame()
        awaitFrame()
        listState.scrollToItem(targetIndex)
        initialScrollDone = true
    }

    LaunchedEffect(messages.size, pendingScrollToMessageId, chatId) {
        val targetMessageId = pendingScrollToMessageId ?: return@LaunchedEffect
        if (messages.isEmpty()) return@LaunchedEffect
        if (targetMessageId == lastAutoScrolledToMessageId) return@LaunchedEffect

        val targetIndex = messages.indexOfFirst { it.id == targetMessageId }
        if (targetIndex < 0) return@LaunchedEffect

        awaitFrame()
        awaitFrame()
        listState.scrollToItem(targetIndex)

        awaitFrame()
        listState.scrollToItem(targetIndex)

        lastAutoScrolledToMessageId = targetMessageId
        pendingScrollToMessageId = null
        initialScrollDone = true
    }

    DisposableEffect(chatId) {
        val messagesReg = repo.listenMessagesRealtime(
            chatId = chatId,
            pageSize = 50,
            onSnapshot = { newMessages, _ ->
                messages.clear()
                messages.addAll(newMessages)
                error = null
            },
            onError = { e ->
                error = e.message ?: "Listener error"
            }
        )

        onDispose { messagesReg.remove() }
    }

    DisposableEffect(chatId) {
        val reg = db.collection("chats")
            .document(chatId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    error = e.message ?: "Chat listener error"
                    return@addSnapshotListener
                }

                val doc = snapshot ?: return@addSnapshotListener
                if (!doc.exists()) return@addSnapshotListener

                chatInfo = ChatInfo(
                    id = doc.id,
                    type = doc.getString("type") ?: "dm",
                    title = doc.getString("title") ?: title,
                    createdBy = doc.getString("createdBy") ?: "",
                    memberIds = (doc.get("memberIds") as? List<*>)
                        ?.filterIsInstance<String>()
                        .orEmpty()
                )
            }

        onDispose { reg.remove() }
    }

    DisposableEffect(chatId, myUid, showManageDialog) {
        if (myUid == null) {
            onDispose { }
        } else {
            val reg = db.collection("chats")
                .document(chatId)
                .collection("members")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        error = e.message ?: "Member listener error"
                        return@addSnapshotListener
                    }

                    val docs = snapshot?.documents.orEmpty()

                    memberLastReadByUid.clear()
                    for (doc in docs) {
                        memberLastReadByUid[doc.id] = doc.getString("lastReadMessageId")
                    }

                    myLastReadMessageId = memberLastReadByUid[myUid]
                    myReadStateLoaded = true
                    myRole = docs.firstOrNull { it.id == myUid }?.getString("role")

                    if (showManageDialog && (chatInfo?.type == "group" || docs.isNotEmpty())) {
                        scope.launch {
                            try {
                                groupMembers = repo.getGroupMembers(chatId)
                                val liveMemberIds = docs.map { it.id }
                                chatInfo = chatInfo?.copy(memberIds = liveMemberIds)
                                    ?: repo.getChatInfo(chatId)
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (ex: Exception) {
                                error = ex.message ?: "Failed to refresh group members"
                            }
                        }
                    }
                }

            onDispose { reg.remove() }
        }
    }

    if (showManageDialog && isGroupChat()) {
        AlertDialog(
            onDismissRequest = {
                if (!managingGroup) {
                    showManageDialog = false
                    memberSearchQuery = ""
                    memberSearchResults.clear()
                }
            },
            title = { Text("Manage Group") },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item("group_manage_header") {
                        Column {
                            Text("Your role: ${myRole ?: "member"}")

                            if (canManageMembers()) {
                                Spacer(Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = memberSearchQuery,
                                    onValueChange = { memberSearchQuery = it },
                                    label = { Text("Search users by name or email") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    enabled = !managingGroup
                                )

                                if (memberSearchQuery.isNotBlank()) {
                                    Spacer(Modifier.height(8.dp))

                                    if (memberSearchResults.isEmpty()) {
                                        Text(
                                            text = "No users found.",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    } else {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            memberSearchResults.forEach { candidate ->
                                                Card(
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(10.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        ProfileAvatar(
                                                            imageUrl = candidate.profilePictureUrl,
                                                            label = candidate.displayName.ifBlank { candidate.email },
                                                            modifier = Modifier.size(36.dp)
                                                        )

                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = candidate.displayName.ifBlank { candidate.email },
                                                                style = MaterialTheme.typography.bodyMedium
                                                            )
                                                            if (candidate.displayName.isNotBlank()) {
                                                                Text(
                                                                    text = candidate.email,
                                                                    style = MaterialTheme.typography.bodySmall
                                                                )
                                                            }
                                                        }

                                                        Button(
                                                            enabled = !managingGroup,
                                                            onClick = {
                                                                scope.launch {
                                                                    try {
                                                                        managingGroup = true
                                                                        repo.addGroupMember(
                                                                            chatId = chatId,
                                                                            actingUid = myUid ?: throw Exception("Not signed in"),
                                                                            newMemberUid = candidate.uid
                                                                        )

                                                                        memberSearchQuery = ""
                                                                        memberSearchResults.clear()
                                                                        refreshGroupMeta()
                                                                    } catch (e: CancellationException) {
                                                                        throw e
                                                                    } catch (e: Exception) {
                                                                        error = e.message ?: "Failed to add member"
                                                                    } finally {
                                                                        managingGroup = false
                                                                    }
                                                                }
                                                            }
                                                        ) {
                                                            Text("Add")
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

                    items(groupMembers, key = { "member_${it.uid}" }) { member ->
                        val label = when {
                            member.displayName.isNotBlank() -> member.displayName
                            member.email.isNotBlank() -> member.email
                            else -> member.uid
                        }

                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ProfileAvatar(
                                    imageUrl = member.profilePictureUrl,
                                    label = label,
                                    modifier = Modifier.size(36.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(label)
                                    if (member.email.isNotBlank()) {
                                        Text(
                                            member.email,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Text(
                                        "Role: ${member.role}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (canEditRoles() && member.uid != myUid && normalizedRole(member.role) != "owner") {
                                    Button(
                                        enabled = !managingGroup && normalizedRole(member.role) != "cohost",
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    managingGroup = true
                                                    repo.updateGroupMemberRole(
                                                        chatId = chatId,
                                                        actingUid = myUid ?: throw Exception("Not signed in"),
                                                        targetUid = member.uid,
                                                        newRole = "cohost"
                                                    )
                                                    refreshGroupMeta()
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    error = e.message ?: "Failed to promote member"
                                                } finally {
                                                    managingGroup = false
                                                }
                                            }
                                        }
                                    ) {
                                        Text("Make cohost")
                                    }

                                    Button(
                                        enabled = !managingGroup && normalizedRole(member.role) == "cohost",
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    managingGroup = true
                                                    repo.updateGroupMemberRole(
                                                        chatId = chatId,
                                                        actingUid = myUid ?: throw Exception("Not signed in"),
                                                        targetUid = member.uid,
                                                        newRole = "member"
                                                    )
                                                    refreshGroupMeta()
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    error = e.message ?: "Failed to demote cohost"
                                                } finally {
                                                    managingGroup = false
                                                }
                                            }
                                        }
                                    ) {
                                        Text("Remove cohost")
                                    }

                                    Button(
                                        enabled = !managingGroup,
                                        onClick = {
                                            transferToUid = member.uid
                                        }
                                    ) {
                                        Text("Transfer Ownership")
                                    }
                                }

                                if (canRemoveMember(member)) {
                                    Button(
                                        enabled = !managingGroup,
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    managingGroup = true
                                                    repo.removeGroupMember(
                                                        chatId = chatId,
                                                        actingUid = myUid ?: throw Exception("Not signed in"),
                                                        targetUid = member.uid
                                                    )
                                                    refreshGroupMeta()
                                                } catch (e: CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    error = e.message ?: "Failed to remove member"
                                                } finally {
                                                    managingGroup = false
                                                }
                                            }
                                        }
                                    ) {
                                        Text("Remove")
                                    }
                                }
                            }

                            //dividers to make space for the Delete group chat functionality - currently removed since it is not implemented

                            // Spacer(Modifier.height(8.dp))
                            // HorizontalDivider()
                        }
                    }

                    item("leave_group_section") {
                        Spacer(Modifier.height(12.dp))
                        Button(
                            enabled = !managingGroup,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { confirmLeaveGroup = true }
                        ) {
                            Text("Leave Group")
                        }
                    }

                    /**
                     * Currently this feature is not implemented
                     *
                    if (canDeleteGroup()) {
                    item("delete_group_section") {
                    Spacer(Modifier.height(12.dp))
                    Button(
                    enabled = !managingGroup,
                    onClick = { confirmDeleteGroup = true }
                    ) {
                    Text("Delete Group Permanently")
                    }
                    }
                    }
                     */
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showManageDialog = false
                        memberSearchQuery = ""
                        memberSearchResults.clear()
                    },
                    enabled = !managingGroup
                ) {
                    Text("Close")
                }
            },
            dismissButton = {}
        )
    }
    /**
     * Currently this feature is not implemented
     *
    if (confirmDeleteGroup) {
    AlertDialog(
    onDismissRequest = {
    if (!managingGroup) confirmDeleteGroup = false
    },
    title = { Text("Delete group permanently?") },
    text = {
    Text("This will delete the group for everyone and cannot be undone.")
    },
    confirmButton = {
    TextButton(
    enabled = !managingGroup,
    onClick = {
    scope.launch {
    try {
    managingGroup = true
    repo.deleteGroupChatPermanently(
    chatId = chatId,
    actingUid = myUid ?: throw Exception("Not signed in")
    )
    confirmDeleteGroup = false
    showManageDialog = false
    onBack()
    } catch (e: CancellationException) {
    throw e
    } catch (e: Exception) {
    error = e.message ?: "Failed to delete group"
    } finally {
    managingGroup = false
    }
    }
    }
    ) {
    Text("Delete")
    }

    },
    dismissButton = {
    TextButton(
    enabled = !managingGroup,
    onClick = { confirmDeleteGroup = false }
    ) {
    Text("Cancel")
    }
    }
    )
    }
     */

    if (confirmLeaveGroup) {
        AlertDialog(
            onDismissRequest = {
                if (!managingGroup) confirmLeaveGroup = false
            },
            title = { Text("Leave group?") },
            text = {
                if (normalizedRole(myRole) == "owner") {
                    Text("As the owner, you must transfer ownership to another member before leaving.")
                } else {
                    Text("Are you sure you want to leave this group chat?")
                }
            },
            confirmButton = {
                if (normalizedRole(myRole) != "owner") {
                    TextButton(
                        enabled = !managingGroup,
                        onClick = {
                            scope.launch {
                                try {
                                    managingGroup = true
                                    repo.leaveGroupChat(
                                        chatId = chatId,
                                        myUid = myUid ?: throw Exception("Not signed in")
                                    )
                                    confirmLeaveGroup = false
                                    showManageDialog = false
                                    onBack()
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    error = e.message ?: "Failed to leave group"
                                } finally {
                                    managingGroup = false
                                }
                            }
                        }
                    ) {
                        Text("Leave")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !managingGroup,
                    onClick = { confirmLeaveGroup = false }
                ) {
                    Text(if (normalizedRole(myRole) == "owner") "OK" else "Cancel")
                }
            }
        )
    }

    if (transferToUid != null) {
        val targetMember = groupMembers.find { it.uid == transferToUid }
        val targetName = targetMember?.displayName?.ifBlank { targetMember.email } ?: "this member"

        AlertDialog(
            onDismissRequest = {
                if (!managingGroup) transferToUid = null
            },
            title = { Text("Transfer Ownership?") },
            text = {
                Text("Are you sure you want to transfer ownership to $targetName? You will become a cohost.")
            },
            confirmButton = {
                TextButton(
                    enabled = !managingGroup,
                    onClick = {
                        scope.launch {
                            try {
                                managingGroup = true
                                repo.transferOwnership(
                                    chatId = chatId,
                                    actingUid = myUid ?: throw Exception("Not signed in"),
                                    newOwnerUid = transferToUid!!
                                )
                                transferToUid = null
                                refreshGroupMeta()
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message ?: "Failed to transfer ownership"
                            } finally {
                                managingGroup = false
                            }
                        }
                    }
                ) {
                    Text("Transfer")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !managingGroup,
                    onClick = { transferToUid = null }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .imePadding()
        ) {
            if (isGroupChat()) {
                GroupChatHeader(
                    title = chatInfo?.title ?: title,
                    onBack = onBack,
                    onManage = { showManageDialog = true }
                )
            } else {
                DirectMessageHeader(
                    title = directMessageTitle,
                    profilePictureUrl = directMessageAvatarUrl,
                    onBack = onBack
                )
            }

            if (error != null) {
                Text(
                    text = "Error: $error",
                    modifier = Modifier.padding(12.dp)
                )
            }

            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 72.dp)
            ) {
                items(
                    items = messages,
                    key = { it.id }
                ) { msg ->
                    val isMine = (myUid != null && msg.senderId == myUid)

                    val seenByProfiles = memberLastReadByUid
                        .filter { (uid, lastReadId) ->
                            uid != myUid && hasUserSeenMessage(lastReadId, msg.id)
                        }
                        .keys
                        .mapNotNull { uid -> userProfiles[uid] }
                        .distinctBy { it.uid }

                    MessageRow(
                        msg = msg,
                        isMine = isMine,
                        senderLabel = if (isMine) "You" else userLabel(msg.senderId),
                        senderAvatarUrl = if (isMine) "" else (userProfiles[msg.senderId]?.profilePictureUrl ?: ""),
                        showReceipt = msg.id == latestMyMessageId,
                        receiptText = if (msg.id == latestMyMessageId) {
                            when {
                                msg.hasPendingWrites -> "Sending..."
                                latestSeen -> "Seen"
                                else -> "Sent"
                            }
                        } else {
                            null
                        },
                        seenByProfiles = if (isMine) seenByProfiles else emptyList(),
                        onLocationClick = onLocationClick
                    )
                }
            }

            HorizontalDivider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Message") },
                    singleLine = false,
                    enabled = myUid != null && !sending,
                    minLines = 1,
                    maxLines = 20
                )

                Spacer(Modifier.width(8.dp))

                Button(
                    enabled = myUid != null && input.trim().isNotEmpty() && !sending,
                    onClick = {
                        val uid = myUid ?: return@Button
                        val text = input.trim()

                        sending = true
                        hasSentMessageThisSession = true
                        initialScrollDone = true

                        scope.launch {
                            try {
                                val sentMessageId = repo.sendTextMessage(
                                    chatId = chatId,
                                    senderId = uid,
                                    text = text
                                )

                                pendingScrollToMessageId = sentMessageId

                                try {
                                    repo.updateMyLastRead(chatId, uid, sentMessageId)
                                    myLastReadMessageId = sentMessageId
                                } catch (_: Exception) {
                                }

                                input = ""
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message ?: "Send failed"
                                pendingScrollToMessageId = null
                            } finally {
                                sending = false
                            }
                        }
                    }
                ) {
                    Text(if (sending) "Sending…" else "Send")
                }
            }
        }
    }
}

@Composable
private fun MessageRow(
    msg: Message,
    isMine: Boolean,
    senderLabel: String,
    senderAvatarUrl: String,
    showReceipt: Boolean = false,
    receiptText: String? = null,
    seenByProfiles: List<ChatUserProfile> = emptyList(),
    onLocationClick: (LatLng, String) -> Unit = { _, _ -> }
) {
    if (msg.type == "system") {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                tonalElevation = 0.dp,
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(999.dp)
            ) {
                Text(
                    text = msg.text.orEmpty(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isMine) {
                ProfileAvatar(
                    imageUrl = senderAvatarUrl,
                    label = senderLabel,
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(
                horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
            ) {
                Surface(
                    tonalElevation = 1.dp,
                    shape = MaterialTheme.shapes.medium,
                    color = if (msg.type == "location") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ) {
                    Column(Modifier.padding(10.dp)) {
                        if (msg.type == "location" && msg.latitude != null && msg.longitude != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = msg.locationName ?: "Shared Location",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Tap to view on map",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.clickable {
                                            onLocationClick(LatLng(msg.latitude, msg.longitude), msg.locationName ?: "Shared Location")
                                        }
                                    )
                                }
                            }
                        } else if (msg.type == "event") {
                            Column {
                                Text(
                                    text = "📅 ${msg.eventTitle ?: "Event"}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (!msg.eventStart.isNullOrBlank()) {
                                    Text("🕒 ${msg.eventStart} → ${msg.eventEnd ?: ""}", style = MaterialTheme.typography.bodySmall)
                                }
                                if (!msg.eventLocation.isNullOrBlank()) {
                                    Text("📍 ${msg.eventLocation}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        } else {
                            Text(msg.text ?: "[${msg.type}]")
                        }
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = senderLabel,
                    style = MaterialTheme.typography.labelSmall
                )

                if (isMine && showReceipt && receiptText != null) {
                    Text(
                        text = receiptText,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                if (isMine && seenByProfiles.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        seenByProfiles.take(6).forEach { profile ->
                            ProfileAvatar(
                                imageUrl = profile.profilePictureUrl,
                                label = profile.displayName.ifBlank {
                                    profile.email.ifBlank { "?" }
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (seenByProfiles.size > 6) {
                            Text(
                                text = "+${seenByProfiles.size - 6}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
