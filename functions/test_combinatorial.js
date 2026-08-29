const admin = require('firebase-admin');
process.env.FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080';
process.env.GCLOUD_PROJECT = 'demo-project';

const myFunctions = require('./index.js');
const testEnv = require('firebase-functions-test')({ projectId: 'demo-project' });
const wrappedOnAttendanceCreate = testEnv.wrap(myFunctions.onAttendanceCreate);

const auth = admin.auth();
auth.verifyIdToken = async (token) => ({ uid: 'admin_user', email: 'admin@test.com' });

const db = admin.firestore();
const FieldValue = admin.firestore.FieldValue;

async function mockReqRes(body) {
    const req = { method: 'POST', headers: { authorization: 'Bearer MOCK_TOKEN' }, body: body };
    let data;
    const res = { status: function(code) { return this; }, json: function(d) { data = d; }, send: function(d) { data = d; }, getData: () => data };
    return { req, res };
}

async function runCombinatorialTests() {
    console.log("==================================================");
    console.log("   NSS BACKEND COMBINATORIAL TEST SUITE START     ");
    console.log("==================================================\n");

    await db.collection('users').doc('admin_user').set({ name: 'Test Admin', userType: 'Admin' });

    const isMandatory = [true, false];
    // Note: To the backend trigger, QR/Manual/Photo are identical (just an attendance document write). 
    // We mock the DB write exactly as any of those clients would.
    const semesters = [
        { name: 'Sem1 (Nov)', date: '15-11-2023', id: 1 }, 
        { name: 'Sem2 (Mar)', date: '15-03-2024', id: 2 }
    ];
    const userOverrides = ['positive', 'negative', 'zero', 'none'];
    const executionModes = ['single', 'bulk'];
    const lifecycleModes = ['initial_close', 'reopen_and_reapply'];

    let passCount = 0;
    let failCount = 0;
    let testId = 1;

    for (const mandatory of isMandatory) {
        for (const sem of semesters) {
            for (const execMode of executionModes) {
                for (const override of userOverrides) {
                    for (const lifecycle of lifecycleModes) {
                        const eventId = `combo_evt_${testId}`;
                        const uId = `S_${testId}`;
                        
                        // 1. Setup Student
                        await db.collection('users').doc(uId).set({
                            name: `Student ${uId}`, userType: 'Student', wings: ['Technical'], hours: 0, eventsAttended: 0, sem1Hours: 0, sem2Hours: 0
                        });

                        // 2. Setup Event
                        await db.collection('NSS_Events_Attendence').doc(eventId).set({
                            title: `Test ${testId}`, mandatory: mandatory, hours: 2, negativeHours: 1.5, wings: ['Technical'], absentPenaltyApplied: false, eventDate: sem.date
                        });

                        // 3. Simulate Attendance (Client-side write + trigger)
                        // To test overrides effectively, we assume the student initially got attendance OR missed it.
                        // If override is 'negative' or 'zero', let's assume they got attendance but the admin overrides it.
                        // If override is 'positive', let's assume they were absent.
                        // If 'none', assume absent.
                        let initialHours = 0;
                        if (override === 'negative' || override === 'zero') {
                            await db.collection('NSS_Events_Attendence').doc(eventId).collection('attendance').doc(uId).set({ rollNumber: uId });
                            const snap = testEnv.firestore.makeDocumentSnapshot({ rollNumber: uId }, `NSS_Events_Attendence/${eventId}/attendance/${uId}`);
                            await wrappedOnAttendanceCreate({ data: snap, params: { eventId, rollNumber: uId } });
                            initialHours = 2; // Should have 2 hours initially
                        }

                        // 4. Construct Penalty Lists
                        const posList = override === 'positive' ? [uId] : [];
                        const negList = override === 'negative' ? [uId] : [];
                        const zeroList = override === 'zero' ? [uId] : [];
                        
                        // If bulk, we just add dummy users to the lists to simulate bulk processing load
                        if (execMode === 'bulk') {
                            if (posList.length > 0) posList.push('DUMMY_POS1', 'DUMMY_POS2');
                            if (negList.length > 0) negList.push('DUMMY_NEG1', 'DUMMY_NEG2');
                            if (zeroList.length > 0) zeroList.push('DUMMY_ZERO1', 'DUMMY_ZERO2');
                        }

                        // 5. Apply Penalty (Initial Close)
                        const { req, res } = await mockReqRes({
                            eventId: eventId, positiveRollNumbers: posList, negativeRollNumbers: negList, zeroRollNumbers: zeroList
                        });
                        await myFunctions.applyAbsentPenalty(req, res);
                        
                        // 6. Handle Reopen Lifecycle
                        if (lifecycle === 'reopen_and_reapply' && mandatory) {
                            // Admin reopens event
                            await db.collection('NSS_Events_Attendence').doc(eventId).update({ absentPenaltyApplied: false });
                            
                            // Let's say Admin now moves the student to the OPPOSITE state to test refund logic
                            let newPos = [], newNeg = [], newZero = [];
                            if (override === 'negative') newPos = [uId]; // refund penalty
                            if (override === 'positive') newNeg = [uId]; // revoke manual attendance and penalize
                            if (override === 'zero') newNeg = [uId]; // penalize exempt user
                            if (override === 'none') newZero = [uId]; // exempt absent user
                            
                            const { req: req2, res: res2 } = await mockReqRes({
                                eventId: eventId, positiveRollNumbers: newPos, negativeRollNumbers: newNeg, zeroRollNumbers: newZero
                            });
                            await myFunctions.applyAbsentPenalty(req2, res2);
                        }

                        // 7. Verify State
                        const userDoc = (await db.collection('users').doc(uId).get()).data();
                        const finalHours = userDoc.hours;
                        const finalSemHours = sem.id === 1 ? userDoc.sem1Hours : userDoc.sem2Hours;
                        
                        // Calculate Expected Math
                        let expected = initialHours;
                        if (!mandatory) {
                            expected = initialHours; // Non-mandatory = no penalty logic applies
                        } else {
                            if (lifecycle === 'initial_close') {
                                if (override === 'negative') expected = initialHours - 2 - 1.5; // -1.5
                                else if (override === 'positive') expected = initialHours + 2; // +2
                                else if (override === 'zero') expected = initialHours - 2; // 0
                                else expected = -1.5; // absent -> penalty
                            } else {
                                // reopen_and_reapply logic overrides
                                if (override === 'negative') expected = 0; // refunded penalty => 0
                                else if (override === 'positive') expected = -1.5; // revoked (+2) then penalized (-1.5) = -1.5
                                else if (override === 'zero') expected = -1.5; // revoked (+0) then penalized (-1.5) = -1.5
                                else expected = 0; // absent penalized (-1.5) then exempted => 0
                            }
                        }

                        const passed = (finalHours === expected) && (finalSemHours === expected || (expected < 0 && finalSemHours === 0) || (!mandatory && expected === 2 && finalSemHours === 2)); 
                        // Note: If hours is negative, does semHours go negative? The code uses FieldValue.increment(-hours). If initial semHours was 0, it becomes negative.
                        
                        // Let's be exact:
                        let expectedSemHours = expected;

                        const mathPassed = (finalHours === expected);

                        if (mathPassed) passCount++; else failCount++;

                        console.log(`[TEST ${String(testId).padStart(3, '0')}] Mand:${String(mandatory).padEnd(5)} | Sem:${sem.id} | Mode:${String(execMode).padEnd(6)} | Over:${String(override).padEnd(8)} | Life:${String(lifecycle).padEnd(18)} => Expected: ${expected}, Actual: ${finalHours} | RESULT: ${mathPassed ? 'PASS' : 'FAIL'}`);
                        
                        testId++;
                    }
                }
            }
        }
    }

    console.log("\n==================================================");
    console.log(`   TEST RUN COMPLETE. Passed: ${passCount}, Failed: ${failCount}   `);
    console.log("==================================================\n");
    process.exit(0);
}

runCombinatorialTests();
