package com.example.classseek.ui.friends

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    var chatId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    // If we already created a chat, show the chat screen
    if (chatId != null) {
        ChatScreen(
            chatId = chatId!!,
            onBack = { chatId = null }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Create DM", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = otherUid,
            onValueChange = { otherUid = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Enter friend's UID") },
            singleLine = true,
            enabled = !working
        )

        Spacer(Modifier.height(12.dp))

        Button(
            enabled = otherUid.trim().isNotEmpty() && !working,
            onClick = {
                scope.launch {
                    try {
                        working = true
                        status = null

                        // Ensure we are signed in (anonymous for now)
                        if (auth.currentUser == null) {
                            auth.signInAnonymously().await()
                        }
                        val myUid = auth.currentUser!!.uid
                        val targetUid = otherUid.trim()

                        val createdChatId = repo.openOrCreateDm(myUid, targetUid)
                        chatId = createdChatId
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
        Text(
            "Tip: UID is the Firebase Auth user id (uid). For testing, you can copy it from the Firebase Console users list."
        )
    }
}