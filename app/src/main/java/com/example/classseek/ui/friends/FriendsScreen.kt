package com.example.classseek.ui.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.classseek.data.ChatListItem
import com.example.classseek.data.ChatRepository
import com.example.classseek.ui.chat.ChatScreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun FriendsScreen(
    modifier: Modifier = Modifier,
    repo: ChatRepository = remember { ChatRepository(FirebaseFirestore.getInstance()) },
    auth: FirebaseAuth = remember { FirebaseAuth.getInstance() }
) {
    val scope = rememberCoroutineScope()

    var otherUid by remember { mutableStateOf("") }
    var chatTitle by remember { mutableStateOf("") }
    var selectedChatId by remember { mutableStateOf<String?>(null) }
    var selectedChatTitle by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    var myUid by remember { mutableStateOf<String?>(null) }
    var isSignedIn by remember { mutableStateOf(false) }

    val myChats = remember { mutableStateListOf<ChatListItem>() }

    suspend fun refreshChats() {
        val uid = auth.currentUser?.uid ?: return
        val chats = repo.getMyChats(uid)
        myChats.clear()
        myChats.addAll(chats)
    }

    LaunchedEffect(Unit) {
        try {
            if (auth.currentUser == null) {
                auth.signInAnonymously().await()
            }
            myUid = auth.currentUser?.uid
            isSignedIn = auth.currentUser != null
            refreshChats()
        } catch (e: Exception) {
            status = "Auth error: ${e.message}"
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("=== DEBUG INFO ===")
        Text("Signed in: $isSignedIn")
        Text("My UID: ${myUid ?: "NULL"}")

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Create DM",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = otherUid,
            onValueChange = { otherUid = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Enter friend's UID") },
            singleLine = true,
            enabled = !working
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = chatTitle,
            onValueChange = { chatTitle = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Chat name (optional)") },
            singleLine = true,
            enabled = !working
        )

        Spacer(Modifier.height(12.dp))

        Button(
            enabled = otherUid.trim().isNotEmpty() && !working && isSignedIn,
            onClick = {
                scope.launch {
                    try {
                        working = true
                        status = null

                        val uid = auth.currentUser?.uid
                            ?: throw Exception("User not signed in")

                        val targetUid = otherUid.trim()
                        val defaultTitle = "Chat ${myChats.size + 1}"
                        val finalTitle = if (chatTitle.trim().isBlank()) {
                            defaultTitle
                        } else {
                            chatTitle.trim()
                        }

                        val createdChatId = repo.openOrCreateDm(
                            uidA = uid,
                            uidB = targetUid,
                            title = finalTitle
                        )

                        refreshChats()
                        selectedChatId = createdChatId
                        selectedChatTitle = finalTitle
                        chatTitle = ""
                        otherUid = ""

                    } catch (e: Exception) {
                        status = e.message ?: "Failed to create DM"
                    } finally {
                        working = false
                    }
                }
            }
        ) {
            Text(if (working) "Working…" else "Create DM")
        }

        if (status != null) {
            Spacer(Modifier.height(12.dp))
            Text("Status: $status")
        }

        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        Text(
            text = "Saved Chats",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(Modifier.height(8.dp))

        if (myChats.isEmpty()) {
            Text("No chats yet.")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(myChats, key = { it.id }) { chat ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                selectedChatId = chat.id
                                selectedChatTitle = chat.title
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
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
                    }
                }
            }
        }
    }
}