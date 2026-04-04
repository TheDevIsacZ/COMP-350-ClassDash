package com.example.classseek.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.classseek.data.ChatRepository
import com.example.classseek.data.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
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
    var otherUserLastReadMessageId by remember(chatId) { mutableStateOf<String?>(null) }

    var pendingScrollToMessageId by remember(chatId) { mutableStateOf<String?>(null) }
    var lastAutoScrolledToMessageId by remember(chatId) { mutableStateOf<String?>(null) }

    // Helps avoid initial-position scroll before my read-state listener has had a chance to respond.
    var myReadStateLoaded by remember(chatId) { mutableStateOf(false) }

    // Once the user has sent a message in this screen instance,
    // never allow the initial "scroll to last read" logic to run again.
    var hasSentMessageThisSession by remember(chatId) { mutableStateOf(false) }

    val newestVisible = messages.firstOrNull()
    val myLatestMessage = messages.firstOrNull { it.senderId == myUid }
    val latestMyMessageId = myLatestMessage?.id

    val latestSeen = remember(
        myLatestMessage?.id,
        myLatestMessage?.hasPendingWrites,
        otherUserLastReadMessageId
    ) {
        val latest = myLatestMessage
        latest != null &&
                !latest.hasPendingWrites &&
                otherUserLastReadMessageId == latest.id
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

    /**
     * Initial mark-read.
     */
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

    /**
     * While visible, mark newly arrived incoming messages as read.
     */
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

    /**
     * Initial snap to last-read position.
     *
     * IMPORTANT:
     * - Do not run if a send-scroll is pending.
     * - Do not run if the user has already sent a message in this session.
     * - Only run once.
     */
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
                if (idx >= 0) {
                    (idx - 1).coerceAtLeast(0)
                } else {
                    0
                }
            }
            else -> 0
        }

        awaitFrame()
        awaitFrame()
        listState.scrollToItem(targetIndex)
        initialScrollDone = true
    }

    /**
     * Sent-message snap.
     *
     * This has priority over everything else.
     */
    LaunchedEffect(messages.size, pendingScrollToMessageId, chatId) {
        val targetMessageId = pendingScrollToMessageId ?: return@LaunchedEffect
        if (messages.isEmpty()) return@LaunchedEffect
        if (targetMessageId == lastAutoScrolledToMessageId) return@LaunchedEffect

        val targetIndex = messages.indexOfFirst { it.id == targetMessageId }
        if (targetIndex < 0) return@LaunchedEffect

        awaitFrame()
        awaitFrame()
        listState.scrollToItem(targetIndex)

        // Extra pass for IME/layout changes
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
            val receiptReg = repo.listenToDmReadReceipt(
                chatId = chatId,
                myUid = myUid,
                onSnapshot = { state ->
                    otherUserLastReadMessageId = state.otherUserLastReadMessageId
                },
                onError = { e ->
                    error = e.message ?: "Read receipt listener error"
                }
            )

            onDispose { receiptReg.remove() }
        }
    }

    DisposableEffect(chatId, myUid) {
        if (myUid == null) {
            onDispose { }
        } else {
            val myReadReg = repo.listenToMyReadState(
                chatId = chatId,
                myUid = myUid,
                onSnapshot = { lastReadId ->
                    myLastReadMessageId = lastReadId
                    myReadStateLoaded = true
                },
                onError = { e ->
                    error = e.message ?: "My read state listener error"
                    myReadStateLoaded = true
                }
            )

            onDispose { myReadReg.remove() }
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
                    MessageRow(
                        msg = msg,
                        isMine = (myUid != null && msg.senderId == myUid),
                        showReceipt = msg.id == latestMyMessageId,
                        receiptText = if (msg.id == latestMyMessageId) {
                            when {
                                msg.hasPendingWrites -> "Sending..."
                                latestSeen -> "Seen"
                                else -> "Sent"
                            }
                        } else {
                            null
                        }
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

                                // Scroll to the exact message that was sent.
                                pendingScrollToMessageId = sentMessageId

                                // Keep my own read state aligned with my newest sent message too.
                                // This prevents late read-state updates from pointing to an older anchor.
                                try {
                                    repo.updateMyLastRead(chatId, uid, sentMessageId)
                                    myLastReadMessageId = sentMessageId
                                } catch (_: Exception) {
                                }

                                input = ""
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
    showReceipt: Boolean = false,
    receiptText: String? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
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
            text = if (isMine) "You" else msg.senderId,
            style = MaterialTheme.typography.labelSmall
        )

        if (isMine && showReceipt && receiptText != null) {
            Text(
                text = receiptText,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}