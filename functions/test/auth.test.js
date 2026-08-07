const { describe, it, before, after } = require('node:test');
const assert = require('node:assert');
const { fft, admin, db } = require('./mock');
const myFunctions = require('../index.js');

describe('Module 3: Authentication & Device Binding', () => {
    
    before(async () => {
        await fetch(`http://127.0.0.1:8080/emulator/v1/projects/demo-test/databases/(default)/documents`, { method: 'DELETE' });
    });

    after(async () => {
        await fetch(`http://127.0.0.1:8080/emulator/v1/projects/demo-test/databases/(default)/documents`, { method: 'DELETE' });
        fft.cleanup();
    });

    const mockReq = (body) => ({
        method: 'POST',
        headers: { authorization: 'Bearer dummy' },
        body
    });

    it('11. getDeviceBindChallenge: Generates and returns a valid challenge', async () => {
        admin.auth().verifyIdToken = async () => ({ uid: 'test-uid' });
        
        let sentData = null;
        let statusSet = 200;
        await db.collection('users').doc('2001CS10').set({ role: 'student' });
        const req = mockReq({ rollNumber: '2001CS10' });
        const res = { status: (c) => { statusSet = c; return res; }, send: (data) => { sentData = data; }, json: (data) => { sentData = data; } };
        
        await myFunctions.getDeviceBindChallenge(req, res);
        if (statusSet !== 200) console.log('ERROR:', sentData);
        assert.ok(sentData && sentData.nonce, 'Failed to get nonce');
    });

    it('12. bindDevice: Fails cleanly with invalid signature', async () => {
        const req = mockReq({ publicKey: 'invalid', signature: 'invalid', deviceId: '123' });
        let statusSet = 200;
        const res = { status: (c) => { statusSet = c; return res; }, send: () => {}, json: () => {} };
        
        await myFunctions.bindDevice(req, res);
        assert.strictEqual(statusSet, 400);
    });

    it('13. bindDevice: Fails cleanly when provided with an invalid signature', async () => {
        const req = mockReq({ publicKey: 'invalid', signature: 'invalid', deviceId: '123' });
        let statusSet = 200;
        const res = { status: (c) => { statusSet = c; return res; }, send: () => {}, json: () => {} };
        
        await myFunctions.bindDevice(req, res);
        assert.strictEqual(statusSet, 400);
    });

    it('14. verifyPlayIntegrity: Rejects if missing integrity token', async () => {
        const req = mockReq({ nonce: 'abc' });
        let statusSet = 200;
        const res = { status: (c) => { statusSet = c; return res; }, send: () => {}, json: () => {} };
        
        await myFunctions.verifyPlayIntegrity(req, res);
        assert.strictEqual(statusSet, 400);
    });
});
