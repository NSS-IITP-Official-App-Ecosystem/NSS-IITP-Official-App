const admin = require('firebase-admin');
process.env.FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080';
process.env.GCLOUD_PROJECT = 'demo-project';

const myFunctions = require('./index.js');
const testEnv = require('firebase-functions-test')({ projectId: 'demo-project' });
const wrappedOnAttendanceCreate = testEnv.wrap(myFunctions.onAttendanceCreate);
const auth = admin.auth();
auth.verifyIdToken = async (token) => ({ uid: 'admin_user', email: 'admin@test.com' });

const db = admin.firestore();

async function mockReqRes(body) {
    const req = { method: 'POST', headers: { authorization: 'Bearer MOCK_TOKEN' }, body: body };
    let data;
    const res = { status: function(code) { return this; }, json: function(d) { data = d; }, send: function(d) { data = d; }, getData: () => data };
    return { req, res };
}

// Ensure the db is clean for these specific tests
async function setupEvent(eventId, isMandatory, date) {
    await db.collection('NSS_Events_Attendence').doc(eventId).set({
        title: `Test Event ${eventId}`, mandatory: isMandatory, hours: 2, negativeHours: 1.5, wings: ['Technical'], absentPenaltyApplied: false, eventDate: date
    });
}

async function setupStudent(uId) {
    await db.collection('users').doc(uId).set({
        name: `Student ${uId}`, userType: 'Student', wings: ['Technical'], hours: 0, eventsAttended: 0, sem1Hours: 0, sem2Hours: 0
    });
}

// Helper to simulate attendance creation exactly as it triggers Cloud Functions
async function simulateAttendanceCreate(eventId, uId, method) {
    let payload = { rollNumber: uId };
    if (method === 'qr') payload.timestamp = new Date();
    if (method === 'photo') payload.status = 'approved';
    if (method === 'manual') payload.addedBy = 'admin';

    await db.collection('NSS_Events_Attendence').doc(eventId).collection('attendance').doc(uId).set(payload);
    const snap = testEnv.firestore.makeDocumentSnapshot(payload, `NSS_Events_Attendence/${eventId}/attendance/${uId}`);
    
    // EXPLICITLY INVOKING TRIGGER
    console.log(`[TRIGGER] Invoking wrappedOnAttendanceCreate for ${uId} (Method: ${method})`);
    await wrappedOnAttendanceCreate({ data: snap, params: { eventId, rollNumber: uId } });
}

async function runExhaustiveTests() {
    console.log("==================================================");
    console.log("   EXHAUSTIVE COMBINATORIAL TEST SUITE START      ");
    console.log("==================================================\n");

    const isMandatoryList = [true, false];
    const semesters = [
        { name: 'Sem1 (Nov)', date: '15-11-2023', semExpected: 1 }, 
        { name: 'Boundary (10-Dec)', date: '10-12-2023', semExpected: 2 }, // 10 Dec is strictly > Dec 10 in logic? Logic: (month === 12 && day > 10). If 10-12, month=12, day=10. 10 > 10 is false -> Sem 1.
        { name: 'Sem2 (Mar)', date: '15-03-2024', semExpected: 2 }
    ];
    const creationMethods = ['qr', 'manual', 'photo'];
    const cyclesList = [0, 1, 2]; // 0 = initial, 1 = reopen once, 2 = reopen twice

    let passCount = 0;
    let failCount = 0;
    let testId = 1;

    for (const mandatory of isMandatoryList) {
        for (const sem of semesters) {
            for (const cycles of cyclesList) {
                
                // For each combination of Mandatory x Sem x Cycles, we will run a MIXED BULK transaction.
                // We will create 5 students:
                // S_pos: Will be passed in positive array
                // S_neg: Will be passed in negative array
                // S_zero: Will be passed in zero array
                // S_none: Will be absent but passed in no array
                // S_att: Will have attendance via one of the creationMethods, but passed in negative array later
                
                for(const method of creationMethods) {
                    const eventId = `combo_evt_${testId}`;
                    const p = `S_${testId}_pos`;
                    const n = `S_${testId}_neg`;
                    const z = `S_${testId}_zero`;
                    const no = `S_${testId}_none`;
                    const att = `S_${testId}_att_${method}`;

                    await setupEvent(eventId, mandatory, sem.date);
                    await setupStudent(p); await setupStudent(n); await setupStudent(z); await setupStudent(no); await setupStudent(att);

                    // Only the 'att' student actually gets attendance BEFORE the penalty
                    await simulateAttendanceCreate(eventId, att, method);

                    // Track expected states mathematically
                    // S_pos (absent -> +2)
                    // S_neg (absent -> -1.5)
                    // S_zero (absent -> 0)
                    // S_none (absent -> ignored, stays 0)
                    // S_att (present (+2) -> moved to neg (-1.5) -> overall -1.5 if penalty overrides attendance) 
                    // Note: applyAbsentPenalty loops through negativeRollNumbers. If student is in negativeRollNumbers, they get -1.5 regardless of presence.
                    // Actually, if they had +2, and we give them -1.5, their balance becomes 0.5. Wait! The code uses FieldValue.increment(-negativeHours). It does NOT remove their earned hours unless they are explicitly removed from attendance!
                    // Let's verify exactly what the Kotlin app does: The Android app does NOT call `removeAttendeeFromEvent` when moving a present student to absent in the apply penalty UI. It just passes them in the negative array. Thus they keep +2 and get -1.5, netting 0.5. Let's strictly test backend math.
                    
                    let posList = [p];
                    let negList = [n, att]; // Admin penalizes 'att' despite them having attendance
                    let zeroList = [z];
                    let noneList = [no];

                    for (let c = 0; c <= cycles; c++) {
                        if (c > 0) {
                            // Reopen event
                            await db.collection('NSS_Events_Attendence').doc(eventId).update({ absentPenaltyApplied: false });
                            
                            // Re-apply penalty with a MIXED bulk load.
                            // To make it interesting, swap their arrays!
                            // S_pos -> negList
                            // S_neg -> posList
                            // S_zero -> noneList
                            // S_none -> zeroList
                            posList = [n];
                            negList = [p];
                            zeroList = [no];
                            noneList = [z, att];
                        }

                        console.log(`[CYCLE ${c}] Applying Bulk Mixed Penalty for ${eventId}`);
                        const { req, res } = await mockReqRes({
                            eventId: eventId, positiveRollNumbers: posList, negativeRollNumbers: negList, zeroRollNumbers: zeroList
                        });
                        await myFunctions.applyAbsentPenalty(req, res);
                    }

                    // Verify expected outcomes based on the LAST cycle executed.
                    // If mandatory == false, applyAbsentPenalty does nothing.
                    const finalP = (await db.collection('users').doc(p).get()).data().hours;
                    const finalN = (await db.collection('users').doc(n).get()).data().hours;
                    const finalZ = (await db.collection('users').doc(z).get()).data().hours;
                    const finalNo = (await db.collection('users').doc(no).get()).data().hours;
                    const finalAtt = (await db.collection('users').doc(att).get()).data().hours;

                    // MATH LOGIC (Strict translation of backend index.js logic)
                    let expP = 0, expN = 0, expZ = 0, expNo = 0, expAtt = 2; // initial states (att has 2 from onAttendanceCreate)
                    
                    if (mandatory) {
                        if (cycles === 0) {
                            expP = 2; expN = -1.5; expZ = 0; expNo = 0; expAtt = 2 - 1.5; // 0.5
                        } else if (cycles >= 1) {
                            // After cycle 0: p=2, n=-1.5, z=0, no=0, att=0.5
                            // In cycle 1, the backend refunds cycle 0: 
                            // p loses 2 (0), n gets back 1.5 (0), z gets 0 (0), no gets 0 (0), att gets back 1.5 (2)
                            // Then applies new lists: posList=[n], negList=[p], zeroList=[no], noneList=[z, att]
                            // n gets +2, p gets -1.5, no gets 0. att is left alone (keeps 2).
                            expP = -1.5; expN = 2; expZ = 0; expNo = 0; expAtt = 2;
                        }
                    }

                    const passed = (finalP === expP && finalN === expN && finalZ === expZ && finalNo === expNo && finalAtt === expAtt);

                    console.log(`[TEST ${String(testId).padStart(3, '0')}] Mand:${String(mandatory).padEnd(5)} | SemDate:${sem.date} | Cycles:${cycles} | Meth:${method}`);
                    console.log(`  -> EXP: P=${expP}, N=${expN}, Z=${expZ}, No=${expNo}, Att=${expAtt}`);
                    console.log(`  -> ACT: P=${finalP}, N=${finalN}, Z=${finalZ}, No=${finalNo}, Att=${finalAtt}`);
                    console.log(`  -> RESULT: ${passed ? 'PASS' : 'FAIL'}`);

                    if (passed) passCount++; else failCount++;
                    testId++;
                }
            }
        }
    }

    console.log("\n==================================================");
    console.log(`   TEST RUN COMPLETE. Passed: ${passCount}, Failed: ${failCount}   `);
    console.log("==================================================\n");
    process.exit(0);
}

runExhaustiveTests();
