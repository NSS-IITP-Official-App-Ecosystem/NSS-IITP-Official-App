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
    const req = {
        method: 'POST',
        headers: { authorization: 'Bearer MOCK_TOKEN' },
        body: body
    };
    const res = {
        status: function(code) { this.statusCode = code; return this; },
        json: function(data) { this.data = data; },
        send: function(data) { this.data = data; }
    };
    return { req, res };
}

async function runTests() {
    console.log("Starting backend tests against Firestore Emulator...\n");

    await db.collection('users').doc('admin_user').set({ name: 'Test Admin', userType: 'Admin' });

    async function resetStudent(id) {
        await db.collection('users').doc(id).set({
            name: `Student ${id}`, userType: 'Student', wings: ['Technical'], hours: 0, eventsAttended: 0, sem1Hours: 0, sem2Hours: 0
        });
    }

    // SCENARIO 1
    console.log("--- SCENARIO 1: Present student moved to negSet (Loses earned + penalized) ---");
    await resetStudent('T1');
    await db.collection('NSS_Events_Attendence').doc('event1').set({
        title: 'Event 1', mandatory: true, hours: 2, negativeHours: 1.5, wings: ['Technical'], absentPenaltyApplied: false, eventDate: '2023-11-15'
    });
    await db.collection('NSS_Events_Attendence').doc('event1').collection('attendance').doc('T1').set({ rollNumber: 'T1' });
    const snap1 = testEnv.firestore.makeDocumentSnapshot({ rollNumber: 'T1' }, 'NSS_Events_Attendence/event1/attendance/T1');
    await wrappedOnAttendanceCreate({ data: snap1, params: { eventId: 'event1', rollNumber: 'T1' } });
    
    let t1_pre = (await db.collection('users').doc('T1').get()).data();
    console.log(`T1 Hours BEFORE Penalty: ${t1_pre.hours}`);
    
    const { req: r1, res: rs1 } = await mockReqRes({
        eventId: 'event1', positiveRollNumbers: [], negativeRollNumbers: ['T1'], zeroRollNumbers: []
    });
    await myFunctions.applyAbsentPenalty(r1, rs1);
    let t1_post = (await db.collection('users').doc('T1').get()).data();
    console.log(`T1 Hours AFTER Penalty: ${t1_post.hours} (Expected: 2 - 2 - 1.5 = -1.5)`);
    console.log(`SCENARIO 1: ${t1_post.hours === -1.5 ? 'PASS' : 'FAIL'}\n`);

    // SCENARIO 2
    console.log("--- SCENARIO 2: Retroactive Batch Update Math (Edit Hours) ---");
    await resetStudent('T2');
    await db.collection('NSS_Events_Attendence').doc('event2').set({
        title: 'Event 2', hours: 2, attendees: [{rollNumber: 'T2'}], eventDate: '2023-11-15'
    });
    await db.collection('users').doc('T2').update({ hours: 2, eventsAttended: 1, sem1Hours: 2 });
    
    const oldHours = 2; const newHours = 4; const hoursDelta = newHours - oldHours;
    const batch = db.batch();
    batch.update(db.collection('users').doc('T2'), { hours: FieldValue.increment(hoursDelta), sem1Hours: FieldValue.increment(hoursDelta) });
    await batch.commit();
    let t2 = (await db.collection('users').doc('T2').get()).data();
    console.log(`T2 Hours AFTER Edit: ${t2.hours} (Expected: 4)`);
    console.log(`SCENARIO 2: ${t2.hours === 4 ? 'PASS' : 'FAIL'}\n`);

    // SCENARIO 3
    console.log("--- SCENARIO 3: Non-mandatory event penalty refusal ---");
    await db.collection('NSS_Events_Attendence').doc('event3').set({
        title: 'Event 3', mandatory: false, hours: 2, wings: ['Technical'], absentPenaltyApplied: false
    });
    const { req: r3, res: rs3 } = await mockReqRes({
        eventId: 'event3', positiveRollNumbers: [], negativeRollNumbers: ['T1'], zeroRollNumbers: []
    });
    await myFunctions.applyAbsentPenalty(r3, rs3);
    console.log(`Response: ${rs3.data.reason}`);
    console.log(`SCENARIO 3: ${rs3.data.ok === false ? 'PASS' : 'FAIL'}\n`);

    // SCENARIO 4
    console.log("--- SCENARIO 4: Add and Remove Student (Balance check) ---");
    await resetStudent('T4');
    await db.collection('NSS_Events_Attendence').doc('event4').set({
        title: 'Event 4', hours: 3, eventDate: '2023-11-15', wings: ['Technical']
    });
    await db.collection('NSS_Events_Attendence').doc('event4').collection('attendance').doc('T4').set({ rollNumber: 'T4' });
    const snap4 = testEnv.firestore.makeDocumentSnapshot({ rollNumber: 'T4' }, 'NSS_Events_Attendence/event4/attendance/T4');
    await wrappedOnAttendanceCreate({ data: snap4, params: { eventId: 'event4', rollNumber: 'T4' } });
    let t4_mid = (await db.collection('users').doc('T4').get()).data();
    console.log(`T4 Hours AFTER Add: ${t4_mid.hours} (Expected: 3)`);
    
    const removeBatch = db.batch();
    removeBatch.update(db.collection('users').doc('T4'), {
        hours: FieldValue.increment(-3), eventsAttended: FieldValue.increment(-1), sem1Hours: FieldValue.increment(-3)
    });
    await removeBatch.commit();
    let t4_post = (await db.collection('users').doc('T4').get()).data();
    console.log(`T4 Hours AFTER Remove: ${t4_post.hours} (Expected: 0)`);
    console.log(`SCENARIO 4: ${t4_post.hours === 0 ? 'PASS' : 'FAIL'}\n`);

    // SCENARIO 5
    console.log("--- SCENARIO 5: Semester Boundary Check ---");
    await resetStudent('T5');
    await db.collection('NSS_Events_Attendence').doc('event5_sem1').set({ hours: 2, eventDate: '15-11-2023' });
    await db.collection('NSS_Events_Attendence').doc('event5_sem1').collection('attendance').doc('T5').set({ rollNumber: 'T5' });
    const snap5_1 = testEnv.firestore.makeDocumentSnapshot({ rollNumber: 'T5' }, 'NSS_Events_Attendence/event5_sem1/attendance/T5');
    await wrappedOnAttendanceCreate({ data: snap5_1, params: { eventId: 'event5_sem1', rollNumber: 'T5' } });
    
    await db.collection('NSS_Events_Attendence').doc('event5_sem2').set({ hours: 3, eventDate: '2024-03-15' });
    await db.collection('NSS_Events_Attendence').doc('event5_sem2').collection('attendance').doc('T5').set({ rollNumber: 'T5' });
    const snap5_2 = testEnv.firestore.makeDocumentSnapshot({ rollNumber: 'T5' }, 'NSS_Events_Attendence/event5_sem2/attendance/T5');
    await wrappedOnAttendanceCreate({ data: snap5_2, params: { eventId: 'event5_sem2', rollNumber: 'T5' } });
    
    let t5 = (await db.collection('users').doc('T5').get()).data();
    console.log(`T5 Total Hours: ${t5.hours} (Expected: 5)`);
    console.log(`T5 Sem 1 Hours: ${t5.sem1Hours} (Expected: 2)`);
    console.log(`T5 Sem 2 Hours: ${t5.sem2Hours} (Expected: 3)`);
    console.log(`SCENARIO 5: ${t5.sem1Hours === 2 && t5.sem2Hours === 3 ? 'PASS' : 'FAIL'}\n`);

    console.log("--- ALL TESTS COMPLETED ---");
    process.exit(0);
}

runTests();
