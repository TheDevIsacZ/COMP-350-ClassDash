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
import java.util.UUID

data class Message(
    val id: String,
    val senderId: String,
    val type: String = "text",
    val text: String? = null,
    val createdAt: Timestamp? = null,
    val replyToMessageId: String? = null,
    val hasPendingWrites: Boolean = false
)

data class ChatListItem(
    val id: String,
    val title: String,
    val lastMessageText: String? = null
)

data class ReadReceiptState(
    val otherUserId: String? = null,
    val otherUserLastReadAt: Timestamp? = null,
    val otherUserLastReadMessageId: String? = null
)

class ChatRepository(private val db: FirebaseFirestore) {

    private val chatsRef = db.collection("chats")
    private val dmThreadsRef = db.collection("dmThreads")
    private val groupThreadsRef = db.collection("groupThreads")
    private val usersRef = db.collection("users")

    /**
     * Open an existing DM between uidA and uidB, or create it if absent.
     * Returns the chatId.
     *
     * Uses a deterministic dmKey so you never create duplicate DM chats:
     * dmKey = min(uidA, uidB) + "_" + max(uidA, uidB)
     */
    suspend fun openOrCreateDm(uidA: String, uidB: String, title: String?): String {
        val (a, b) = listOf(uidA, uidB).sorted()
        val dmKey = "${a}_${b}"
        val dmDocRef = dmThreadsRef.document(dmKey)

        val existing = dmDocRef.get().await()
        if (existing.exists()) {
            val chatId = existing.getString("chatId")
            if (!chatId.isNullOrBlank()) {
                inboxRef(uidA, chatId).update("hidden", false).await()
                return chatId
            }
        }

        val newChatRef = chatsRef.document()

        db.runTransaction { tx ->
            val now = Timestamp.now()
            val finalTitle = if (title.isNullOrBlank()) "Chat" else title.trim()

            tx.set(
                newChatRef,
                mapOf(
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
                )
            )

            val memberARef = newChatRef.collection("members").document(a)
            val memberBRef = newChatRef.collection("members").document(b)

            tx.set(
                memberARef,
                mapOf(
                    "role" to "member",
                    "joinedAt" to now,
                    "lastReadAt" to null,
                    "lastReadMessageId" to null
                )
            )

            tx.set(
                memberBRef,
                mapOf(
                    "role" to "member",
                    "joinedAt" to now,
                    "lastReadAt" to null,
                    "lastReadMessageId" to null
                )
            )

            tx.set(
                inboxRef(a, newChatRef.id),
                mapOf(
                    "chatId" to newChatRef.id,
                    "title" to finalTitle,
                    "type" to "dm",
                    "lastMessageText" to null,
                    "lastMessageAt" to null,
                    "hidden" to false
                )
            )

            tx.set(
                inboxRef(b, newChatRef.id),
                mapOf(
                    "chatId" to newChatRef.id,
                    "title" to finalTitle,
                    "type" to "dm",
                    "lastMessageText" to null,
                    "lastMessageAt" to null,
                    "hidden" to false
                )
            )

            tx.set(
                dmDocRef,
                mapOf(
                    "chatId" to newChatRef.id,
                    "userA" to a,
                    "userB" to b,
                    "createdAt" to now
                )
            )
        }.await()

        return newChatRef.id
    }

    /**
     * Possibly temporary until real user accounts are created.
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
                inboxRef(createdBy, chatId).update("hidden", false).await()
                return chatId
            }
        }

        val chatRef = chatsRef.document()

        db.runTransaction { tx ->
            val now = Timestamp.now()

            tx.set(
                chatRef,
                mapOf(
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
                )
            )

            uniqueMembers.forEach { uid ->
                val role = if (uid == createdBy) "owner" else "member"
                val memberRef = chatRef.collection("members").document(uid)

                tx.set(
                    memberRef,
                    mapOf(
                        "role" to role,
                        "joinedAt" to now,
                        "lastReadAt" to null,
                        "lastReadMessageId" to null,
                        "hidden" to false
                    )
                )

                tx.set(
                    inboxRef(uid, chatRef.id),
                    mapOf(
                        "chatId" to chatRef.id,
                        "title" to title,
                        "type" to "group",
                        "lastMessageText" to null,
                        "lastMessageAt" to null,
                        "hidden" to false
                    )
                )
            }

            tx.set(
                groupThreadRef,
                mapOf(
                    "chatId" to chatRef.id,
                    "groupKey" to groupKey,
                    "memberIds" to uniqueMembers,
                    "createdAt" to now
                )
            )
        }.await()

        return chatRef.id
    }

    /**
     * Get a list of chat summaries for the current user.
     * Checks hidden attribute to decide whether to display chat.
     */
    suspend fun getMyChats(myUid: String): List<ChatListItem> {
        val snap = usersRef.document(myUid)
            .collection("inbox")
            .whereEqualTo("hidden", false)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .get()
            .await()

        return snap.documents.map { doc ->
            ChatListItem(
                id = doc.id,
                title = doc.getString("title") ?: "Untitled Chat",
                lastMessageText = doc.getString("lastMessageText")
            )
        }
    }

    /**
     * Send a text message to a chat.
     * Returns the exact messageId so the UI can scroll to the exact message sent.
     *
     * IMPORTANT: Updating each member's inbox/unread count is best done in Cloud Functions.
     */
    suspend fun sendTextMessage(
        chatId: String,
        senderId: String,
        text: String,
        replyToMessageId: String? = null
    ): String {
        val messageId = UUID.randomUUID().toString()

        val chatRef = chatsRef.document(chatId)
        val messageRef = chatRef.collection("messages").document(messageId)

        val chatSnap = chatRef.get().await()
        val memberIds =
            (chatSnap.get("memberIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

        val messageData = mapOf(
            "senderId" to senderId,
            "type" to "text",
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp(),
            "replyToMessageId" to replyToMessageId
        )

        val batch = db.batch()
        batch.set(messageRef, messageData)

        batch.update(
            chatRef,
            mapOf(
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastMessageText" to text,
                "lastMessageSenderId" to senderId
            )
        )

        memberIds.forEach { uid ->
            batch.set(
                inboxRef(uid, chatId),
                mapOf(
                    "chatId" to chatId,
                    "title" to (chatSnap.getString("title") ?: "Chat"),
                    "type" to (chatSnap.getString("type") ?: "dm"),
                    "lastMessageText" to text,
                    "lastMessageAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }

        batch.commit().await()
        return messageId
    }

    /**
     * Listen to recent messages in realtime for chat logs.
     * Returns a ListenerRegistration; call remove() when the screen is disposed.
     *
     * Firestore already returns these ordered by createdAt DESC.
     * We preserve snapshot order in the UI instead of re-sorting by hasPendingWrites,
     * because re-sorting by metadata can make messages jump around.
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

        return q.addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
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
     * This function specifically updates the most recent messages of each saved chat in the Friends screen.
     */
    fun listenToMyChats(
        myUid: String,
        onSnapshot: (List<ChatListItem>) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ): ListenerRegistration {
        return usersRef.document(myUid)
            .collection("inbox")
            .whereEqualTo("hidden", false)
            .orderBy("lastMessageAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError?.invoke(err)
                    return@addSnapshotListener
                }

                if (snap == null) {
                    onSnapshot(emptyList())
                    return@addSnapshotListener
                }

                val chats = snap.documents.map { doc ->
                    ChatListItem(
                        id = doc.id,
                        title = doc.getString("title") ?: "Untitled Chat",
                        lastMessageText = doc.getString("lastMessageText")
                    )
                }

                onSnapshot(chats)
            }
    }

    fun listenToMyReadState(
        chatId: String,
        myUid: String,
        onSnapshot: (String?) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ): ListenerRegistration {
        return chatsRef.document(chatId)
            .collection("members")
            .document(myUid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError?.invoke(err)
                    return@addSnapshotListener
                }

                onSnapshot(snap?.getString("lastReadMessageId"))
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
     * Update the user's read state in the membership doc.
     * Call when user opens the chat or after a newly visible incoming message is shown.
     */
    suspend fun updateMyLastRead(
        chatId: String,
        myUid: String,
        lastReadMessageId: String?
    ) {
        val memberRef = chatsRef.document(chatId).collection("members").document(myUid)

        memberRef.update(
            mapOf(
                "lastReadAt" to FieldValue.serverTimestamp(),
                "lastReadMessageId" to lastReadMessageId
            )
        ).await()
    }

    private fun docToMessage(doc: DocumentSnapshot): Message? {
        val senderId = doc.getString("senderId") ?: return null

        return Message(
            id = doc.id,
            senderId = senderId,
            type = doc.getString("type") ?: "text",
            text = doc.getString("text"),
            createdAt = doc.getTimestamp("createdAt"),
            replyToMessageId = doc.getString("replyToMessageId"),
            hasPendingWrites = doc.metadata.hasPendingWrites()
        )
    }

    /**
     * Hides chats from users without deleting them from database.
     */
    suspend fun hideChatForUser(chatId: String, myUid: String) {
        inboxRef(myUid, chatId).update("hidden", true).await()
    }

    suspend fun getChatTitle(chatId: String): String {
        val doc = chatsRef.document(chatId).get().await()
        return doc.getString("title") ?: "Chat"
    }

    fun listenToDmReadReceipt(
        chatId: String,
        myUid: String,
        onSnapshot: (ReadReceiptState) -> Unit,
        onError: ((Exception) -> Unit)? = null
    ): ListenerRegistration {
        val membersRef = chatsRef.document(chatId).collection("members")

        return membersRef.addSnapshotListener { snap, err ->
            if (err != null) {
                onError?.invoke(err)
                return@addSnapshotListener
            }

            if (snap == null) {
                onSnapshot(ReadReceiptState())
                return@addSnapshotListener
            }

            val otherDoc = snap.documents.firstOrNull { it.id != myUid }

            onSnapshot(
                ReadReceiptState(
                    otherUserId = otherDoc?.id,
                    otherUserLastReadAt = otherDoc?.getTimestamp("lastReadAt"),
                    otherUserLastReadMessageId = otherDoc?.getString("lastReadMessageId")
                )
            )
        }
    }

    private fun inboxRef(uid: String, chatId: String) =
        usersRef.document(uid).collection("inbox").document(chatId)
}