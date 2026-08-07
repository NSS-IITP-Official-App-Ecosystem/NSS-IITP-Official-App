const { describe, it, before, after } = require('node:test');
const assert = require('node:assert');
const { fft, admin, db } = require('./mock');
const myFunctions = require('../index.js');

describe('Module 1: Attendance Engine', () => {
    
    before(async () => {
        // Clear demo-test database before tests
        await fetch(`http://127.0.0.1:8080/emulator/v1/projects/demo-test/databases/(default)/documents`, { method: 'DELETE' });
    });

    after(async () => {
        // Clear demo-test database after tests
        await fetch(`http://127.0.0.1:8080/emulator/v1/projects/demo-test/databases/(default)/documents`, { method: 'DELETE' });
        fft.cleanup();
    });

    it('1. onAttendanceCreate: Valid attendance creates correct stats (+1 event, +hours)', async () => {
        const wrapped = fft.wrap(myFunctions.onAttendanceCreate);
        await db.collection('NSS_Events_Attendence').doc('event1').set({ hours: 2, eventDate: '2026-08-01', total_marked: 0 });
        await db.collection('users').doc('2001CS01').set({ eventsAttended: 0, hours: 0, sem1Hours: 0, eventsList: [] });

        const snap = fft.firestore.makeDocumentSnapshot({ method: 'QR' }, 'NSS_Events_Attendence/event1/attendance/2001CS01');
        await wrapped({ data: snap, params: { eventId: 'event1', rollNumber: '2001CS01' } });

        const event = (await db.collection('NSS_Events_Attendence').doc('event1').get()).data();
        assert.strictEqual(event.total_marked, 1);
        const user = (await db.collection('users').doc('2001CS01').get()).data();
        assert.strictEqual(user.eventsAttended, 1);
        assert.strictEqual(user.hours, 2);
    });

    it('2. onAttendanceCreate: Skips hour credit if isMigration is true', async () => {
        const wrapped = fft.wrap(myFunctions.onAttendanceCreate);
        await db.collection('NSS_Events_Attendence').doc('event2').set({ hours: 2, total_marked: 0 });
        await db.collection('users').doc('2001CS02').set({ eventsAttended: 0, hours: 0 });

        const snap = fft.firestore.makeDocumentSnapshot({ isMigration: true }, 'NSS_Events_Attendence/event2/attendance/2001CS02');
        await wrapped({ data: snap, params: { eventId: 'event2', rollNumber: '2001CS02' } });

        const event = (await db.collection('NSS_Events_Attendence').doc('event2').get()).data();
        assert.strictEqual(event.total_marked, 0);
        const user = (await db.collection('users').doc('2001CS02').get()).data();
        assert.strictEqual(user.hours, 0);
    });

    it('3. onAttendanceCreate: Safely ignores orphaned attendance records', async () => {
        const wrapped = fft.wrap(myFunctions.onAttendanceCreate);
        const snap = fft.firestore.makeDocumentSnapshot({}, 'NSS_Events_Attendence/event999/attendance/2001CS03');
        await assert.doesNotReject(wrapped({ data: snap, params: { eventId: 'event999', rollNumber: '2001CS03' } }));
    });

    it('4. applyAbsentPenalty: Rejects non-POST requests', async () => {
        const req = { method: 'GET' };
        let statusSet;
        const res = { 
          status: (code) => { statusSet = code; return res; }, 
          send: () => {},
          json: () => {} 
        };
        await myFunctions.applyAbsentPenalty(req, res);
        assert.strictEqual(statusSet, 405);
    });

    // Mock auth globally for the HTTP functions
    const mockReq = (body) => ({
        method: 'POST',
        headers: { authorization: 'Bearer dummy' },
        body
    });

    it('5. applyAbsentPenalty: Applies NEGATIVE penalty', async () => {
        // Stub verifyIdToken for these tests
        admin.auth().verifyIdToken = async () => ({ uid: 'admin-uid', email: 'admin@example.com', role: 'admin' });
        await db.collection('users_by_uid').doc('admin-uid').set({ role: 'admin' });
        await db.collection('users').doc('ADMIN').set({ userType: 'Admin' });
        
        await db.collection('NSS_Events_Attendence').doc('eventNeg').set({ negativeHours: 1, mandatory: true });
        await db.collection('users').doc('2001CS04').set({ hours: 5, sem1Hours: 5 });

        let statusSet = 200;
        const req = mockReq({ 
            eventId: 'eventNeg', 
            positiveRollNumbers: [],
            negativeRollNumbers: ['2001CS04'],
            zeroRollNumbers: []
        });
        const res = { status: (c) => { statusSet = c; return res; }, send: () => {}, json: () => {} };
        
        await myFunctions.applyAbsentPenalty(req, res);
        
        const user = (await db.collection('users').doc('2001CS04').get()).data();
        assert.strictEqual(user.hours, 4);
        assert.strictEqual(statusSet, 200);
    });
});
