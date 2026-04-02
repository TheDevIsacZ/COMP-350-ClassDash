package com.example.classseek.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await
import java.util.UUID

data class Message(
    val id: String,
    val senderId: String,
    val type: String = "text",
    val text: String? = null,
    val createdAt: Timestamp? = null,
    val replyToMessageId: String? = null
)

data class ChatListItem(
    val id: String,
    val title: String,
    val lastMessageText: String? = null
)

class ChatRepository(private val db: FirebaseFirestore) {

    private val chatsRef = db.collection("chats")
    private val dmThreadsRef = db.collection("dmThreads")

    private val groupThreadsRef = db.collection("groupThreads")

    /**
     * Open an existing DM between uidA and uidB, or create it if absent.
     * Returns the chatId.
     *
     * Uses a deterministic dmKey so you never create duplicate DM chats:
     * dmKey = min(uidA, uidB) + "_" + max(uidA, uidB)
     */
    suspend fun openOrCreateDm(uidA: String, uidB: String, title: String?): String {
        val (a, b) = listOf(uidA, uidB).sorted()
        val dmKey = "${a}_$b"
        val dmDocRef = dmThreadsRef.document(dmKey)

        val existing = dmDocRef.get().await()
        if (existing.exists()) {
            val chatId = existing.getString("chatId")

            if (!chatId.isNullOrBlank()) {
                val myMemberRef = chatsRef.document(chatId)
                    .collection("members")
                    .document(uidA)

                myMemberRef.update("hidden", false).await()
                return chatId
            }
        }

        val newChatRef = chatsRef.document()

        db.runTransaction { tx ->
            val now = Timestamp.now()

            val finalTitle = if (title.isNullOrBlank()) "Chat" else title.trim()

            tx.set(newChatRef, mapOf(
                "type" to "dm",
                "title" to finalTitle,
                "memberIds" to listOf(a, b),
                "createdAt" to now,
                "createdBy" to uidA,
                "memberCount" to 2,
                "lastMessageAt" to null,
                "lastMessageText" to null,
                "lastMessageSenderId" to null,
                "hidden" to false
            ))

            val memberARef = newChatRef.collection("members").document(a)
            val memberBRef = newChatRef.collection("members").document(b)

            tx.set(memberARef, mapOf("role" to "member", "joinedAt" to now, "lastReadAt" to null))
            tx.set(memberBRef, mapOf("role" to "member", "joinedAt" to now, "lastReadAt" to null))

            tx.set(dmDocRef, mapOf(
                "chatId" to newChatRef.id,
                "userA" to a,
                "userB" to b,
                "createdAt" to now
            ))
        }.await()

        return newChatRef.id
    }

    /**
    Possibly temporary until real user accounts are created.
     */
    private fun buildGroupKey(memberIds: List<String>): String {
        return memberIds
            .distinct()
            .sorted()
            .joinToString("_")
    }

    suspend fun openOrCreateGroupChat(
        createdBy: String,
        memberIds: List<String>,
        title: String,
        photoURL: String? = null
    ): String {
        require(memberIds.isNotEmpty()) { "memberIds cannot be empty" }

        val uniqueMembers = memberIds.distinct()
        require(uniqueMembers.contains(createdBy)) { "memberIds should include createdBy" }

        val groupKey = buildGroupKey(uniqueMembers)
        val groupThreadRef = groupThreadsRef.document(groupKey)

        val existing = groupThreadRef.get().await()
        if (existing.exists()) {
            val chatId = existing.getString("chatId")
            if (!chatId.isNullOrBlank()) {
                val myMemberRef = chatsRef.document(chatId)
                    .collection("members")
                    .document(createdBy)

                myMemberRef.update("hidden", false).await()
                return chatId
            }
        }

        val chatRef = chatsRef.document()

        db.runTransaction { tx ->
            val now = Timestamp.now()

            tx.set(chatRef, mapOf(
                "type" to "group",
                "title" to title,
                "memberIds" to uniqueMembers,
                "createdAt" to now,
                "createdBy" to createdBy,
                "memberCount" to uniqueMembers.size,
                "photoURL" to (photoURL ?: ""),
                "lastMessageAt" to null,
                "lastMessageText" to null,
                "lastMessageSenderId" to null
            ))

            uniqueMembers.forEach { uid ->
                val role = if (uid == createdBy) "owner" else "member"
                val memberRef = chatRef.collection("members").document(uid)
                tx.set(memberRef, mapOf(
                    "role" to role,
                    "joinedAt" to now,
                    "lastReadAt" to null,
                    "hidden" to false
                ))
            }

            tx.set(groupThreadRef, mapOf(
                "chatId" to chatRef.id,
                "groupKey" to groupKey,
                "memberIds" to uniqueMembers,
                "createdAt" to now
            ))
        }.await()

        return chatRef.id
    }

    /**
     * Get a list of chat summaries for the current user.
     * Checks for Hidden attribute to decide wether to display chat
     */
    suspend fun getMyChats(myUid: String): List<ChatListItem> {
        val snap = chatsRef
            .whereArrayContains("memberIds", myUid)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .get()
            .await()

        val result = mutableListOf<ChatListItem>()

        for (doc in snap.documents) {
            val memberSnap = doc.reference
                .collection("members")
                .document(myUid)
                .get()
                .await()

            val hidden = memberSnap.getBoolean("hidden") ?: false
            if (hidden) continue

            result.add(
                ChatListItem(
                    id = doc.id,
                    title = doc.getString("title") ?: "Untitled Chat",
                    lastMessageText = doc.getString("lastMessageText")
                )
            )
        }

        return result
    }

    /**
     * Send a text message to a chat.
     * Writes message + updates chat summary in one batch.
     *
     * IMPORTANT: Updating each member's inbox/unread count is best done in Cloud Functions.
     */
    suspend fun sendTextMessage(
        chatId: String,
        senderId: String,
        text: String,
        replyToMessageId: String? = null
    ) {
        val now = Timestamp.now()
        val messageId = UUID.randomUUID().toString()

        val chatRef = chatsRef.document(chatId)
        val messageRef = chatRef.collection("messages").document(messageId)

        val messageData = mapOf(
            "senderId" to senderId,
            "type" to "text",
            "text" to text,
            "createdAt" to now,
            "replyToMessageId" to replyToMessageId
        )

        val batch = db.batch()
        batch.set(messageRef, messageData)
        batch.update(chatRef, mapOf(
            "lastMessageAt" to now,
            "lastMessageText" to text,
            "lastMessageSenderId" to senderId
        ))
        batch.commit().await()
    }

    /**
     * Listen to recent messages in realtime.
     * Returns a ListenerRegistration; call remove() when the screen is disposed.
     */
    fun listenMessagesRealtime(
        chatId: String,
        pageSize: Int = 50,
        onSnapshot: (List<Message>, List<DocumentSnapshot>) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ): ListenerRegistration {
        val q = chatsRef.document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(pageSize.toLong())

        return q.addSnapshotListener { snap, err ->
            if (err != null) {
                onError?.invoke(err)
                return@addSnapshotListener
            }
            if (snap == null) {
                onSnapshot(emptyList(), emptyList())
                return@addSnapshotListener
            }
            val docs = snap.documents
            val messages = docs.mapNotNull { docToMessage(it) }
            onSnapshot(messages, docs)
        }
    }

    /**
     * Pagination: load older messages after the last seen DocumentSnapshot.
     */
    suspend fun loadMoreMessages(
        chatId: String,
        lastSeenDoc: DocumentSnapshot,
        pageSize: Int = 50
    ): Pair<List<Message>, List<DocumentSnapshot>> {
        val q = chatsRef.document(chatId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .startAfter(lastSeenDoc)
            .limit(pageSize.toLong())

        val snap = q.get().await()
        val docs = snap.documents
        val messages = docs.mapNotNull { docToMessage(it) }
        return messages to docs
    }

    /**
     * Update the user's lastReadAt field in the membership doc.
     * Call when user opens the chat or after they scroll to bottom.
     */
    suspend fun updateMyLastRead(chatId: String, myUid: String) {
        val memberRef = chatsRef.document(chatId).collection("members").document(myUid)
        memberRef.update("lastReadAt", Timestamp.now()).await()
    }

    private fun docToMessage(doc: DocumentSnapshot): Message? {
        val senderId = doc.getString("senderId") ?: return null
        return Message(
            id = doc.id,
            senderId = senderId,
            type = doc.getString("type") ?: "text",
            text = doc.getString("text"),
            createdAt = doc.getTimestamp("createdAt"),
            replyToMessageId = doc.getString("replyToMessageId")
        )
    }

    /**
     * hides chats from users without deleting them from database
     */
    suspend fun hideChatForUser(chatId: String, myUid: String) {
        val memberRef = chatsRef.document(chatId)
            .collection("members")
            .document(myUid)

        memberRef.update("hidden", true).await()
    }

    suspend fun getChatTitle(chatId: String): String {
        val doc = chatsRef.document(chatId).get().await()
        return doc.getString("title") ?: "Chat"
    }

}