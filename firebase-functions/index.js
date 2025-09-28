/**
 * Cloud Functions for Firebase
 * 
 * This file contains cloud functions that respond to events in Firebase and 
 * handle sending push notifications through Firebase Cloud Messaging
 */

const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

/**
 * Firebase Cloud Function triggered when a new document is created in the notifications collection
 * It processes the notification data and sends FCM messages to the intended recipients
 */
exports.sendNotification = functions.firestore
  .document('notifications/{notificationId}')
  .onCreate(async (snapshot, context) => {
    try {
      const notificationData = snapshot.data();
      
      // Skip already processed notifications
      if (notificationData.processed === true) {
        console.log('Notification already processed, skipping...');
        return null;
      }
      
      console.log('Processing notification:', notificationData);
      
      // Get the list of recipients
      const participants = notificationData.participants || [];
      const mentionedUsers = notificationData.mentionedUsers || [];
      const isEveryoneMention = notificationData.mentionsEveryone || false;
      const senderName = notificationData.senderName || 'Someone';
      const groupName = notificationData.groupName || 'Group Chat';
      const message = notificationData.message || '';
      
      if (participants.length === 0) {
        console.log('No participants to notify');
        return null;
      }
      
      // Fetch FCM tokens for all participants
      const db = admin.firestore();
      const fcmTokens = {};
      const userPromises = participants.map(async (userId) => {
        const userDoc = await db.collection('users').doc(userId).get();
        if (userDoc.exists) {
          const userData = userDoc.data();
          if (userData.fcmToken) {
            fcmTokens[userId] = userData.fcmToken;
          }
        }
      });
      
      await Promise.all(userPromises);
      
      console.log(`Found ${Object.keys(fcmTokens).length} FCM tokens for ${participants.length} participants`);
      
      // Prepare notifications
      const notificationPromises = [];
      
      // Handle the @everyone mention
      if (isEveryoneMention) {
        // All participants get the same high-priority notification
        const everyoneTokens = Object.values(fcmTokens);
        
        if (everyoneTokens.length > 0) {
          const notificationPayload = {
            notification: {
              title: `${senderName} mentioned everyone in ${groupName}`,
              body: message,
              clickAction: 'OPEN_GROUP_ACTIVITY',
              sound: 'default'
            },
            data: {
              groupId: notificationData.groupId,
              groupName: notificationData.groupName,
              senderId: notificationData.senderRollNumber,
              senderName: notificationData.senderName,
              message: notificationData.message,
              isEveryone: 'true',
              isTagged: 'false'
            }
          };
          
          console.log(`Sending @everyone notification to ${everyoneTokens.length} tokens`);
          notificationPromises.push(
            admin.messaging().sendMulticast({
              tokens: everyoneTokens,
              notification: notificationPayload.notification,
              data: notificationPayload.data
            })
          );
        }
      } 
      // Handle individual user mentions
      else if (mentionedUsers.length > 0) {
        // Mentioned users get high-priority notifications
        const mentionedTokens = [];
        const mentionedUserIds = [];
        
        mentionedUsers.forEach(userId => {
          if (fcmTokens[userId]) {
            mentionedTokens.push(fcmTokens[userId]);
            mentionedUserIds.push(userId);
            delete fcmTokens[userId]; // Remove from regular recipients
          }
        });
        
        if (mentionedTokens.length > 0) {
          const taggedNotificationPayload = {
            notification: {
              title: `${senderName} mentioned you in ${groupName}`,
              body: message,
              clickAction: 'OPEN_GROUP_ACTIVITY',
              sound: 'default'
            },
            data: {
              groupId: notificationData.groupId,
              groupName: notificationData.groupName,
              senderId: notificationData.senderRollNumber,
              senderName: notificationData.senderName,
              message: notificationData.message,
              isEveryone: 'false',
              isTagged: 'true'
            }
          };
          
          console.log(`Sending mention notification to ${mentionedTokens.length} users`);
          notificationPromises.push(
            admin.messaging().sendMulticast({
              tokens: mentionedTokens,
              notification: taggedNotificationPayload.notification,
              data: taggedNotificationPayload.data
            })
          );
        }
        
        // Regular notification for other participants
        const regularTokens = Object.values(fcmTokens);
        if (regularTokens.length > 0) {
          const regularNotificationPayload = {
            notification: {
              title: `${senderName} in ${groupName}`,
              body: message,
              clickAction: 'OPEN_GROUP_ACTIVITY',
              sound: 'default'
            },
            data: {
              groupId: notificationData.groupId,
              groupName: notificationData.groupName,
              senderId: notificationData.senderRollNumber,
              senderName: notificationData.senderName,
              message: notificationData.message,
              isEveryone: 'false',
              isTagged: 'false'
            }
          };
          
          console.log(`Sending regular notification to ${regularTokens.length} users`);
          notificationPromises.push(
            admin.messaging().sendMulticast({
              tokens: regularTokens,
              notification: regularNotificationPayload.notification,
              data: regularNotificationPayload.data
            })
          );
        }
      } 
      // Send regular notifications to all participants
      else {
        const regularTokens = Object.values(fcmTokens);
        if (regularTokens.length > 0) {
          const regularNotificationPayload = {
            notification: {
              title: `${senderName} in ${groupName}`,
              body: message,
              clickAction: 'OPEN_GROUP_ACTIVITY',
              sound: 'default'
            },
            data: {
              groupId: notificationData.groupId,
              groupName: notificationData.groupName,
              senderId: notificationData.senderRollNumber,
              senderName: notificationData.senderName,
              message: notificationData.message,
              isEveryone: 'false',
              isTagged: 'false'
            }
          };
          
          console.log(`Sending regular notification to ${regularTokens.length} users`);
          notificationPromises.push(
            admin.messaging().sendMulticast({
              tokens: regularTokens,
              notification: regularNotificationPayload.notification,
              data: regularNotificationPayload.data
            })
          );
        }
      }
      
      // Execute all notification sends
      const results = await Promise.all(notificationPromises);
      
      // Mark notification as processed
      await snapshot.ref.update({
        processed: true,
        processedAt: admin.firestore.FieldValue.serverTimestamp(),
        sendResults: results.map(result => ({
          successCount: result.successCount,
          failureCount: result.failureCount
        }))
      });
      
      console.log('Notification processing complete');
      return null;
    } catch (error) {
      console.error('Error sending notification:', error);
      return null;
    }
  }); 