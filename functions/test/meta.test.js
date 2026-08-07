const { describe, it, before, after } = require('node:test');
const assert = require('node:assert');
const { fft, admin, db } = require('./mock');
const myFunctions = require('../index.js');

describe('Module 2: Event Meta Statistics', () => {
    
    before(async () => {
        await fetch(`http://127.0.0.1:8080/emulator/v1/projects/demo-test/databases/(default)/documents`, { method: 'DELETE' });
    });

    after(async () => {
        await fetch(`http://127.0.0.1:8080/emulator/v1/projects/demo-test/databases/(default)/documents`, { method: 'DELETE' });
        fft.cleanup();
    });

    it('9. onEventWrite: Atomically increments totalEvents by 1 when a new event is created', async () => {
        const wrapped = fft.wrap(myFunctions.onEventWrite);
        await db.collection('meta').doc('statistics').set({ totalEvents: 10 });
        
        const snap = fft.firestore.makeDocumentSnapshot({ title: 'New Event' }, 'NSS_Events_Attendence/event1');
        await wrapped({ data: snap, params: { eventId: 'event1' } });
        
        const stats = (await db.collection('meta').doc('statistics').get()).data();
        assert.strictEqual(stats.totalEvents, 11);
    });

    it('10. onEventDelete: Atomically decrements totalEvents by 1 when an event is deleted', async () => {
        const wrapped = fft.wrap(myFunctions.onEventDelete);
        await db.collection('meta').doc('statistics').set({ totalEvents: 11 });
        
        const snap = fft.firestore.makeDocumentSnapshot({ title: 'Old Event' }, 'NSS_Events_Attendence/event1');
        await wrapped({ data: snap, params: { eventId: 'event1' } });
        
        const stats = (await db.collection('meta').doc('statistics').get()).data();
        assert.strictEqual(stats.totalEvents, 10);
    });
});
