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

class ChatRepository(private val db: FirebaseFirestore) {

    private val chatsRef = db.collection("chats")
    private val dmThreadsRef = db.collection("dmThreads")

    /**
     * Open an existing DM between uidA and uidB, or create it if absent.
     * Returns the chatId.
     *
     * Uses a deterministic dmKey so you never create duplicate DM chats:
     * dmKey = min(uidA, uidB) + "_" + max(uidA, uidB)
     */
    suspend fun openOrCreateDm(uidA: String, uidB: String): String {
        val (a, b) = listOf(uidA, uidB).sorted()
        val dmKey = "${a}_$b"
        val dmDocRef = dmThreadsRef.document(dmKey)

        val existing = dmDocRef.get().await()
        if (existing.exists()) {
            val chatId = existing.getString("chatId")
            if (!chatId.isNullOrBlank()) return chatId
        }

        val newChatRef = chatsRef.document()

        // Transaction prevents race conditions (two clients creating the DM at the same time)
        db.runTransaction { tx ->
            val now = Timestamp.now()

            tx.set(newChatRef, mapOf(
                "type" to "dm",
                "createdAt" to now,
                "createdBy" to uidA,
                "memberCount" to 2,
                "lastMessageAt" to null,
                "lastMessageText" to null,
                "lastMessageSenderId" to null
            ))

            // membership docs are what rules use to validate access
            val memberARef = newChatRef.collection("members").document(a)
            val memberBRef = newChatRef.collection("members").document(b)

            tx.set(memberARef, mapOf("role" to "member", "joinedAt" to now, "lastReadAt" to null))
            tx.set(memberBRef, mapOf("role" to "member", "joinedAt" to now, "lastReadAt" to null))

            // DM mapping so this pair always resolves to the same chat
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
     * Create a group chat with initial members.
     * Returns chatId.
     *
     * NOTE: For production, you'd enforce "who can add/remove members" via security rules + roles
     * or via Cloud Functions.
     */
    suspend fun createGroupChat(
        createdBy: String,
        memberIds: List<String>,
        title: String,
        photoURL: String? = null
    ): String {
        require(memberIds.isNotEmpty()) { "memberIds cannot be empty" }
        require(memberIds.contains(createdBy)) { "memberIds should include createdBy" }

        val chatRef = chatsRef.document()

        db.runTransaction { tx ->
            val now = Timestamp.now()

            tx.set(chatRef, mapOf(
                "type" to "group",
                "createdAt" to now,
                "createdBy" to createdBy,
                "memberCount" to memberIds.size,
                "title" to title,
                "photoURL" to (photoURL ?: ""),
                "lastMessageAt" to null,
                "lastMessageText" to null,
                "lastMessageSenderId" to null
            ))

            memberIds.distinct().forEach { uid ->
                val role = if (uid == createdBy) "owner" else "member"
                val memberRef = chatRef.collection("members").document(uid)
                tx.set(memberRef, mapOf("role" to role, "joinedAt" to now, "lastReadAt" to null))
            }
        }.await()

        return chatRef.id
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
}