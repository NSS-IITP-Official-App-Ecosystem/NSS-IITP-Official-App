const admin = require('firebase-admin');
process.env.FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080';
process.env.GCLOUD_PROJECT = 'demo-project';

// We will use require to load the functions. index.js initializes admin.
const myFunctions = require('./index.js');

// Mock auth
const auth = admin.auth();
auth.verifyIdToken = async (token) => {
    return { uid: 'admin_user', email: 'admin@test.com' };
};

const db = admin.firestore();

async function runTests() {
    console.log("Starting backend tests against Firestore Emulator...");

    // Setup Test Data
    const eventId1 = 'test_event_1';
    
    // Create admin user for assertCallerIsAdmin
    console.log("Setting up admin user...");
    await db.collection('users').doc('admin_user').set({
        name: 'Test Admin', userType: 'Admin'
    });

    // Create students
    console.log("Setting up 3 test students...");
    await db.collection('users').doc('TEST01').set({
        name: 'Student A', userType: 'Student', wings: ['Technical'], hours: 0, eventsAttended: 0
    });
    await db.collection('users').doc('TEST02').set({
        name: 'Student B', userType: 'Student', wings: ['Technical'], hours: 0, eventsAttended: 0
    });
    await db.collection('users').doc('TEST03').set({
        name: 'Student C', userType: 'Student', wings: ['Technical'], hours: 0, eventsAttended: 0
    });

    console.log("--- TEST 1: Apply Absent Penalty (The Batch Crash Scenario) ---");
    // Create an event
    await db.collection('NSS_Events_Attendence').doc(eventId1).set({
        title: 'Mandatory Test Event',
        mandatory: true,
        hours: 2,
        negativeHours: 1.5,
        wings: ['Technical'],
        absentPenaltyApplied: false
    });

    // We will call applyAbsentPenalty. It expects a req and res.
    const req = {
        method: 'POST',
        headers: { authorization: 'Bearer MOCK_TOKEN' },
        body: {
            eventId: eventId1,
            positiveRollNumbers: [], // no one attended
            negativeRollNumbers: ['TEST01', 'TEST02'], // absentees being penalized
            zeroRollNumbers: ['TEST03'] // exempt
        }
    };
    
    // Mock response object
    const res = {
        status: function(code) { this.statusCode = code; return this; },
        json: function(data) { this.data = data; },
        send: function(data) { this.data = data; }
    };

    console.log("Calling applyAbsentPenalty...");
    try {
        await myFunctions.applyAbsentPenalty(req, res);
        console.log("applyAbsentPenalty response:", res.data);
    } catch (e) {
        console.error("applyAbsentPenalty failed:", e);
    }

    // Verify DB
    const u1 = (await db.collection('users').doc('TEST01').get()).data();
    const u2 = (await db.collection('users').doc('TEST02').get()).data();
    const u3 = (await db.collection('users').doc('TEST03').get()).data();
    
    console.log("Results after penalty:");
    console.log(`TEST01 Hours: ${u1.hours} (Expected: -1.5)`);
    console.log(`TEST02 Hours: ${u2.hours} (Expected: -1.5)`);
    console.log(`TEST03 Hours: ${u3.hours} (Expected: 0)`);
    
    const passed1 = u1.hours === -1.5 && u2.hours === -1.5 && u3.hours === 0;
    console.log(`TEST 1 RESULT: ${passed1 ? 'PASS' : 'FAIL'}`);

    console.log("\n--- TEST 2: Reopening Event & Refunding Penalty (Latecomer) ---");
    // To simulate a refund, the admin manually marks TEST01 as present. 
    // They add them to the attendance collection. This would normally trigger onAttendanceCreate.
    // Wait, since we are directly calling functions, we need to call onAttendanceCreate, OR
    // we can call applyAbsentPenalty again with updated arrays to see if it refunds!
    // But applyAbsentPenalty refuses to run if absentPenaltyApplied is true.
    // Let's reset the event manually to simulate "Make Live" (reopening).
    console.log("Simulating 'Make Live' (reopen event)...");
    await db.collection('NSS_Events_Attendence').doc(eventId1).update({
        absentPenaltyApplied: false
    });

    console.log("Admin manually applies penalty again after TEST01 is moved to positive (refunded)...");
    const req2 = {
        method: 'POST',
        headers: { authorization: 'Bearer MOCK_TOKEN' },
        body: {
            eventId: eventId1,
            positiveRollNumbers: ['TEST01'], // latecomer marked present
            negativeRollNumbers: ['TEST02'], // still absent
            zeroRollNumbers: ['TEST03'] // still exempt
        }
    };
    
    const res2 = {
        status: function(code) { this.statusCode = code; return this; },
        json: function(data) { this.data = data; },
        send: function(data) { this.data = data; }
    };

    await myFunctions.applyAbsentPenalty(req2, res2);
    console.log("applyAbsentPenalty response 2:", res2.data);

    // Verify DB again
    const u1_2 = (await db.collection('users').doc('TEST01').get()).data();
    const u2_2 = (await db.collection('users').doc('TEST02').get()).data();
    
    console.log("Results after refund:");
    console.log(`TEST01 Hours: ${u1_2.hours} (Expected: 0 from refund. Note: positive hours would normally be added by onAttendanceCreate or client batch)`);
    console.log(`TEST02 Hours: ${u2_2.hours} (Expected: -1.5)`);

    const passed2 = u1_2.hours === 0 && u2_2.hours === -1.5;
    console.log(`TEST 2 RESULT: ${passed2 ? 'PASS' : 'FAIL'}`);

    console.log("\n--- TESTS COMPLETED ---");
    process.exit(0);
}

runTests();
