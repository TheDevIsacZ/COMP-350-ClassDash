package com.example.classseek.ui.chat

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.classseek.data.ChatRepository
import com.example.classseek.data.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ChatUserProfile(
    val uid: String,
    val displayName: String,
    val email: String,
    val profilePictureUrl: String
)

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
fun ChatScreen(
    chatId: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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

    val newestVisible = messages.firstOrNull()
    val myLatestMessage = messages.firstOrNull { it.senderId == myUid }
    val latestMyMessageId = myLatestMessage?.id

    fun userLabel(uid: String): String {
        val profile = userProfiles[uid]
        return when {
            uid == myUid -> "You"
            profile?.displayName?.isNotBlank() == true -> profile.displayName
            profile?.email?.isNotBlank() == true -> profile.email.substringBefore("@")
            else -> uid
        }
    }

    fun hasUserSeenMessage(lastReadMessageId: String?, targetMessageId: String): Boolean {
        if (lastReadMessageId == null) return false

        val lastReadIndex = messages.indexOfFirst { it.id == lastReadMessageId }
        val targetIndex = messages.indexOfFirst { it.id == targetMessageId }

        if (lastReadIndex == -1 || targetIndex == -1) return false

        // reverseLayout = true, so smaller index means newer.
        return lastReadIndex <= targetIndex
    }

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
                // Do not cache a blank profile.
                // Let a later effect retry.
            }
        }
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

    val senderIdsKey = messages.map { it.senderId }.distinct().sorted().joinToString("|")
    val memberIdsKey = memberLastReadByUid.keys.sorted().joinToString("|")

    LaunchedEffect(chatId, senderIdsKey, memberIdsKey) {
        try {
            val allUids = buildList {
                addAll(messages.map { it.senderId })
                addAll(memberLastReadByUid.keys)
            }.distinct()

            loadMissingUserProfiles(allUids)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
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

    DisposableEffect(chatId, myUid) {
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
                }

            onDispose { reg.remove() }
        }
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
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )

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
                        seenByProfiles = if (isMine) seenByProfiles else emptyList()
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
    seenByProfiles: List<ChatUserProfile> = emptyList()
) {
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
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(msg.text ?: "[${msg.type}]")
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