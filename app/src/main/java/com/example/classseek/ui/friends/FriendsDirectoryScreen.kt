
package com.example.classseek.ui.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background

@Composable
fun FriendsDirectoryScreen(
    friends: List<UserSearchItem>,
    incomingRequests: List<UserSearchItem>,
    outgoingRequests: List<UserSearchItem>,
    searchResults: List<UserSearchItem>,
    onBack: () -> Unit,
    onFriendClick: (UserSearchItem) -> Unit,
    onIncomingClick: (UserSearchItem) -> Unit,
    onOutgoingClick: (UserSearchItem) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onSendFriendRequest: (UserSearchItem) -> Unit
) {
    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.padding(4.dp))
            Text(
                text = "Friends",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                onSearchQueryChanged(it)
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search friends or users") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (query.isNotBlank()) {
                item {
                    SearchSectionCard(
                        title = "Add Friends",
                        items = searchResults,
                        emptyText = "No users found",
                        onItemClick = onFriendClick,
                        onActionClick = onSendFriendRequest
                    )
                }
            }

            item {
                SectionCard(
                    title = "Incoming Requests",
                    items = incomingRequests,
                    emptyText = "No incoming friend requests",
                    onItemClick = onIncomingClick
                )
            }

            item {
                SectionCard(
                    title = "Sent Requests",
                    items = outgoingRequests,
                    emptyText = "No sent friend requests",
                    onItemClick = onOutgoingClick
                )
            }

            item {
                SectionCard(
                    title = "All Friends",
                    items = friends,
                    emptyText = "No friends added yet",
                    onItemClick = onFriendClick
                )
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    items: List<UserSearchItem>,
    emptyText: String,
    onItemClick: (UserSearchItem) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            if (items.isEmpty()) {
                Text(emptyText)
            } else {
                items.forEachIndexed { index, item ->
                    Column(
                        modifier = Modifier.fillMaxWidth().clickable { onItemClick(item) }.padding(vertical = 10.dp)
                    ) {
                        Text(
                            text = item.displayName.ifBlank { item.name.ifBlank { item.email } },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = item.major.ifBlank { item.email },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    if (index != items.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchSectionCard(
    title: String,
    items: List<UserSearchItem>,
    emptyText: String,
    onItemClick: (UserSearchItem) -> Unit,
    onActionClick: (UserSearchItem) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            if (items.isEmpty()) {
                Text(emptyText)
            } else {
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).clickable { onItemClick(item) }
                        ) {
                            Text(
                                text = item.displayName.ifBlank { item.name.ifBlank { item.email } },
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = item.major.ifBlank { item.email },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Button(onClick = { onActionClick(item) }) {
                            Text("Add")
                        }
                    }

                    if (index != items.lastIndex) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
