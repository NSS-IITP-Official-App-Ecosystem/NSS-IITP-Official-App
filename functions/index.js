/**
 * Import function triggers from their respective submodules:
 *
 * const {onCall} = require("firebase-functions/v2/https");
 * const {onDocumentWritten} = require("firebase-functions/v2/firestore");
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

// eslint-disable-next-line no-unused-vars
const logger = require("firebase-functions/logger");
// Gen 1 - no billing required for deployment

/**
 * Cloud Functions for Firebase
 * 
 * This file contains cloud functions that respond to events in Firebase and 
 * handle sending push notifications through Firebase Cloud Messaging
 */

// firebase-functions v6: Gen 2 API (functions are deployed as Gen 2 on nssiitp-app)
const { onRequest } = require('firebase-functions/v2/https');
const { onDocumentCreated, onDocumentDeleted } = require('firebase-functions/v2/firestore');
const { onSchedule } = require('firebase-functions/v2/scheduler');
const REGION = 'asia-south1';
const admin = require('firebase-admin');
admin.initializeApp();
const { Timestamp, FieldValue, GeoPoint } = require('firebase-admin/firestore');

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
exports.verifyPlayIntegrity = onRequest({ region: REGION, invoker: 'public' }, async (req, res) => {
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

    // Bypass integrity check when running in the local Firebase Emulator
    if (process.env.FUNCTIONS_EMULATOR === 'true') {
      console.log(`[PlayIntegrity] Emulator environment detected. Bypassing validation for user: ${userId}`);
      res.json({ allowed: true, reason: 'Bypassed in emulator mode' });
      return;
    }

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
exports.getDeviceBindChallenge = onRequest({ region: REGION, invoker: 'public' }, async (req, res) => {
  try {
    if (req.method !== 'POST') throw Object.assign(new Error('Method not allowed'), { status: 405 });
    const decoded = await verifyAuthToken(req);
    const { rollNumber } = req.body || {};
    if (!rollNumber) throw Object.assign(new Error('Missing rollNumber'), { status: 400 });
    const db = admin.firestore();
    await assertUserIdentityMatches(db, rollNumber, decodedToken = decoded);
    const nonce = generateNonce();
    const expiresAt = Timestamp.fromDate(new Date(Date.now() + 5 * 60 * 1000));
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
exports.bindDevice = onRequest({ region: REGION, invoker: 'public' }, async (req, res) => {
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
        registeredAt: FieldValue.serverTimestamp(),
        lastVerifiedAt: FieldValue.serverTimestamp(),
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
exports.getAttendanceChallenge = onRequest({ region: REGION, invoker: 'public' }, async (req, res) => {
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
    const expiresAt = Timestamp.fromDate(new Date(Date.now() + 2 * 60 * 1000));
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
exports.markAttendance = onRequest({ region: REGION, invoker: 'public' }, async (req, res) => {
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
      scanLocation = new GeoPoint(attendee.scan_location.latitude, attendee.scan_location.longitude);
    }

    const normalizedAttendee = Object.assign({}, attendee, {
      roll_number: rollNumber.toUpperCase(),
      scan_timestamp: Timestamp.now(),
      ...(scanLocation ? { scan_location: scanLocation } : {}),
    });

    // Proceed to write attendance using existing schema
    const eventRef = db.collection('NSS_Events_Attendence').doc(eventId);
    const attendanceDocRef = eventRef.collection('attendance').doc(rollNumber.toUpperCase());

    await db.runTransaction(async (tx) => {
      const eventSnap = await tx.get(eventRef);
      if (!eventSnap.exists) {
        if (process.env.FUNCTIONS_EMULATOR === 'true') {
          const parts = eventId.split('_');
          const eventName = parts.length >= 3 ? parts.slice(2).join(' ').replace(/_/g, ' ') : eventId;
          const eventDate = parts.length >= 2 ? `${parts[0]} ${parts[1]} 2026` : '31 May 2026';
          
          const mockEvent = {
            description: 'Auto-created mock event for local debugging',
            createdBy: 'SYSTEM',
            creatorName: 'System Admin',
            eventDate: eventDate,
            eventTime: '10:00 AM - 11:00 AM',
            hours: 1.5,
            mandatory: false,
            negativeHours: 0.0,
            location: 'IIT Patna',
            createdAt: Timestamp.now(),
            attendees: [],
            closedAt: null,
            liveCount: 1,
            wings: [],
            visibleOnlyToPresent: false,
            isLive: true,
            total_marked: 0
          };
          tx.set(eventRef, mockEvent);
        } else {
          throw Object.assign(new Error('Event not found'), { status: 404 });
        }
      }
      tx.set(attendanceDocRef, normalizedAttendee);
      tx.update(eventRef, {
        attendees: FieldValue.arrayUnion(normalizedAttendee),
        total_marked: FieldValue.increment(1),
      });
    });

    await challengeRef.delete();
    await userRef.set({ deviceBinding: { ...deviceBinding, lastVerifiedAt: FieldValue.serverTimestamp() } }, { merge: true });

    res.json({ ok: true });
  } catch (err) {
    sendError(res, err);
  }
});

// Increment counters and update user stats when a new attendance record is written
exports.onAttendanceCreate = onDocumentCreated(
  { document: 'NSS_Events_Attendence/{eventId}/attendance/{rollNumber}', region: REGION },
  async (event) => {
    const snap = event.data;
    const context = { params: event.params };
    const { eventId, rollNumber } = context.params;
    const db = admin.firestore();
    const attendee = snap.data() || {};
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

      await eventRef.update({ total_marked: FieldValue.increment(1) });

      // Ensure meta.totalEvents exists and is equal to number of events documents
      // Increment events count only when event document is newly created elsewhere.

      // Update user stats atomically
      const userRef = db.collection('users').doc(rollNumber);
      const semester = (() => {
        // Expecting format like "dd MMM yyyy"; fallback to 0 if unknown
        try {
          const parts = eventDate.split(' ');
          if (parts.length < 3) return 0;
          const day = parseInt(parts[0], 10);
          const month = parts[1];
          const monthIndex = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'].indexOf(month);
          if (monthIndex === -1) return 0;
          
          const month1Based = monthIndex + 1;
          // Semester 1: July 1 - December 10
          if (month1Based >= 7 && month1Based <= 11) {
            return 1;
          } else if (month1Based === 12 && day <= 10) {
            return 1;
          }
          // Semester 2: December 11 - June 30
          else if (month1Based === 12 && day >= 11) {
            return 2;
          } else if (month1Based >= 1 && month1Based <= 6) {
            return 2;
          }
        } catch (e) { }
        return 0;
      })();

      const updates = {
        eventsAttended: FieldValue.increment(1),
        hours: FieldValue.increment(hours),
        eventsList: FieldValue.arrayUnion(eventId)
      };
      if (semester === 1) updates.sem1Hours = FieldValue.increment(hours);
      if (semester === 2) updates.sem2Hours = FieldValue.increment(hours);

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
  { document: 'NSS_Events_Attendence/{eventId}', region: REGION },
  async (event) => {
    try {
      const db = admin.firestore();
      const metaRef = db.collection('meta').doc('statistics');
      await metaRef.set({
        totalEvents: FieldValue.increment(1)
      }, { merge: true });
      return null;
    } catch (e) {
      console.error('onEventWrite create error', e);
      return null;
    }
  }
);

exports.onEventDelete = onDocumentDeleted(
  { document: 'NSS_Events_Attendence/{eventId}', region: REGION },
  async (event) => {
    try {
      const db = admin.firestore();
      const metaRef = db.collection('meta').doc('statistics');
      await metaRef.set({
        totalEvents: FieldValue.increment(-1)
      }, { merge: true });
      return null;
    } catch (e) {
      console.error('onEventWrite delete error', e);
      return null;
    }
  }
);


/**
 * Apply negative hours to volunteers who missed a mandatory event.
 * Called when an admin closes a mandatory event.
 * POST body: { eventId }
 */
exports.applyAbsentPenalty = onRequest({ region: REGION, invoker: 'public' }, async (req, res) => {
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
const Busboy = require('busboy');
const path = require('path');
const fs = require('fs');
const os = require('os');

const app = express();

const cloudinary = require('cloudinary').v2;
cloudinary.config({
  cloud_name: process.env.CLOUDINARY_CLOUD_NAME,
  api_key: process.env.CLOUDINARY_API_KEY,
  api_secret: process.env.CLOUDINARY_API_SECRET
});

// Enable CORS
app.use(cors({ origin: true }));
app.use(express.json());



// Custom multipart parser middleware for Firebase Functions
const parseMultipart = (req, res, next) => {
  if (req.method !== 'POST') {
    return next();
  }

  const contentType = req.headers['content-type'] || '';
  if (!contentType.includes('multipart/form-data')) {
    return next();
  }

  try {
    const busboy = Busboy({ headers: req.headers });
    req.body = req.body || {};

    busboy.on('field', (fieldname, val) => {
      req.body[fieldname] = val;
    });

    busboy.on('file', (fieldname, file, fileInfo) => {
      let filename, mimeType;
      if (fileInfo && typeof fileInfo === 'object') {
        filename = fileInfo.filename;
        mimeType = fileInfo.mimeType;
      } else {
        filename = arguments[2];
        mimeType = arguments[4];
      }

      const uniqueFilename = crypto.randomBytes(16).toString('hex') + path.extname(filename || '.jpg');
      const tempFilePath = path.join(os.tmpdir(), uniqueFilename);

      const writeStream = fs.createWriteStream(tempFilePath);
      file.pipe(writeStream);

      req.file = {
        fieldname: fieldname,
        originalname: filename,
        encoding: fileInfo?.encoding || '7bit',
        mimetype: mimeType || 'image/jpeg',
        destination: os.tmpdir(),
        filename: uniqueFilename,
        path: tempFilePath,
        size: 0
      };
    });

    busboy.on('finish', () => {
      next();
    });

    busboy.on('error', (err) => {
      next(err);
    });

    if (req.rawBody) {
      busboy.end(req.rawBody);
    } else {
      req.pipe(busboy);
    }
  } catch (err) {
    next(err);
  }
};

/**
 * Endpoint: POST /api/attendance/submit-photo
 * Accept multipart/form-data with fields: userId, eventId, latitude, longitude and file: image
 * Photos are uploaded to Firebase Storage (permanent) instead of /tmp (ephemeral).
 */
app.post('/api/attendance/submit-photo', parseMultipart, async (req, res) => {
  try {
    console.log('[submit-photo] Received request');
    const decoded = await verifyAuthToken(req);

    const { userId, eventId, latitude, longitude } = req.body;
    const file = req.file;

    if (!userId || !eventId || !latitude || !longitude || !file) {
      console.warn('[submit-photo] Missing required fields or file');
      if (file && fs.existsSync(file.path)) fs.unlinkSync(file.path);
      return res.status(400).json({ error: 'Missing required fields (userId, eventId, latitude, longitude) or image file' });
    }

    const rollNoUpper = userId.toUpperCase();
    console.log(`[submit-photo] User: ${rollNoUpper}, Event: ${eventId}, Lat: ${latitude}, Lon: ${longitude}`);

    const db = admin.firestore();
    const projectId = process.env.GCLOUD_PROJECT || 'nssiitp-app';

    // Fetch user's name from Firestore
    const userSnap = await db.collection('users').doc(rollNoUpper).get();
    const userData = userSnap.data() || {};
    const userName = userData.name || 'Unknown Student';

    let photoUrl;
    let storagePath = null;

    console.log(`[submit-photo] Uploading photo to Cloudinary for ${rollNoUpper}`);
    const cloudinaryResponse = await cloudinary.uploader.upload(file.path, {
      folder: `photo_attendance/${eventId}`,
      public_id: `${rollNoUpper}_${file.filename.split('.')[0]}`,
      context: `uploadedBy=${rollNoUpper}|eventId=${eventId}`
    });
    
    photoUrl = cloudinaryResponse.secure_url;
    storagePath = cloudinaryResponse.public_id;
    console.log(`[submit-photo] Photo uploaded to Cloudinary: ${storagePath}`);

    // Clean up local /tmp file after successful upload
    if (fs.existsSync(file.path)) fs.unlinkSync(file.path);

    const docId = `${eventId}_${rollNoUpper}`;
    const logData = {
      id: docId,
      rollNumber: rollNoUpper,
      name: userName,
      eventId: eventId,
      latitude: parseFloat(latitude),
      longitude: parseFloat(longitude),
      photo_url: photoUrl,
      photo_filename: file.filename,
      storage_path: storagePath,  // Firebase Storage path for permanent deletion
      verification_status: 'Pending',
      attendance_method: 'Photo_GPS',
      submittedAt: Timestamp.now()
    };

    await db.collection('PhotoAttendanceLog').doc(docId).set(logData);
    console.log(`[submit-photo] Logged pending attendance in PhotoAttendanceLog for ${rollNoUpper}`);

    res.status(200).json({ ok: true, id: docId });
  } catch (err) {
    console.error('[submit-photo] Error:', err);
    if (req.file && fs.existsSync(req.file.path)) fs.unlinkSync(req.file.path);
    const status = err.status || 500;
    res.status(status).json({ error: err.message || 'Internal server error' });
  }
});

/**
 * Endpoint: GET /api/attendance/photo/:filename
 * Serves the temporary uploaded image from os.tmpdir()
 */
app.get('/api/attendance/photo/:filename', (req, res) => {
  try {
    const filename = req.params.filename;
    const safeFilename = path.basename(filename);
    const filePath = path.join(os.tmpdir(), safeFilename);

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
      .get();

    const results = [];
    snap.forEach(doc => {
      results.push(doc.data());
    });

    // Sort in memory by submittedAt descending
    results.sort((a, b) => {
      const aTime = a.submittedAt ? (typeof a.submittedAt.toMillis === 'function' ? a.submittedAt.toMillis() : (a.submittedAt.seconds * 1000 || 0)) : 0;
      const bTime = b.submittedAt ? (typeof b.submittedAt.toMillis === 'function' ? b.submittedAt.toMillis() : (b.submittedAt.seconds * 1000 || 0)) : 0;
      return bTime - aTime;
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

    // 2. If Approved, write to the main event attendance subcollection
    if (status === 'Approved') {
      const eventRef = db.collection('NSS_Events_Attendence').doc(eventId);
      const attendanceDocRef = eventRef.collection('attendance').doc(rollNumber);

      const gpLocation = new GeoPoint(latitude, longitude);
      const scannedFromObj = {
        adminRollNumber: adminRollNumber || adminRoll,
        adminName: adminName || 'Admin'
      };

      const normalizedAttendee = {
        rollNumber: rollNumber,
        roll_number: rollNumber,
        name: name,
        scanTimestamp: Timestamp.now(),
        scan_timestamp: Timestamp.now(),
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
        if (!eventSnap.exists) {
          if (process.env.FUNCTIONS_EMULATOR === 'true') {
            const parts = eventId.split('_');
            const eventName = parts.length >= 3 ? parts.slice(2).join(' ').replace(/_/g, ' ') : eventId;
            const eventDate = parts.length >= 2 ? `${parts[0]} ${parts[1]} 2026` : '31 May 2026';
            
            const mockEvent = {
              description: 'Auto-created mock event for local debugging',
              createdBy: 'SYSTEM',
              creatorName: 'System Admin',
              eventDate: eventDate,
              eventTime: '10:00 AM - 11:00 AM',
              hours: 1.5,
              mandatory: false,
              negativeHours: 0.0,
              location: 'IIT Patna',
              createdAt: Timestamp.now(),
              attendees: [],
              closedAt: null,
              liveCount: 1,
              wings: [],
              visibleOnlyToPresent: false,
              isLive: true,
              total_marked: 0
            };
            tx.set(eventRef, mockEvent);
          } else {
            throw Object.assign(new Error('Event not found'), { status: 404 });
          }
        }
        tx.set(attendanceDocRef, normalizedAttendee);
        tx.update(eventRef, {
          attendees: FieldValue.arrayUnion(normalizedAttendee),
          total_marked: FieldValue.increment(1),
        });
      });
      console.log(`[verify] Approved attendance written to consolidated event ${eventId} for ${rollNumber}`);

      // Also directly update user hours (belt-and-suspenders alongside onAttendanceCreate trigger)
      const eventSnap = await db.collection('NSS_Events_Attendence').doc(eventId).get();
      if (eventSnap.exists) {
        const eventData = eventSnap.data();
        const hours = Number(eventData.hours) || 0;
        const eventDate = eventData.eventDate || '';

        // Determine semester using same logic as onAttendanceCreate trigger
        const semester = (() => {
          try {
            const parts = eventDate.split(' ');
            if (parts.length < 3) return 0;
            const day = parseInt(parts[0], 10);
            const month = parts[1];
            const monthIndex = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'].indexOf(month);
            if (monthIndex === -1) return 0;
            const m = monthIndex + 1;
            if (m >= 7 && m <= 11) return 1;
            if (m === 12 && day <= 10) return 1;
            if (m === 12 && day >= 11) return 2;
            if (m >= 1 && m <= 6) return 2;
          } catch (e) {}
          return 0;
        })();

        const userRef = db.collection('users').doc(rollNumber);
        const userUpdates = {
          eventsAttended: FieldValue.increment(1),
          hours: FieldValue.increment(hours),
          eventsList: FieldValue.arrayUnion(eventId)
        };
        if (semester === 1) userUpdates.sem1Hours = FieldValue.increment(hours);
        if (semester === 2) userUpdates.sem2Hours = FieldValue.increment(hours);

        await userRef.set(userUpdates, { merge: true });
        console.log(`[verify] Updated user ${rollNumber} hours: +${hours} (sem${semester})`);
      }
    }

    // 3. Update verification log status and nullify photo_url
    await logRef.update({
      photo_url: null,
      verification_status: status,
      verifiedAt: Timestamp.now(),
      verifiedBy: adminRollNumber || adminRoll
    });

    // 4. Delete photo from Cloudinary after DB update succeeds
    const { storage_path } = logData;
    if (storage_path) {
      try {
        await cloudinary.uploader.destroy(storage_path);
        console.log(`[verify] Deleted Cloudinary file: ${storage_path}`);
      } catch (cloudErr) {
        // Non-fatal: log but don't fail the verification
        console.error(`[verify] Failed to delete Cloudinary file ${storage_path}:`, cloudErr);
      }
    }

    console.log(`[verify] Log updated to status: ${status}`);
    res.status(200).json({ ok: true, status });
  } catch (err) {
    console.error('[verify] Error:', err);
    const status = err.status || 500;
    res.status(status).json({ error: err.message || 'Internal server error' });
  }
});

/**
 * Endpoint: POST /api/attendance/notify
 * Sends push notifications to FCM topics for an event. Includes a 10-min cooldown.
 */
app.post('/api/attendance/notify', async (req, res) => {
  try {
    console.log('[notify] Notification request received');
    await verifyAuthToken(req); // Ensure caller is authenticated

    const { eventId, title, body, targetWings } = req.body;
    if (!eventId || !title || !body) {
      return res.status(400).json({ error: 'Missing required fields: eventId, title, body' });
    }

    const db = admin.firestore();
    const eventRef = db.collection('NSS_Events_Attendence').doc(eventId);
    let topicsToNotify = [];

    await db.runTransaction(async (tx) => {
      const eventSnap = await tx.get(eventRef);
      if (!eventSnap.exists) {
        throw Object.assign(new Error('Event not found'), { status: 404 });
      }

      const data = eventSnap.data();
      const lastNotifiedAt = data.lastNotifiedAt;

      // Check 30-minute cooldown
      if (lastNotifiedAt) {
        const lastTime = lastNotifiedAt.toMillis();
        const now = Date.now();
        const diffMins = (now - lastTime) / (1000 * 60);
        if (diffMins < 30) {
          throw Object.assign(
            new Error(`Please wait ${Math.ceil(30 - diffMins)} minutes before notifying again.`), 
            { status: 429 }
          );
        }
      }

      // Allow sending, update timestamp
      tx.update(eventRef, {
        lastNotifiedAt: Timestamp.now()
      });
    });

    // Determine target topics
    if (!targetWings || targetWings.length === 0 || targetWings.includes('all')) {
      topicsToNotify.push('all');
    } else {
      targetWings.forEach(wing => {
        // Format wing exactly like ChatApplication.kt does
        const formattedWing = "wing_" + wing.toLowerCase().replace(/ /g, "_").replace(/&/g, "and");
        topicsToNotify.push(formattedWing);
      });
    }

    // Send using admin.messaging()
    if (topicsToNotify.length > 0) {
      const notification = { title, body };
      
      if (topicsToNotify.includes('all')) {
        await admin.messaging().send({
          notification,
          topic: 'all'
        });
      } else {
        const condition = topicsToNotify.map(t => `'${t}' in topics`).join(' || ');
        await admin.messaging().send({
          notification,
          condition: condition
        });
      }
    }

    console.log(`[notify] Notification sent for event ${eventId} to topics: ${topicsToNotify.join(', ')}`);
    res.status(200).json({ ok: true, message: 'Notification sent successfully' });
  } catch (err) {
    console.error('[notify] Error:', err);
    const status = err.status || 500;
    res.status(status).json({ error: err.message || 'Internal server error' });
  }
});

/**
 * Endpoint: POST /api/attendance/schedule-notification
 * Admin schedules a custom notification for an event at a chosen time.
 * Body: { eventId, title, body, scheduledAt (ms), targetWings }
 */
app.post('/api/attendance/schedule-notification', async (req, res) => {
  try {
    const decoded = await verifyAuthToken(req);
    const { eventId, title, body, scheduledAt, targetWings } = req.body || {};

    if (!eventId || !title || !body || !scheduledAt) {
      return res.status(400).json({ error: 'Missing required fields: eventId, title, body, scheduledAt' });
    }

    const now = Date.now();
    if (scheduledAt <= now) {
      return res.status(400).json({ error: 'scheduledAt must be in the future' });
    }

    const db = admin.firestore();

    // Derive FCM topics from targetWings
    const isOpenEvent = !targetWings || targetWings.length === 0 || targetWings.includes('all');
    const topics = isOpenEvent
      ? ['all']
      : targetWings.map(w => 'wing_' + w.toLowerCase().replace(/ /g, '_').replace(/&/g, 'and'));

    const docRef = await db.collection('scheduledNotifications').add({
      eventId,
      title,
      body,
      scheduledAt: Timestamp.fromMillis(scheduledAt),
      targetWings: targetWings || [],
      topics,
      status: 'pending',
      createdBy: decoded.uid,
      createdAt: Timestamp.now(),
    });

    console.log(`[schedule-notification] Scheduled notification ${docRef.id} for event ${eventId} at ${new Date(scheduledAt).toISOString()}`);
    res.status(200).json({ ok: true, id: docRef.id });
  } catch (err) {
    console.error('[schedule-notification] Error:', err);
    const status = err.status || 500;
    res.status(status).json({ error: err.message || 'Internal server error' });
  }
});

/**
 * Endpoint: DELETE /api/attendance/schedule-notification/:id
 * Cancel a pending scheduled notification.
 */
app.delete('/api/attendance/schedule-notification/:id', async (req, res) => {
  try {
    await verifyAuthToken(req);
    const { id } = req.params;
    const db = admin.firestore();
    const docRef = db.collection('scheduledNotifications').doc(id);
    const snap = await docRef.get();
    if (!snap.exists) return res.status(404).json({ error: 'Notification not found' });
    if (snap.data().status !== 'pending') {
      return res.status(400).json({ error: 'Only pending notifications can be cancelled' });
    }
    await docRef.update({ status: 'cancelled' });
    console.log(`[schedule-notification] Cancelled notification ${id}`);
    res.status(200).json({ ok: true });
  } catch (err) {
    console.error('[schedule-notification] Cancel error:', err);
    const status = err.status || 500;
    res.status(status).json({ error: err.message || 'Internal server error' });
  }
});

/**
 * Endpoint: GET /api/attendance/schedule-notification/:eventId
 * Fetch all scheduled notifications for an event.
 */
app.get('/api/attendance/schedule-notification/:eventId', async (req, res) => {
  try {
    await verifyAuthToken(req);
    const { eventId } = req.params;
    const db = admin.firestore();
    const snap = await db.collection('scheduledNotifications')
      .where('eventId', '==', eventId)
      .orderBy('scheduledAt', 'asc')
      .get();
    const results = snap.docs.map(d => ({ id: d.id, ...d.data(), scheduledAt: d.data().scheduledAt.toMillis() }));
    res.status(200).json(results);
  } catch (err) {
    console.error('[schedule-notification] Fetch error:', err);
    const status = err.status || 500;
    res.status(status).json({ error: err.message || 'Internal server error' });
  }
});

// Export Express app as Firebase Cloud Function
exports.attendance = onRequest({ region: REGION, invoker: 'public' }, app);

/**
 * Scheduled Cloud Function (Gen 1 pubsub) — runs every 5 minutes.
 * Finds all pending scheduled notifications whose time has passed and sends them via FCM.
 * Requires Firebase Blaze plan (billing).
 */
exports.processScheduledNotifications = onSchedule(
  { schedule: 'every 5 minutes', timeZone: 'Asia/Kolkata', region: REGION },
  async () => {
    const db = admin.firestore();
    const now = Timestamp.now();

    const snap = await db.collection('scheduledNotifications')
      .where('status', '==', 'pending')
      .where('scheduledAt', '<=', now)
      .get();

    if (snap.empty) {
      console.log('[processScheduledNotifications] No pending notifications due.');
      return null;
    }

    const batch = db.batch();
    const sendPromises = [];

    for (const doc of snap.docs) {
      const { title, body, topics } = doc.data();

      for (const topic of (topics || ['all'])) {
        const message = {
          notification: { title, body },
          topic,
        };
        sendPromises.push(
          admin.messaging().send(message)
            .then(() => console.log(`[processScheduledNotifications] Sent to topic ${topic}: "${title}"`))
            .catch(e => console.error(`[processScheduledNotifications] Failed to send to ${topic}:`, e))
        );
      }

      batch.update(doc.ref, { status: 'sent', sentAt: Timestamp.now() });
    }

    await Promise.all(sendPromises);
    await batch.commit();
    console.log(`[processScheduledNotifications] Processed ${snap.docs.length} notification(s).`);
  }
);


