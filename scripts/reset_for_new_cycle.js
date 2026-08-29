/**
 * NSS IITP — New Cycle Reset Script
 * ====================================================
 * Resets the database for a new semester cycle:
 *   - Deletes all events (NSS_Events_Attendence + attendance subcollections)
 *   - Clears PhotoAttendanceLog (references old events)
 *   - Clears scheduledNotifications (references old events)
 *   - Resets user hours/stats to 0 (keeps names, wings, roll numbers intact)
 *
 * SAFETY FEATURES:
 *   - DRY_RUN mode (default: true) — shows counts, touches NOTHING
 *   - Date filter — optionally skip events after a cutoff date
 *   - Emulator support — set USE_EMULATOR=true to test safely first
 *   - Explicit confirmation prompt before live deletion
 *
 * USAGE:
 *   node reset_for_new_cycle.js                    # Dry-run (safe, no changes)
 *   node reset_for_new_cycle.js --live             # LIVE mode (asks for confirmation)
 *   node reset_for_new_cycle.js --emulator         # Against local Firestore Emulator
 *   node reset_for_new_cycle.js --emulator --live  # Live on Emulator (for testing)
 *   node reset_for_new_cycle.js --cutoff 2026-07-01  # Only delete events BEFORE this date
 */

'use strict';

const admin = require('firebase-admin');
const readline = require('readline');
const path = require('path');

// ─── CONFIGURATION ────────────────────────────────────────────────────────────

const USE_EMULATOR  = process.argv.includes('--emulator');
const DRY_RUN       = !process.argv.includes('--live');
const SKIP_CONFIRM  = process.argv.includes('--yes') && USE_EMULATOR;
const CUTOFF_ARG    = process.argv.find(a => a.startsWith('--cutoff'));
const CUTOFF_DATE   = CUTOFF_ARG ? new Date(CUTOFF_ARG.split('=')[1] || process.argv[process.argv.indexOf(CUTOFF_ARG) + 1]) : null;

// Path to your Firebase service account key (download from Firebase Console → Project Settings → Service Accounts)
const SERVICE_ACCOUNT_PATH = path.join(__dirname, '..', 'serviceAccountKey.json');

// Collections to fully delete
const COLLECTIONS_TO_CLEAR = [
  'PhotoAttendanceLog',
  'scheduledNotifications',
];

// User fields to reset (everything else in the user doc is kept)
const USER_RESET_FIELDS = {
  hours:          0,
  sem1Hours:      0,
  sem2Hours:      0,
  eventsAttended: 0,
  eventsList:     [],
};

// ─── INIT ─────────────────────────────────────────────────────────────────────

if (USE_EMULATOR) {
  process.env.FIRESTORE_EMULATOR_HOST = 'localhost:8080';
  console.log('🧪 [EMULATOR MODE] Connecting to local Firestore Emulator at localhost:8080\n');
  // For emulator, we can init without a service account
  admin.initializeApp({ projectId: 'nssiitp-app' });
} else {
  let serviceAccount;
  try {
    serviceAccount = require(SERVICE_ACCOUNT_PATH);
  } catch (e) {
    console.error(`❌ Could not load service account key from: ${SERVICE_ACCOUNT_PATH}`);
    console.error(`   Download it from Firebase Console → Project Settings → Service Accounts → Generate New Private Key`);
    process.exit(1);
  }
  const { cert } = require('firebase-admin/app');
  admin.initializeApp({ credential: cert(serviceAccount) });
}

const { getFirestore } = require('firebase-admin/firestore');
const db = getFirestore();

// ─── HELPERS ──────────────────────────────────────────────────────────────────

function separator(char = '─', len = 60) {
  return char.repeat(len);
}

function formatDate(ts) {
  if (!ts) return 'N/A';
  try {
    const d = ts.toDate ? ts.toDate() : new Date(ts);
    return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  } catch { return 'N/A'; }
}

async function confirm(question) {
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
  return new Promise(resolve => {
    rl.question(question, answer => {
      rl.close();
      resolve(answer.trim().toLowerCase());
    });
  });
}

/**
 * Get all docs in a collection (handles pagination)
 */
async function getAllDocs(collectionRef) {
  const snap = await collectionRef.get();
  return snap.docs;
}

/**
 * Delete a collection in batches of 500
 */
async function deleteCollection(collRef, dryRun, label) {
  const docs = await getAllDocs(collRef);
  if (docs.length === 0) {
    console.log(`  ✅ ${label}: already empty`);
    return 0;
  }
  if (dryRun) {
    console.log(`  📋 ${label}: ${docs.length} document(s) would be deleted`);
    return docs.length;
  }
  // Delete in batches of 500
  const BATCH_SIZE = 500;
  let deleted = 0;
  for (let i = 0; i < docs.length; i += BATCH_SIZE) {
    const batch = db.batch();
    docs.slice(i, i + BATCH_SIZE).forEach(doc => batch.delete(doc.ref));
    await batch.commit();
    deleted += Math.min(BATCH_SIZE, docs.length - i);
    process.stdout.write(`\r  🗑️  ${label}: ${deleted}/${docs.length} deleted...`);
  }
  console.log(`\r  ✅ ${label}: ${deleted} document(s) deleted         `);
  return deleted;
}

// ─── MAIN ─────────────────────────────────────────────────────────────────────

async function main() {
  console.log('\n' + separator('═'));
  console.log('  NSS IITP — New Cycle Database Reset');
  console.log(separator('═'));
  console.log(`  Mode:    ${DRY_RUN ? '🔍 DRY RUN (no changes will be made)' : '🔴 LIVE (will modify data!)'}`);
  console.log(`  Target:  ${USE_EMULATOR ? '🧪 Firestore Emulator' : '☁️  Production Firestore'}`);
  if (CUTOFF_DATE) {
    console.log(`  Cutoff:  Events BEFORE ${CUTOFF_DATE.toDateString()} will be deleted`);
  } else {
    console.log(`  Cutoff:  ALL events will be deleted (no date filter)`);
  }
  console.log(separator('─'));

  // ── STEP 1: Scan Events ──────────────────────────────────────────────────
  console.log('\n📂 STEP 1: Scanning NSS_Events_Attendence...\n');

  const eventsSnap = await db.collection('NSS_Events_Attendence').get();
  let eventsToDelete = [];
  let eventsSkipped  = [];
  let totalAttendanceDocs = 0;

  for (const eventDoc of eventsSnap.docs) {
    const data = eventDoc.data();
    const eventName = data.eventName || data.name || eventDoc.id;
    const eventDate = data.eventDate || '';

    // Apply cutoff filter
    if (CUTOFF_DATE && eventDate) {
      // Try parse eventDate
      const parsed = new Date(eventDate);
      if (!isNaN(parsed) && parsed >= CUTOFF_DATE) {
        eventsSkipped.push({ id: eventDoc.id, name: eventName, date: eventDate });
        continue;
      }
    }

    // Count attendance subcollection docs
    const attendanceSnap = await eventDoc.ref.collection('attendance').get();
    const attendanceCount = attendanceSnap.size;
    totalAttendanceDocs += attendanceCount;

    eventsToDelete.push({
      ref:        eventDoc.ref,
      id:         eventDoc.id,
      name:       eventName,
      date:       eventDate,
      attendees:  attendanceCount,
      mandatory:  data.mandatory || data.isMandatory || false,
    });
  }

  // Print events to be deleted
  console.log(`  Found ${eventsToDelete.length} event(s) to DELETE, ${eventsSkipped.length} to SKIP\n`);

  if (eventsToDelete.length > 0) {
    console.log('  Events that WILL be deleted:');
    eventsToDelete.forEach(e => {
      console.log(`    🗑️  [${e.date || 'no date'}] ${e.name} — ${e.attendees} attendance record(s) ${e.mandatory ? '(MANDATORY)' : ''}`);
    });
  }
  if (eventsSkipped.length > 0) {
    console.log('\n  Events that will be KEPT (after cutoff date):');
    eventsSkipped.forEach(e => {
      console.log(`    ✅ [${e.date}] ${e.name}`);
    });
  }

  // ── STEP 2: Scan Other Collections ──────────────────────────────────────
  console.log('\n' + separator('─'));
  console.log('\n📂 STEP 2: Scanning other collections to clear...\n');

  const otherCounts = {};
  for (const col of COLLECTIONS_TO_CLEAR) {
    const snap = await db.collection(col).get();
    otherCounts[col] = snap.size;
    console.log(`  ${col}: ${snap.size} document(s)`);
  }

  // ── STEP 3: Scan Users ───────────────────────────────────────────────────
  console.log('\n' + separator('─'));
  console.log('\n👥 STEP 3: Scanning users to reset...\n');

  const usersSnap = await db.collection('users').get();
  let usersToReset = [];
  let adminsSkipped = 0;

  for (const userDoc of usersSnap.docs) {
    const data = userDoc.data();
    if ((data.userType || '').trim().toLowerCase() === 'admin') {
      adminsSkipped++;
      continue;
    }
    const currentHours = data.hours || 0;
    const sem1         = data.sem1Hours || 0;
    const sem2         = data.sem2Hours || 0;
    const events       = data.eventsAttended || 0;
    usersToReset.push({
      ref:      userDoc.ref,
      id:       userDoc.id,
      name:     data.name || 'Unknown',
      roll:     data.rollNumber || userDoc.id,
      hours:    currentHours,
      sem1:     sem1,
      sem2:     sem2,
      events:   events,
    });
  }

  console.log(`  ${usersToReset.length} student(s) will have hours reset to 0`);
  console.log(`  ${adminsSkipped} admin(s) skipped (untouched)\n`);

  // Show students with non-zero hours
  const nonZeroUsers = usersToReset.filter(u => u.hours !== 0 || u.events !== 0);
  if (nonZeroUsers.length > 0) {
    console.log('  Students with non-zero hours (will be reset):');
    nonZeroUsers.forEach(u => {
      console.log(`    👤 ${u.name} (${u.roll}) — hours: ${u.hours} | sem1: ${u.sem1} | sem2: ${u.sem2} | events: ${u.events}`);
    });
  } else {
    console.log('  All students already have 0 hours.');
  }

  // ── STEP 4: Summary ──────────────────────────────────────────────────────
  console.log('\n' + separator('─'));
  console.log('\n📊 SUMMARY OF CHANGES:\n');
  console.log(`  Event documents to delete:     ${eventsToDelete.length}`);
  console.log(`  Attendance subdocs to delete:  ${totalAttendanceDocs}`);
  for (const [col, count] of Object.entries(otherCounts)) {
    console.log(`  ${col} to clear:`.padEnd(38) + count);
  }
  console.log(`  Students to reset:             ${usersToReset.length}`);
  console.log(`  Admins/Users preserved:        ${adminsSkipped} admins + all profile data`);
  console.log('\n  ⚠️  NOT touched: users collection structure, wings, roll numbers, names, contact info');

  if (DRY_RUN) {
    console.log('\n' + separator('═'));
    console.log('  ✅ DRY RUN COMPLETE — No data was modified.');
    console.log('  To execute for real: node reset_for_new_cycle.js --live');
    console.log('  To test on emulator: node reset_for_new_cycle.js --emulator --live');
    console.log(separator('═') + '\n');
    process.exit(0);
  }

  // ── STEP 5: Confirmation (LIVE mode only) ────────────────────────────────
  console.log('\n' + separator('═'));
  console.log('  🔴 LIVE MODE — This will PERMANENTLY delete data.');
  console.log(separator('═'));

  if (SKIP_CONFIRM) {
    console.log('  [--yes + --emulator] Skipping confirmation prompts (emulator-only shortcut).');
  } else {
    const ans1 = await confirm('\n  Type "YES" to confirm you have taken a Firestore backup: ');
    if (ans1 !== 'yes') {
      console.log('\n  ❌ Aborted. Please take a backup first.');
      process.exit(0);
    }

    const ans2 = await confirm(`  Type "RESET" to confirm deletion of ${eventsToDelete.length} events and reset of ${usersToReset.length} users: `);
    if (ans2 !== 'reset') {
      console.log('\n  ❌ Aborted. You typed something other than RESET.');
      process.exit(0);
    }
  }

  console.log('\n  ✅ Confirmed. Starting reset...\n');

  // ── STEP 6: Execute ──────────────────────────────────────────────────────

  // 6a. Delete attendance subcollections first (Firestore doesn't cascade-delete)
  console.log('🗑️  Deleting attendance subcollections...');
  let totalAttDeleted = 0;
  for (const event of eventsToDelete) {
    const attDocs = await event.ref.collection('attendance').get();
    if (attDocs.size > 0) {
      const batch = db.batch();
      attDocs.docs.forEach(d => batch.delete(d.ref));
      await batch.commit();
      totalAttDeleted += attDocs.size;
    }
  }
  console.log(`  ✅ ${totalAttDeleted} attendance record(s) deleted\n`);

  // 6b. Delete event documents
  console.log('🗑️  Deleting event documents...');
  const eventBatch = db.batch();
  eventsToDelete.forEach(e => eventBatch.delete(e.ref));
  await eventBatch.commit();
  console.log(`  ✅ ${eventsToDelete.length} event(s) deleted\n`);

  // 6c. Clear other collections
  for (const col of COLLECTIONS_TO_CLEAR) {
    console.log(`🗑️  Clearing ${col}...`);
    await deleteCollection(db.collection(col), false, col);
  }
  console.log();

  // 6d. Reset user stats
  console.log('🔄 Resetting user stats...');
  const BATCH_SIZE = 500;
  let resetCount = 0;
  for (let i = 0; i < usersToReset.length; i += BATCH_SIZE) {
    const batch = db.batch();
    usersToReset.slice(i, i + BATCH_SIZE).forEach(u => {
      batch.update(u.ref, USER_RESET_FIELDS);
    });
    await batch.commit();
    resetCount += Math.min(BATCH_SIZE, usersToReset.length - i);
    process.stdout.write(`\r  🔄 ${resetCount}/${usersToReset.length} users reset...`);
  }
  console.log(`\r  ✅ ${resetCount} student(s) reset to 0 hours         \n`);

  // ── STEP 7: Final Report ─────────────────────────────────────────────────
  console.log(separator('═'));
  console.log('  ✅ RESET COMPLETE');
  console.log(separator('═'));
  console.log(`  Events deleted:        ${eventsToDelete.length}`);
  console.log(`  Attendance deleted:    ${totalAttDeleted}`);
  console.log(`  Students reset:        ${resetCount}`);
  console.log(`  Events preserved:      ${eventsSkipped.length}`);
  console.log('\n  Next steps:');
  console.log('  1. Open the app and verify the event list is empty');
  console.log('  2. Check a student profile — hours should show 0/0');
  console.log('  3. Create a test event and verify the full flow works');
  console.log(separator('═') + '\n');
}

main().catch(err => {
  console.error('\n❌ Fatal error:', err.message);
  process.exit(1);
});
