package com.example.classseek.Notification

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object FcmManager {
    private const val TAG = "FcmManager"
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Saves the current FCM token to Firestore under the user's devices subcollection.
     * Call this after sign‑in and whenever the token refreshes.
     */
    suspend fun saveCurrentToken() {
        val user = auth.currentUser ?: return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            saveTokenToFirestore(user.uid, token)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get or save FCM token", e)
        }
    }

    /**
     * Saves a given token to Firestore.
     */
    suspend fun saveTokenToFirestore(uid: String, token: String) {
        val deviceDoc = mapOf(
            "token" to token,
            "platform" to "android",
            "updatedAt" to FieldValue.serverTimestamp()
        )
        db.collection("users")
            .document(uid)
            .collection("devices")
            .document(token)
            .set(deviceDoc)
            .await()
        Log.d(TAG, "FCM token saved for user $uid")
    }

    /**
     * Deletes a specific token (e.g., on logout).
     */
    suspend fun deleteToken(uid: String, token: String) {
        db.collection("users")
            .document(uid)
            .collection("devices")
            .document(token)
            .delete()
            .await()
    }

    /**
     * Subscribes the current device to a topic (e.g., for broadcast notifications).
     */
    suspend fun subscribeToTopic(topic: String) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic).await()
        Log.d(TAG, "Subscribed to topic: $topic")
    }

    /**
     * Unsubscribes from a topic.
     */
    suspend fun unsubscribeFromTopic(topic: String) {
        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).await()
        Log.d(TAG, "Unsubscribed from topic: $topic")
    }
}
