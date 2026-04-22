package com.example.classseek.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

data class ChatListItem(
    val id: String = "",
    val title: String = "",
    val type: String = "dm",
    val lastMessageText: String? = null,
    val lastMessageAt: Timestamp? = null,
    val hidden: Boolean = false
)

data class Message(
    val id: String = "",
    val senderId: String = "",
    val type: String = "text",
    val text: String? = null,
    val createdAt: Timestamp? = null,
    val replyToMessageId: String? = null,
    val hasPendingWrites: Boolean = false
)

data class ReadReceiptState(
    val otherUserLastReadMessageId: String? = null
)

data class GroupMember(
    val uid: String,
    val role: String,
    val joinedAt: Timestamp? = null,
    val lastReadAt: Timestamp? = null,
    val lastReadMessageId: String? = null,
    val hidden: Boolean = false,
    val displayName: String = "",
    val email: String = "",
    val profilePictureUrl: String = ""
)

data class ChatInfo(
    val id: String,
    val type: String,
    val title: String,
    val createdBy: String,
    val memberIds: List<String>
)

class ChatRepository(
    private val db: FirebaseFirestore
) {
    private val chats = db.collection("chats")
    private val users = db.collection("users")
    private val dmThreads = db.collection("dmThreads")
    private val groupThreads = db.collection("groupThreads")

    private fun chatRef(chatId: String) = chats.document(chatId)
    private fun chatMembersRef(chatId: String) = chatRef(chatId).collection("members")
    private fun chatMessagesRef(chatId: String) = chatRef(chatId).collection("messages")
    private fun userInboxRef(uid: String) = users.document(uid).collection("inbox")

    private fun stableDmKey(uidA: String, uidB: String): String {
        val sorted = listOf(uidA, uidB).sorted()
        return "${sorted[0]}_${sorted[1]}"
    }

    private fun stableGroupKey(memberIds: List<String>, title: String): String {
        val canonical = memberIds.distinct().sorted().joinToString("|") + "|" + title.trim()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return digest
    }

    suspend fun getChatInfo(chatId: String): ChatInfo {
        val doc = chatRef(chatId).get().await()
        if (!doc.exists()) throw Exception("Chat not found")

        return ChatInfo(
            id = doc.id,
            type = doc.getString("type") ?: "dm",
            title = doc.getString("title") ?: "Chat",
            createdBy = doc.getString("createdBy") ?: "",
            memberIds = (doc.get("memberIds") as? List<*>)?.filterIsInstance<String>().orEmpty()
        )
    }

    suspend fun getChatTitle(chatId: String): String {
        val doc = chatRef(chatId).get().await()
        return doc.getString("title") ?: "Chat"
    }

    suspend fun getMyChats(myUid: String): List<ChatListItem> {
        val snap = userInboxRef(myUid)
            .whereEqualTo("hidden", false)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .get()
            .await()

        return snap.documents.map { it.toChatListItem() }
    }

    fun listenToMyChats(
        myUid: String,
        onSnapshot: (List<ChatListItem>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return userInboxRef(myUid)
            .whereEqualTo("hidden", false)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(Exception(error.message ?: "Inbox listener error"))
                    return@addSnapshotListener
                }

                val items = snapshot?.documents
                    ?.map { it.toChatListItem() }
                    .orEmpty()

                onSnapshot(items)
            }
    }

    suspend fun openOrCreateDm(
        uidA: String,
        uidB: String,
        title: String
    ): String {
        if (uidA == uidB) throw Exception("Cannot create DM with yourself")

        val sorted = listOf(uidA, uidB).sorted()
        val userA = sorted[0]
        val userB = sorted[1]
        val dmKey = stableDmKey(userA, userB)

        val existingThread = dmThreads.document(dmKey).get().await()
        val existingChatId = existingThread.getString("chatId")
        if (!existingChatId.isNullOrBlank()) {
            return existingChatId
        }

        val chatId = chats.document().id
        val now = FieldValue.serverTimestamp()

        val batch = db.batch()

        val chatDoc = mapOf(
            "type" to "dm",
            "title" to title.trim(),
            "memberIds" to listOf(userA, userB),
            "createdAt" to now,
            "createdBy" to uidA,
            "memberCount" to 2,
            "lastMessageAt" to null,
            "lastMessageText" to null,
            "lastMessageSenderId" to null,
            "hidden" to false
        )

        batch.set(chatRef(chatId), chatDoc)

        batch.set(
            chatMembersRef(chatId).document(userA),
            mapOf(
                "role" to "owner",
                "joinedAt" to now,
                "lastReadAt" to null,
                "lastReadMessageId" to null,
                "hidden" to false
            )
        )

        batch.set(
            chatMembersRef(chatId).document(userB),
            mapOf(
                "role" to "member",
                "joinedAt" to now,
                "lastReadAt" to null,
                "lastReadMessageId" to null,
                "hidden" to false
            )
        )

        val inboxDoc = mapOf(
            "chatId" to chatId,
            "title" to title.trim(),
            "type" to "dm",
            "lastMessageText" to null,
            "lastMessageAt" to null,
            "hidden" to false
        )

        batch.set(userInboxRef(userA).document(chatId), inboxDoc, SetOptions.merge())
        batch.set(userInboxRef(userB).document(chatId), inboxDoc, SetOptions.merge())

        batch.set(
            dmThreads.document(dmKey),
            mapOf(
                "chatId" to chatId,
                "userA" to userA,
                "userB" to userB,
                "createdAt" to now
            )
        )

        batch.commit().await()
        return chatId
    }

    suspend fun openOrCreateGroupChat(
        createdBy: String,
        memberIds: List<String>,
        title: String
    ): String {
        val distinctMembers = memberIds.distinct()
        if (distinctMembers.size < 2) throw Exception("Group must have at least 2 members")
        if (!distinctMembers.contains(createdBy)) throw Exception("Creator must be a member")

        val finalTitle = title.trim().ifBlank { "Group Chat" }
        val groupKey = stableGroupKey(distinctMembers, finalTitle)

        val existingThread = groupThreads.document(groupKey).get().await()
        val existingChatId = existingThread.getString("chatId")
        if (!existingChatId.isNullOrBlank()) {
            return existingChatId
        }

        val chatId = chats.document().id
        val now = FieldValue.serverTimestamp()

        val batch = db.batch()

        val chatDoc = mapOf(
            "type" to "group",
            "title" to finalTitle,
            "memberIds" to distinctMembers,
            "createdAt" to now,
            "createdBy" to createdBy,
            "memberCount" to distinctMembers.size,
            "lastMessageAt" to null,
            "lastMessageText" to null,
            "lastMessageSenderId" to null,
            "hidden" to false
        )

        batch.set(chatRef(chatId), chatDoc)

        distinctMembers.forEach { uid ->
            val role = if (uid == createdBy) "owner" else "member"

            batch.set(
                chatMembersRef(chatId).document(uid),
                mapOf(
                    "role" to role,
                    "joinedAt" to now,
                    "lastReadAt" to null,
                    "lastReadMessageId" to null,
                    "hidden" to false
                )
            )

            batch.set(
                userInboxRef(uid).document(chatId),
                mapOf(
                    "chatId" to chatId,
                    "title" to finalTitle,
                    "type" to "group",
                    "lastMessageText" to null,
                    "lastMessageAt" to null,
                    "hidden" to false
                ),
                SetOptions.merge()
            )
        }

        batch.set(
            groupThreads.document(groupKey),
            mapOf(
                "chatId" to chatId,
                "groupKey" to groupKey,
                "memberIds" to distinctMembers,
                "createdAt" to now
            )
        )

        batch.commit().await()
        return chatId
    }

    suspend fun getGroupMembers(chatId: String): List<GroupMember> {
        val memberDocs = chatMembersRef(chatId).get().await().documents
        val memberIds = memberDocs.map { it.id }

        val profiles = memberIds.associateWith { uid ->
            users.document(uid).get()
        }.mapValues { (_, task) ->
            try {
                task.await()
            } catch (_: Exception) {
                null
            }
        }

        return memberDocs.map { doc ->
            val profile = profiles[doc.id]
            GroupMember(
                uid = doc.id,
                role = doc.getString("role") ?: "member",
                joinedAt = doc.getTimestamp("joinedAt"),
                lastReadAt = doc.getTimestamp("lastReadAt"),
                lastReadMessageId = doc.getString("lastReadMessageId"),
                hidden = doc.getBoolean("hidden") ?: false,
                displayName = profile?.getString("displayName")
                    ?.takeIf { it.isNotBlank() }
                    ?: profile?.getString("name").orEmpty(),
                email = profile?.getString("email").orEmpty(),
                profilePictureUrl = profile?.getString("profilePictureUrl").orEmpty()
            )
        }.sortedWith(compareBy<GroupMember> {
            when (it.role) {
                "owner" -> 0
                "cohost" -> 1
                else -> 2
            }
        }.thenBy { it.displayName.ifBlank { it.email }.lowercase() })
    }

    suspend fun getMyRole(chatId: String, myUid: String): String? {
        val doc = chatMembersRef(chatId).document(myUid).get().await()
        return doc.getString("role")
    }

    private suspend fun getUserDisplayName(uid: String): String {
        val doc = users.document(uid).get().await()

        return doc.getString("displayName")
            ?.trim()
            .orEmpty()
            .ifBlank { doc.getString("name")?.trim().orEmpty() }
            .ifBlank { doc.getString("email")?.substringBefore("@").orEmpty() }
            .ifBlank { "User" }
    }

    private suspend fun sendSystemMembershipMessage(
        chatId: String,
        senderId: String,
        text: String
    ): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw Exception("Message cannot be empty")

        val chat = getChatInfo(chatId)
        val msgRef = chatMessagesRef(chatId).document()
        val now = FieldValue.serverTimestamp()
        val batch = db.batch()

        batch.set(
            msgRef,
            mapOf(
                "senderId" to senderId,
                "type" to "system",
                "text" to trimmed,
                "createdAt" to now,
                "replyToMessageId" to null
            )
        )

        batch.update(
            chatRef(chatId),
            mapOf(
                "lastMessageAt" to now,
                "lastMessageText" to trimmed,
                "lastMessageSenderId" to senderId
            )
        )

        chat.memberIds.forEach { uid ->
            batch.set(
                userInboxRef(uid).document(chatId),
                mapOf(
                    "chatId" to chatId,
                    "title" to chat.title,
                    "type" to chat.type,
                    "lastMessageText" to trimmed,
                    "lastMessageAt" to now,
                    "hidden" to false
                ),
                SetOptions.merge()
            )
        }

        batch.commit().await()
        return msgRef.id
    }

    suspend fun addGroupMember(
        chatId: String,
        actingUid: String,
        newMemberUid: String
    ) {
        val chat = getChatInfo(chatId)
        if (chat.type != "group") throw Exception("Not a group chat")
        if (chat.memberIds.contains(newMemberUid)) return

        val role = getMyRole(chatId, actingUid)
        if (role != "owner" && role != "cohost") {
            throw Exception("Only owner or cohost can add members")
        }

        val updatedMemberIds = (chat.memberIds + newMemberUid).distinct()
        val title = chat.title
        val now = FieldValue.serverTimestamp()
        val newMemberName = getUserDisplayName(newMemberUid)
        val systemText = "$newMemberName has been added"
        val msgRef = chatMessagesRef(chatId).document()

        val batch = db.batch()

        // 1. Update chat doc (member list + metadata)
        batch.update(
            chatRef(chatId),
            mapOf(
                "memberIds" to updatedMemberIds,
                "memberCount" to updatedMemberIds.size,
                "lastMessageAt" to now,
                "lastMessageText" to systemText,
                "lastMessageSenderId" to actingUid
            )
        )

        // 2. Create the member doc
        batch.set(
            chatMembersRef(chatId).document(newMemberUid),
            mapOf(
                "role" to "member",
                "joinedAt" to now,
                "lastReadAt" to null,
                "lastReadMessageId" to null,
                "hidden" to false
            )
        )

        // 3. Create the system message
        batch.set(
            msgRef,
            mapOf(
                "senderId" to actingUid,
                "type" to "system",
                "text" to systemText,
                "createdAt" to now,
                "replyToMessageId" to null
            )
        )

        // 4. Update inboxes for ALL members (including the new one)
        updatedMemberIds.forEach { uid ->
            batch.set(
                userInboxRef(uid).document(chatId),
                mapOf(
                    "chatId" to chatId,
                    "title" to title,
                    "type" to "group",
                    "lastMessageText" to systemText,
                    "lastMessageAt" to now,
                    "hidden" to false
                ),
                SetOptions.merge()
            )
        }

        batch.commit().await()
    }

    suspend fun removeGroupMember(
        chatId: String,
        actingUid: String,
        targetUid: String
    ) {
        val chat = getChatInfo(chatId)
        if (chat.type != "group") throw Exception("Not a group chat")

        val actingRole = getMyRole(chatId, actingUid)
        val targetMemberDoc = chatMembersRef(chatId).document(targetUid).get().await()
        val targetRole = targetMemberDoc.getString("role") ?: "member"

        val allowed = when (actingRole) {
            "owner" -> targetUid != actingUid && (targetRole == "member" || targetRole == "cohost")
            "cohost" -> targetUid != actingUid && targetRole == "member"
            else -> false
        }

        if (!allowed) throw Exception("You do not have permission to remove this member")

        val updatedMemberIds = chat.memberIds.filter { it != targetUid }
        if (updatedMemberIds.size < 2) throw Exception("Group must keep at least 2 members")

        val targetName = getUserDisplayName(targetUid)
        val systemText = "$targetName has been removed"
        val now = FieldValue.serverTimestamp()
        val msgRef = chatMessagesRef(chatId).document()

        val batch = db.batch()

        // 1. Update chat doc
        batch.update(
            chatRef(chatId),
            mapOf(
                "memberIds" to updatedMemberIds,
                "memberCount" to updatedMemberIds.size,
                "lastMessageAt" to now,
                "lastMessageText" to systemText,
                "lastMessageSenderId" to actingUid
            )
        )

        // 2. Create system message
        batch.set(
            msgRef,
            mapOf(
                "senderId" to actingUid,
                "type" to "system",
                "text" to systemText,
                "createdAt" to now,
                "replyToMessageId" to null
            )
        )

        // 3. Delete target's membership and inbox
        batch.delete(chatMembersRef(chatId).document(targetUid))
        batch.delete(userInboxRef(targetUid).document(chatId))

        // 4. Update inboxes for remaining members
        updatedMemberIds.forEach { uid ->
            batch.set(
                userInboxRef(uid).document(chatId),
                mapOf(
                    "chatId" to chatId,
                    "title" to chat.title,
                    "type" to "group",
                    "lastMessageText" to systemText,
                    "lastMessageAt" to now,
                    "hidden" to false
                ),
                SetOptions.merge()
            )
        }

        batch.commit().await()
    }

    suspend fun leaveGroupChat(chatId: String, myUid: String) {
        val chat = getChatInfo(chatId)
        if (chat.type != "group") throw Exception("Not a group chat")

        val myRole = getMyRole(chatId, myUid)
        if (myRole == "owner") {
            throw Exception("Owner cannot leave without transferring or deleting the group")
        }

        val updatedMemberIds = chat.memberIds.filter { it != myUid }
        if (updatedMemberIds.size < 2) throw Exception("Group must keep at least 2 members")

        val myName = getUserDisplayName(myUid)
        val systemText = "$myName has left the group"
        val now = FieldValue.serverTimestamp()
        val msgRef = chatMessagesRef(chatId).document()

        val batch = db.batch()

        // 1. Update chat doc
        batch.update(
            chatRef(chatId),
            mapOf(
                "memberIds" to updatedMemberIds,
                "memberCount" to updatedMemberIds.size,
                "lastMessageAt" to now,
                "lastMessageText" to systemText,
                "lastMessageSenderId" to myUid
            )
        )

        // 2. Create system message
        batch.set(
            msgRef,
            mapOf(
                "senderId" to myUid,
                "type" to "system",
                "text" to systemText,
                "createdAt" to now,
                "replyToMessageId" to null
            )
        )

        // 3. Delete my membership and inbox
        batch.delete(chatMembersRef(chatId).document(myUid))
        batch.delete(userInboxRef(myUid).document(chatId))

        // 4. Update inboxes for remaining members
        updatedMemberIds.forEach { uid ->
            batch.set(
                userInboxRef(uid).document(chatId),
                mapOf(
                    "chatId" to chatId,
                    "title" to chat.title,
                    "type" to "group",
                    "lastMessageText" to systemText,
                    "lastMessageAt" to now,
                    "hidden" to false
                ),
                SetOptions.merge()
            )
        }

        batch.commit().await()
    }


    suspend fun updateGroupMemberRole(
        chatId: String,
        actingUid: String,
        targetUid: String,
        newRole: String
    ) {
        if (newRole !in listOf("cohost", "member")) {
            throw Exception("Role must be cohost or member")
        }

        val actingRole = getMyRole(chatId, actingUid)
        if (actingRole != "owner") {
            throw Exception("Only owner can change roles")
        }

        if (actingUid == targetUid) {
            throw Exception("Owner cannot change their own role here")
        }

        val targetRef = chatMembersRef(chatId).document(targetUid)
        val targetDoc = targetRef.get().await()
        val currentRole = targetDoc.getString("role") ?: "member"

        if (currentRole == "owner") {
            throw Exception("Cannot change owner role here")
        }

        targetRef.update("role", newRole).await()
    }

    suspend fun hideChatForUser(chatId: String, uid: String) {
        userInboxRef(uid).document(chatId)
            .set(mapOf("hidden" to true), SetOptions.merge())
            .await()

        val memberDoc = chatMembersRef(chatId).document(uid)
        if (memberDoc.get().await().exists()) {
            memberDoc.set(mapOf("hidden" to true), SetOptions.merge()).await()
        }
    }

    suspend fun unhideChatForUser(chatId: String, uid: String) {
        userInboxRef(uid).document(chatId)
            .set(mapOf("hidden" to false), SetOptions.merge())
            .await()

        val memberDoc = chatMembersRef(chatId).document(uid)
        if (memberDoc.get().await().exists()) {
            memberDoc.set(mapOf("hidden" to false), SetOptions.merge()).await()
        }
    }

    suspend fun sendTextMessage(
        chatId: String,
        senderId: String,
        text: String
    ): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw Exception("Message cannot be empty")

        val msgRef = chatMessagesRef(chatId).document()
        val batch = db.batch()
        val now = FieldValue.serverTimestamp()
        val chat = getChatInfo(chatId)

        batch.set(
            msgRef,
            mapOf(
                "senderId" to senderId,
                "type" to "text",
                "text" to trimmed,
                "createdAt" to now,
                "replyToMessageId" to null
            )
        )

        batch.update(
            chatRef(chatId),
            mapOf(
                "lastMessageAt" to now,
                "lastMessageText" to trimmed,
                "lastMessageSenderId" to senderId
            )
        )

        chat.memberIds.forEach { uid ->
            batch.set(
                userInboxRef(uid).document(chatId),
                mapOf(
                    "chatId" to chatId,
                    "title" to chat.title,
                    "type" to chat.type,
                    "lastMessageText" to trimmed,
                    "lastMessageAt" to now,
                    "hidden" to false
                ),
                SetOptions.merge()
            )
        }

        batch.commit().await()
        return msgRef.id
    }

    suspend fun updateMyLastRead(
        chatId: String,
        myUid: String,
        messageId: String
    ) {
        chatMembersRef(chatId).document(myUid)
            .set(
                mapOf(
                    "lastReadAt" to FieldValue.serverTimestamp(),
                    "lastReadMessageId" to messageId
                ),
                SetOptions.merge()
            )
            .await()
    }

    fun listenMessagesRealtime(
        chatId: String,
        pageSize: Long = 50,
        onSnapshot: (List<Message>, Boolean) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return chatMessagesRef(chatId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(pageSize)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    onError(Exception(error.message ?: "Messages listener error"))
                    return@addSnapshotListener
                }

                val messages = snapshot?.documents
                    ?.map { it.toMessage() }
                    .orEmpty()

                val hasPendingWrites = snapshot?.metadata?.hasPendingWrites() == true
                onSnapshot(messages, hasPendingWrites)
            }
    }

    fun listenToDmReadReceipt(
        chatId: String,
        myUid: String,
        onSnapshot: (ReadReceiptState) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return chatMembersRef(chatId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(Exception(error.message ?: "Read receipt listener error"))
                    return@addSnapshotListener
                }

                val otherDoc = snapshot?.documents?.firstOrNull { it.id != myUid }
                onSnapshot(
                    ReadReceiptState(
                        otherUserLastReadMessageId = otherDoc?.getString("lastReadMessageId")
                    )
                )
            }
    }

    fun listenToMyReadState(
        chatId: String,
        myUid: String,
        onSnapshot: (String?) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return chatMembersRef(chatId)
            .document(myUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(Exception(error.message ?: "My read-state listener error"))
                    return@addSnapshotListener
                }

                onSnapshot(snapshot?.getString("lastReadMessageId"))
            }
    }

    /**
     * IMPORTANT:
     * With the current Firestore rules, a client cannot fully and safely
     * recursively delete:
     * - messages
     * - owner member doc
     * - chat doc
     * - inbox docs
     *
     * So a true permanent delete should be implemented in a Cloud Function
     * or with expanded admin rules.
     */
    suspend fun transferOwnership(
        chatId: String,
        actingUid: String,
        newOwnerUid: String
    ) {
        val chat = getChatInfo(chatId)
        val actingRole = getMyRole(chatId, actingUid)
        if (actingRole != "owner") throw Exception("Only owner can transfer ownership")

        val targetRef = chatMembersRef(chatId).document(newOwnerUid)
        val targetDoc = targetRef.get().await()
        if (!targetDoc.exists()) throw Exception("Target user is not a member of this chat")

        val newOwnerName = getUserDisplayName(newOwnerUid)
        val systemText = "Ownership transferred to $newOwnerName"
        val now = FieldValue.serverTimestamp()
        val msgRef = chatMessagesRef(chatId).document()

        val batch = db.batch()

        // 1. Update old owner to cohost
        batch.update(chatMembersRef(chatId).document(actingUid), "role", "cohost")

        // 2. Update new owner to owner
        batch.update(chatMembersRef(chatId).document(newOwnerUid), "role", "owner")

        // 3. Update chat doc (createdBy + metadata)
        batch.update(
            chatRef(chatId),
            mapOf(
                "createdBy" to newOwnerUid,
                "lastMessageAt" to now,
                "lastMessageText" to systemText,
                "lastMessageSenderId" to actingUid
            )
        )

        // 4. Create system message
        batch.set(
            msgRef,
            mapOf(
                "senderId" to actingUid,
                "type" to "system",
                "text" to systemText,
                "createdAt" to now,
                "replyToMessageId" to null
            )
        )

        // 5. Update inboxes for ALL members
        chat.memberIds.forEach { uid ->
            batch.set(
                userInboxRef(uid).document(chatId),
                mapOf(
                    "chatId" to chatId,
                    "title" to chat.title,
                    "type" to chat.type,
                    "lastMessageText" to systemText,
                    "lastMessageAt" to now,
                    "hidden" to false
                ),
                SetOptions.merge()
            )
        }

        batch.commit().await()
    }


    suspend fun deleteChatListItem(myUid: String, chatId: String) {
        userInboxRef(myUid).document(chatId).delete().await()
    }

    private fun DocumentSnapshot.toChatListItem(): ChatListItem {
        return ChatListItem(
            id = getString("chatId") ?: id,
            title = getString("title") ?: "Chat",
            type = getString("type") ?: "dm",
            lastMessageText = getString("lastMessageText"),
            lastMessageAt = getTimestamp("lastMessageAt"),
            hidden = getBoolean("hidden") ?: false
        )
    }

    private fun DocumentSnapshot.toMessage(): Message {
        return Message(
            id = id,
            senderId = getString("senderId") ?: "",
            type = getString("type") ?: "text",
            text = getString("text"),
            createdAt = getTimestamp("createdAt"),
            replyToMessageId = getString("replyToMessageId"),
            hasPendingWrites = metadata.hasPendingWrites()
        )
    }

    suspend fun sendFriendRequest(
        myUid: String,
        targetUid: String
    ) {
        if (myUid == targetUid) return

        val myProfileDoc = users.document(myUid).get().await()
        val targetProfileDoc = users.document(targetUid).get().await()

        val myName = myProfileDoc.getString("displayName") ?: myProfileDoc.getString("name") ?: "User"
        val myEmail = myProfileDoc.getString("email") ?: ""
        val myPhoto = myProfileDoc.getString("profilePictureUrl") ?: ""

        val targetName = targetProfileDoc.getString("displayName") ?: targetProfileDoc.getString("name") ?: "User"
        val targetEmail = targetProfileDoc.getString("email") ?: ""
        val targetPhoto = targetProfileDoc.getString("profilePictureUrl") ?: ""

        val now = FieldValue.serverTimestamp()
        val batch = db.batch()

        // 1. Add to recipient's friendRequests
        batch.set(
            users.document(targetUid).collection("friendRequests").document(myUid),
            mapOf(
                "uid" to myUid,
                "fromUid" to myUid,
                "toUid" to targetUid,
                "displayName" to myName,
                "email" to myEmail,
                "profilePictureUrl" to myPhoto,
                "status" to "pending",
                "createdAt" to now
            )
        )

        // 2. Add to sender's sentFriendRequests
        batch.set(
            users.document(myUid).collection("sentFriendRequests").document(targetUid),
            mapOf(
                "uid" to targetUid,
                "fromUid" to myUid,
                "toUid" to targetUid,
                "displayName" to targetName,
                "email" to targetEmail,
                "profilePictureUrl" to targetPhoto,
                "status" to "pending",
                "createdAt" to now
            )
        )

        batch.commit().await()
    }

    suspend fun acceptFriendRequest(
        myUid: String,
        requesterUid: String
    ) {
        val myProfileDoc = users.document(myUid).get().await()
        val requesterProfileDoc = users.document(requesterUid).get().await()

        val myName = myProfileDoc.getString("displayName") ?: myProfileDoc.getString("name") ?: "User"
        val myPhoto = myProfileDoc.getString("profilePictureUrl") ?: ""
        
        val requesterName = requesterProfileDoc.getString("displayName") ?: requesterProfileDoc.getString("name") ?: "User"
        val requesterPhoto = requesterProfileDoc.getString("profilePictureUrl") ?: ""

        val now = FieldValue.serverTimestamp()
        val batch = db.batch()

        // 1. Add to my friends
        batch.set(
            users.document(myUid).collection("friends").document(requesterUid),
            mapOf(
                "uid" to requesterUid,
                "name" to requesterName,
                "profilePictureUrl" to requesterPhoto,
                "status" to "accepted",
                "addedAt" to now
            )
        )

        // 2. Add to their friends
        batch.set(
            users.document(requesterUid).collection("friends").document(myUid),
            mapOf(
                "uid" to myUid,
                "name" to myName,
                "profilePictureUrl" to myPhoto,
                "status" to "accepted",
                "addedAt" to now
            )
        )

        // 3. Remove the request docs
        batch.delete(users.document(myUid).collection("friendRequests").document(requesterUid))
        batch.delete(users.document(requesterUid).collection("sentFriendRequests").document(myUid))

        batch.commit().await()
    }

    suspend fun declineFriendRequest(
        myUid: String,
        requesterUid: String
    ) {
        val batch = db.batch()
        batch.delete(users.document(myUid).collection("friendRequests").document(requesterUid))
        batch.delete(users.document(requesterUid).collection("sentFriendRequests").document(myUid))
        batch.commit().await()
    }

    suspend fun removeFriend(
        myUid: String,
        friendUid: String
    ) {
        val batch = db.batch()
        // 1. Remove from my friends
        batch.delete(users.document(myUid).collection("friends").document(friendUid))
        // 2. Remove from their friends
        batch.delete(users.document(friendUid).collection("friends").document(myUid))
        batch.commit().await()
    }

    suspend fun cancelFriendRequest(
        myUid: String,
        targetUid: String
    ) {
        val batch = db.batch()
        batch.delete(users.document(myUid).collection("sentFriendRequests").document(targetUid))
        batch.delete(users.document(targetUid).collection("friendRequests").document(myUid))
        batch.commit().await()
    }

    suspend fun findExistingDmChatId(uidA: String, uidB: String): String? {
        if (uidA == uidB) return null

        val sorted = listOf(uidA, uidB).sorted()
        val dmKey = "${sorted[0]}_${sorted[1]}"

        val doc = dmThreads.document(dmKey).get().await()
        return doc.getString("chatId")
    }
}
