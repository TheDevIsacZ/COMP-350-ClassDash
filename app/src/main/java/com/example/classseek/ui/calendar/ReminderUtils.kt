package com.example.classseek.ui.calendar

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun saveRemindersToFirestore(
    uid: String,
    eventId: String,
    eventTitle: String,
    eventTimeMillis: Long,
    reminders: List<Int>
) {
    val db = FirebaseFirestore.getInstance()
    val userRef = db.collection("users").document(uid)
    
    // 1. Delete existing reminders for this event to avoid duplicates/orphans
    userRef.collection("reminders")
        .whereEqualTo("eventId", eventId)
        .get()
        .addOnSuccessListener { snapshot ->
            val batch = db.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }
            
            // 2. Add new reminders
            reminders.forEach { minutes ->
                val eventDate = Date(eventTimeMillis)
                val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
                val dateFormatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                val formattedTime = timeFormatter.format(eventDate)
                val formattedDate = dateFormatter.format(eventDate)

                val reminderTimeInMillis = eventTimeMillis - (minutes * 60 * 1000L)
                
                val reminderData = hashMapOf(
                    "eventId" to eventId,
                    "eventTitle" to eventTitle,
                    "eventTime" to eventTimeMillis,
                    "eventTimeFormatted" to formattedTime,
                    "eventDateFormatted" to formattedDate,
                    "reminderMinutes" to minutes,
                    "reminderTime" to reminderTimeInMillis,
                    "notificationSent" to false,
                )
                
                // Use a unique ID for each reminder instance (eventId + minutes)
                val reminderDocId = "${eventId}_$minutes"
                batch.set(userRef.collection("reminders").document(reminderDocId), reminderData)
            }
            
            batch.commit().addOnSuccessListener {
                Log.d("REMINDER_DEBUG", "✅ Reminders updated successfully for $eventId")
            }
        }

    // 3. Save the user's preferences (the list of minutes)
    val preferenceData = hashMapOf(
        "eventId" to eventId,
        "selectedMinutesList" to reminders,
        "lastUpdated" to System.currentTimeMillis()
    )

    userRef.collection("reminderPreferences")
        .document(eventId)
        .set(preferenceData)
        .addOnSuccessListener {
            Log.d("REMINDER_DEBUG", "✅ Reminder preferences saved for event $eventId")
        }
}

fun deleteReminderFromFirestore(uid: String, eventId: String) {
    val db = FirebaseFirestore.getInstance()
    val userRef = db.collection("users").document(uid)

    userRef.collection("reminders")
        .whereEqualTo("eventId", eventId)
        .get()
        .addOnSuccessListener { snapshot ->
            val batch = db.batch()
            snapshot.documents.forEach { batch.delete(it.reference) }
            batch.commit()
        }

    userRef.collection("reminderPreferences")
        .document(eventId)
        .delete()
}
