import * as admin from "firebase-admin";
import { onSchedule } from "firebase-functions/v2/scheduler";

export const sendEventReminders = onSchedule(
  {
    schedule: "every 1 minutes",
    timeZone: "America/Los_Angeles",
    retryCount: 3,
  },
  async (event) => {
    const now = admin.firestore.Timestamp.now();
    const tenMinutesFromNow = new Date(now.toDate().getTime() + 10 * 60 * 1000);
    const tenMinutesFromNowTimestamp = admin.firestore.Timestamp.fromDate(tenMinutesFromNow);

    console.log(`Running reminder check at ${now.toDate().toISOString()}`);

    const remindersSnap = await admin.firestore()
      .collectionGroup("reminders")
      .where("eventStart", "<=", tenMinutesFromNowTimestamp)
      .where("notified", "==", false)
      .get();

    if (remindersSnap.empty) {
      console.log("No pending reminders found.");
      return;
    }

    const batch = admin.firestore().batch();
    const messaging = admin.messaging();
    let successCount = 0;
    let failureCount = 0;

    for (const doc of remindersSnap.docs) {
      const reminder = doc.data();
      const eventId = reminder.eventId;
      const eventTitle = reminder.eventTitle || "Untitled Event";
      const userId = doc.ref.parent.parent?.id;

      if (!userId) {
        console.warn(`Could not determine user ID for reminder doc ${doc.ref.path}`);
        continue;
      }

      console.log(`Processing reminder for user ${userId}, event: ${eventTitle}`);

      const devicesSnap = await admin.firestore()
        .collection("users")
        .doc(userId)
        .collection("devices")
        .get();

      const tokens: string[] = [];
      devicesSnap.forEach((devDoc) => {
        const token = devDoc.data().token;
        if (token) tokens.push(token);
      });

      if (tokens.length === 0) {
        console.log(`User ${userId} has no registered devices.`);
      } else {
        const payload: admin.messaging.MulticastMessage = {
          notification: {
            title: "⏰ Event Reminder",
            body: `${eventTitle} starts in 10 minutes`,
          },
          data: {
            eventId: eventId,
            click_action: "FLUTTER_NOTIFICATION_CLICK",
          },
          tokens: tokens,
        };

        try {
          const response = await messaging.sendEachForMulticast(payload);
          successCount += response.successCount;
          failureCount += response.failureCount;

          response.responses.forEach((resp, idx) => {
            if (!resp.success) {
              const error = resp.error;
              if (
                error &&
                (error.code === "messaging/invalid-registration-token" ||
                 error.code === "messaging/registration-token-not-registered")
              ) {
                const badToken = tokens[idx];
                admin.firestore()
                  .collection("users")
                  .doc(userId)
                  .collection("devices")
                  .where("token", "==", badToken)
                  .get()
                  .then((snap) => {
                    snap.forEach((d) => d.ref.delete());
                  })
                  .catch((err) => console.error("Token cleanup error:", err));
              }
            }
          });
        } catch (e) {
          console.error(`Failed to send reminders to user ${userId}:`, e);
        }
      }

      batch.update(doc.ref, { notified: true });
    }

    await batch.commit();
    console.log(`Reminder job completed. Notifications: ${successCount} sent, ${failureCount} failed.`);
  }
);