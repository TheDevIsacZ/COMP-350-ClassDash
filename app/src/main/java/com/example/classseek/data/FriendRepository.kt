package com.example.classseek.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class FriendRelationshipState(
    val isFriend: Boolean = false,
    val hasOutgoingPendingRequest: Boolean = false,
    val hasIncomingPendingRequest: Boolean = false,
    val allowMessagesByFriendsOnly: Boolean = false
) {
    val canMessageNow: Boolean
        get() = !allowMessagesByFriendsOnly || isFriend

    val messageButtonLabel: String
        get() = if (canMessageNow) "Message" else "Friends only"

    val friendButtonLabel: String
        get() = when {
            isFriend -> "Friends"
            hasOutgoingPendingRequest -> "Requested"
            hasIncomingPendingRequest -> "Respond in Inbox"
            else -> "Add Friend"
        }

    val friendButtonEnabled: Boolean
        get() = !isFriend && !hasOutgoingPendingRequest && !hasIncomingPendingRequest
}

data class FriendSummary(
    val uid: String,
    val displayName: String,
    val email: String,
    val profilePictureUrl: String = "",
    val chatId: String? = null
)

data class FriendRequestSummary(
    val uid: String,
    val fromUid: String,
    val toUid: String,
    val displayName: String,
    val email: String,
    val profilePictureUrl: String = "",
    val status: String = "pending"
)

class FriendRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val chatRepository: ChatRepository = ChatRepository(db)
) {

    suspend fun getRelationshipState(currentUid: String, targetUid: String): FriendRelationshipState {
        val friendDoc = db.collection("users")
            .document(currentUid)
            .collection("friends")
            .document(targetUid)
            .get()
            .await()

        val outgoingRequestDoc = db.collection("users")
            .document(currentUid)
            .collection("sentFriendRequests")
            .document(targetUid)
            .get()
            .await()

        val incomingRequestDoc = db.collection("users")
            .document(currentUid)
            .collection("friendRequests")
            .document(targetUid)
            .get()
            .await()

        val targetUserDoc = db.collection("users")
            .document(targetUid)
            .get()
            .await()

        return FriendRelationshipState(
            isFriend = friendDoc.exists() && friendDoc.getString("status") == "accepted",
            hasOutgoingPendingRequest = outgoingRequestDoc.exists()
                && outgoingRequestDoc.getString("status") == "pending",
            hasIncomingPendingRequest = incomingRequestDoc.exists()
                && incomingRequestDoc.getString("status") == "pending",
            allowMessagesByFriendsOnly = targetUserDoc.getBoolean("allowMessagesByFriendsOnly") == true
        )
    }

    suspend fun getRelationshipStates(
        currentUid: String,
        targetUids: List<String>
    ): Map<String, FriendRelationshipState> {
        return targetUids.distinct().associateWith { targetUid ->
            getRelationshipState(currentUid, targetUid)
        }
    }

    suspend fun sendFriendRequest(currentUid: String, targetUid: String) {
        if (currentUid == targetUid) {
            throw IllegalArgumentException("You cannot add yourself")
        }

        val existingFriend = db.collection("users")
            .document(currentUid)
            .collection("friends")
            .document(targetUid)
            .get()
            .await()

        if (existingFriend.exists() && existingFriend.getString("status") == "accepted") {
            throw IllegalStateException("You are already friends")
        }

        val outgoingExisting = db.collection("users")
            .document(currentUid)
            .collection("sentFriendRequests")
            .document(targetUid)
            .get()
            .await()

        if (outgoingExisting.exists() && outgoingExisting.getString("status") == "pending") {
            throw IllegalStateException("Friend request already sent")
        }

        val meDoc = db.collection("users").document(currentUid).get().await()
        val targetDoc = db.collection("users").document(targetUid).get().await()

        if (!targetDoc.exists()) {
            throw IllegalStateException("User no longer exists")
        }

        val myDisplayName = meDoc.getString("displayName")?.trim().orEmpty()
            .ifBlank { meDoc.getString("name")?.trim().orEmpty() }
            .ifBlank { meDoc.getString("email")?.trim().orEmpty() }

        val myEmail = meDoc.getString("email")?.trim().orEmpty()
        val myProfilePictureUrl = meDoc.getString("profilePictureUrl")?.trim().orEmpty()

        val targetDisplayName = targetDoc.getString("displayName")?.trim().orEmpty()
            .ifBlank { targetDoc.getString("name")?.trim().orEmpty() }
            .ifBlank { targetDoc.getString("email")?.trim().orEmpty() }

        val targetEmail = targetDoc.getString("email")?.trim().orEmpty()
        val targetProfilePictureUrl = targetDoc.getString("profilePictureUrl")?.trim().orEmpty()

        val now = Timestamp.now()

        val incomingPayload = hashMapOf(
            "uid" to currentUid,
            "fromUid" to currentUid,
            "toUid" to targetUid,
            "displayName" to myDisplayName,
            "email" to myEmail,
            "profilePictureUrl" to myProfilePictureUrl,
            "status" to "pending",
            "createdAt" to now,
            "updatedAt" to now
        )

        val outgoingPayload = hashMapOf(
            "uid" to targetUid,
            "fromUid" to currentUid,
            "toUid" to targetUid,
            "displayName" to targetDisplayName,
            "email" to targetEmail,
            "profilePictureUrl" to targetProfilePictureUrl,
            "status" to "pending",
            "createdAt" to now,
            "updatedAt" to now
        )

        db.runBatch { batch ->
            val incomingRef = db.collection("users")
                .document(targetUid)
                .collection("friendRequests")
                .document(currentUid)

            val outgoingRef = db.collection("users")
                .document(currentUid)
                .collection("sentFriendRequests")
                .document(targetUid)

            batch.set(incomingRef, incomingPayload)
            batch.set(outgoingRef, outgoingPayload)
        }.await()
    }

    suspend fun cancelFriendRequest(currentUid: String, targetUid: String) {
        db.runBatch { batch ->
            batch.delete(
                db.collection("users")
                    .document(currentUid)
                    .collection("sentFriendRequests")
                    .document(targetUid)
            )
            batch.delete(
                db.collection("users")
                    .document(targetUid)
                    .collection("friendRequests")
                    .document(currentUid)
            )
        }.await()
    }

    suspend fun declineFriendRequest(myUid: String, fromUid: String) {
        db.runBatch { batch ->
            batch.delete(
                db.collection("users")
                    .document(myUid)
                    .collection("friendRequests")
                    .document(fromUid)
            )
            batch.delete(
                db.collection("users")
                    .document(fromUid)
                    .collection("sentFriendRequests")
                    .document(myUid)
            )
        }.await()
    }

    suspend fun acceptFriendRequest(myUid: String, fromUid: String): String {
        val myUserDoc = db.collection("users").document(myUid).get().await()
        val fromUserDoc = db.collection("users").document(fromUid).get().await()

        if (!myUserDoc.exists() || !fromUserDoc.exists()) {
            throw IllegalStateException("User data missing")
        }

        val myDisplayName = myUserDoc.getString("displayName")?.trim().orEmpty()
            .ifBlank { myUserDoc.getString("name")?.trim().orEmpty() }
            .ifBlank { myUserDoc.getString("email")?.trim().orEmpty() }

        val fromDisplayName = fromUserDoc.getString("displayName")?.trim().orEmpty()
            .ifBlank { fromUserDoc.getString("name")?.trim().orEmpty() }
            .ifBlank { fromUserDoc.getString("email")?.trim().orEmpty() }

        val chatId = chatRepository.openOrCreateDm(
            uidA = myUid,
            uidB = fromUid,
            title = fromDisplayName
        )

        val now = Timestamp.now()

        val myFriendPayload = hashMapOf(
            "uid" to fromUid,
            "displayName" to fromDisplayName,
            "email" to fromUserDoc.getString("email").orEmpty(),
            "profilePictureUrl" to fromUserDoc.getString("profilePictureUrl").orEmpty(),
            "status" to "accepted",
            "chatId" to chatId,
            "createdAt" to now,
            "addedAt" to now
        )

        val theirFriendPayload = hashMapOf(
            "uid" to myUid,
            "displayName" to myDisplayName,
            "email" to myUserDoc.getString("email").orEmpty(),
            "profilePictureUrl" to myUserDoc.getString("profilePictureUrl").orEmpty(),
            "status" to "accepted",
            "chatId" to chatId,
            "createdAt" to now,
            "addedAt" to now
        )

        db.runBatch { batch ->
            batch.set(
                db.collection("users").document(myUid)
                    .collection("friends").document(fromUid),
                myFriendPayload
            )
            batch.set(
                db.collection("users").document(fromUid)
                    .collection("friends").document(myUid),
                theirFriendPayload
            )

            batch.delete(
                db.collection("users").document(myUid)
                    .collection("friendRequests").document(fromUid)
            )
            batch.delete(
                db.collection("users").document(fromUid)
                    .collection("sentFriendRequests").document(myUid)
            )
        }.await()

        return chatId
    }

    suspend fun ensureFriendDm(myUid: String, friendUid: String): String {
        val relationship = getRelationshipState(myUid, friendUid)
        if (!relationship.isFriend) {
            throw IllegalStateException("You can only use this path for accepted friends")
        }

        val existingFriendDoc = db.collection("users")
            .document(myUid)
            .collection("friends")
            .document(friendUid)
            .get()
            .await()

        val existingChatId = existingFriendDoc.getString("chatId")
        if (!existingChatId.isNullOrBlank()) return existingChatId

        val friendUserDoc = db.collection("users").document(friendUid).get().await()
        val friendDisplayName = friendUserDoc.getString("displayName")?.trim().orEmpty()
            .ifBlank { friendUserDoc.getString("name")?.trim().orEmpty() }
            .ifBlank { friendUserDoc.getString("email")?.trim().orEmpty() }

        val chatId = chatRepository.openOrCreateDm(
            uidA = myUid,
            uidB = friendUid,
            title = friendDisplayName
        )

        db.runBatch { batch ->
            batch.update(
                db.collection("users").document(myUid)
                    .collection("friends").document(friendUid),
                "chatId", chatId
            )
            batch.update(
                db.collection("users").document(friendUid)
                    .collection("friends").document(myUid),
                "chatId", chatId
            )
        }.await()

        return chatId
    }

    suspend fun removeFriend(myUid: String, friendUid: String) {
        val myFriendDoc = db.collection("users")
            .document(myUid)
            .collection("friends")
            .document(friendUid)
            .get()
            .await()

        val chatId = myFriendDoc.getString("chatId")

        db.runBatch { batch ->
            batch.delete(
                db.collection("users").document(myUid)
                    .collection("friends").document(friendUid)
            )
            batch.delete(
                db.collection("users").document(friendUid)
                    .collection("friends").document(myUid)
            )

            batch.delete(
                db.collection("users").document(myUid)
                    .collection("sentFriendRequests").document(friendUid)
            )
            batch.delete(
                db.collection("users").document(myUid)
                    .collection("friendRequests").document(friendUid)
            )
            batch.delete(
                db.collection("users").document(friendUid)
                    .collection("sentFriendRequests").document(myUid)
            )
            batch.delete(
                db.collection("users").document(friendUid)
                    .collection("friendRequests").document(myUid)
            )

            if (!chatId.isNullOrBlank()) {
                batch.set(
                    db.collection("users").document(myUid)
                        .collection("inbox").document(chatId),
                    mapOf("hidden" to true),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                batch.set(
                    db.collection("users").document(friendUid)
                        .collection("inbox").document(chatId),
                    mapOf("hidden" to true),
                    com.google.firebase.firestore.SetOptions.merge()
                )
            }
        }.await()
    }
}
