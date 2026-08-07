const { describe, it, before, after } = require('node:test');
const assert = require('node:assert');
const { fft, admin, db } = require('./mock');
const myFunctions = require('../index.js');

describe('Module 5: Scheduled Tasks', () => {
    
    before(async () => {
        await fetch(`http://127.0.0.1:8080/emulator/v1/projects/demo-test/databases/(default)/documents`, { method: 'DELETE' });
    });

    after(async () => {
        await fetch(`http://127.0.0.1:8080/emulator/v1/projects/demo-test/databases/(default)/documents`, { method: 'DELETE' });
        fft.cleanup();
    });

    it('20. processScheduledNotifications: Executes daily Cron job', async () => {
        const wrapped = fft.wrap(myFunctions.processScheduledNotifications);
        
        // Mock FCM since it touches google APIs
        admin.messaging = () => ({
            sendMulticast: async () => ({ successCount: 1, failureCount: 0 })
        });

        const context = { timestamp: new Date().toISOString() };
        await assert.doesNotReject(wrapped(context));
    });
});
