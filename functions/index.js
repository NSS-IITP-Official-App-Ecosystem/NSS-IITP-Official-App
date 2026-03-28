/**
 * Import function triggers from their respective submodules:
 *
 * const {onCall} = require("firebase-functions/v2/https");
 * const {onDocumentWritten} = require("firebase-functions/v2/firestore");
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

// eslint-disable-next-line no-unused-vars
const { onRequest } = require("firebase-functions/v2/https");
// eslint-disable-next-line no-unused-vars
const logger = require("firebase-functions/logger");
// Firestore v2 triggers
const { onDocumentCreated, onDocumentDeleted } = require("firebase-functions/v2/firestore");

/**
 * Cloud Functions for Firebase
 * 
 * This file contains cloud functions that respond to events in Firebase and 
 * handle sending push notifications through Firebase Cloud Messaging
 */

const functions = require('firebase-functions');
const admin = require('firebase-admin');
admin.initializeApp();

// Utilities for HTTPS endpoints
const crypto = require('crypto');
const { GoogleAuth } = require('google-auth-library');

// Play Integrity API configuration
const PLAY_INTEGRITY_API_URL = 'https://playintegrity.googleapis.com/v1';
// Replace with your actual package name
const EXPECTED_PACKAGE_NAME = 'com.phad.chatapp';

/**
 * Verify Firebase Auth ID token from Authorization: Bearer header
 */
async function verifyAuthToken(req) {
  const authHeader = req.headers.authorization || '';
  const match = authHeader.match(/^Bearer (.+)$/);
  if (!match) throw Object.assign(new Error('Missing Authorization header'), { status: 401 });
  const idToken = match[1];
  try {
    const decoded = await admin.auth().verifyIdToken(idToken);
    return decoded; // contains uid, email, etc.
  } catch (e) {
    throw Object.assign(new Error('Invalid ID token'), { status: 401 });
  }
}

/**
 * Verify Play Integrity token for QR attendance access.
 * 
 * POST body: { integrityToken, userId, nonce }
 * Returns: { allowed: true/false, reason: string }
 * 
 * Verdict Rules:
 * - MUST: appRecognitionVerdict == PLAY_RECOGNIZED
 * - MUST: appLicensingVerdict == LICENSED  
 * - MUST: deviceRecognitionVerdict contains MEETS_DEVICE_INTEGRITY
 * - BLOCK: Emulator detected
 * - LOG ONLY: Root detected (soft block)
 */
exports.verifyPlayIntegrity = onRequest({ region: 'asia-south1' }, async (req, res) => {
  try {
    if (req.method !== 'POST') {
      throw Object.assign(new Error('Method not allowed'), { status: 405 });
    }

    // Verify Firebase Auth
    const decoded = await verifyAuthToken(req);
    const { integrityToken, userId, nonce } = req.body || {};

    if (!integrityToken) {
      throw Object.assign(new Error('Missing integrityToken'), { status: 400 });
    }

    console.log(`[PlayIntegrity] Verifying integrity for user: ${userId}`);

    // Decode the integrity token using Google's API
    const auth = new GoogleAuth({
      scopes: ['https://www.googleapis.com/auth/playintegrity']
    });
    const client = await auth.getClient();
    const projectId = await auth.getProjectId();

    // Call Play Integrity API to decode the token
    const decodeUrl = `${PLAY_INTEGRITY_API_URL}/${EXPECTED_PACKAGE_NAME}:decodeIntegrityToken`;

    const response = await client.request({
      url: decodeUrl,
      method: 'POST',
      data: {
        integrityToken: integrityToken
      }
    });

    const tokenPayload = response.data.tokenPayloadExternal;

    if (!tokenPayload) {
      console.error('[PlayIntegrity] No token payload in response');
      res.json({ allowed: false, reason: 'Invalid integrity token' });
      return;
    }

    console.log('[PlayIntegrity] Token payload received:', JSON.stringify(tokenPayload, null, 2));

    // Extract verdicts
    const appIntegrity = tokenPayload.appIntegrity || {};
    const deviceIntegrity = tokenPayload.deviceIntegrity || {};
    const accountDetails = tokenPayload.accountDetails || {};
    const requestDetails = tokenPayload.requestDetails || {};

    // Verify nonce matches (optional but recommended)
    const receivedNonce = requestDetails.nonce;
    if (nonce && receivedNonce && receivedNonce !== nonce) {
      console.warn('[PlayIntegrity] Nonce mismatch - possible replay attack');
      // We'll still continue but log the warning
    }

    // Check app recognition verdict
    const appRecognitionVerdict = appIntegrity.appRecognitionVerdict;
    if (appRecognitionVerdict !== 'PLAY_RECOGNIZED') {
      console.warn(`[PlayIntegrity] App not recognized from Play Store: ${appRecognitionVerdict}`);
      res.json({
        allowed: false,
        reason: 'App is not installed from Google Play Store'
      });
      return;
    }

    // Check app licensing verdict
    const licensingVerdict = accountDetails.appLicensingVerdict;
    if (licensingVerdict !== 'LICENSED') {
      console.warn(`[PlayIntegrity] App not licensed: ${licensingVerdict}`);
      res.json({
        allowed: false,
        reason: 'App is not properly licensed from Play Store'
      });
      return;
    }

    // Check device integrity verdict
    const deviceVerdict = deviceIntegrity.deviceRecognitionVerdict || [];

    // Check for emulator (BLOCK)
    if (deviceVerdict.includes('VIRTUAL_DEVICE')) {
      console.warn('[PlayIntegrity] Emulator detected - blocking');
      res.json({
        allowed: false,
        reason: 'QR attendance is not available on emulators'
      });
      return;
    }

    // Check for basic device integrity (allow budget phones without STRONG_INTEGRITY)
    if (!deviceVerdict.includes('MEETS_DEVICE_INTEGRITY')) {
      console.warn(`[PlayIntegrity] Device does not meet integrity requirements: ${deviceVerdict.join(', ')}`);
      res.json({
        allowed: false,
        reason: 'Device integrity check failed'
      });
      return;
    }

    // Soft check for rooted devices (log only, don't block)
    if (!deviceVerdict.includes('MEETS_STRONG_INTEGRITY')) {
      console.info('[PlayIntegrity] Device may be rooted or have unlocked bootloader (soft warning, not blocking)');
      // Continue anyway - this is just for logging
    }

    // Log successful verification
    console.log(`[PlayIntegrity] ✅ Integrity verification PASSED for user: ${userId}`);
    console.log(`[PlayIntegrity] App: ${appRecognitionVerdict}, License: ${licensingVerdict}, Device: ${deviceVerdict.join(', ')}`);

    // Log successful verification
    console.log(`[PlayIntegrity] ✅ Integrity verification PASSED for user: ${userId}`);
    console.log(`[PlayIntegrity] App: ${appRecognitionVerdict}, License: ${licensingVerdict}, Device: ${deviceVerdict.join(', ')}`);

    // Integrity log saving removed as per request to save storage/writes.

    res.json({ allowed: true, reason: 'Integrity check passed' });

  } catch (err) {
    console.error('[PlayIntegrity] Error:', err);

    // Don't expose internal error details to client
    const status = err.status || 500;
    res.status(status).json({
      allowed: false,
      reason: status === 401 ? 'Authentication required' : 'Verification failed, please try again'
    });
  }
});

function sendError(res, err) {
  const status = err && err.status ? err.status : 500;
  res.status(status).json({ error: err.message || 'Internal error' });
}

function generateNonce(bytes = 32) {
  return crypto.randomBytes(bytes).toString('base64url');
}

function pemToKeyObject(pem) {
  return crypto.createPublicKey({ key: pem, format: 'pem' });
}

async function assertUserIdentityMatches(db, rollNumber, decodedToken) {
  // This project stores users by rollNumber in Firestore. We map auth identity to that user by email field.
  // If you want to enforce a different mapping, adjust here.
  const userSnap = await db.collection('users').doc(rollNumber).get();
  if (!userSnap.exists) throw Object.assign(new Error('User not found'), { status: 404 });
  const data = userSnap.data() || {};
  const storedEmail = data.instituteOutlookId || data.email;
  if (storedEmail && decodedToken.email && storedEmail.toLowerCase() === decodedToken.email.toLowerCase()) return;
  // If no email on file, allow but log; otherwise enforce match
  if (!storedEmail) return;
  throw Object.assign(new Error('Authenticated user does not match roll number'), { status: 403 });
}

/**
 * Issue a short-lived bind challenge for device binding
 * POST body: { rollNumber }
 */
exports.getDeviceBindChallenge = onRequest({ region: 'asia-south1' }, async (req, res) => {
  try {
    if (req.method !== 'POST') throw Object.assign(new Error('Method not allowed'), { status: 405 });
    const decoded = await verifyAuthToken(req);
    const { rollNumber } = req.body || {};
    if (!rollNumber) throw Object.assign(new Error('Missing rollNumber'), { status: 400 });
    const db = admin.firestore();
    await assertUserIdentityMatches(db, rollNumber, decodedToken = decoded);
    const nonce = generateNonce();
    const expiresAt = admin.firestore.Timestamp.fromDate(new Date(Date.now() + 5 * 60 * 1000));
    await db.collection('users').doc(rollNumber)
      .collection('deviceChallenges').doc('bind')
      .set({ nonce, expiresAt }, { merge: true });
    res.json({ nonce, expiresAt: expiresAt.toMillis() });
  } catch (err) {
    sendError(res, err);
  }
});

/**
 * Bind a device public key when user has no active binding (NULL policy)
 * POST body: { rollNumber, publicKeyPem, signatureBase64 } where signature = sign(nonce)
 */
exports.bindDevice = onRequest({ region: 'asia-south1' }, async (req, res) => {
  try {
    if (req.method !== 'POST') throw Object.assign(new Error('Method not allowed'), { status: 405 });
    const decoded = await verifyAuthToken(req);
    const { rollNumber, publicKeyPem, signatureBase64 } = req.body || {};
    if (!rollNumber || !publicKeyPem || !signatureBase64) throw Object.assign(new Error('Missing fields'), { status: 400 });
    const db = admin.firestore();
    await assertUserIdentityMatches(db, rollNumber, decoded);

    const userRef = db.collection('users').doc(rollNumber);
    await db.runTransaction(async (tx) => {
      const userDoc = await tx.get(userRef);
      if (!userDoc.exists) throw Object.assign(new Error('User not found'), { status: 404 });
      const deviceBinding = userDoc.get('deviceBinding') || null;
      if (deviceBinding && deviceBinding.status === 'active') {
        throw Object.assign(new Error('Device already bound'), { status: 409 });
      }
      const challengeDocRef = userRef.collection('deviceChallenges').doc('bind');
      const challengeSnap = await tx.get(challengeDocRef);
      if (!challengeSnap.exists) throw Object.assign(new Error('No challenge found'), { status: 400 });
      const { nonce, expiresAt } = challengeSnap.data();
      if (!nonce || !expiresAt) throw Object.assign(new Error('Invalid challenge'), { status: 400 });
      if (expiresAt.toMillis() < Date.now()) throw Object.assign(new Error('Challenge expired'), { status: 400 });

      // Verify client signed the nonce with the provided public key
      const verifier = crypto.createVerify('SHA256');
      verifier.update(Buffer.from(nonce));
      verifier.end();
      const pubKey = pemToKeyObject(publicKeyPem);
      const isValid = verifier.verify(pubKey, Buffer.from(signatureBase64, 'base64'));
      if (!isValid) throw Object.assign(new Error('Invalid signature'), { status: 400 });

      const binding = {
        publicKey: publicKeyPem,
        fingerprint: crypto.createHash('sha256').update(pubKey.export({ type: 'spki', format: 'der' })).digest('hex'),
        status: 'active',
        registeredAt: admin.firestore.FieldValue.serverTimestamp(),
        lastVerifiedAt: admin.firestore.FieldValue.serverTimestamp(),
      };
      tx.set(userRef, { deviceBinding: binding }, { merge: true });
      tx.delete(challengeDocRef);
    });
    res.json({ ok: true });
  } catch (err) {
    sendError(res, err);
  }
});

/**
 * Issue attendance challenge for the bound device
 * POST body: { rollNumber, eventId }
 */
exports.getAttendanceChallenge = onRequest({ region: 'asia-south1' }, async (req, res) => {
  try {
    if (req.method !== 'POST') throw Object.assign(new Error('Method not allowed'), { status: 405 });
    const decoded = await verifyAuthToken(req);
    const { rollNumber, eventId } = req.body || {};
    if (!rollNumber || !eventId) throw Object.assign(new Error('Missing fields'), { status: 400 });
    const db = admin.firestore();
    await assertUserIdentityMatches(db, rollNumber, decoded);
    const userRef = db.collection('users').doc(rollNumber);
    const userSnap = await userRef.get();
    const deviceBinding = userSnap.get('deviceBinding') || null;
    if (!deviceBinding || deviceBinding.status !== 'active') {
      throw Object.assign(new Error('No active device binding'), { status: 403 });
    }
    const nonce = generateNonce();
    const expiresAt = admin.firestore.Timestamp.fromDate(new Date(Date.now() + 2 * 60 * 1000));
    await userRef.collection('deviceChallenges').doc(`attendance_${eventId}`)
      .set({ nonce, expiresAt }, { merge: true });
    res.json({ nonce, expiresAt: expiresAt.toMillis() });
  } catch (err) {
    sendError(res, err);
  }
});

/**
 * Verify attendance signature and write attendance (server-side)
 * POST body: { rollNumber, eventId, attendee, signatureBase64 }
 *   where signature signs the nonce from getAttendanceChallenge
 */
exports.markAttendance = onRequest({ region: 'asia-south1' }, async (req, res) => {
  try {
    if (req.method !== 'POST') throw Object.assign(new Error('Method not allowed'), { status: 405 });
    const decoded = await verifyAuthToken(req);
    const { rollNumber, eventId, signatureBase64, attendee } = req.body || {};
    if (!rollNumber || !eventId || !signatureBase64 || !attendee) throw Object.assign(new Error('Missing fields'), { status: 400 });
    const db = admin.firestore();
    await assertUserIdentityMatches(db, rollNumber, decoded);
    const userRef = db.collection('users').doc(rollNumber);
    const userSnap = await userRef.get();
    const deviceBinding = userSnap.get('deviceBinding') || null;
    if (!deviceBinding || deviceBinding.status !== 'active') throw Object.assign(new Error('No active device binding'), { status: 403 });

    const challengeRef = userRef.collection('deviceChallenges').doc(`attendance_${eventId}`);
    const challengeSnap = await challengeRef.get();
    if (!challengeSnap.exists) throw Object.assign(new Error('No challenge found'), { status: 400 });
    const { nonce, expiresAt } = challengeSnap.data();
    if (!nonce || !expiresAt) throw Object.assign(new Error('Invalid challenge'), { status: 400 });
    if (expiresAt.toMillis() < Date.now()) throw Object.assign(new Error('Challenge expired'), { status: 400 });

    // Verify signature using stored public key
    const pubKey = pemToKeyObject(deviceBinding.publicKey);
    const verifier = crypto.createVerify('SHA256');
    verifier.update(Buffer.from(nonce));
    verifier.end();
    const isValid = verifier.verify(pubKey, Buffer.from(signatureBase64, 'base64'));
    if (!isValid) throw Object.assign(new Error('Invalid signature'), { status: 400 });

    // Build normalized attendee object (server authoritative timestamp)
    let scanLocation = null;
    if (attendee && attendee.scan_location && typeof attendee.scan_location.latitude === 'number' && typeof attendee.scan_location.longitude === 'number') {
      scanLocation = new admin.firestore.GeoPoint(attendee.scan_location.latitude, attendee.scan_location.longitude);
    }

    const normalizedAttendee = Object.assign({}, attendee, {
      roll_number: rollNumber.toUpperCase(),
      scan_timestamp: admin.firestore.Timestamp.now(),
      ...(scanLocation ? { scan_location: scanLocation } : {}),
    });

    // Proceed to write attendance using existing schema
    const eventRef = db.collection('NSS_Events_Attendence').doc(eventId);
    const attendanceDocRef = eventRef.collection('attendance').doc(rollNumber.toUpperCase());

    await db.runTransaction(async (tx) => {
      const eventSnap = await tx.get(eventRef);
      if (!eventSnap.exists) throw Object.assign(new Error('Event not found'), { status: 404 });
      tx.set(attendanceDocRef, normalizedAttendee);
      tx.update(eventRef, {
        attendees: admin.firestore.FieldValue.arrayUnion(normalizedAttendee),
        total_marked: admin.firestore.FieldValue.increment(1),
      });
    });

    await challengeRef.delete();
    await userRef.set({ deviceBinding: { ...deviceBinding, lastVerifiedAt: admin.firestore.FieldValue.serverTimestamp() } }, { merge: true });

    res.json({ ok: true });
  } catch (err) {
    sendError(res, err);
  }
});

// Increment counters and update user stats when a new attendance record is written
exports.onAttendanceCreate = onDocumentCreated(
  { document: 'NSS_Events_Attendence/{eventId}/attendance/{rollNumber}', region: 'asia-south1' },
  async (event) => {
    const { eventId, rollNumber } = event.params;
    const db = admin.firestore();
    const attendee = event.data?.data() || {};
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
          const monthIndex = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'].indexOf(month);
          if ((year === 2025 && monthIndex >= 6) || (year === 2026 && monthIndex <= 4)) {
            return year === 2025 ? 1 : 2;
          }
        } catch (e) { }
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
exports.onEventWrite = onDocumentCreated(
  { document: 'NSS_Events_Attendence/{eventId}', region: 'asia-south1' },
  async (event) => {
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
  }
);

exports.onEventDelete = onDocumentDeleted(
  { document: 'NSS_Events_Attendence/{eventId}', region: 'asia-south1' },
  async (event) => {
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
  }
);

/**
 * Firebase Cloud Function triggered when a new document is created in the notifications collection
 * It processes the notification data and sends FCM messages to the intended recipients
 */
exports.sendNotification = onDocumentCreated(
  { document: 'notifications/{notificationId}', region: 'asia-south1' },
  async (event) => {
    try {
      const snapshot = event.data;
      const notificationData = snapshot?.data() || {};

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

/**
 * Triggered when a new app_notification is created (e.g. for events/updates)
 * Sends a broadcast message to a specific topic
 */
exports.sendAppNotification = onDocumentCreated(
  { document: 'app_notifications/{notificationId}', region: 'asia-south1' },
  async (event) => {
    try {
      const notificationData = event.data?.data() || {};
      
      // Expected: "ttw_user", "nss_user", "all", etc.
      const topic = notificationData.targetType; 
      if (!topic) {
        console.log('No targetType specified, skipping broadcast.');
        return null;
      }

      const payload = {
        notification: {
          title: notificationData.title || 'New Notification',
          body: notificationData.message || notificationData.body || '',
        },
        data: {
          type: notificationData.type || 'general',
          relatedId: notificationData.relatedId || ''
        },
        topic: topic
      };

      await admin.messaging().send(payload);
      console.log(`Successfully broadcasted app notification to topic: ${topic}`);

      return null;
    } catch (error) {
      console.error('Error broadcasting app notification:', error);
      return null;
    }
  });
