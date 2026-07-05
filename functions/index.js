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

// Notification functions removed in favor of Vercel Serverless Backend


/**
 * Apply negative hours to volunteers who missed a mandatory event.
 * Called when an admin closes a mandatory event.
 * POST body: { eventId }
 */
exports.applyAbsentPenalty = onRequest({ region: 'asia-south1' }, async (req, res) => {
  try {
    if (req.method !== 'POST') throw Object.assign(new Error('Method not allowed'), { status: 405 });

    const decoded = await verifyAuthToken(req);
    const { eventId, positiveRollNumbers, negativeRollNumbers, zeroRollNumbers } = req.body || {};
    if (!eventId) throw Object.assign(new Error('Missing eventId'), { status: 400 });

    const db = admin.firestore();
    const eventRef = db.collection('NSS_Events_Attendence').doc(eventId);
    const eventSnap = await eventRef.get();

    if (!eventSnap.exists) throw Object.assign(new Error('Event not found'), { status: 404 });

    const event = eventSnap.data() || {};

    if (!event.mandatory) {
      return res.json({ ok: false, reason: 'Event is not mandatory. No penalty applied.' });
    }
    if (event.absentPenaltyApplied) {
      return res.json({ ok: false, reason: 'Penalty already applied for this event.' });
    }

    const negativeHours = Number(event.negativeHours) || 0;
    if (negativeHours <= 0) {
      return res.json({ ok: false, reason: 'No negativeHours value set on this event.' });
    }

    const eventDate = event.eventDate || '';
    const semester = (() => {
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

    const usersSnap = await db.collection('users').get();
    const attendanceSnap = await eventRef.collection('attendance').get();
    const attendedRollNumbers = new Set(
      attendanceSnap.docs.map(doc => doc.id.toUpperCase())
    );

    const userMap = {};
    for (const doc of usersSnap.docs) {
      userMap[doc.id.toUpperCase()] = doc.data();
    }

    const eventWings = event.wings || [];
    const batch = db.batch();
    let penaltyCount = 0;

    const hasSelectiveLists = Array.isArray(positiveRollNumbers) || Array.isArray(negativeRollNumbers) || Array.isArray(zeroRollNumbers);

    if (hasSelectiveLists) {
      const posSet = new Set((positiveRollNumbers || []).map(r => r.toUpperCase()));
      const negSet = new Set((negativeRollNumbers || []).map(r => r.toUpperCase()));
      const zeroSet = new Set((zeroRollNumbers || []).map(r => r.toUpperCase()));

      console.log(`[AbsentPenalty] Processing selective: positive=${posSet.size}, negative=${negSet.size}, zero=${zeroSet.size}`);

      for (const rollNumber of Object.keys(userMap)) {
        const user = userMap[rollNumber];
        if (user.userType === 'Admin') continue;

        const userRef = db.collection('users').doc(rollNumber);
        const hadAttendance = attendedRollNumbers.has(rollNumber);

        if (posSet.has(rollNumber)) {
          if (!hadAttendance) {
            // Mark as present -> create attendance document in subcollection
            const attendanceDocRef = eventRef.collection('attendance').doc(rollNumber);
            const name = user.name || 'Unknown Student';
            const attendeeData = {
              rollNumber: rollNumber,
              roll_number: rollNumber,
              name: name,
              scanTimestamp: admin.firestore.Timestamp.now(),
              scan_timestamp: admin.firestore.Timestamp.now(),
              deviceId: "Manual_Penalty_Exemption",
              manualEntry: true,
              attendanceMethod: "Manual",
              attendance_method: "Manual"
            };
            batch.set(attendanceDocRef, attendeeData);
            // Note: The onAttendanceCreate Cloud Function trigger will automatically add the positive hours to the user profile
          }
        } else if (negSet.has(rollNumber)) {
          // Deduct negative hours
          const updates = {
            hours: admin.firestore.FieldValue.increment(-negativeHours),
          };
          if (semester === 1) updates.sem1Hours = admin.firestore.FieldValue.increment(-negativeHours);
          if (semester === 2) updates.sem2Hours = admin.firestore.FieldValue.increment(-negativeHours);

          if (hadAttendance) {
            // If they had attendance, we delete the attendance doc and also deduct the positive hours they received
            const attendanceDocRef = eventRef.collection('attendance').doc(rollNumber);
            batch.delete(attendanceDocRef);

            const eventHours = Number(event.hours) || 0;
            updates.eventsAttended = admin.firestore.FieldValue.increment(-1);
            updates.hours = admin.firestore.FieldValue.increment(-eventHours - negativeHours);
            if (semester === 1) updates.sem1Hours = admin.firestore.FieldValue.increment(-eventHours - negativeHours);
            if (semester === 2) updates.sem2Hours = admin.firestore.FieldValue.increment(-eventHours - negativeHours);
            updates.eventsList = admin.firestore.FieldValue.arrayRemove(eventId);
          }
          batch.set(userRef, updates, { merge: true });
          penaltyCount++;
        } else if (zeroSet.has(rollNumber)) {
          if (hadAttendance) {
            // If they had attendance but are set to zero/exempted, they lose the positive hours and the attendance doc
            const attendanceDocRef = eventRef.collection('attendance').doc(rollNumber);
            batch.delete(attendanceDocRef);

            const eventHours = Number(event.hours) || 0;
            const updates = {
              eventsAttended: admin.firestore.FieldValue.increment(-1),
              hours: admin.firestore.FieldValue.increment(-eventHours),
              eventsList: admin.firestore.FieldValue.arrayRemove(eventId)
            };
            if (semester === 1) updates.sem1Hours = admin.firestore.FieldValue.increment(-eventHours);
            if (semester === 2) updates.sem2Hours = admin.firestore.FieldValue.increment(-eventHours);

            batch.set(userRef, updates, { merge: true });
          }
        }
      }
    } else {
      // Old fallback logic (apply negative hours to all absent volunteers)
      for (const rollNumber of Object.keys(userMap)) {
        const user = userMap[rollNumber];
        if (user.userType === 'Admin') continue;
        if (attendedRollNumbers.has(rollNumber)) continue;

        if (eventWings.length > 0) {
          const userWings = user.wings || [];
          const isInWing = userWings.some(w => eventWings.includes(w));
          if (!isInWing) continue;
        }

        const userRef = db.collection('users').doc(rollNumber);
        const updates = {
          hours: admin.firestore.FieldValue.increment(-negativeHours),
        };
        if (semester === 1) updates.sem1Hours = admin.firestore.FieldValue.increment(-negativeHours);
        if (semester === 2) updates.sem2Hours = admin.firestore.FieldValue.increment(-negativeHours);

        batch.set(userRef, updates, { merge: true });
        penaltyCount++;
      }
    }

    batch.update(eventRef, { absentPenaltyApplied: true });
    await batch.commit();

    console.log(`[AbsentPenalty] Applied penalty: count=${penaltyCount}, negativeHours=${negativeHours} for event ${eventId}`);
    res.json({ ok: true, penaltyCount, negativeHours });

  } catch (err) {
    console.error('[AbsentPenalty] Error:', err);
    sendError(res, err);
  }
});
// ==========================================
// Parallel Geo-Tagged Photo Attendance System
// ==========================================

const express = require('express');
const cors = require('cors');
const multer = require('multer');
const path = require('path');
const fs = require('fs');

const app = express();

// Enable CORS
app.use(cors({ origin: true }));
app.use(express.json());

// Configure Multer to upload to /tmp (the only writable directory in Firebase Functions)
const upload = multer({ dest: '/tmp/' });

/**
 * Endpoint: POST /api/attendance/submit-photo
 * Accept multipart/form-data with fields: userId, eventId, latitude, longitude and file: image
 */
app.post('/api/attendance/submit-photo', upload.single('image'), async (req, res) => {
  try {
    console.log('[submit-photo] Received request');
    const decoded = await verifyAuthToken(req);
    
    const { userId, eventId, latitude, longitude } = req.body;
    const file = req.file;

    if (!userId || !eventId || !latitude || !longitude || !file) {
      console.warn('[submit-photo] Missing required fields or file');
      if (file && fs.existsSync(file.path)) {
        fs.unlinkSync(file.path);
      }
      return res.status(400).json({ error: 'Missing required fields (userId, eventId, latitude, longitude) or image file' });
    }

    const rollNoUpper = userId.toUpperCase();
    console.log(`[submit-photo] User: ${rollNoUpper}, Event: ${eventId}, Lat: ${latitude}, Lon: ${longitude}`);

    const db = admin.firestore();

    // Fetch user's name from Firestore users collection
    const userSnap = await db.collection('users').doc(rollNoUpper).get();
    const userData = userSnap.data() || {};
    const userName = userData.name || 'Unknown Student';

    // Construct photo url to serve this image from /tmp
    const photoUrl = `https://asia-south1-nssiitp-app.cloudfunctions.net/attendance/api/attendance/photo/${file.filename}`;

    const docId = `${eventId}_${rollNoUpper}`;
    const logData = {
      id: docId,
      rollNumber: rollNoUpper,
      name: userName,
      eventId: eventId,
      latitude: parseFloat(latitude),
      longitude: parseFloat(longitude),
      photo_url: photoUrl,
      photo_filename: file.filename, // Store filename to make deletion easy
      verification_status: 'Pending',
      attendance_method: 'Photo_GPS',
      submittedAt: admin.firestore.Timestamp.now()
    };

    await db.collection('PhotoAttendanceLog').doc(docId).set(logData);
    console.log(`[submit-photo] Logged pending attendance in PhotoAttendanceLog for ${rollNoUpper}`);

    res.status(200).json({ ok: true, id: docId });
  } catch (err) {
    console.error('[submit-photo] Error:', err);
    if (req.file && fs.existsSync(req.file.path)) {
      fs.unlinkSync(req.file.path);
    }
    const status = err.status || 500;
    res.status(status).json({ error: err.message || 'Internal server error' });
  }
});

/**
 * Endpoint: GET /api/attendance/photo/:filename
 * Serves the temporary uploaded image from /tmp
 */
app.get('/api/attendance/photo/:filename', (req, res) => {
  try {
    const filename = req.params.filename;
    const safeFilename = path.basename(filename);
    const filePath = path.join('/tmp', safeFilename);

    if (fs.existsSync(filePath)) {
      res.sendFile(filePath);
    } else {
      console.warn(`[get-photo] File not found: ${filePath}`);
      res.status(404).send('Photo not found or expired');
    }
  } catch (err) {
    console.error('[get-photo] Error:', err);
    res.status(500).send('Internal server error');
  }
});

/**
 * Endpoint: GET /api/attendance/pending-photos
 * Fetches all attendance requests with status 'Pending'
 */
app.get('/api/attendance/pending-photos', async (req, res) => {
  try {
    console.log('[pending-photos] Fetching pending records');
    await verifyAuthToken(req);

    const db = admin.firestore();
    const snap = await db.collection('PhotoAttendanceLog')
      .where('verification_status', '==', 'Pending')
      .orderBy('submittedAt', 'desc')
      .get();

    const results = [];
    snap.forEach(doc => {
      results.push(doc.data());
    });

    console.log(`[pending-photos] Found ${results.length} pending records`);
    res.status(200).json(results);
  } catch (err) {
    console.error('[pending-photos] Error:', err);
    const status = err.status || 500;
    res.status(status).json({ error: err.message || 'Internal server error' });
  }
});

/**
 * Endpoint: PUT /api/attendance/verify/:id
 * Admin approves or rejects a photo attendance log
 */
app.put('/api/attendance/verify/:id', async (req, res) => {
  try {
    const docId = req.params.id; // eventId_rollNumber
    console.log(`[verify] Verifying log ID: ${docId}`);
    const decoded = await verifyAuthToken(req);
    
    // Extract admin details from body or token
    const adminRoll = decoded.email ? decoded.email.split('@')[0].toUpperCase() : 'ADMIN';
    const { status, adminRollNumber, adminName } = req.body;

    if (!status || !['Approved', 'Rejected'].includes(status)) {
      return res.status(400).json({ error: 'Invalid or missing status (must be Approved or Rejected)' });
    }

    const db = admin.firestore();
    const logRef = db.collection('PhotoAttendanceLog').doc(docId);
    const logSnap = await logRef.get();

    if (!logSnap.exists) {
      return res.status(404).json({ error: 'Attendance log not found' });
    }

    const logData = logSnap.data();
    if (logData.verification_status !== 'Pending') {
      return res.status(400).json({ error: 'Record already verified' });
    }

    const { rollNumber, name, eventId, latitude, longitude, photo_filename } = logData;

    // 1. Delete physical photo from /tmp
    if (photo_filename) {
      const filePath = path.join('/tmp', path.basename(photo_filename));
      try {
        if (fs.existsSync(filePath)) {
          fs.unlinkSync(filePath);
          console.log(`[verify] Deleted file: ${filePath}`);
        } else {
          console.warn(`[verify] File not found for deletion: ${filePath}`);
        }
      } catch (fileErr) {
        console.error(`[verify] Failed to delete file ${filePath}:`, fileErr);
      }
    }

    // 2. If Approved, write to the main event attendance subcollection
    if (status === 'Approved') {
      const eventRef = db.collection('NSS_Events_Attendence').doc(eventId);
      const attendanceDocRef = eventRef.collection('attendance').doc(rollNumber);

      const gpLocation = new admin.firestore.GeoPoint(latitude, longitude);
      const scannedFromObj = {
        adminRollNumber: adminRollNumber || adminRoll,
        adminName: adminName || 'Admin'
      };

      const normalizedAttendee = {
        rollNumber: rollNumber,
        roll_number: rollNumber,
        name: name,
        scanTimestamp: admin.firestore.Timestamp.now(),
        scan_timestamp: admin.firestore.Timestamp.now(),
        scannedFrom: scannedFromObj,
        deviceId: 'Photo_GPS',
        scanLocation: gpLocation,
        scan_location: gpLocation,
        manualEntry: false,
        attendanceMethod: 'Photo_GPS',
        attendance_method: 'Photo_GPS',
        latitude: latitude,
        longitude: longitude,
        photoUrl: null,
        photo_url: null,
        verificationStatus: 'Approved',
        verification_status: 'Approved'
      };

      await db.runTransaction(async (tx) => {
        const eventSnap = await tx.get(eventRef);
        if (!eventSnap.exists) throw Object.assign(new Error('Event not found'), { status: 404 });
        tx.set(attendanceDocRef, normalizedAttendee);
        tx.update(eventRef, {
          attendees: admin.firestore.FieldValue.arrayUnion(normalizedAttendee),
          total_marked: admin.firestore.FieldValue.increment(1),
        });
      });
      console.log(`[verify] Approved attendance written to consolidated event ${eventId} for ${rollNumber}`);
    }

    // 3. Update verification log status and nullify photo_url
    await logRef.update({
      photo_url: null,
      verification_status: status,
      verifiedAt: admin.firestore.Timestamp.now(),
      verifiedBy: adminRollNumber || adminRoll
    });

    console.log(`[verify] Log updated to status: ${status}`);
    res.status(200).json({ ok: true, status });
  } catch (err) {
    console.error('[verify] Error:', err);
    const status = err.status || 500;
    res.status(status).json({ error: err.message || 'Internal server error' });
  }
});

// Export Express app as Firebase Cloud Function
exports.attendance = onRequest({ region: 'asia-south1' }, app);

