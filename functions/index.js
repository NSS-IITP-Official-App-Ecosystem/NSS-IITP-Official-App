/**
 * Import function triggers from their respective submodules:
 *
 * const {onCall} = require("firebase-functions/v2/https");
 * const {onDocumentWritten} = require("firebase-functions/v2/firestore");
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

// eslint-disable-next-line no-unused-vars
const {onRequest} = require("firebase-functions/v2/https");
// eslint-disable-next-line no-unused-vars
const logger = require("firebase-functions/logger");

/**
 * Cloud Functions for Firebase
 * 
 * This file contains cloud functions that respond to events in Firebase and 
 * handle sending push notifications through Firebase Cloud Messaging
 */

const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

// Increment counters and update user stats when a new attendance record is written
exports.onAttendanceCreate = functions.firestore
  .document('NSS_Events_Attendence/{eventId}/attendance/{rollNumber}')
  .onCreate(async (snapshot, context) => {
    const {eventId, rollNumber} = context.params;
    const db = admin.firestore();
    const attendee = snapshot.data() || {};
    try {
      // Increment total_marked on parent event
      const eventRef = db.collection('NSS_Events_Attendence').doc(eventId);
      const eventSnap = await eventRef.get();
      if (!eventSnap.exists) {
        console.warn('Event not found for attendance create:', eventId);
        return null;
      }
      const event = eventSnap.data() || {};
      const hours = Number(event.hours) || 0;
      const eventDate = event.eventDate || '';

      await eventRef.update({ total_marked: admin.firestore.FieldValue.increment(1) });

      // Ensure meta.totalEvents exists and is equal to number of events documents
      // Increment events count only when event document is newly created elsewhere.

      // Update user stats atomically
      const userRef = db.collection('users').doc(rollNumber);
      const semester = (() => {
        // Expecting format like "dd MMM yyyy"; fallback to 0 if unknown
        try {
          const parts = eventDate.split(' ');
          const month = parts[1];
          const year = parseInt(parts[2], 10);
          const monthIndex = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'].indexOf(month);
          if ((year === 2025 && monthIndex >= 6) || (year === 2026 && monthIndex <= 4)) {
            return year === 2025 ? 1 : 2;
          }
        } catch (e) {}
        return 0;
      })();

      const updates = {
        eventsAttended: admin.firestore.FieldValue.increment(1),
        hours: admin.firestore.FieldValue.increment(hours),
        eventsList: admin.firestore.FieldValue.arrayUnion(eventId)
      };
      if (semester === 1) updates.sem1Hours = admin.firestore.FieldValue.increment(hours);
      if (semester === 2) updates.sem2Hours = admin.firestore.FieldValue.increment(hours);

      await userRef.set(updates, { merge: true });

      // Do not change totalEvents here; that belongs to event create. Optionally maintain semester totals of actual consumed hours if desired per attendance, but
      // to avoid over-counting for multiple attendees, we skip meta updates here.

      console.log('Attendance processed for', rollNumber, 'event', eventId);
      return null;
    } catch (e) {
      console.error('onAttendanceCreate error', e);
      return null;
    }
  });

// Maintain meta.statistics when an event is created or deleted
exports.onEventWrite = functions.firestore
  .document('NSS_Events_Attendence/{eventId}')
  .onCreate(async (snap, context) => {
    try {
      const db = admin.firestore();
      const metaRef = db.collection('meta').doc('statistics');
      await metaRef.set({
        totalEvents: admin.firestore.FieldValue.increment(1)
      }, { merge: true });
      return null;
    } catch (e) {
      console.error('onEventWrite create error', e);
      return null;
    }
  });

exports.onEventDelete = functions.firestore
  .document('NSS_Events_Attendence/{eventId}')
  .onDelete(async (snap, context) => {
    try {
      const db = admin.firestore();
      const metaRef = db.collection('meta').doc('statistics');
      await metaRef.set({
        totalEvents: admin.firestore.FieldValue.increment(-1)
      }, { merge: true });
      return null;
    } catch (e) {
      console.error('onEventWrite delete error', e);
      return null;
    }
  });

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
