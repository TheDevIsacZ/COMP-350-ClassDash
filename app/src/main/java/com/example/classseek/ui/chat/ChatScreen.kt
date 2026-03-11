package com.example.classseek.ui.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.classseek.data.ChatRepository
import com.example.classseek.data.Message
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    chatId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    repo: ChatRepository = remember { ChatRepository(FirebaseFirestore.getInstance()) },
    auth: FirebaseAuth = remember { FirebaseAuth.getInstance() }
) {
    val scope = rememberCoroutineScope()
    val myUid = auth.currentUser?.uid

    val messages = remember { mutableStateListOf<Message>() }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }

    // Start realtime listener
    DisposableEffect(chatId) {
        val reg = repo.listenMessagesRealtime(
            chatId = chatId,
            pageSize = 50,
            onSnapshot = { newMessages, _ ->
                // repo returns newest-first; show oldest-first
                messages.clear()
                messages.addAll(newMessages.asReversed())
                error = null
            },
            onError = { e -> error = e.message ?: "Listener error" }
        )
        onDispose { reg.remove() }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("DM Chat") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (error != null) {
                Text(
                    text = "Error: $error",
                    modifier = Modifier.padding(12.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageRow(
                        msg = msg,
                        isMine = (myUid != null && msg.senderId == myUid)
                    )
                }
            }

            Divider()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Message") },
                    singleLine = true,
                    enabled = myUid != null && !sending
                )

                Spacer(Modifier.width(8.dp))

                Button(
                    enabled = myUid != null && input.trim().isNotEmpty() && !sending,
                    onClick = {
                        val uid = myUid ?: return@Button
                        val text = input.trim()
                        sending = true
                        scope.launch {
                            try {
                                repo.sendTextMessage(chatId = chatId, senderId = uid, text = text)
                                input = ""
                            } catch (e: Exception) {
                                error = e.message ?: "Send failed"
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
private fun MessageRow(msg: Message, isMine: Boolean) {
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
    }
}