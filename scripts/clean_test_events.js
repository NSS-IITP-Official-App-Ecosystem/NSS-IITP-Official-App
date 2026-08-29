/**
 * NSS IITP - Clean Specific Test Events
 * ====================================================
 * Safely deletes ONLY the explicitly listed event IDs and their 
 * 'attendance' subcollections from 'NSS_Events_Attendence'.
 * Does NOT modify users, PhotoAttendanceLog, or scheduledNotifications.
 * 
 * USAGE:
 *   node scripts/clean_test_events.js          # Dry-run (safe)
 *   node scripts/clean_test_events.js --live   # LIVE mode (asks for confirmation)
 */

const { getApps, initializeApp, cert } = require('firebase-admin/app');
const { getFirestore } = require('firebase-admin/firestore');
const readline = require('readline');
const path = require('path');

// 1. EDIT THIS LIST WITH THE EXACT IDs YOU WANT TO DELETE
const EVENTS_TO_DELETE = [
    "15_Aug_1", "15_Aug_2", "15_Aug_3", "15_Aug_4",
    "15_Aug_Test_1", "15_Aug_Test_2", "15_Aug_test_5", "15_Aug_Test_6", 
    "15_Aug_Test_7", "15_Aug_Test_8", "15_Aug_Test_9", "15_Aug_Test_10", 
    "15_Aug_Test_11", "15_Aug_Test_12", "15_Aug_Test_13", "15_Aug_Test_14", 
    "15_Aug_Test_15", "15_Aug_Test_16"
];

const isLive = process.argv.includes('--live');

function initFirebase() {
    if (getApps().length === 0) {
        const serviceAccountPath = path.resolve(__dirname, '../serviceAccountKey.json');
        const serviceAccount = require(serviceAccountPath);
        initializeApp({
            credential: cert(serviceAccount),
            projectId: 'nssiitp-app'
        });
    }
    return getFirestore();
}

const db = initFirebase();

async function askQuestion(query) {
    const rl = readline.createInterface({
        input: process.stdin,
        output: process.stdout,
    });
    return new Promise(resolve => rl.question(query, ans => {
        rl.close();
        resolve(ans);
    }));
}

// Helper to delete collections in batches
async function deleteCollection(db, collectionRef, batchSize) {
    const query = collectionRef.limit(batchSize);
    let deletedCount = 0;
    return new Promise((resolve, reject) => {
        deleteQueryBatch(db, query, resolve).then(count => {
            deletedCount = count;
        }).catch(reject);
    });
}

async function deleteQueryBatch(db, query, resolve, totalDeleted = 0) {
    const snapshot = await query.get();
    const batchSize = snapshot.size;
    if (batchSize === 0) {
        resolve(totalDeleted);
        return;
    }
    const batch = db.batch();
    snapshot.docs.forEach((doc) => {
        batch.delete(doc.ref);
    });
    await batch.commit();
    process.nextTick(() => {
        deleteQueryBatch(db, query, resolve, totalDeleted + batchSize);
    });
}

async function run() {
    console.log("==========================================");
    console.log("  NSS IITP - Clean Specific Test Events");
    console.log("==========================================\n");

    if (EVENTS_TO_DELETE.length === 0) {
        console.log("No events specified in EVENTS_TO_DELETE array. Please edit the script to add IDs.");
        process.exit(0);
    }

    console.log(`Targeting ${EVENTS_TO_DELETE.length} specific events for deletion.`);
    console.log("\nFetching attendance counts...\n");

    let totalAttendanceToDelete = 0;
    const eventsFound = [];
    const eventsNotFound = [];

    for (const eventId of EVENTS_TO_DELETE) {
        const eventRef = db.collection('NSS_Events_Attendence').doc(eventId);
        const eventDoc = await eventRef.get();

        if (!eventDoc.exists) {
            eventsNotFound.push(eventId);
            continue;
        }

        const attendanceRef = eventRef.collection('attendance');
        const attendanceDocs = await attendanceRef.get();
        totalAttendanceToDelete += attendanceDocs.size;
        eventsFound.push({
            id: eventId,
            attendanceCount: attendanceDocs.size
        });
    }

    console.log("--- SCAN RESULTS ---");
    for (const evt of eventsFound) {
        console.log(`[FOUND] ${evt.id} | Attendance records: ${evt.attendanceCount}`);
    }
    for (const evt of eventsNotFound) {
        console.log(`[NOT FOUND] ${evt} (already deleted or typo)`);
    }

    console.log("\n--- SUMMARY ---");
    console.log(`Total Events to delete: ${eventsFound.length}`);
    console.log(`Total Attendance Sub-documents to delete: ${totalAttendanceToDelete}`);
    console.log("WILL NOT TOUCH: users, PhotoAttendanceLog, scheduledNotifications");
    console.log("------------------------------------------");

    if (!isLive) {
        console.log("\n[DRY RUN] This was a dry run. Nothing was deleted.");
        console.log("Run with --live to actually execute deletions.\n");
        process.exit(0);
    }

    console.log("\n🚨 WARNING: LIVE MODE ENABLED 🚨");
    console.log("This will permanently delete the events and attendance documents listed above.");
    const answer = await askQuestion("Type 'YES' (all caps) to confirm deletion: ");

    if (answer !== 'YES') {
        console.log("\nAborting deletion. Nothing was changed.");
        process.exit(0);
    }

    console.log("\nExecuting deletions...");

    for (const evt of eventsFound) {
        console.log(`Deleting ${evt.id}...`);
        const eventRef = db.collection('NSS_Events_Attendence').doc(evt.id);
        const attendanceRef = eventRef.collection('attendance');
        
        // Delete subcollection first
        if (evt.attendanceCount > 0) {
            await deleteCollection(db, attendanceRef, 500);
            console.log(`  -> Deleted ${evt.attendanceCount} attendance records.`);
        }
        
        // Delete event document itself
        await eventRef.delete();
        console.log(`  -> Deleted event document.`);
    }

    console.log("\n✅ SUCCESS: All specified events and their subcollections were deleted.");
    process.exit(0);
}

run().catch(console.error);
