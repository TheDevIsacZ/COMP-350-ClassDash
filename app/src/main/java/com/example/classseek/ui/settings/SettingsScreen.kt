package com.example.classseek.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

@Composable
fun SettingsPage(
    modifier: Modifier = Modifier,
    auth: FirebaseAuth = remember { FirebaseAuth.getInstance() },
    db: FirebaseFirestore = remember { FirebaseFirestore.getInstance() }
) {
    var allowMessagesByFriendsOnly by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(auth.currentUser?.uid) {
        val uid = auth.currentUser?.uid ?: return@LaunchedEffect
        try {
            loading = true
            val doc = db.collection("users").document(uid).get().await()
            allowMessagesByFriendsOnly = doc.getBoolean("allowMessagesByFriendsOnly") == true
            status = null
        } catch (e: Exception) {
            status = e.message ?: "Failed to load settings"
        } finally {
            loading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Messaging privacy",
                    style = MaterialTheme.typography.titleMedium
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Allow Messages By Friends Only",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = if (allowMessagesByFriendsOnly) {
                                "Only users in your friends list can start a new direct message with you."
                            } else {
                                "Anyone can start a new direct message with you."
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    Switch(
                        checked = allowMessagesByFriendsOnly,
                        enabled = !loading,
                        onCheckedChange = { checked ->
                            allowMessagesByFriendsOnly = checked
                            val uid = auth.currentUser?.uid ?: return@Switch
                            db.collection("users")
                                .document(uid)
                                .update("allowMessagesByFriendsOnly", checked)
                                .addOnSuccessListener {
                                    status = "Saved"
                                }
                                .addOnFailureListener { e ->
                                    allowMessagesByFriendsOnly = !checked
                                    status = e.message ?: "Failed to save setting"
                                }
                        }
                    )
                }
            }
        }

        if (!status.isNullOrBlank()) {
            Text(status!!)
        }
    }
}
