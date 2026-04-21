const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

exports.sendChatNotification = functions.firestore
    .document('chats/{chatId}/messages/{messageId}')
    .onCreate(async (snap, context) => {
        const messageData = snap.data();
        const chatId = context.params.chatId;
        const senderId = messageData.senderId;
        const messageText = messageData.text || 'New message';
        const messageType = messageData.type || 'text';

        // System messages shouldn't trigger notifications
        if (messageType === 'system') {
            return null;
        }

        try {
            // 1. Get chat document to know title and members
            const chatDoc = await db.collection('chats').doc(chatId).get();
            if (!chatDoc.exists) {
                console.error(`Chat ${chatId} not found`);
                return null;
            }
            const chatData = chatDoc.data();
            const chatTitle = chatData.title || 'Chat';
            const memberIds = chatData.memberIds || [];

            // 2. Get sender's display name for notification body
            const senderDoc = await db.collection('users').doc(senderId).get();
            const senderName = senderDoc.exists
                ? (senderDoc.data().displayName || senderDoc.data().name || 'Someone')
                : 'Someone';

            // 3. Collect all device tokens for members except sender
            const tokens = [];
            for (const uid of memberIds) {
                if (uid === senderId) continue;

                const devicesSnapshot = await db
                    .collection('users')
                    .doc(uid)
                    .collection('devices')
                    .get();

                devicesSnapshot.forEach(deviceDoc => {
                    const token = deviceDoc.data().token;
                    if (token) tokens.push(token);
                });
            }

            if (tokens.length === 0) {
                console.log('No tokens to send notification to');
                return null;
            }

            // 4. Build notification payload
            const payload = {
                notification: {
                    title: chatTitle,
                    body: `${senderName}: ${messageText}`
                },
                data: {
                    chatId: chatId,
                    chatTitle: chatTitle,
                    click_action: 'FLUTTER_NOTIFICATION_CLICK' // or your custom action
                },
                tokens: tokens // Up to 500 tokens per call
            };

            // 5. Send multicast message
            const response = await messaging.sendMulticast(payload);
            console.log(`${response.successCount} notifications sent, ${response.failureCount} failed`);

            if (response.failureCount > 0) {
                // Optionally clean up invalid tokens
                response.responses.forEach((resp, idx) => {
                    if (!resp.success) {
                        console.error('Failed token:', tokens[idx], resp.error);
                        // TODO: remove invalid token from Firestore
                    }
                });
            }

            return null;
        } catch (error) {
            console.error('Error sending notification:', error);
            return null;
        }
    });